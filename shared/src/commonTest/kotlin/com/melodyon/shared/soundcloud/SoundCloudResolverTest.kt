package com.melodyon.shared.soundcloud

import com.melodyon.shared.model.SongItem
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.melodyon.shared.youtube.playback.PlaybackResource
import com.melodyon.shared.youtube.playback.StreamResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SoundCloudResolverTest {
    private class FakeYouTube : StreamResolver {
        val calls = mutableListOf<String>()
        override suspend fun resolve(videoId: String, hints: ContentHints, audioQuality: AudioQuality, sourceSong: SongItem?) =
            PlaybackResource(videoId, "https://yt/$videoId", emptyMap(), null, null).also { calls += videoId }
        override suspend fun refresh(videoId: String, hints: ContentHints, audioQuality: AudioQuality, sourceSong: SongItem?) =
            resolve(videoId, hints, audioQuality, sourceSong)
    }

    @Test
    fun routesScIdsToSoundCloudAndOthersToYouTube() = runTest {
        val yt = FakeYouTube()
        val looked = mutableListOf<Long>()
        val resolver = SoundCloudStreamResolver(yt) { id -> looked += id; ResolvedStream("https://cf-media.sndcdn.com/$id.mp3", isHls = false, mimeType = "audio/mpeg") }
        val sc = resolver.resolve("sc:77")
        assertEquals("https://cf-media.sndcdn.com/77.mp3", sc.url)
        assertEquals("sc:77", sc.videoId)
        assertEquals("audio/mpeg", sc.mimeType)
        assertFalse(sc.requireBoundedRange)
        assertEquals(listOf(77L), looked)
        assertEquals("https://yt/abcdefghijk", resolver.resolve("abcdefghijk").url)
        assertEquals(listOf("abcdefghijk"), yt.calls)
    }

    @Test
    fun hlsStreamsAreMarkedAsPlaylists() = runTest {
        val resolver = SoundCloudStreamResolver(FakeYouTube()) { ResolvedStream("https://x/playlist.m3u8", isHls = true, mimeType = "audio/mpeg") }
        assertEquals("application/x-mpegurl", resolver.refresh("sc:1").mimeType)
    }

    @Test
    fun trackMapsToSongItem() {
        val track = parseTrack("""{"id":9,"title":"Song","duration":185000,"genre":"","artwork_url":"https://i1.sndcdn.com/a-large.jpg",
            "user":{"id":2,"username":"dj"},"media":{"transcodings":[]}}""")
        val song = track.toSongItem()
        assertEquals("sc:9", song.id)
        assertEquals(185L, song.duration)
        assertEquals("dj", song.artists.single().name)
        assertNull(song.artists.single().id)
        assertNull(song.album)
        assertNull(song.genre)
        assertEquals("https://i1.sndcdn.com/a-t500x500.jpg", song.thumbnail)
    }
}
