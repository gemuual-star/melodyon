package com.melodyon.shared.network

import com.melodyon.shared.settings.Settings

enum class ProxyType { HTTP, SOCKS }

/** User proxy; every app request goes through it while it is enabled. */
data class ProxyConfig(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
) {
    val hasCredentials: Boolean get() = !username.isNullOrEmpty()
}

/**
 * Returns a usable config or null when the proxy is off or host/port are invalid.
 * Tolerates pasted values: `http://user:pass@host:port` or `host:port` in the host field,
 * and stray spaces/newlines around the login and password.
 */
fun proxyConfigOf(
    enabled: Boolean,
    type: String,
    host: String,
    port: String,
    username: String,
    password: String,
): ProxyConfig? {
    if (!enabled) return null
    val parsed = parseProxyHostInput(host) ?: return null
    val cleanPort =
        port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
            ?: parsed.port
            ?: return null
    val proxyType =
        ProxyType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) }
            ?: if (parsed.socks) ProxyType.SOCKS else ProxyType.HTTP
    val user = username.trim().ifEmpty { parsed.username.orEmpty() }.ifEmpty { null }
    val pass = password.trim().ifEmpty { parsed.password.orEmpty() }.ifEmpty { null }
    return ProxyConfig(proxyType, parsed.host, cleanPort, user, pass)
}

internal data class ParsedProxyHost(
    val host: String,
    val port: Int?,
    val username: String?,
    val password: String?,
    val socks: Boolean,
)

/** Splits a pasted proxy address into host, optional port and optional credentials. */
internal fun parseProxyHostInput(raw: String): ParsedProxyHost? {
    var rest = raw.trim()
    val scheme = rest.substringBefore("://", "").lowercase()
    rest = rest.substringAfter("://").substringBefore('/').trim()
    var user: String? = null
    var pass: String? = null
    if ('@' in rest) {
        val creds = rest.substringBeforeLast('@')
        rest = rest.substringAfterLast('@')
        user = creds.substringBefore(':').ifEmpty { null }
        pass = creds.substringAfter(':', "").ifEmpty { null }
    }
    var port: Int? = null
    if (!rest.startsWith("[") && rest.count { it == ':' } == 1) {
        port = rest.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        rest = rest.substringBefore(':')
    } else if (rest.startsWith("[") && "]:" in rest) {
        port = rest.substringAfter("]:").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        rest = rest.substringBefore("]:") + "]"
    }
    val host = normalizeProxyHost(rest) ?: return null
    return ParsedProxyHost(host, port, user, pass, scheme.startsWith("socks"))
}

/** Accepts "host" or "http://host"; rejects spaces and empty values. */
fun normalizeProxyHost(raw: String): String? {
    val host = raw.trim().substringAfter("://").substringBefore('/').trim()
    if (host.isEmpty() || host.any { it.isWhitespace() }) return null
    return host
}

/** Proxy for the current settings, or null. */
fun Settings.proxyConfig(): ProxyConfig? =
    proxyConfigOf(proxyEnabled, proxyType, proxyHost, proxyPort, proxyUsername, proxyPassword)

/** Hosts that must never go through the proxy (local cast server, device connect). */
fun isLocalProxyBypassHost(host: String?): Boolean {
    val h = host?.trim()?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return true
    return h.isEmpty() || h == "localhost" || h.endsWith(".local") || h == "::1" || h.startsWith("127.") ||
        h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.") ||
        Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(h)
}

/**
 * Counts consecutive failed connections to the proxy. When [threshold] is reached and auto-disable is on,
 * [onTrip] switches the proxy off. Any successful proxy connection resets the counter.
 */
class ProxyFailureTracker(
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val autoDisableEnabled: () -> Boolean,
    private val onTrip: () -> Unit,
) {
    private var failures = 0
    val consecutiveFailures: Int get() = failures

    fun onSuccess() {
        failures = 0
    }

    /** Returns true when this failure tripped the auto-disable. */
    fun onFailure(): Boolean {
        failures++
        val tripped = failures >= threshold && autoDisableEnabled()
        if (tripped) {
            failures = 0
            onTrip()
        }
        return tripped
    }

    companion object {
        const val DEFAULT_THRESHOLD = 5
    }
}

data class ProxyTestResult(val ip: String, val latencyMs: Long)

object ProxyController {
    val failureTracker =
        ProxyFailureTracker(
            autoDisableEnabled = { Settings.shared.proxyAutoDisable && Settings.shared.proxyEnabled },
            onTrip = {
                Settings.shared.proxyEnabled = false
                Settings.shared.proxyAutoDisabledNotice = true
                applyProxySettings()
            },
        )
}

/** Drops pooled connections so the next requests use the new proxy settings. */
expect fun applyProxySettings()

/** Requests a public IP echo through [config] (independent of the saved settings). */
expect suspend fun testProxyConnection(config: ProxyConfig): Result<ProxyTestResult>
