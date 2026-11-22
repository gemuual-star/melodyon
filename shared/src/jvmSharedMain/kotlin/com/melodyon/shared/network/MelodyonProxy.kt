package com.melodyon.shared.network

import com.melodyon.shared.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource

fun ProxyConfig.toJavaProxy(): Proxy =
    Proxy(if (type == ProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))

/**
 * Global proxy routing for Android and desktop. The selector reads the settings on every connection,
 * so OkHttp, HttpURLConnection (ExoPlayer) and the JDK all follow the current proxy without restarts.
 */
object MelodyonProxy {
    @Volatile private var installed = false
    private var fallback: ProxySelector? = null

    val selector: ProxySelector =
        object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val config = Settings.shared.proxyConfig()
                if (config != null && !isLocalProxyBypassHost(uri?.host)) return listOf(config.toJavaProxy())
                return fallback?.let { runCatching { it.select(uri) }.getOrNull() }?.takeIf { it.isNotEmpty() } ?: listOf(Proxy.NO_PROXY)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                if (Settings.shared.proxyConfig() != null) ProxyController.failureTracker.onFailure()
            }
        }

    /** OkHttp proxy auth (OkHttp ignores java.net.Authenticator). */
    val okHttpAuthenticator =
        okhttp3.Authenticator { route, response ->
            val config = Settings.shared.proxyConfig() ?: return@Authenticator null
            if (!config.hasCredentials || route?.proxy?.type() != Proxy.Type.HTTP) return@Authenticator null
            if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
            response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(config.username.orEmpty(), config.password.orEmpty())).build()
        }

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        // Basic auth for HTTPS tunnels through an HTTP proxy is disabled in the JDK by default.
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "")
        fallback = ProxySelector.getDefault()?.takeIf { it !== selector }
        ProxySelector.setDefault(selector)
        Authenticator.setDefault(
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    // SOCKS5 auth asks with requestorType=SERVER and protocol "SOCKS5", so accept it explicitly.
                    val socks = requestingProtocol?.startsWith("SOCKS", ignoreCase = true) == true
                    if (requestorType != RequestorType.PROXY && !socks) return null
                    val config = Settings.shared.proxyConfig()?.takeIf { it.hasCredentials } ?: return null
                    return PasswordAuthentication(config.username, config.password.orEmpty().toCharArray())
                }
            },
        )
    }

    /** `http://user:pass@host:port` for FFmpeg's native HTTP; null when no HTTP proxy is active. */
    fun ffmpegProxyUrl(): String? {
        val config = Settings.shared.proxyConfig()?.takeIf { it.type == ProxyType.HTTP } ?: return null
        val auth =
            if (config.hasCredentials) {
                "${java.net.URLEncoder.encode(config.username, "UTF-8")}:${java.net.URLEncoder.encode(config.password.orEmpty(), "UTF-8")}@"
            } else {
                ""
            }
        return "http://$auth${config.host}:${config.port}"
    }
}

actual fun applyProxySettings() {
    MelodyonProxy.install()
    ProxyController.failureTracker.onSuccess()
    runCatching { sharedOkHttpClient.connectionPool.evictAll() }
}

internal const val PROXY_TEST_URL = "https://api.ipify.org"

actual suspend fun testProxyConnection(config: ProxyConfig): Result<ProxyTestResult> =
    withContext(Dispatchers.IO) {
        runCatching {
            val client =
                OkHttpClient
                    .Builder()
                    .proxy(config.toJavaProxy())
                    .proxyAuthenticator { route, response ->
                        if (!config.hasCredentials || route?.proxy?.type() != Proxy.Type.HTTP ||
                            response.request.header("Proxy-Authorization") != null
                        ) {
                            null
                        } else {
                            response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(config.username.orEmpty(), config.password.orEmpty())).build()
                        }
                    }.connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            MelodyonProxy.install()
            val mark = TimeSource.Monotonic.markNow()
            try {
                client.newCall(Request.Builder().url(PROXY_TEST_URL).build()).execute().use { response ->
                    if (response.code == 407) throw IOException("Proxy authentication failed (407)")
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val ip = response.body.string().trim()
                    ProxyTestResult(ip = ip, latencyMs = mark.elapsedNow().inWholeMilliseconds)
                }
            } catch (e: IOException) {
                throw IOException(describeProxyFailure(e), e)
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }

/** Human-readable reason for the settings screen (the raw exception text is kept as a hint). */
internal fun describeProxyFailure(e: Throwable): String {
    val msg = e.message.orEmpty()
    val reason =
        when {
            e is java.net.UnknownHostException -> "хост прокси не найден"
            e is java.net.SocketTimeoutException -> "прокси не отвечает (таймаут)"
            e is java.net.ConnectException -> "соединение с прокси отклонено, проверьте хост, порт и тип"
            "407" in msg || msg.contains("authentication", ignoreCase = true) -> "неверный логин или пароль"
            msg.contains("SOCKS", ignoreCase = true) -> "ошибка SOCKS, возможно выбран не тот тип прокси"
            else -> null
        }
    return listOfNotNull(reason, msg.takeIf { it.isNotBlank() } ?: e::class.simpleName).joinToString(" — ")
}
