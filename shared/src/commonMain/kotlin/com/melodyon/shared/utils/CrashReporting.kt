package com.melodyon.shared.utils

import com.melodyon.shared.BuildConfig

/** Melodyon: crash reports went to Metrolist's Sentry; reporting is removed, so it is never available. */
@Suppress("UNUSED_PARAMETER")
fun isCrashReportingAvailable(appVersion: String = BuildConfig.APP_VERSION): Boolean = false
