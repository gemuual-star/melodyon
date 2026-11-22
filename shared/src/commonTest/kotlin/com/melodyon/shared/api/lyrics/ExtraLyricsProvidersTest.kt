package com.melodyon.shared.api.lyrics

import com.melodyon.shared.model.LyricsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtraLyricsProvidersTest {
    private fun candidate(id: String, title: String, artist: String, duration: Long) = LyricsCandidate(id, title, listOf(artist), duration)

    @Test
    fun candidateMustBeTheSameRecording() {
        val hits =
            listOf(
                candidate("live", "Get Lucky (Live)", "Daft Punk", 400),
                candidate("cover", "Get Lucky", "Some Cover Band", 370),
                candidate("right", "Get Lucky (feat. Pharrell Williams)", "Daft Punk", 369),
                candidate("other", "Lucky", "Daft Punk", 369),
            )
        assertEquals("right", pickLyricsCandidate(hits, "Get Lucky [Official Audio]", "Daft Punk", 368)?.id)
        // Nothing within the duration tolerance: no lyrics rather than wrong lyrics.
        assertNull(pickLyricsCandidate(hits, "Get Lucky", "Daft Punk", 200))
        // Unknown duration: the artist has to match.
        assertEquals("live", pickLyricsCandidate(hits.take(2), "Get Lucky", "Daft Punk", 0)?.id)
        assertNull(pickLyricsCandidate(hits.drop(1).take(1), "Get Lucky", "Daft Punk", 0))
        assertNull(pickLyricsCandidate(hits, "", "Daft Punk", 369))
    }

    @Test
    fun netEaseResponsesAreParsed() {
        val search = """{"result":{"songs":[{"id":28285910,"name":"Get Lucky","duration":369684,"artists":[{"name":"Daft Punk"},{"name":"Pharrell Williams"}]},{"name":"no id"}]},"code":200}"""
        assertEquals(listOf(LyricsCandidate("28285910", "Get Lucky", listOf("Daft Punk", "Pharrell Williams"), 369)), parseNetEaseSearch(search))
        assertEquals(emptyList(), parseNetEaseSearch("""{"result":{},"code":200}"""))
        val lyric = """{"lrc":{"version":44,"lyric":"{\"t\":0,\"c\":[{\"tx\":\"credits\"}]}\n[00:01.00]First\n[00:03.50]Second"},"code":200}"""
        assertEquals("[00:01.00]First\n[00:03.50]Second", parseNetEaseLyric(lyric))
        assertNull(parseNetEaseLyric("""{"lrc":{"lyric":""},"code":200}"""))
        assertNull(parseNetEaseLyric("""{"nolyric":true,"code":200}"""))
    }

    @Test
    fun qqMusicResponsesAreParsed() {
        val search = """{"code":0,"req_1":{"code":0,"data":{"body":{"song":{"list":[{"mid":"004fDVvw1DxZeE","name":"Get Lucky","interval":369,"singer":[{"name":"Daft Punk"}]}]}}}}}"""
        assertEquals(listOf(LyricsCandidate("004fDVvw1DxZeE", "Get Lucky", listOf("Daft Punk"), 369)), parseQQMusicSearch(search))
        assertEquals(emptyList(), parseQQMusicSearch("""{"code":0,"req_1":{"code":500}}"""))
        assertEquals("[00:01.00]We're up & down", parseQQMusicLyric("""{"retcode":0,"lyric":"[00:01.00]We&apos;re up &amp; down"}"""))
        assertNull(parseQQMusicLyric("""{"retcode":-1901}"""))
    }

    @Test
    fun lyricsOvhResponseIsParsed() {
        assertEquals("One\nTwo", parseLyricsOvh("""{"lyrics":"One\r\nTwo"}"""))
        assertNull(parseLyricsOvh("""{"error":"No lyrics found"}"""))
    }

    @Test
    fun netEaseProviderReturnsSyncedLyricsForTheMatchingSong() =
        runTest {
            val requests = mutableListOf<String>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        requests += request.url.encodedPath
                        if (request.url.encodedPath.endsWith("/search/get")) {
                            respond("""{"result":{"songs":[{"id":7,"name":"Track","duration":180000,"artists":[{"name":"Artist"}]}]}}""")
                        } else {
                            assertEquals("7", request.url.parameters["id"])
                            respond("""{"lrc":{"lyric":"[00:01.00]Line one\n[00:02.00]Line two"}}""")
                        }
                    },
                )
            try {
                val provider = NetEaseLyricsProvider(client)
                val document = MlxParser.parse(assertNotNull(provider.getLyrics("Track (Official Video)", "Artist", 181)))
                assertEquals(listOf("Line one", "Line two"), document.lines.map { it.text })
                assertEquals("7", document.meta.sourceId)
                assertNull(provider.getLyrics("Different", "Artist", 181))
                assertEquals(listOf("/api/search/get", "/api/song/lyric", "/api/search/get"), requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun newSourcesAreSelectableAndTriedBeforeYouTubeFallbacks() {
        val added = listOf(LyricsProvider.NETEASE, LyricsProvider.QQMUSIC, LyricsProvider.LYRICSOVH)
        assertTrue(LyricsProvider.userConfigurable.containsAll(added))
        assertEquals(added, LyricsProvider.userConfigurable.takeLast(3))
        val order = LyricsProvider.preferredProvidersForFetch(LyricsProvider.userConfigurable, LyricsProvider.userConfigurable.toSet())
        assertTrue(order.indexOf(LyricsProvider.LYRICSOVH) < order.indexOf(LyricsProvider.YOUTUBE))
        assertEquals(LyricsProvider.NETEASE, LyricsProvider.parseTokenFromSettings("NetEase"))
        assertEquals(LyricsProvider.QQMUSIC, LyricsProvider.parseTokenFromSettings("QQMUSIC"))
    }
}
