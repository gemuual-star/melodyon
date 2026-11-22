package com.melodyon.shared.network

import okhttp3.Dns
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.Base64
import javax.net.SocketFactory

/** Thrown with a readable reason when the proxy handshake fails. */
class ProxyHandshakeException(message: String) : IOException(message)

internal const val PROXY_HANDSHAKE_TIMEOUT_MS = 20_000

/**
 * Melodyon's own proxy client. Android's built-in SOCKS has no username/password support and an
 * HTTP proxy that answers 407 and then closes the connection breaks OkHttp ("unexpected end of
 * stream"). The socket therefore performs the SOCKS5 (RFC 1928/1929) or HTTP CONNECT handshake
 * itself, sending credentials up front, and hands OkHttp a ready tunnel. The target host name is
 * sent to the proxy (remote DNS).
 */
internal class ProxyTunnelSocket(
    private val resolve: (String) -> ProxyConfig?,
    private val onResult: (Boolean) -> Unit,
) : Socket() {
    override fun connect(endpoint: SocketAddress) = connect(endpoint, 0)

    override fun connect(endpoint: SocketAddress, timeout: Int) {
        val target = endpoint as? InetSocketAddress ?: throw IOException("Unsupported address")
        val host = target.hostString
        val config = resolve(host)
        if (config == null) {
            val direct =
                if (target.isUnresolved || target.address?.isAnyLocalAddress == true) InetSocketAddress(InetAddress.getByName(host), target.port) else target
            super.connect(direct, timeout)
            return
        }
        try {
            connectToProxy(config, if (timeout > 0) timeout else PROXY_HANDSHAKE_TIMEOUT_MS)
            val previous = soTimeout
            soTimeout = PROXY_HANDSHAKE_TIMEOUT_MS
            when (config.type) {
                ProxyType.SOCKS -> socks5Handshake(config, host, target.port)
                ProxyType.HTTP -> httpConnectHandshake(config, host, target.port)
            }
            soTimeout = previous
            onResult(true)
        } catch (e: IOException) {
            runCatching { close() }
            onResult(false)
            throw e
        }
    }

    private fun connectToProxy(config: ProxyConfig, timeout: Int) {
        val address =
            try {
                InetAddress.getAllByName(config.host).minByOrNull { if (it is Inet4Address) 0 else 1 }
                    ?: throw java.net.UnknownHostException(config.host)
            } catch (e: java.net.UnknownHostException) {
                throw ProxyHandshakeException("хост прокси не найден: ${config.host}")
            }
        try {
            super.connect(InetSocketAddress(address, config.port), timeout)
        } catch (e: java.net.SocketTimeoutException) {
            throw ProxyHandshakeException("прокси не отвечает (таймаут) ${config.host}:${config.port}")
        } catch (e: IOException) {
            throw ProxyHandshakeException("не удалось подключиться к ${config.host}:${config.port}: ${e.message}")
        }
    }

    private fun socks5Handshake(config: ProxyConfig, host: String, port: Int) {
        val out = getOutputStream()
        val input = getInputStream()
        out.write(if (config.hasCredentials) byteArrayOf(5, 2, 0, 2) else byteArrayOf(5, 1, 0))
        out.flush()
        val greeting = input.readExactly(2)
        if (greeting[0].toInt() != 5) throw ProxyHandshakeException("это не SOCKS5-прокси, попробуйте тип HTTP")
        when (greeting[1].toInt() and 0xFF) {
            0 -> Unit
            2 -> {
                if (!config.hasCredentials) throw ProxyHandshakeException("прокси требует логин и пароль")
                val user = config.username.orEmpty().toByteArray(Charsets.UTF_8)
                val pass = config.password.orEmpty().toByteArray(Charsets.UTF_8)
                if (user.size > 255 || pass.size > 255) throw ProxyHandshakeException("слишком длинный логин или пароль")
                out.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
                out.flush()
                val auth = input.readExactly(2)
                if (auth[1].toInt() != 0) throw ProxyHandshakeException("неверный логин или пароль")
            }
            else -> throw ProxyHandshakeException("прокси не принял способ входа (SOCKS5)")
        }
        val name = host.toByteArray(Charsets.UTF_8)
        out.write(byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + byteArrayOf((port shr 8).toByte(), port.toByte()))
        out.flush()
        val reply = input.readExactly(4)
        val code = reply[1].toInt() and 0xFF
        if (code != 0) throw ProxyHandshakeException("прокси не смог подключиться к $host (${socksError(code)})")
        when (reply[3].toInt() and 0xFF) {
            1 -> input.readExactly(4 + 2)
            4 -> input.readExactly(16 + 2)
            3 -> input.readExactly((input.readExactly(1)[0].toInt() and 0xFF) + 2)
            else -> throw ProxyHandshakeException("некорректный ответ SOCKS5")
        }
    }

    private fun httpConnectHandshake(config: ProxyConfig, host: String, port: Int) {
        val out = getOutputStream()
        val input = getInputStream()
        val authority = if (':' in host) "[$host]:$port" else "$host:$port"
        val request = StringBuilder("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\nProxy-Connection: Keep-Alive\r\n")
        if (config.hasCredentials) {
            val token = Base64.getEncoder().encodeToString("${config.username}:${config.password.orEmpty()}".toByteArray(Charsets.UTF_8))
            request.append("Proxy-Authorization: Basic $token\r\n")
        }
        request.append("\r\n")
        out.write(request.toString().toByteArray(Charsets.ISO_8859_1))
        out.flush()
        val status = input.readLine() ?: throw ProxyHandshakeException("прокси закрыл соединение, возможно выбран не тот тип")
        var contentLength = 0
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("content-length:", ignoreCase = true)) contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val code = status.split(' ').getOrNull(1)?.toIntOrNull()
        when {
            !status.startsWith("HTTP/") -> throw ProxyHandshakeException("это не HTTP-прокси, попробуйте тип SOCKS5")
            code == 200 -> Unit
            code == 407 -> throw ProxyHandshakeException(if (config.hasCredentials) "неверный логин или пароль" else "прокси требует логин и пароль")
            else -> {
                if (contentLength in 1..65536) runCatching { input.readExactly(contentLength) }
                throw ProxyHandshakeException("прокси ответил: ${status.substringAfter(' ')}")
            }
        }
    }

    private fun socksError(code: Int) =
        when (code) {
            1 -> "общий сбой"
            2 -> "запрещено правилами"
            3 -> "сеть недоступна"
            4 -> "хост недоступен"
            5 -> "соединение отклонено"
            6 -> "истёк TTL"
            7 -> "команда не поддерживается"
            8 -> "тип адреса не поддерживается"
            else -> "код $code"
        }
}

