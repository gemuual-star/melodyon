package com.melodyon.android.playback

import com.melodyon.shared.model.SongItem
import com.melodyon.shared.playback.PlaybackErrorContext
import com.melodyon.shared.youtube.playback.PlaybackResource

internal object PlaybackErrorReporter {
    fun setActivePlayback(
        song: SongItem,
        resource: PlaybackResource?,
    ) = Unit

    fun clearActivePlayback() = Unit

    fun report(
        error: Throwable,
        context: PlaybackErrorContext,
    ) = Unit
}
