package com.melodyon.desktop.utils

import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopWebViewStorageTest {
    @Test
    fun newAndExistingProfilesAreOwnerOnly() {
        val root = Files.createTempDirectory("webview-permissions")
        try {
            if (Files.getFileAttributeView(root, PosixFileAttributeView::class.java) == null) return
            val profile = root.resolve("Melodyon/WebView")
            prepareAppOwnedDirectory(profile.toFile(), root.toFile())
            val ownerOnly = PosixFilePermissions.fromString("rwx------")
            assertEquals(ownerOnly, Files.getPosixFilePermissions(profile))
            Files.setPosixFilePermissions(profile, PosixFilePermissions.fromString("rwxr-xr-x"))
            prepareAppOwnedDirectory(profile.toFile(), root.toFile())
            assertEquals(ownerOnly, Files.getPosixFilePermissions(profile))
            assertEquals(ownerOnly, Files.getPosixFilePermissions(profile.parent))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsProfilePathThroughSymlinkWhenSupported() {
        val dataRoot = Files.createTempDirectory("melodyon-data")
        val external = Files.createTempDirectory("melodyon-external")
        try {
            external.resolve("WebView").toFile().mkdirs()
            runCatching { Files.createSymbolicLink(dataRoot.resolve("Melodyon"), external) }.getOrElse { return }
            assertFailsWith<IllegalStateException> {
                prepareAppOwnedDirectory(dataRoot.resolve("Melodyon/WebView").toFile(), dataRoot.toFile())
            }
        } finally {
            dataRoot.toFile().deleteRecursively()
            external.toFile().deleteRecursively()
        }
    }
}
