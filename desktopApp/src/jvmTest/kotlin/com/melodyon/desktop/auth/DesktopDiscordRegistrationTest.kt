package com.melodyon.desktop.auth

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDiscordRegistrationTest {
    @Test
    fun desktopEntryQuotesLauncherAndKeepsUrlAsOneArgument() {
        val launcher = "/opt/Melodyon żółć/AppImage"
        val entry = linuxDiscordDesktopEntry(launcher, "Melodyon")
        assertTrue("Exec=env \"$launcher\" %u\n" in entry)
        assertTrue("MimeType=x-scheme-handler/melodyon-auth;\n" in entry)
        assertEquals(
            listOf("xdg-mime", "default", "com.melodyon.desktop.discord-auth.desktop", "x-scheme-handler/melodyon-auth"),
            linuxDiscordRegistrationCommand(),
        )
    }

    @Test
    fun desktopEntryEscapesExecMetacharactersAndRejectsNewlines() {
        val entry = linuxDiscordDesktopEntry("/tmp/a\"b\$c`d\\e%f", "Melodyon")
        val escaped = "/tmp/a" + "\\\\" + "\"b" + "\\\\" + "\$c" + "\\\\" + "`d" + "\\\\\\\\" + "e%%f"
        assertTrue("Exec=env \"$escaped\" %u\n" in entry)
        assertFailsWith<IllegalArgumentException> { linuxDiscordDesktopEntry("/tmp/a\nExec=bad", "Melodyon") }
        assertFailsWith<IllegalArgumentException> { linuxDiscordDesktopEntry("/tmp/app", "Melodyon\nExec=bad") }
    }

    @Test
    fun appImageUsesPersistentPathAndNativeLaunchUsesEnvironmentWrapper() {
        val directory = Files.createTempDirectory("melodyon-registration ").toFile()
        try {
            fun executable(name: String) =
                File(directory, name).apply {
                    writeText("")
                    setExecutable(true)
                }
            val appImage = executable("Melodyon.AppImage")
            val native = executable("Melodyon.bin")
            val wrapper = executable("Melodyon")
            val java = executable("java")
            assertEquals(appImage, linuxDiscordLauncher(appImage.path, native.path))
            assertEquals(wrapper, linuxDiscordLauncher(null, native.path))
            assertNull(linuxDiscordLauncher(null, java.path))
            assertNull(linuxDiscordLauncher(null, "Melodyon"))
            assertNull(linuxDiscordLauncher(File(directory, "missing.AppImage").path, native.path))
        } finally {
            directory.deleteRecursively()
        }
    }
}
