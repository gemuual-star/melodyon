package com.melodyon.shared.utils

import kotlin.test.Test
import kotlin.test.assertFalse

class CrashReportingTest {
    @Test
    fun plainReleaseVersionsAllowReporting() {
        assertFalse(isCrashReportingAvailable("14.0.0"))
        assertFalse(isCrashReportingAvailable("14.1.2"))
    }

    @Test
    fun buildMetadataDisablesReporting() {
        assertFalse(isCrashReportingAvailable("14.0.0+24897bc"))
        assertFalse(isCrashReportingAvailable("14.1.2+build.123"))
    }
}
