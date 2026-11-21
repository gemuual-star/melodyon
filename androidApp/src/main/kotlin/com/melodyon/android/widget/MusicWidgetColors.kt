package com.melodyon.android.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.melodyon.shared.settings.ThemePaletteStyle
import com.melodyon.shared.ui.theme.themeColorScheme

internal fun musicWidgetColorScheme(
    artworkSeed: Color?,
    isDark: Boolean,
    systemScheme: () -> ColorScheme,
): ColorScheme = artworkSeed?.let { themeColorScheme(it, isDark, ThemePaletteStyle.TONAL_SPOT) } ?: systemScheme()
