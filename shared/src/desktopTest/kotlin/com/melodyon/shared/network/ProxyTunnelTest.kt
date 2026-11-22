package com.melodyon.shared.network

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProxyTunnelTest {
    /** Fake SOCKS5 server: checks user/pass, then echoes one line back through the "tunnel". */
    private fun fakeSocks(user: String, pass: String, captured: MutableList<String>): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.accept().use { s ->
                val i = DataInputStream(s.getInputStream())
                val o = s.getOutputStream()
                i.readUnsignedByte(); val n = i.readUnsignedByte(); repeat(n) { i.readUnsignedByte() }
                o.write(byteArrayOf(5, 2)); o.flush()
                i.readUnsignedByte()
                val u = String(ByteArray(i.readUnsignedByte()).also { i.readFully(it) })
                val p = String(ByteArray(i.readUnsignedByte()).also { i.readFully(it) })
                val ok = u == user && p == pass
                o.write(byteArrayOf(1, if (ok) 0 else 1)); o.flush()
                if (!ok) return@thread
                repeat(3) { i.readUnsignedByte() }
                check(i.readUnsignedByte() == 3)
                captured += String(ByteArray(i.readUnsignedByte()).also { i.readFully(it) }) + ":" + i.readUnsignedShort()
                o.write(byteArrayOf(5, 0, 0, 1, 1, 2, 3, 4, 0, 80)); o.flush()
                o.write("pong\n".toByteArray()); o.flush()
            }
        }
        return server
    }

    private fun fakeHttp(expectAuth: String, captured: MutableList<String>): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.accept().use { s ->
                val input = s.getInputStream()
                val lines = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                captured += lines.first()
                val authOk = lines.any { it == "Proxy-Authorization: Basic $expectAuth" }
                val o = s.getOutputStream()
                if (!authOk) {
                    o.write("HTTP/1.1 407 Unauthorized\r\ncontent-length: 0\r\n\r\n".toByteArray()); o.flush(); return@thread
                }
                o.write("HTTP/1.1 200 Connection established\r\n\r\npong\n".toByteArray()); o.flush()
            }
        }
        return server
    }

    private fun connect(config: ProxyConfig): String {
        val socket = ProxyTunnelSocketFactory({ config }).createSocket()
        socket.use {
            it.connect(InetSocketAddress.createUnresolved("api.ipify.org", 443), 5000)
            return it.getInputStream().readLine().orEmpty()
        }
    }

    @Test
    fun socks5SendsCredentialsAndHostName() {
        val captured = mutableListOf<String>()
        fakeSocks("disp_user", "p@ss", captured).use { server ->
            assertEquals("pong", connect(ProxyConfig(ProxyType.SOCKS, "127.0.0.1", server.localPort, "disp_user", "p@ss")))
        }
        assertEquals(listOf("api.ipify.org:443"), captured)
    }

    @Test
    fun socks5WrongPasswordIsReadable() {
        fakeSocks("u", "right", mutableListOf()).use { server ->
            val e = assertFailsWith<ProxyHandshakeException> { connect(ProxyConfig(ProxyType.SOCKS, "127.0.0.1", server.localPort, "u", "wrong")) }
            assertTrue(e.message!!.contains("логин"))
        }
    }

    @Test
    fun httpConnectSendsPreemptiveAuth() {
        val captured = mutableListOf<String>()
        val token = java.util.Base64.getEncoder().encodeToString("u:p".toByteArray())
        fakeHttp(token, captured).use { server ->
            assertEquals("pong", connect(ProxyConfig(ProxyType.HTTP, "127.0.0.1", server.localPort, "u", "p")))
        }
        assertEquals("CONNECT api.ipify.org:443 HTTP/1.1", captured.single())
    }

    @Test
    fun http407IsReadable() {
        fakeHttp("other", mutableListOf()).use { server ->
            val e = assertFailsWith<ProxyHandshakeException> { connect(ProxyConfig(ProxyType.HTTP, "127.0.0.1", server.localPort, "u", "p")) }
            assertTrue(e.message!!.contains("логин"))
        }
    }
}
