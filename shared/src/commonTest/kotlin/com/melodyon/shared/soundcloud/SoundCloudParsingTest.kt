package com.melodyon.shared.soundcloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundCloudParsingTest {
    private fun track(
        id: Long,
        policy: String = "ALLOW",
        transcodings: String = PROGRESSIVE + "," + HLS,
    ) = """{"id":$id,"kind":"track","title":"T$id","duration":1000,"policy":"$policy","streamable":true,
        "artwork_url":"https://i1.sndcdn.com/artworks-x-large.jpg","user":{"id":1,"username":"uploader"},
        "media":{"transcodings":[$transcodings]}}"""

    @Test
    fun idsRoundTripAndRejectGarbage() {
        assertEquals("sc:42", soundCloudSongId(42))
        assertEquals(42L, soundCloudTrackId("sc:42"))
        assertTrue(isSoundCloudSongId("sc:42"))
        assertFalse(isSoundCloudSongId("dQw4w9WgXcQ"))
        assertFalse(isSoundCloudSongId("sc:abc"))
        assertNull(soundCloudTrackId("sc:-1"))
    }

    @Test
    fun searchPageHidesPreviewsAndBlockedTracks() {
        val body = """{"collection":[${track(1)},${track(2, policy = "SNIP")},${track(3, policy = "BLOCK")},
            ${track(4, transcodings = SNIPPED)}],"next_href":"https://api-v2.soundcloud.com/search/tracks?offset=30"}"""
        val page = parseSearchPage(body)
        assertEquals(listOf(1L), page.collection.map { it.id })
        assertEquals("https://api-v2.soundcloud.com/search/tracks?offset=30", page.nextHref)
    }

    @Test
    fun prefersProgressiveMp3OverHls() {
        val t = parseTrack(track(5, transcodings = HLS + "," + PROGRESSIVE))
        assertEquals("progressive", t.bestTranscoding()?.format?.protocol)
        val hlsOnly = parseTrack(track(6, transcodings = HLS))
        assertEquals("hls", hlsOnly.bestTranscoding()?.format?.protocol)
    }

    @Test
    fun artworkAndArtistMapping() {
        val t = parseTrack(track(7))
        assertEquals("https://i1.sndcdn.com/artworks-x-t500x500.jpg", t.largeArtworkUrl)
        assertEquals("uploader", t.artistName)
        val withPublisher = parseTrack(track(8).replace("\"user\"", "\"publisher_metadata\":{\"artist\":\"Real Artist\"},\"user\""))
        assertEquals("Real Artist", withPublisher.artistName)
    }

    @Test
    fun streamUrlMustBeHttps() {
        assertEquals("https://cf-media.sndcdn.com/a.mp3", parseStreamUrl("""{"url":"https://cf-media.sndcdn.com/a.mp3"}"""))
        assertNull(parseStreamUrl("""{"url":"http://insecure"}"""))
    }

    @Test
    fun clientIdExtraction() {
        val html = """<script crossorigin src="https://a-v2.sndcdn.com/assets/0-abc.js"></script>
            <script crossorigin src="https://a-v2.sndcdn.com/assets/49-def.js"></script>"""
        assertEquals(listOf("https://a-v2.sndcdn.com/assets/0-abc.js", "https://a-v2.sndcdn.com/assets/49-def.js"), SoundCloudClientId.scriptUrls(html))
        assertEquals("AbCdEfGhIjKlMnOpQrStUvWxYz012345", SoundCloudClientId.extract("""x={client_id:"AbCdEfGhIjKlMnOpQrStUvWxYz012345",env:"p"}"""))
        assertNull(SoundCloudClientId.extract("no id here"))
    }

    private companion object {
        const val PROGRESSIVE = """{"url":"https://api-v2.soundcloud.com/media/p","preset":"mp3_1_0","snipped":false,"format":{"protocol":"progressive","mime_type":"audio/mpeg"}}"""
        const val HLS = """{"url":"https://api-v2.soundcloud.com/media/h","preset":"mp3_1_0","snipped":false,"format":{"protocol":"hls","mime_type":"audio/mpeg"}}"""
        const val SNIPPED = """{"url":"https://api-v2.soundcloud.com/media/s","preset":"mp3_1_0","snipped":true,"format":{"protocol":"progressive","mime_type":"audio/mpeg"}}"""
    }
}
