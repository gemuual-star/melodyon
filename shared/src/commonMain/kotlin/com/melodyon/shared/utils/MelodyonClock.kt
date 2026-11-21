package com.melodyon.shared.utils

import kotlin.time.Clock

object MelodyonClock {
    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
