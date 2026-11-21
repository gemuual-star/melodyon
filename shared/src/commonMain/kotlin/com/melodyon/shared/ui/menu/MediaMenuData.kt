package com.melodyon.shared.ui.menu

import com.melodyon.shared.api.playlistSongs
import com.melodyon.shared.model.AlbumItem
import com.melodyon.shared.model.ArtistItem
import com.melodyon.shared.model.EpisodeItem
import com.melodyon.shared.model.PlaylistItem
import com.melodyon.shared.model.PodcastItem
import com.melodyon.shared.model.SongItem
import com.melodyon.shared.model.YTItem
import com.melodyon.shared.model.toSongItem
import com.melodyon.shared.state.GlobalYouTubeRepository

suspend fun loadMediaItemSongs(item: YTItem): List<SongItem> {
    val repository = GlobalYouTubeRepository.instance
    return when (item) {
        is SongItem -> {
            listOf(item)
        }

        is EpisodeItem -> {
            listOf(item.toSongItem())
        }

        is AlbumItem -> {
            repository.getAlbum(item.id)?.songs.orEmpty()
        }

        is ArtistItem -> {
            repository.getArtistSongs(item.id)
        }

        is PlaylistItem -> {
            repository.getPlaylist(item.id).playlistSongs()
        }

        is PodcastItem -> {
            // Every page, so playback covers the whole podcast and can start from its first episode.
            buildList {
                repository.getPodcastPages(item.id).collect { page -> page.episodes.mapTo(this) { it.toSongItem(page.podcast.title) } }
            }
        }

        else -> {
            emptyList()
        }
    }
}
