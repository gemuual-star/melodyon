@file:Suppress("FunctionName", "ktlint:standard:no-wildcard-imports")

package com.melodyon.shared.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.melodyon.shared.generated.resources.*
import com.melodyon.shared.network.ProxyTestResult
import com.melodyon.shared.network.ProxyType
import com.melodyon.shared.network.applyProxySettings
import com.melodyon.shared.network.proxyConfigOf
import com.melodyon.shared.network.testProxyConnection
import com.melodyon.shared.settings.Settings
import com.melodyon.shared.ui.icons.MelodyonIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Melodyon: separate «Proxy» page — connection, server, authentication, check and reliability sections. */
@Composable
internal fun SettingsUiRegistryBuilder.registerProxySettings() {
    val settings = Settings.shared
    val config = proxyConfigOf(true, settings.proxyType, settings.proxyHost, settings.proxyPort, settings.proxyUsername, settings.proxyPassword)
    val valid = config != null
    val status =
        when {
            settings.proxyEnabled && config != null -> stringResource(Res.string.proxy_status_on, "${config.host}:${config.port}")
            settings.proxyAutoDisabledNotice -> stringResource(Res.string.proxy_status_auto_off)
            else -> stringResource(Res.string.proxy_status_off)
        }
    page(
        id = SettingsPageId.PROXY,
        title = stringResource(Res.string.proxy_settings_title),
        subtitle = stringResource(Res.string.proxy_page_subtitle),
        icon = MelodyonIcons.Outlined.VpnKey,
        tone = SettingsTone.BLUE,
    ) {
        group(
            id = "proxyConnection",
            title = stringResource(Res.string.proxy_group_connection),
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
                description = status,
                checked = settings.proxyEnabled,
                enabled = valid || settings.proxyEnabled,
            ) {
                settings.proxyEnabled = it
                if (it) settings.proxyAutoDisabledNotice = false
                applyProxySettings()
            }
        }
        group(id = "proxyServer", title = stringResource(Res.string.proxy_group_server)) {
            choice(
                id = "proxyType",
                title = stringResource(Res.string.proxy_type),
                valueLabel = if (settings.proxyType == ProxyType.SOCKS.name) "SOCKS5" else "HTTP / HTTPS",
                choices = ProxyType.entries.map { SettingsChoice(it.name, if (it == ProxyType.HTTP) "HTTP / HTTPS" else "SOCKS5") },
                selectedChoiceId = settings.proxyType,
            ) {
                settings.proxyType = it
                applyProxySettings()
            }
            text(
                id = "proxyHost",
                title = stringResource(Res.string.proxy_host),
                description = stringResource(Res.string.proxy_host_desc),
                value = settings.proxyHost,
                placeholder = "proxy.example.com",
            ) {
                settings.proxyHost = it
                applyProxySettings()
            }
            text(id = "proxyPort", title = stringResource(Res.string.proxy_port), value = settings.proxyPort, placeholder = "8080") {
                settings.proxyPort = it.filter(Char::isDigit).take(5)
                applyProxySettings()
            }
        }
        group(id = "proxyAuth", title = stringResource(Res.string.proxy_group_auth), description = stringResource(Res.string.proxy_auth_desc)) {
            text(id = "proxyUsername", title = stringResource(Res.string.proxy_username), value = settings.proxyUsername) {
                settings.proxyUsername = it
                applyProxySettings()
            }
            text(id = "proxyPassword", title = stringResource(Res.string.proxy_password), value = settings.proxyPassword, secret = true) {
                settings.proxyPassword = it
                applyProxySettings()
            }
        }
        group(id = "proxyCheck", title = stringResource(Res.string.proxy_group_check), description = stringResource(Res.string.proxy_check_desc)) {
            custom(id = "proxyTest", title = stringResource(Res.string.proxy_test), enabled = valid) {
                ProxyConnectionTest(enabled = valid)
            }
        }
        group(id = "proxyReliability", title = stringResource(Res.string.proxy_group_reliability)) {
            switch(
                id = "proxyAutoDisable",
                title = stringResource(Res.string.proxy_auto_disable),
                description = stringResource(Res.string.proxy_auto_disable_desc),
                checked = settings.proxyAutoDisable,
            ) { settings.proxyAutoDisable = it }
        }
    }
}

@Composable
private fun ProxyConnectionTest(enabled: Boolean) {
    val settings = Settings.shared
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<ProxyTestResult>?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(
            onClick = {
                val config =
                    proxyConfigOf(true, settings.proxyType, settings.proxyHost, settings.proxyPort, settings.proxyUsername, settings.proxyPassword)
                        ?: return@FilledTonalButton
                busy = true
                result = null
                scope.launch {
                    result = testProxyConnection(config)
                    busy = false
                }
            },
            enabled = enabled && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(stringResource(if (busy) Res.string.proxy_test_running else Res.string.proxy_test))
        }
        AnimatedVisibility(visible = result != null) {
            val r = result ?: return@AnimatedVisibility
            val ok = r.isSuccess
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (ok) Res.string.proxy_test_ok_title else Res.string.proxy_test_fail_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    r.fold(
                        onSuccess = {
                            Text(stringResource(Res.string.proxy_test_ip, it.ip), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(Res.string.proxy_test_latency, it.latencyMs.toInt()), style = MaterialTheme.typography.bodyMedium)
                        },
                        onFailure = {
                            Text(it.message ?: it::class.simpleName.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        },
                    )
                }
            }
        }
    }
}
