package com.melodyon.android.playlistimport

import androidx.compose.runtime.Composable
import com.melodyon.shared.deezerimport.DeezerImportManager
import com.melodyon.shared.generated.resources.Res
import com.melodyon.shared.generated.resources.deezer_login_failed
import com.melodyon.shared.generated.resources.import_connect_deezer_account

@Composable
@Suppress("FunctionName")
fun DeezerLoginScreen(
    isAuthenticating: Boolean,
    authenticationError: String?,
    onClearAuthenticationError: () -> Unit,
    onSessionToken: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    ProviderLoginShell(
        titleRes = Res.string.import_connect_deezer_account,
        loginErrorRes = Res.string.deezer_login_failed,
        cookieUrl = "https://www.deezer.com",
        loginUrl = "https://www.deezer.com/",
        logTag = "DeezerImport",
        credentialLogMessage = "Deezer WebView session token",
        extractCredentials = { DeezerImportManager.extractArl(it) },
        credentialKey = { it },
        isAuthenticating = isAuthenticating,
        authenticationError = authenticationError,
        onClearAuthenticationError = onClearAuthenticationError,
        onCredentials = onSessionToken,
        onNavigateBack = onNavigateBack,
    )
}
