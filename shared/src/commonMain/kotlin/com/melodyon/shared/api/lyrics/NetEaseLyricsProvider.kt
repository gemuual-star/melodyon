package com.melodyon.shared.api.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

/** NetEase Cloud Music: large catalogue of line-synced LRC, no account needed. */
class NetEaseLyricsProvider(
    private val httpClient: HttpClient,
) {
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSec: Long,
    ): String? {
        val query = listOf(lyricsSearchTitle(title), artist.trim()).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val search =
            httpClient
                .prepareGet("https://music.163.com/api/search/get") {
                    expectMissingLyrics()
                    header(HttpHeaders.Referrer, REFERER)
                    header(HttpHeaders.UserAgent, LYRICS_BROWSER_USER_AGENT)
                    parameter("s", query)
                    parameter("type", 1)
                    parameter("limit", 10)
                    parameter("offset", 0)
                }.readLyricsText() ?: return null
        val song = pickLyricsCandidate(parseNetEaseSearch(search), title, artist, durationSec) ?: return null
        val body =
            httpClient
                .prepareGet("https://music.163.com/api/song/lyric") {
                    expectMissingLyrics()
                    header(HttpHeaders.Referrer, REFERER)
                    header(HttpHeaders.UserAgent, LYRICS_BROWSER_USER_AGENT)
                    parameter("id", song.id)
                    parameter("lv", 1)
                    parameter("kv", 1)
                    parameter("tv", -1)
                }.readLyricsText() ?: return null
        val lrc = parseNetEaseLyric(body) ?: return null
        return lrcToMlx(lrc, title, artist, source = "NetEase", sourceId = song.id)
    }

    private companion object {
        const val REFERER = "https://music.163.com/"
    }
}

private val netEaseJson = Json { ignoreUnknownKeys = true }

internal fun parseNetEaseSearch(body: String): List<LyricsCandidate> =
    netEaseJson
        .parseToJsonElement(body)
        .asObject()
        ?.get("result")
        .asObject()
        ?.get("songs")
        .asArray()
        .orEmpty()
        .mapNotNull { element ->
            val song = element.asObject() ?: return@mapNotNull null
            LyricsCandidate(
                id = song["id"].asLong()?.toString() ?: return@mapNotNull null,
                title = song["name"].asText() ?: return@mapNotNull null,
                artists = song["artists"].asArray().orEmpty().mapNotNull { it.asObject()?.get("name").asText() },
                durationSec = (song["duration"].asLong() ?: 0L) / 1000,
            )
        }

/** The LRC text; NetEase prepends JSON credit lines (`{"t":0,"c":[…]}`) that are not lyrics. */
internal fun parseNetEaseLyric(body: String): String? =
    netEaseJson
        .parseToJsonElement(body)
        .asObject()
        ?.get("lrc")
        .asObject()
        ?.get("lyric")
        .asText()
        ?.lineSequence()
        ?.filterNot { it.trimStart().startsWith("{") }
        ?.joinToString("\n")
        ?.takeIf { it.isNotBlank() }
