package com.melodyon.shared.settings

import com.melodyon.shared.model.CollectionSort
import com.melodyon.shared.model.PlaylistSongSort
import com.melodyon.shared.model.SortDirection
import kotlinx.serialization.Serializable

@Serializable
data class SavedPlaylistSongSorting(
    val sort: PlaylistSongSort,
    val direction: SortDirection,
)

@Serializable
data class SavedPlaylistCollectionSorting(
    val sort: CollectionSort,
    val direction: SortDirection,
)
