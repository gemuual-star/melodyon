package com.melodyon.shared.api.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.http.appendPathSegments
import kotlinx.serialization.json.Json

/** lyrics.ovh: plain (unsynced) lyrics by exact artist and title; a last resort when nothing synced exists. */
class LyricsOvhLyricsProvider(
    private val httpClient: HttpClient,
) {
    suspend fun getLyrics(
        title: String,
        artist: String,
    ): String? {
        val cleanTitle = lyricsSearchTitle(title)
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
        val body =
            httpClient
                .prepareGet("https://api.lyrics.ovh/v1") {
                    expectMissingLyrics()
                    url { appendPathSegments(listOf(cleanArtist, cleanTitle), encodeSlash = true) }
                }.readLyricsText() ?: return null
        val text = parseLyricsOvh(body) ?: return null
        return lrcToMlx(text, title, artist, source = "lyrics.ovh")
    }
}

private val lyricsOvhJson = Json { ignoreUnknownKeys = true }

internal fun parseLyricsOvh(body: String): String? =
    lyricsOvhJson
        .parseToJsonElement(body)
        .asObject()
        ?.get("lyrics")
        .asText()
        ?.replace("\r", "")
        ?.takeIf { it.isNotBlank() }
