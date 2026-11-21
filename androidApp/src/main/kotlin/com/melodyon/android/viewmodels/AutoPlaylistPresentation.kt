package com.melodyon.android.viewmodels

import com.melodyon.shared.db.entities.CachedAudioEntity
import com.melodyon.shared.model.CollectionMeta
import com.melodyon.shared.model.CollectionSort
import com.melodyon.shared.model.PlaylistSongSort
import com.melodyon.shared.model.SongItem
import com.melodyon.shared.model.SortDirection
import com.melodyon.shared.model.filterForPlaylistSearch
import com.melodyon.shared.model.isReordering
import com.melodyon.shared.model.presentCollection
import com.melodyon.shared.model.sortedForLikedPlaylist
import com.melodyon.shared.model.sortedForPlaylist
import com.melodyon.shared.model.sortedForTopPlaylist
import com.melodyon.shared.viewmodels.DownloadPlaylistFilterState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AutoPlaylistPresentationInput(
    val type: String,
    val songs: List<SongItem>,
    val metadataRevision: Long,
    val collectionEntries: List<CachedAudioEntity>,
    val query: String,
    val collectionSort: CollectionSort,
    val collectionDirection: SortDirection,
    val playCounts: Map<String, Int>,
    val playlistSort: PlaylistSongSort,
    val playlistDirection: SortDirection,
    val topDirection: SortDirection,
    val reorder: Boolean = false,
    val likedSongRecency: List<String> = emptyList(),
    val listeningTimes: Map<String, Long> = emptyMap(),
    val downloadFilter: DownloadPlaylistFilterState = DownloadPlaylistFilterState(),
)

internal fun AutoPlaylistPresentationInput.withSongSorting(
    locked: Boolean,
    listeningTimes: () -> Map<String, Long>,
): AutoPlaylistPresentationInput =
    copy(
        reorder = playlistSort.isReordering(locked, type == "liked"),
        listeningTimes = if (playlistSort == PlaylistSongSort.LISTENING_TIME) listeningTimes() else emptyMap(),
    )

internal suspend fun prepareAutoPlaylistSongs(
    input: AutoPlaylistPresentationInput,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<SongItem> =
    withContext(dispatcher) {
        with(input) {
            when (type) {
                "downloaded", "cached" -> {
                    val entries = collectionEntries.associateBy(CachedAudioEntity::songId)
                    val filtered = if (type == "downloaded") downloadFilter.apply(songs) else songs
                    filtered.presentCollection(query, collectionSort, collectionDirection) { song ->
                        entries[song.id]?.let { entry ->
                            CollectionMeta(
                                timestamp = entry.cachedAt,
                                fileSize = entry.fileSize,
                                lastAccessed = entry.lastAccessedAt,
                                playCount = playCounts[song.id] ?: 0,
                            )
                        }
                    }
                }

                "top100" -> {
                    songs.sortedForTopPlaylist(topDirection).filterForPlaylistSearch(query)
                }

                "liked" -> {
                    val ordered =
                        if (reorder) {
                            songs
                        } else {
                            songs.sortedForLikedPlaylist(
                                playlistSort,
                                playlistDirection,
                                likedSongRecency,
                                listeningTimes,
                            )
                        }
                    ordered.filterForPlaylistSearch(query)
                }

                "uploaded" -> {
                    songs.sortedForPlaylist(playlistSort, playlistDirection, listeningTimes = listeningTimes).filterForPlaylistSearch(query)
                }

                else -> {
                    songs.filterForPlaylistSearch(query)
                }
            }
        }
    }
