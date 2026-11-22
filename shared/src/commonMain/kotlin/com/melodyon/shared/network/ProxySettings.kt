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

/** Returns a usable config or null when the proxy is off or host/port are invalid. */
fun proxyConfigOf(
    enabled: Boolean,
    type: String,
    host: String,
    port: String,
    username: String,
    password: String,
): ProxyConfig? {
    if (!enabled) return null
    val cleanHost = normalizeProxyHost(host) ?: return null
    val cleanPort = port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    val proxyType = ProxyType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: ProxyType.HTTP
    return ProxyConfig(proxyType, cleanHost, cleanPort, username.trim().ifEmpty { null }, password.ifEmpty { null })
}

/** Accepts "host", "http://host" or "host:port"-free input; rejects spaces and empty values. */
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
