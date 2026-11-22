package com.melodyon.shared.network

actual fun applyProxySettings() = Unit

actual suspend fun testProxyConnection(config: ProxyConfig): Result<ProxyTestResult> =
    Result.failure(UnsupportedOperationException("Proxy is not supported on iOS"))
