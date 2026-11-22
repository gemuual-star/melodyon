package com.melodyon.shared.api.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** QQ Music: line-synced LRC, no account needed. */
class QQMusicLyricsProvider(
    private val httpClient: HttpClient,
) {
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSec: Long,
    ): String? {
        val query = listOf(lyricsSearchTitle(title), artist.trim()).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val request =
            buildJsonObject {
                putJsonObject("req_1") {
                    put("method", "DoSearchForQQMusicDesktop")
                    put("module", "music.search.SearchCgiService")
                    putJsonObject("param") {
                        put("num_per_page", 10)
                        put("page_num", 1)
                        put("query", query)
                        put("search_type", 0)
                    }
                }
            }.toString()
        val search =
            httpClient
                .preparePost("https://u.y.qq.com/cgi-bin/musicu.fcg") {
                    expectMissingLyrics()
                    header(HttpHeaders.Referrer, REFERER)
                    header(HttpHeaders.UserAgent, LYRICS_BROWSER_USER_AGENT)
                    setBody(TextContent(request, ContentType.Application.Json))
                }.readLyricsText() ?: return null
        val song = pickLyricsCandidate(parseQQMusicSearch(search), title, artist, durationSec) ?: return null
        val body =
            httpClient
                .prepareGet("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg") {
                    expectMissingLyrics()
                    header(HttpHeaders.Referrer, REFERER)
                    header(HttpHeaders.UserAgent, LYRICS_BROWSER_USER_AGENT)
                    parameter("songmid", song.id)
                    parameter("format", "json")
                    parameter("nobase64", 1)
                }.readLyricsText() ?: return null
        val lrc = parseQQMusicLyric(body) ?: return null
        return lrcToMlx(lrc, title, artist, source = "QQ Music", sourceId = song.id)
    }

    private companion object {
        const val REFERER = "https://y.qq.com/"
    }
}

private val qqMusicJson = Json { ignoreUnknownKeys = true }

internal fun parseQQMusicSearch(body: String): List<LyricsCandidate> =
    qqMusicJson
        .parseToJsonElement(body)
        .asObject()
        ?.get("req_1")
        .asObject()
        ?.get("data")
        .asObject()
        ?.get("body")
        .asObject()
        ?.get("song")
        .asObject()
        ?.get("list")
        .asArray()
        .orEmpty()
        .mapNotNull { element ->
            val song = element.asObject() ?: return@mapNotNull null
            LyricsCandidate(
                id = song["mid"].asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                title = song["name"].asText() ?: return@mapNotNull null,
                artists = song["singer"].asArray().orEmpty().mapNotNull { it.asObject()?.get("name").asText() },
                durationSec = song["interval"].asLong() ?: 0L,
            )
        }

private val htmlEntities =
    mapOf("&apos;" to "'", "&#39;" to "'", "&quot;" to "\"", "&#34;" to "\"", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ", "&#32;" to " ", "&#10;" to "\n", "&#13;" to "", "&amp;" to "&")

internal fun parseQQMusicLyric(body: String): String? {
    val lyric =
        qqMusicJson
            .parseToJsonElement(body)
            .asObject()
            ?.get("lyric")
            .asText()
            ?.takeIf { it.isNotBlank() } ?: return null
    return htmlEntities.entries.fold(lyric) { text, (entity, replacement) -> text.replace(entity, replacement) }
}
