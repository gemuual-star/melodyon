package com.melodyon.android.viewmodels

import com.melodyon.shared.api.PlaylistSongAlreadyPresentException
import com.melodyon.shared.db.MusicDatabase
import com.melodyon.shared.db.entities.Playlist
import com.melodyon.shared.model.SongItem
import com.melodyon.shared.playlist.addSongToStoredPlaylist
import com.melodyon.shared.state.GlobalYouTubeRepository
import java.util.Collections

internal enum class AddSongToPlaylistResult {
    Added,
    AlreadyPresent,
    InProgress,
}

internal suspend fun addSongToPlaylist(
    database: MusicDatabase,
    playlist: Playlist,
    song: SongItem,
    allowDuplicate: Boolean = false,
): AddSongToPlaylistResult =
    playlistAddOperations.run(playlist.id, song.id) {
        val result =
            addSongToStoredPlaylist(
                database,
                GlobalYouTubeRepository.instance,
                playlist.id,
                song,
                allowDuplicate,
            )
        val error = result.exceptionOrNull()
        if (error is PlaylistSongAlreadyPresentException) return@run AddSongToPlaylistResult.AlreadyPresent
        if (error != null) throw error
        AddSongToPlaylistResult.Added
    }

private val playlistAddOperations = PlaylistAddOperations()

internal class PlaylistAddOperations {
    private val active = Collections.synchronizedSet(mutableSetOf<Pair<String, String>>())

    suspend fun run(
        playlistId: String,
        songId: String,
        add: suspend () -> AddSongToPlaylistResult,
    ): AddSongToPlaylistResult {
        val key = playlistId to songId
        if (!active.add(key)) return AddSongToPlaylistResult.InProgress
        try {
            return add()
        } finally {
            active.remove(key)
        }
    }
}
