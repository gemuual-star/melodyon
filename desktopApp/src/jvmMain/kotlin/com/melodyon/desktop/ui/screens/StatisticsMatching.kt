package com.melodyon.desktop.ui.screens

import com.melodyon.desktop.history.AlbumStat
import com.melodyon.shared.model.AlbumItem
import com.melodyon.shared.ui.statistics.matchingStatisticsAlbum

internal fun statisticsArtistName(id: String): String = id.removePrefix("artist:").substringAfter(':')

internal fun List<AlbumItem>.matchingStatisticsAlbum(album: AlbumStat): AlbumItem? = matchingStatisticsAlbum(album.name, album.artists)
