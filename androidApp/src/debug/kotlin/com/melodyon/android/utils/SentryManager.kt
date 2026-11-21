package com.melodyon.android.utils

import android.app.Application
import com.melodyon.shared.settings.Settings

object SentryManager {
    fun initialize(application: Application) = Unit

    fun setEnabled(enabled: Boolean) {
        Settings.shared.sentryEnabled = enabled
    }
}
