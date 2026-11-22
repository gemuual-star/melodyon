package com.melodyon.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyPasteTest {
    @Test
    fun hostFieldWithPortIsSplit() {
        val c = proxyConfigOf(true, "HTTP", "disp.oxylabs.io:8001", "", "u", "p")!!
        assertEquals("disp.oxylabs.io", c.host)
        assertEquals(8001, c.port)
    }

    @Test
    fun portFieldWinsWhenValid() {
        assertEquals(9000, proxyConfigOf(true, "HTTP", "h.example:8001", "9000", "", "")!!.port)
    }

    @Test
    fun fullUrlWithCredentials() {
        val c = proxyConfigOf(true, "", "socks5://user:secret@h.example:1080", "", "", "")!!
        assertEquals(ProxyType.SOCKS, c.type)
        assertEquals("h.example", c.host)
        assertEquals(1080, c.port)
        assertEquals("user", c.username)
        assertEquals("secret", c.password)
    }

    @Test
    fun pastedCredentialsAreTrimmed() {
        val c = proxyConfigOf(true, "HTTP", " h.example ", "8001", " login\n", " pass \n")!!
        assertEquals("login", c.username)
        assertEquals("pass", c.password)
    }

    @Test
    fun badEmbeddedPortIsRejected() {
        assertNull(proxyConfigOf(true, "HTTP", "h.example:99999", "", "", ""))
        assertNull(proxyConfigOf(true, "HTTP", "h.example", "", "", ""))
    }
}
