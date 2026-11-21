package com.melodyon.desktop.state.playback

import com.melodyon.shared.model.RepeatMode
import com.melodyon.shared.playback.MusicQueueSnapshot
import com.melodyon.shared.playback.StoredQueueSnapshotSerializer
import kotlinx.serialization.Serializable

@Serializable
data class PersistedPlaybackState(
    @Serializable(with = StoredQueueSnapshotSerializer::class)
    val queue: MusicQueueSnapshot,
    val positionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val revision: String? = null,
)
