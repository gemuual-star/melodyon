/**
 * Melodyon Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.melodyon.desktop.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.melodyon.desktop.deviceconnect.DesktopDeviceConnect
import com.melodyon.desktop.state.app.AppState
import com.melodyon.desktop.state.app.UiState
import com.melodyon.desktop.state.content.ArtistState
import com.melodyon.desktop.state.content.BrowseState
import com.melodyon.desktop.state.content.LibraryContentState
import com.melodyon.desktop.state.content.PlaylistAlbumState
import com.melodyon.desktop.state.content.PodcastState
import com.melodyon.desktop.state.content.SearchState
import com.melodyon.desktop.state.playback.ListenTogetherPlaybackRequest
import com.melodyon.desktop.state.playback.ListenTogetherState
import com.melodyon.desktop.state.playback.LyricsState
import com.melodyon.desktop.state.playback.PlayerState
import com.melodyon.desktop.ui.screens.AboutArtistScreenHost
import com.melodyon.desktop.ui.screens.AlbumScreen
import com.melodyon.desktop.ui.screens.ArtistScreenHost
import com.melodyon.desktop.ui.screens.BrowseScreen
import com.melodyon.desktop.ui.screens.HistoryScreen
import com.melodyon.desktop.ui.screens.HomeScreen
import com.melodyon.desktop.ui.screens.LibraryScreen
import com.melodyon.desktop.ui.screens.ListenTogetherScreen
import com.melodyon.desktop.ui.screens.LocalPlaylistScreen
import com.melodyon.desktop.ui.screens.PlaylistScreen
import com.melodyon.desktop.ui.screens.PodcastScreen
import com.melodyon.desktop.ui.screens.SearchResultsList
import com.melodyon.desktop.ui.screens.StatsScreen
import com.melodyon.shared.db.isHistoryPlaylistId
import com.melodyon.shared.deviceconnect.DeviceConnectPairingHost
import com.melodyon.shared.generated.resources.Res
import com.melodyon.shared.generated.resources.cancel
import com.melodyon.shared.generated.resources.listen_together_guest_choice_message
import com.melodyon.shared.generated.resources.listen_together_guest_choice_title
import com.melodyon.shared.generated.resources.listen_together_guest_queue
import com.melodyon.shared.generated.resources.listen_together_guest_solo
import com.melodyon.shared.ui.components.AlertDialog
import com.melodyon.shared.ui.components.CollectionHeaderNavigation
import com.melodyon.shared.ui.components.LoadingScreen
import com.melodyon.shared.ui.components.PopupContent
import com.melodyon.shared.ui.components.UnavailableSongSnackbar
import com.melodyon.shared.ui.components.lyrics.LyricsTranslationSnackbar
import com.melodyon.shared.ui.icons.MelodyonIcons
import com.melodyon.shared.ui.listentogether.ListenTogetherBlockedUsersScreen
import com.melodyon.shared.ui.listentogether.ListenTogetherGlobalEvents
import com.melodyon.shared.ui.menu.ContentUndoHintsSnackbar
import com.melodyon.shared.ui.menu.HiddenHomeItemsSnackbar
import com.melodyon.shared.ui.menu.settingsPage
import com.melodyon.shared.ui.navigation.LocalNavigator
import com.melodyon.shared.ui.navigation.NavigationTransition
import com.melodyon.shared.ui.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AppContentHost(
    route: Screen,
    colorScheme: ColorScheme,
    libraryGridState: LazyGridState,
    onLibraryScrollStateChange: (LazyGridState) -> Unit,
    modifier: Modifier = Modifier,
    onSearchChange: (String) -> Unit = {},
) {
    val libraryNeeded = route == Screen.Library || (route is Screen.Playlist && isHistoryPlaylistId(route.id))
    if (libraryNeeded && !PlayerState.isExpanded && !UiState.videoFullscreenObscuresPlayer) {
        LifecycleStartEffect(Unit) {
            LibraryContentState.setActive(true)
            onStopOrDispose { LibraryContentState.setActive(false) }
        }
    }
    val navigator = LocalNavigator.current
    HiddenHomeItemsSnackbar(AppState.snackbarHostState)
    UnavailableSongSnackbar(AppState.snackbarHostState)
    ContentUndoHintsSnackbar(AppState.snackbarHostState) { page ->
        PlayerState.closeFullscreenPlayer()
        val settingsPage = page.settingsPage
        if (settingsPage == null) {
            UiState.showSettings = false
            navigator.navigate(Screen.ListenTogetherBlockedUsers)
        } else {
            UiState.selectedSettingsPage = settingsPage
            UiState.showSettings = true
        }
    }
    LyricsTranslationSnackbar(AppState.snackbarHostState, LyricsState.lyricsTranslationFailures)
    ListenTogetherGlobalEvents(ListenTogetherState.listenTogetherManager) { AppState.snackbarHostState.showSnackbar(it) }
    val deviceConnectState by DesktopDeviceConnect.manager.state.collectAsState()
    DeviceConnectPairingHost(
        state = deviceConnectState,
        onApprovePairing = DesktopDeviceConnect.manager::approvePairing,
        onRejectPairing = DesktopDeviceConnect.manager::rejectPairing,
    )
    var pendingPlaybackRequest by remember { mutableStateOf<ListenTogetherPlaybackRequest?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        PlayerState.listenTogetherPlaybackRequests.collect { pendingPlaybackRequest = it }
    }
    LaunchedEffect(route) { LayoutUtils.syncContentState(route) }
    val contentShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.background(colorScheme.surface, contentShape).clip(contentShape),
    ) {
        key(LayoutUtils.contentRefreshKey) {
            NavigationTransition(route = route) { destination ->
                when (destination) {
                    is Screen.Artist -> {
                        if (ArtistState.selectedArtistId == destination.id ||
                            ArtistState.loadedArtistId == destination.id
                        ) {
                            ArtistScreenHost()
                        } else {
                            CollectionHeaderNavigation({ navigator.goBack() }) { LoadingScreen() }
                        }
                    }

                    is Screen.AboutArtist -> {
                        if (ArtistState.selectedArtistId == destination.id) {
                            AboutArtistScreenHost(destination.id, destination.name, destination.artist)
                        } else {
                            LoadingScreen()
                        }
                    }

                    is Screen.Playlist -> {
                        if (PlaylistAlbumState.selectedPlaylistId == destination.id) {
                            PlaylistScreen(colorScheme)
                        } else {
                            CollectionHeaderNavigation({ navigator.goBack() }) { LoadingScreen() }
                        }
                    }

                    is Screen.Album -> {
                        AlbumScreen(destination.id, colorScheme)
                    }

                    is Screen.HomeSection -> {
                        BrowseScreen(colorScheme, destination)
                    }

                    is Screen.Browse -> {
                        if (
                            BrowseState.selectedBrowseId == destination.browseId &&
                            BrowseState.selectedBrowseParams == destination.params
                        ) {
                            BrowseScreen(colorScheme)
                        } else {
                            CollectionHeaderNavigation({ navigator.goBack() }) { LoadingScreen() }
                        }
                    }

                    is Screen.Podcast -> {
                        if (PodcastState.selectedPodcastId == destination.id) PodcastScreen(colorScheme) else LoadingScreen()
                    }

                    Screen.Search -> {
                        SearchResultsList(
                            colorScheme = colorScheme,
                            onBack = {
                                SearchState.reset()
                                onSearchChange("")
                                navigator.goBack()
                            },
                        )
                    }

                    is Screen.AutoPlaylist -> {
                        LocalPlaylistScreen(destination.type)
                    }

                    Screen.ListenTogetherBlockedUsers -> {
                        ListenTogetherBlockedUsersScreen(
                            manager = ListenTogetherState.listenTogetherManager,
                            onNavigateBack = { navigator.goBack() },
                        )
                    }

                    Screen.Library, Screen.Statistics, Screen.ListenTogether -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = 1400.dp).fillMaxSize()) {
                                when (destination) {
                                    Screen.Library -> {
                                        LibraryScreen(
                                            LibraryContentState.librarySections,
                                            colorScheme,
                                            libraryGridState,
                                            onScrollStateChange = onLibraryScrollStateChange,
                                        )
                                    }

                                    Screen.Statistics -> {
                                        StatsScreen()
                                    }

                                    else -> {
                                        ListenTogetherScreen(ListenTogetherState.listenTogetherManager)
                                    }
                                }
                            }
                        }
                    }

                    Screen.History -> {
                        HistoryScreen()
                    }

                    else -> {
                        HomeScreen()
                    }
                }
            }
        }
        PopupContent(pendingPlaybackRequest) { request ->
            AlertDialog(
                onDismissRequest = { pendingPlaybackRequest = null },
                icon = {
                    Icon(
                        imageVector = MelodyonIcons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = { Text(stringResource(Res.string.listen_together_guest_choice_title)) },
                text = { Text(stringResource(Res.string.listen_together_guest_choice_message)) },
                confirmButton = {
                    Button(
                        onClick = {
                            ListenTogetherState.listenTogetherManager.suggestTrack(request.song)
                            pendingPlaybackRequest = null
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(Res.string.listen_together_guest_queue))
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pendingPlaybackRequest = null }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                pendingPlaybackRequest = null
                                scope.launch {
                                    ListenTogetherState.listenTogetherManager.leaveRoom()
                                    withTimeoutOrNull(3000.milliseconds) {
                                        ListenTogetherState.listenTogetherManager.isInRoom.first { !it }
                                    }
                                    if (request.queue.isNotEmpty()) {
                                        PlayerState.playTrack(request.song, request.queue)
                                    } else {
                                        PlayerState.playTrack(request.song)
                                    }
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(Res.string.listen_together_guest_solo))
                        }
                    }
                },
            )
        }
    }
}
