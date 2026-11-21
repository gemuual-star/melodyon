package com.melodyon.desktop.state.content

import com.melodyon.shared.db.entities.PlaylistEntity
import com.melodyon.shared.db.hasCompleteStoredSongs
import com.melodyon.shared.model.SongItem
import kotlinx.coroutines.Job

internal fun hasCompleteStoredPlaylist(
    playlist: PlaylistEntity,
    songs: List<SongItem>,
): Boolean = playlist.hasCompleteStoredSongs(songs)

internal suspend fun awaitCompletePlaylist(
    loadJob: Job?,
    hasMore: () -> Boolean,
): Boolean {
    if (hasMore()) loadJob?.join()
    return !hasMore()
}
