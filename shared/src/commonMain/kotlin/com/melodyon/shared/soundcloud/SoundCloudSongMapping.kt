package com.melodyon.shared.soundcloud

import com.melodyon.shared.model.ArtistTiny
import com.melodyon.shared.model.SongItem

/**
 * SoundCloud tracks become ordinary [SongItem]s with an `sc:` id, so queue, library,
 * local playlists, downloads and lyrics lookup (by title/artist) work unchanged.
 * Artist id is null: SoundCloud uploaders have no YouTube Music artist page.
 */
fun ScTrack.toSongItem(): SongItem =
    SongItem(
        id = songId,
        title = title,
        thumbnail = largeArtworkUrl,
        artists = listOf(ArtistTiny(id = null, name = artistName, thumbnail = user.avatarUrl)),
        album = null,
        duration = (fullDuration ?: duration) / 1000,
        genre = genre?.takeIf { it.isNotBlank() },
    )
