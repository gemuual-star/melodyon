package com.melodyon.android.listentogether

import com.melodyon.shared.listentogether.ListenTogetherTrackResolver
import com.melodyon.shared.listentogether.toSongItem
import com.melodyon.shared.model.SongItem

internal fun androidListenTogetherTrackResolver(
    metadata: suspend (String) -> SongItem?,
    fallbackArtist: suspend () -> String,
): ListenTogetherTrackResolver =
    ListenTogetherTrackResolver { track ->
        metadata(track.id) ?: track.toSongItem(
            fallbackArtist = fallbackArtist(),
            omitNonPositiveDuration = true,
        )
    }
