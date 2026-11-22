package com.melodyon.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxySettingsTest {
    @Test
    fun configRequiresEnabledHostAndValidPort() {
        assertNull(proxyConfigOf(false, "HTTP", "h", "8080", "", ""))
        assertNull(proxyConfigOf(true, "HTTP", "", "8080", "", ""))
        assertNull(proxyConfigOf(true, "HTTP", "h", "0", "", ""))
        assertNull(proxyConfigOf(true, "HTTP", "h", "70000", "", ""))
        assertNull(proxyConfigOf(true, "HTTP", "bad host", "80", "", ""))
        val c = proxyConfigOf(true, "socks", " http://proxy.example.com/ ", " 1080 ", " user ", "p@ss")
        assertEquals(ProxyConfig(ProxyType.SOCKS, "proxy.example.com", 1080, "user", "p@ss"), c)
        assertTrue(c!!.hasCredentials)
        assertFalse(proxyConfigOf(true, "HTTP", "h", "1", "", "")!!.hasCredentials)
    }

    @Test
    fun localHostsBypassProxy() {
        listOf("localhost", "127.0.0.1", "192.168.1.5", "10.0.0.2", "172.20.1.1", "[::1]", "tv.local", null).forEach {
            assertTrue(isLocalProxyBypassHost(it), "$it")
        }
        listOf("music.youtube.com", "api-v2.soundcloud.com", "172.32.0.1", "8.8.8.8").forEach {
            assertFalse(isLocalProxyBypassHost(it), it)
        }
    }

    @Test
    fun trackerTripsAfterConsecutiveFailuresOnly() {
        var tripped = 0
        var auto = true
        val t = ProxyFailureTracker(threshold = 3, autoDisableEnabled = { auto }, onTrip = { tripped++ })
        t.onFailure(); t.onFailure(); t.onSuccess(); t.onFailure(); t.onFailure()
        assertEquals(0, tripped)
        assertTrue(t.onFailure())
        assertEquals(1, tripped)
        assertEquals(0, t.consecutiveFailures)
        auto = false
        repeat(5) { t.onFailure() }
        assertEquals(1, tripped)
    }
}
