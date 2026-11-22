package com.melodyon.shared.api.lyrics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

/** A search hit of a catalogue-style lyrics source (NetEase, QQ Music). */
internal data class LyricsCandidate(
    val id: String,
    val title: String,
    val artists: List<String>,
    val durationSec: Long,
)

private val bracketedTitlePart = Regex("""\s*[(\[（【].*?[)\]）】]""")

/** Title without "(feat. …)", "[Official Video]" and similar suffixes; used for searching and matching. */
internal fun lyricsSearchTitle(title: String): String = title.replace(bracketedTitlePart, "").trim().ifBlank { title.trim() }

private fun String.matchKey(): String = lowercase().filter { it.isLetterOrDigit() }

/**
 * Picks the hit that is the same recording: the title must match, and the duration must be within
 * [toleranceSec]. When the duration is unknown the artist must match instead. Wrong lyrics are worse than none.
 */
internal fun pickLyricsCandidate(
    candidates: List<LyricsCandidate>,
    title: String,
    artist: String,
    durationSec: Long,
    toleranceSec: Long = 8,
): LyricsCandidate? {
    val wantedTitle = lyricsSearchTitle(title).matchKey()
    if (wantedTitle.isEmpty()) return null
    val wantedArtist = artist.matchKey()

    fun LyricsCandidate.artistMatches(): Boolean =
        wantedArtist.isNotEmpty() &&
            artists.any {
                val key = it.matchKey()
                key.isNotEmpty() && (key in wantedArtist || wantedArtist in key)
            }
    return candidates
        .filter { lyricsSearchTitle(it.title).matchKey() == wantedTitle }
        .filter {
            if (durationSec > 0) it.durationSec > 0 && abs(it.durationSec - durationSec) <= toleranceSec else it.artistMatches()
        }.minWithOrNull(
            compareBy<LyricsCandidate>({ !it.artistMatches() }, { if (durationSec > 0) abs(it.durationSec - durationSec) else 0L }),
        )
}

internal const val LYRICS_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

internal fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull
