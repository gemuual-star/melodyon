package com.melodyon.shared.ui.menu

import androidx.compose.runtime.Composable
import com.melodyon.shared.generated.resources.Res
import com.melodyon.shared.generated.resources.add_to_playlist
import com.melodyon.shared.generated.resources.adjust_audio_equalizer
import com.melodyon.shared.generated.resources.allow_song_to_be_liked_again
import com.melodyon.shared.generated.resources.block_artist
import com.melodyon.shared.generated.resources.dislike_song
import com.melodyon.shared.generated.resources.dislike_this_song_on_youtube_music
import com.melodyon.shared.generated.resources.equalizer
import com.melodyon.shared.generated.resources.playback_speed
import com.melodyon.shared.generated.resources.remove_dislike
import com.melodyon.shared.generated.resources.share
import com.melodyon.shared.generated.resources.start_radio
import com.melodyon.shared.generated.resources.suggest_to_host
import com.melodyon.shared.generated.resources.tempo_and_pitch
import com.melodyon.shared.generated.resources.view_album
import com.melodyon.shared.generated.resources.view_podcast
import com.melodyon.shared.ui.LocalIsTv
import com.melodyon.shared.ui.components.PlayerMenuAction
import com.melodyon.shared.ui.icons.MelodyonIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun songPrimaryActions(
    onDismiss: () -> Unit,
    isGuest: Boolean = false,
    onStartRadio: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onAltShare: (() -> Unit)? = null,
    playbackEnabled: Boolean = true,
): List<PlayerMenuAction> =
    listOfNotNull(
        onStartRadio?.takeUnless { isGuest }?.let {
            action(MelodyonIcons.Outlined.Sensors, stringResource(Res.string.start_radio), onDismiss, it).copy(enabled = playbackEnabled)
        },
        onAddToPlaylist?.let {
            PlayerMenuAction(MelodyonIcons.AutoMirrored.Outlined.PlaylistAdd, stringResource(Res.string.add_to_playlist), onClick = it)
        },
        onShare?.takeUnless { LocalIsTv.current }?.let {
            action(MelodyonIcons.Outlined.Share, stringResource(Res.string.share), onDismiss, it, onLongClick = onAltShare)
        },
    )

@Composable
fun dislikeMenuAction(
    isDisliked: Boolean,
    onDismiss: () -> Unit,
    onDislike: (() -> Unit)?,
    showDescription: Boolean = false,
): PlayerMenuAction? =
    onDislike?.let {
        action(
            if (isDisliked) MelodyonIcons.Filled.ThumbDown else MelodyonIcons.Outlined.ThumbDown,
            stringResource(if (isDisliked) Res.string.remove_dislike else Res.string.dislike_song),
            onDismiss,
            it,
            description =
                if (showDescription) {
                    stringResource(
                        if (isDisliked) Res.string.allow_song_to_be_liked_again else Res.string.dislike_this_song_on_youtube_music,
                    )
                } else {
                    null
                },
        )
    }

@Composable
fun blockArtistMenuAction(
    onDismiss: () -> Unit,
    onBlockArtist: (() -> Unit)?,
    description: String? = null,
): PlayerMenuAction? =
    onBlockArtist?.let {
        action(MelodyonIcons.Outlined.Block, stringResource(Res.string.block_artist), onDismiss, it, description)
    }

@Composable
fun albumNavigationMenuAction(
    onDismiss: () -> Unit,
    onGoToAlbum: (() -> Unit)?,
    description: String? = null,
    isPodcast: Boolean = false,
): PlayerMenuAction? =
    onGoToAlbum?.let {
        action(
            MelodyonIcons.Outlined.Album,
            stringResource(if (isPodcast) Res.string.view_podcast else Res.string.view_album),
            onDismiss,
            it,
            description,
        )
    }

@Composable
fun suggestToHostMenuAction(
    onDismiss: () -> Unit,
    onSuggest: () -> Unit,
): PlayerMenuAction =
    action(MelodyonIcons.AutoMirrored.Outlined.QueueMusic, stringResource(Res.string.suggest_to_host), onDismiss, onSuggest)

@Composable
fun equalizerMenuAction(
    onOpen: () -> Unit,
    showDescription: Boolean = true,
): PlayerMenuAction =
    PlayerMenuAction(
        MelodyonIcons.Outlined.BarChart,
        stringResource(Res.string.equalizer),
        if (showDescription) stringResource(Res.string.adjust_audio_equalizer) else null,
        onClick = onOpen,
    )

@Composable
fun tempoPitchMenuAction(
    varispeed: Boolean,
    onOpen: () -> Unit,
): PlayerMenuAction =
    PlayerMenuAction(
        MelodyonIcons.Outlined.Tune,
        stringResource(if (varispeed) Res.string.playback_speed else Res.string.tempo_and_pitch),
        onClick = onOpen,
    )