internal fun InputStream.readExactly(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var read = 0
    while (read < count) {
        val n = read(buffer, read, count - read)
        if (n < 0) throw ProxyHandshakeException("прокси неожиданно закрыл соединение")
        read += n
    }
    return buffer
}

/** Reads one CRLF line byte by byte (no buffering, so TLS bytes after the headers stay in the stream). */
internal fun InputStream.readLine(): String? {
    val sb = StringBuilder()
    while (true) {
        val b = read()
        if (b < 0) return if (sb.isEmpty()) null else sb.toString()
        if (b == '\n'.code) return sb.toString().trimEnd('\r')
        sb.append(b.toChar())
        if (sb.length > 8192) throw ProxyHandshakeException("слишком длинный ответ прокси")
    }
}

internal class ProxyTunnelSocketFactory(
    private val resolve: (String) -> ProxyConfig?,
    private val onResult: (Boolean) -> Unit = {},
) : SocketFactory() {
    override fun createSocket(): Socket = ProxyTunnelSocket(resolve, onResult)

    override fun createSocket(host: String, port: Int): Socket = createSocket().apply { connect(InetSocketAddress.createUnresolved(host, port)) }

    override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket(host, port)

    override fun createSocket(host: InetAddress, port: Int): Socket = createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress?, localPort: Int): Socket = createSocket(address, port)
}

/**
 * While a proxy is active the target is resolved by the proxy: OkHttp gets a placeholder address
 * that keeps the host name, and [ProxyTunnelSocket] sends that name in the handshake.
 */
internal class ProxyAwareDns(private val resolve: (String) -> ProxyConfig?) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        if (resolve(hostname) != null) listOf(InetAddress.getByAddress(hostname, ByteArray(4))) else Dns.SYSTEM.lookup(hostname)
}
