/**
 * Melodyon Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.melodyon.android.ui.menu

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.melodyon.android.playback.PlayerConnection
import com.melodyon.android.ui.component.AdaptiveModalBottomSheet
import com.melodyon.shared.db.MusicDatabase
import com.melodyon.shared.model.SongItem
import com.melodyon.shared.ui.components.rememberModalSheetState
import com.melodyon.shared.ui.menu.SongDetailsContent
import com.melodyon.shared.ui.theme.ComponentShapes
import com.melodyon.shared.youtube.playback.PlaybackResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsSheet(
    song: SongItem?,
    playbackResource: PlaybackResource? = null,
    volume: Float? = null,
    onDismiss: () -> Unit,
) {
    val database: MusicDatabase = koinInject()
    val playerConnection: PlayerConnection = koinInject()
    val currentResource by playerConnection.currentPlaybackResource.collectAsState()
    val currentVolume by playerConnection.volume.collectAsState()
    val sheetState = rememberModalSheetState(SheetValue.Hidden)
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = ComponentShapes.BottomSheetShape) {
        SongDetailsContent(song, database, playbackResource ?: currentResource, volume ?: currentVolume)
    }
}
