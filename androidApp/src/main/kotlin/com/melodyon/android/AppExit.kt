package com.melodyon.android

import android.app.Activity
import com.melodyon.android.utils.isAndroidTv
import com.melodyon.shared.settings.Settings

internal fun Activity.exitApp() {
    if (isAndroidTv() && Settings.shared.stopMusicOnTaskClear) {
        // TV has no recents gesture; a confirmed root Back exit removes the task instead.
        finishAndRemoveTask()
    } else {
        finish()
    }
}
