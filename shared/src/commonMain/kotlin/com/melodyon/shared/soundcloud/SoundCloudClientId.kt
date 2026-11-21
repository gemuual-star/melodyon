package com.melodyon.shared.soundcloud

/**
 * SoundCloud has no public sign-up for API keys. The web player embeds a public `client_id`
 * in one of its JS bundles; we read it the same way the browser does (no user account involved).
 */
object SoundCloudClientId {
    private val scriptRegex = Regex("""<script[^>]+src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
    private val clientIdRegex = Regex("""client_id\s*[:=]\s*"([a-zA-Z0-9]{32})"""")

    /** Script URLs in page order; the client id lives in one of the last bundles, so callers try them in reverse. */
    fun scriptUrls(html: String): List<String> = scriptRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()

    fun extract(js: String): String? = clientIdRegex.find(js)?.groupValues?.get(1)
}
