package com.melodyon.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun apkSelectionPicksTheUniversalApk() {
        assertEquals("Melodyon.apk", releaseWithAssets("Melodyon.apk", "melodyon-android-14.0.0-arm64-v8a.apk").apk?.name)
        assertEquals(
            "Melodyon.apk",
            releaseWithAssets("Melodyon-izzy.apk", "Melodyon-with-Google-Cast.apk", "Melodyon.apk").apk?.name,
        )
        assertEquals("melodyon-android-14.0.0.apk", releaseWithAssets("melodyon-android-14.0.0.apk").apk?.name)
        assertEquals("melodyon-android-14.0.0-universal.apk", releaseWithAssets("melodyon-android-14.0.0-universal.apk").apk?.name)
    }

    @Test
    fun apkSelectionRejectsAbiSpecificIncompleteAndAmbiguousAssets() {
        assertNull(releaseWithAssets("melodyon-android-14.0.0-arm64-v8a.apk").apk)
        assertNull(releaseWithAssets("melodyon-android-14.0.0-debug.apk").apk)
        assertNull(releaseWithAssets("melodyon-android-13.0.0.apk").apk)
        assertNull(releaseWithAssets("unknown.apk").apk)
        val incomplete = releaseWithAssets("Melodyon.apk")
        assertNull(incomplete.copy(assets = incomplete.assets.map { it.copy(state = "new") }).apk)
        assertNull(releaseWithAssets("Melodyon.apk", "MELODYON.APK").apk)
    }

    private fun releaseWithAssets(vararg names: String) =
        GitHubRelease(
            tagName = "v14.0.0",
            htmlUrl = "https://github.com/gemuual-star/melodyon-releases/releases/tag/v14.0.0",
            assets = names.map { GitHubReleaseAsset(it, "uploaded", 100, downloadUrl = "https://example.com/$it") },
        )

    @Test
    fun apkSignerMustMatchCurrentSignerOrProveForwardRotation() {
        val old = "old".encodeToByteArray()
        val current = "current".encodeToByteArray()
        val next = "next".encodeToByteArray()

        assertTrue(AppUpdateManager.signerDigestsMatch(listOf(current), listOf(current), listOf(old, current)))
        assertTrue(AppUpdateManager.signerDigestsMatch(listOf(current), listOf(next), listOf(old, current, next)))
        assertFalse(AppUpdateManager.signerDigestsMatch(listOf(current), listOf(old), listOf(old)))
        assertFalse(AppUpdateManager.signerDigestsMatch(listOf(current), listOf(next), listOf(next)))
        assertTrue(AppUpdateManager.signerDigestsMatch(listOf(old, current), listOf(current, old), emptyList()))
        assertFalse(AppUpdateManager.signerDigestsMatch(listOf(old, current), listOf(current), listOf(old, current)))
        assertFalse(AppUpdateManager.signerDigestsMatch(emptyList(), listOf(current), listOf(current)))
        assertFalse(AppUpdateManager.signerDigestsMatch(listOf(current), emptyList(), listOf(current)))
        assertFalse(AppUpdateManager.signerDigestsMatch(emptyList(), emptyList(), listOf(current)))
    }
}
