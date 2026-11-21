package com.melodyon.android.ui.menu

import androidx.compose.runtime.Composable
import com.melodyon.shared.db.MusicDatabase
import com.melodyon.shared.model.YTItem
import com.melodyon.shared.ui.menu.SpeedDialToggle
import org.koin.compose.koinInject
import com.melodyon.shared.ui.menu.rememberSpeedDialToggle as rememberSharedSpeedDialToggle

@Composable
internal fun rememberSpeedDialToggle(item: YTItem): SpeedDialToggle = rememberSharedSpeedDialToggle(item, koinInject<MusicDatabase>())
