package com.melodyon.shared.utils

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebCookieUtilsTest {
    @Test
    fun deletesProfileInsideUserHome() {
        val userHome = Files.createTempDirectory("melodyon-home")
        val profile = userHome.resolve(".local/share/Melodyon/WebView").createDirectories()
        profile.resolve("cookie.db").createFile()

        deleteWebViewProfileDirectory(profile.toFile(), userHome.toFile())

        assertFalse(Files.exists(profile))
    }

    @Test
    fun rejectsProfilePathThroughSymlink() {
        val userHome = Files.createTempDirectory("melodyon-home")
        val external = Files.createTempDirectory("melodyon-external")
        val sentinel =
            external
                .resolve("WebView")
                .createDirectories()
                .resolve("cookie.db")
                .createFile()
        val localShare = userHome.resolve(".local/share").createDirectories()
        runCatching { Files.createSymbolicLink(localShare.resolve("Melodyon"), external) }.getOrElse { return }
        val profile = localShare.resolve("Melodyon/WebView")

        assertFailsWith<IllegalStateException> {
            deleteWebViewProfileDirectory(profile.toFile(), userHome.toFile())
        }
        assertTrue(Files.exists(sentinel))
    }
}
