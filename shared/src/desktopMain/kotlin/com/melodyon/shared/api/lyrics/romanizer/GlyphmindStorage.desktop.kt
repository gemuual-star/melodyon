/*
 * Melodyon Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.melodyon.shared.api.lyrics.romanizer

import com.melodyon.shared.utils.XdgUtils
import java.io.File

internal actual fun glyphmindStorageDirectory(): String = File(XdgUtils.getXdgDataHome(), "melodyon/glyphmind").absolutePath

internal actual fun removeLegacyJapaneseDictionary() {
    File(XdgUtils.getXdgDataHome(), "melodyon/japanese-dictionary").deleteRecursively()
}
