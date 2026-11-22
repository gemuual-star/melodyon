package com.melodyon.shared.network

import kotlinx.coroutines.runBlocking
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs only in CI with MELODYON_TEST_PROXY=host:port:user:pass (repository secret). */
class MelodyonProxyLiveTest {
    private val config: ProxyConfig? =
        System.getenv("MELODYON_TEST_PROXY")?.takeIf { it.isNotBlank() }?.split(":", limit = 4)?.let {
            proxyConfigOf(true, "HTTP", it[0], it[1], it.getOrElse(2) { "" }, it.getOrElse(3) { "" })
        }

    @Test
    fun connectionTestGoesThroughProxy() {
        val proxy = config ?: return
        val direct = URI(PROXY_TEST_URL).toURL().readText().trim()
        val viaProxy = runBlocking { testProxyConnection(proxy) }.getOrThrow()
        println("PROXY_LIVE direct=$direct proxy=${viaProxy.ip} ${viaProxy.latencyMs}ms")
        assertTrue(viaProxy.ip.isNotBlank())
        assertTrue(viaProxy.ip != direct, "proxy IP must differ from direct IP")
    }

    @Test
    fun socks5WithLoginGoesThroughProxy() {
        val proxy = config ?: return
        val direct = URI(PROXY_TEST_URL).toURL().readText().trim()
        val viaProxy = runBlocking { testProxyConnection(proxy.copy(type = ProxyType.SOCKS)) }.getOrThrow()
        println("PROXY_LIVE_SOCKS direct=$direct proxy=${viaProxy.ip} ${viaProxy.latencyMs}ms")
        assertTrue(viaProxy.ip != direct, "SOCKS5 proxy IP must differ from direct IP")
    }

    @Test
    fun wrongPasswordIsReported() {
        val proxy = config ?: return
        val result = runBlocking { testProxyConnection(proxy.copy(password = "wrong-password")) }
        assertTrue(result.isFailure)
    }

    @Test
    fun unreachableProxyFailsAndTrackerTrips() {
        if (config == null) return
        val result = runBlocking { testProxyConnection(ProxyConfig(ProxyType.HTTP, "127.0.0.1", 1)) }
        assertTrue(result.isFailure)
        var trips = 0
        val tracker = ProxyFailureTracker(threshold = 5, autoDisableEnabled = { true }, onTrip = { trips++ })
        repeat(5) { tracker.onFailure() }
        assertEquals(1, trips)
    }
}
