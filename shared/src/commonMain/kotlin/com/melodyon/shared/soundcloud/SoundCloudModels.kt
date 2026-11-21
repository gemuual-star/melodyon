package com.melodyon.shared.soundcloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Prefix that marks a SoundCloud track id inside Melodyon's song/queue/download ids. */
const val SOUNDCLOUD_ID_PREFIX = "sc:"

fun soundCloudSongId(trackId: Long): String = "$SOUNDCLOUD_ID_PREFIX$trackId"

fun isSoundCloudSongId(id: String?): Boolean = id != null && id.startsWith(SOUNDCLOUD_ID_PREFIX) && soundCloudTrackId(id) != null

fun soundCloudTrackId(songId: String): Long? =
    songId.takeIf { it.startsWith(SOUNDCLOUD_ID_PREFIX) }?.removePrefix(SOUNDCLOUD_ID_PREFIX)?.toLongOrNull()?.takeIf { it > 0 }

@Serializable
data class ScUser(
    val id: Long = 0,
    val username: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class ScFormat(
    val protocol: String = "",
    @SerialName("mime_type") val mimeType: String = "",
)

@Serializable
data class ScTranscoding(
    val url: String = "",
    val preset: String = "",
    val snipped: Boolean = false,
    val format: ScFormat = ScFormat(),
)

@Serializable
data class ScMedia(val transcodings: List<ScTranscoding> = emptyList())

@Serializable
data class ScTrack(
    val id: Long = 0,
    val kind: String = "track",
    val title: String = "",
    val duration: Long = 0,
    @SerialName("full_duration") val fullDuration: Long? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    val streamable: Boolean = true,
    val policy: String? = null,
    val genre: String? = null,
    @SerialName("publisher_metadata") val publisherMetadata: ScPublisherMetadata? = null,
    val user: ScUser = ScUser(),
    val media: ScMedia = ScMedia(),
) {
    val songId: String get() = soundCloudSongId(id)

    /** Artist name: publisher metadata wins over the uploader's username. */
    val artistName: String get() = publisherMetadata?.artist?.takeIf { it.isNotBlank() } ?: user.username

    /** Large artwork; SoundCloud serves `-large` (100px) by default, `-t500x500` is the 500px variant. */
    val largeArtworkUrl: String?
        get() = (artworkUrl ?: user.avatarUrl)?.replace("-large.", "-t500x500.")

    /** Only full, streamable tracks are offered; previews (SNIP / Go+) are hidden. */
    val isFullyPlayable: Boolean
        get() = kind == "track" && streamable && policy != "BLOCK" && policy != "SNIP" && bestTranscoding() != null
}

@Serializable
data class ScPublisherMetadata(val artist: String? = null)

@Serializable
data class ScSearchPage(
    val collection: List<ScTrack> = emptyList(),
    @SerialName("next_href") val nextHref: String? = null,
)

@Serializable
data class ScStreamUrl(val url: String = "")

internal val soundCloudJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

/** Prefers progressive MP3 (seekable, downloadable as one file), then HLS MP3, then HLS Opus/AAC. Snipped previews are skipped. */
fun ScTrack.bestTranscoding(): ScTranscoding? {
    val full = media.transcodings.filter { !it.snipped && it.url.isNotBlank() }
    return full.firstOrNull { it.format.protocol == "progressive" && it.format.mimeType.startsWith("audio/mpeg") }
        ?: full.firstOrNull { it.format.protocol == "progressive" }
        ?: full.firstOrNull { it.format.protocol == "hls" && it.format.mimeType.startsWith("audio/mpeg") }
        ?: full.firstOrNull { it.format.protocol == "hls" }
}

fun parseSearchPage(body: String): ScSearchPage {
    val page = soundCloudJson.decodeFromString<ScSearchPage>(body)
    return page.copy(collection = page.collection.filter { it.isFullyPlayable })
}

fun parseTrack(body: String): ScTrack = soundCloudJson.decodeFromString<ScTrack>(body)

fun parseStreamUrl(body: String): String? = soundCloudJson.decodeFromString<ScStreamUrl>(body).url.takeIf { it.startsWith("https://") }
