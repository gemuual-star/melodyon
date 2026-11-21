package com.melodyon.shared.soundcloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SoundCloudHlsTest {
    @Test
    fun segmentsResolveAbsoluteRootAndRelativeUrls() {
        val body = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            https://cf-hls-media.sndcdn.com/media/0/1/a.mp3?x=1
            #EXTINF:10.0,
            /media/1/2/b.mp3
            #EXTINF:5.0,
            c.mp3
            #EXT-X-ENDLIST
        """.trimIndent()
        assertEquals(
            listOf(
                "https://cf-hls-media.sndcdn.com/media/0/1/a.mp3?x=1",
                "https://cf-hls-media.sndcdn.com/media/1/2/b.mp3",
                "https://cf-hls-media.sndcdn.com/playlist/abc/c.mp3",
            ),
            parseHlsSegments("https://cf-hls-media.sndcdn.com/playlist/abc/playlist.m3u8?sig=1", body),
        )
    }

    @Test
    fun initSegmentComesFirst() {
        val body = "#EXTM3U\n#EXT-X-MAP:URI=\"https://h.example/init.mp4?p=1\"\n#EXTINF:10.0,\nhttps://h.example/s0.m4s"
        assertEquals(listOf("https://h.example/init.mp4?p=1", "https://h.example/s0.m4s"), parseHlsSegments("https://h.example/playlist.m3u8", body))
    }

    @Test
    fun skipsAbrAndEncryptedTranscodings() {
        val tr = { preset: String, proto: String, mime: String ->
            """{"url":"https://api-v2.soundcloud.com/media/$preset$proto","preset":"$preset","snipped":false,"format":{"protocol":"$proto","mime_type":"$mime"}}"""
        }
        val body = """{"id":9,"title":"t","user":{"username":"u"},"media":{"transcodings":[
            ${tr("abr_sq", "hls", "audio/mpegurl")},${tr("aac_96k", "hls", "audio/mp4; codecs=\\"mp4a.40.2\\"")},
            ${tr("aac_160k", "hls", "audio/mp4; codecs=\\"mp4a.40.2\\"")}]}}"""
        assertEquals("aac_160k", parseTrack(body).bestTranscoding()?.preset)
        val drm = """{"id":10,"title":"t","user":{"username":"u"},"media":{"transcodings":[${tr("aac_160k", "cbc-encrypted-hls", "audio/mp4")}]}}"""
        assertEquals(null, parseTrack(drm).bestTranscoding())
    }

    @Test
    fun encryptedPlaylistIsRejected() {
        assertFailsWith<SoundCloudException> {
            parseHlsSegments("https://x/p.m3u8", "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\"\na.mp3")
        }
    }

    @Test
    fun audioMimeFollowsTranscoding() {
        assertEquals("audio/mpeg", soundCloudAudioMime(ResolvedStream("https://a", true, "audio/mpeg")))
        assertEquals("audio/ogg", soundCloudAudioMime(ResolvedStream("https://a", true, "audio/ogg; codecs=\"opus\"")))
        assertEquals("audio/mp4", soundCloudAudioMime(ResolvedStream("https://a", true, "audio/mp4; codecs=\"mp4a.40.2\"")))
    }
}
