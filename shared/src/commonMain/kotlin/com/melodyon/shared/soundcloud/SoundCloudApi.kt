package com.melodyon.shared.soundcloud

import io.ktor.client.statement.readRawBytes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SoundCloudException(message: String) : Exception(message)

/**
 * Anonymous SoundCloud access (no login): search, track lookup and stream URL resolution
 * through the public api-v2 used by soundcloud.com. The client id is scraped once and
 * refreshed automatically when SoundCloud rotates it (401/403).
 */
class SoundCloudApi(
    private val http: HttpClient,
    private val siteUrl: String = "https://soundcloud.com",
    private val apiUrl: String = "https://api-v2.soundcloud.com",
) {
    private val lock = Mutex()
    private var clientId: String? = null

    suspend fun searchTracks(query: String, limit: Int = 30, offset: Int = 0): ScSearchPage {
        require(query.isNotBlank()) { "Empty query" }
        val body = authorized { id ->
            http.get("$apiUrl/search/tracks") {
                parameter("q", query)
                parameter("limit", limit)
                parameter("offset", offset)
                parameter("client_id", id)
            }
        }
        return parseSearchPage(body)
    }

    /** Next page from `next_href` (it lacks client_id, which is appended here). */
    suspend fun nextPage(nextHref: String): ScSearchPage {
        require(nextHref.startsWith(apiUrl)) { "Unexpected next_href" }
        val body = authorized { id -> http.get(nextHref) { parameter("client_id", id) } }
        return parseSearchPage(body)
    }

    suspend fun track(trackId: Long): ScTrack {
        val body = authorized { id -> http.get("$apiUrl/tracks/$trackId") { parameter("client_id", id) } }
        return parseTrack(body)
    }

    /** SoundCloud "related tracks" — used as radio/autoplay after a SoundCloud track. */
    suspend fun relatedTracks(trackId: Long, limit: Int = 30): List<ScTrack> {
        val body = authorized { id ->
            http.get("$apiUrl/tracks/$trackId/related") {
                parameter("limit", limit)
                parameter("client_id", id)
            }
        }
        return parseSearchPage(body).collection
    }

    /**
     * Downloads an HLS stream (SoundCloud serves unencrypted MP3/Opus segments) by fetching the
     * playlist and every segment in order; [sink] receives the bytes, giving one playable file.
     */
    internal suspend fun rawGet(url: String): ByteArray {
        val response = http.get(url)
        if (!response.status.isSuccess()) throw SoundCloudException("SoundCloud media HTTP ${response.status.value}")
        return response.readRawBytes()
    }

    suspend fun downloadHls(playlistUrl: String, sink: suspend (ByteArray) -> Unit) {
        val playlist = http.get(playlistUrl)
        if (!playlist.status.isSuccess()) throw SoundCloudException("HLS playlist HTTP ${playlist.status.value}")
        val body = playlist.bodyAsText()
        val segments = parseHlsSegments(playlistUrl, body)
        // AAC streams are fragmented MP4: glued together they report one segment as the whole duration
        // and cannot seek, so they are rebuilt into a regular M4A. MP3 segments can simply be appended.
        val remuxer = if (hlsHasInitSegment(body)) FragmentedMp4Remuxer(rawGet(segments.first())) else null
        val media = if (remuxer != null) segments.drop(1) else segments
        if (media.isEmpty()) throw SoundCloudException("Empty HLS playlist")
        media.forEach { url ->
            val bytes = rawGet(url)
            sink(remuxer?.append(bytes) ?: bytes)
        }
        remuxer?.let { sink(it.finish()) }
    }

    /** Resolves a short-lived direct media URL (progressive MP3 or HLS playlist) for [track]. */
    suspend fun streamUrl(track: ScTrack): ResolvedStream {
        val transcoding = track.bestTranscoding() ?: throw SoundCloudException("Track ${track.id} has no playable stream")
        val body = authorized { id -> http.get(transcoding.url) {
                parameter("client_id", id)
                track.trackAuthorization?.let { parameter("track_authorization", it) }
            } }
        val url = parseStreamUrl(body) ?: throw SoundCloudException("Empty stream url")
        return ResolvedStream(url = url, isHls = transcoding.format.protocol == "hls", mimeType = transcoding.format.mimeType)
    }

    suspend fun streamUrl(trackId: Long): ResolvedStream = streamUrl(track(trackId))

    private suspend fun authorized(request: suspend (String) -> HttpResponse): String {
        var id = clientId()
        var response = request(id)
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            id = clientId(forceRefresh = true)
            response = request(id)
        }
        if (!response.status.isSuccess()) throw SoundCloudException("SoundCloud HTTP ${response.status.value}")
        return response.bodyAsText()
    }

    private suspend fun clientId(forceRefresh: Boolean = false): String =
        lock.withLock {
            clientId?.takeIf { !forceRefresh }?.let { return it }
            val html = http.get(siteUrl).bodyAsText()
            val scripts = SoundCloudClientId.scriptUrls(html).asReversed()
            for (script in scripts) {
                val id = runCatching { SoundCloudClientId.extract(http.get(script).bodyAsText()) }.getOrNull()
                if (id != null) {
                    clientId = id
                    return id
                }
            }
            throw SoundCloudException("SoundCloud client id not found")
        }
}

data class ResolvedStream(val url: String, val isHls: Boolean, val mimeType: String)
