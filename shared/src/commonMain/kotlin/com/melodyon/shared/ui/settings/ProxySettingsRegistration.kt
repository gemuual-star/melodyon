@file:Suppress("FunctionName", "ktlint:standard:no-wildcard-imports")

package com.melodyon.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.melodyon.shared.generated.resources.*
import com.melodyon.shared.network.ProxyTestResult
import com.melodyon.shared.network.ProxyType
import com.melodyon.shared.network.applyProxySettings
import com.melodyon.shared.network.proxyConfigOf
import com.melodyon.shared.network.testProxyConnection
import com.melodyon.shared.settings.Settings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Melodyon: proxy group (host, port, optional credentials, connection check, auto-disable). */
@Composable
internal fun SettingsPageRegistryBuilder.registerProxyGroup() {
    val settings = Settings.shared
    val valid =
        proxyConfigOf(true, settings.proxyType, settings.proxyHost, settings.proxyPort, settings.proxyUsername, settings.proxyPassword) != null
    group(
        id = "proxy",
        title = stringResource(Res.string.proxy_settings_title),
        footer =
            when {
                settings.proxyAutoDisabledNotice && !settings.proxyEnabled -> stringResource(Res.string.proxy_auto_disabled_notice)
                !valid && (settings.proxyHost.isNotBlank() || settings.proxyPort.isNotBlank()) -> stringResource(Res.string.proxy_invalid)
                else -> null
            },
    ) {
        mainSwitch(
            id = "proxyEnabled",
            title = stringResource(Res.string.proxy_enabled),
            description = stringResource(Res.string.proxy_enabled_desc),
            checked = settings.proxyEnabled,
            enabled = valid || settings.proxyEnabled,
        ) {
            settings.proxyEnabled = it
            if (it) settings.proxyAutoDisabledNotice = false
            applyProxySettings()
        }
        choice(
            id = "proxyType",
            title = stringResource(Res.string.proxy_type),
            valueLabel = settings.proxyType,
            choices = ProxyType.entries.map { SettingsChoice(it.name, if (it == ProxyType.HTTP) "HTTP / HTTPS" else "SOCKS5") },
            selectedChoiceId = settings.proxyType,
        ) {
            settings.proxyType = it
            applyProxySettings()
        }
        text(id = "proxyHost", title = stringResource(Res.string.proxy_host), value = settings.proxyHost, placeholder = "proxy.example.com") {
            settings.proxyHost = it
            applyProxySettings()
        }
        text(id = "proxyPort", title = stringResource(Res.string.proxy_port), value = settings.proxyPort, placeholder = "8080") {
            settings.proxyPort = it.filter(Char::isDigit).take(5)
            applyProxySettings()
        }
        text(id = "proxyUsername", title = stringResource(Res.string.proxy_username), value = settings.proxyUsername) {
            settings.proxyUsername = it
            applyProxySettings()
        }
        text(id = "proxyPassword", title = stringResource(Res.string.proxy_password), value = settings.proxyPassword, secret = true) {
            settings.proxyPassword = it
            applyProxySettings()
        }
        switch(
            id = "proxyAutoDisable",
            title = stringResource(Res.string.proxy_auto_disable),
            description = stringResource(Res.string.proxy_auto_disable_desc),
            checked = settings.proxyAutoDisable,
        ) { settings.proxyAutoDisable = it }
        custom(id = "proxyTest", title = stringResource(Res.string.proxy_test), enabled = valid) {
            ProxyConnectionTest(enabled = valid)
        }
    }
}

@Composable
private fun ProxyConnectionTest(enabled: Boolean) {
    val settings = Settings.shared
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<ProxyTestResult>?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val config =
                    proxyConfigOf(true, settings.proxyType, settings.proxyHost, settings.proxyPort, settings.proxyUsername, settings.proxyPassword)
                        ?: return@Button
                busy = true
                result = null
                scope.launch {
                    result = testProxyConnection(config)
                    busy = false
                }
            },
            enabled = enabled && !busy,
        ) {
            Text(stringResource(if (busy) Res.string.proxy_test_running else Res.string.proxy_test))
        }
        result?.fold(
            onSuccess = {
                Text(stringResource(Res.string.proxy_test_success, it.ip, it.latencyMs.toInt()), color = MaterialTheme.colorScheme.primary)
            },
            onFailure = {
                Text(stringResource(Res.string.proxy_test_failed, it.message ?: it::class.simpleName.orEmpty()), color = MaterialTheme.colorScheme.error)
            },
        )
    }
}
