package com.melodyon.shared.ui.components

import androidx.compose.runtime.Composable
import com.melodyon.shared.generated.resources.Res
import com.melodyon.shared.generated.resources.menu_repeat_all
import com.melodyon.shared.generated.resources.menu_repeat_off
import com.melodyon.shared.generated.resources.menu_repeat_one
import com.melodyon.shared.generated.resources.repeat_mode_description
import com.melodyon.shared.model.RepeatMode
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun repeatContentDescription(repeatMode: RepeatMode): String =
    stringResource(
        Res.string.repeat_mode_description,
        stringResource(
            when (repeatMode) {
                RepeatMode.OFF -> Res.string.menu_repeat_off
                RepeatMode.ONE -> Res.string.menu_repeat_one
                RepeatMode.ALL -> Res.string.menu_repeat_all
            },
        ),
    )
