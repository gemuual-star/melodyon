/**
 * Melodyon Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.melodyon.android.deviceconnect

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.melodyon.android.listentogether.AndroidListenTogetherManager
import com.melodyon.shared.deviceconnect.DcPlaybackAction
import com.melodyon.shared.deviceconnect.DcPlaybackCommand
import com.melodyon.shared.deviceconnect.DcPlaybackSnapshot
import com.melodyon.shared.deviceconnect.DcRepeatMode
import com.melodyon.shared.deviceconnect.DeviceConnectDeviceType
import com.melodyon.shared.deviceconnect.DeviceConnectManager
import com.melodyon.shared.model.SongItem
import com.melodyon.shared.playback.MusicQueueSnapshot

object AndroidDeviceConnect {
    val manager = DeviceConnectManager(localDeviceType = DeviceConnectDeviceType.ANDROID_PHONE)

    fun intercept(
        command: DcPlaybackCommand,
        expectedQueue: MusicQueueSnapshot? = null,
    ): Boolean =
        manager.remote.intercept(command, expectedQueue = expectedQueue) ||
            (false && command.action != DcPlaybackAction.VOLUME)

    internal fun requiredLocalNetworkPermission(): String? = deviceConnectLocalNetworkPermission(Build.VERSION.SDK_INT)

    internal fun canUseLocalNetwork(context: Context): Boolean =
        requiredLocalNetworkPermission()?.let { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        } ?: true
}

@SuppressLint("InlinedApi")
internal fun deviceConnectLocalNetworkPermission(sdkInt: Int): String? =
    if (sdkInt >= Build.VERSION_CODES.CINNAMON_BUN) Manifest.permission.ACCESS_LOCAL_NETWORK else null

internal fun Int.toDeviceConnectRepeatMode(): DcRepeatMode =
    when (this) {
        Player.REPEAT_MODE_ONE -> DcRepeatMode.ONE
        Player.REPEAT_MODE_ALL -> DcRepeatMode.ALL
        else -> DcRepeatMode.OFF
    }

internal fun DcRepeatMode.toPlayerRepeatMode(): Int =
    when (this) {
        DcRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        DcRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        DcRepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }

internal fun requiresDeviceConnectTrackReload(
    currentSongId: String?,
    currentResourceId: String?,
    previousQueue: MusicQueueSnapshot,
    snapshot: DcPlaybackSnapshot,
    appliedSessionId: String?,
    appliedTrackRevision: Long?,
    targetLoaded: Boolean = true,
): Boolean {
    val targetId =
        snapshot.playback.queue.displayedItems
            .getOrNull(snapshot.playback.queue.currentIndex)
            ?.id
    return !targetLoaded || currentResourceId == null || currentResourceId != targetId || currentSongId != targetId ||
        (appliedSessionId == snapshot.sessionId && appliedTrackRevision != snapshot.trackRevision) ||
        (
            appliedSessionId == null && previousQueue.displayedItems == snapshot.playback.queue.displayedItems &&
                previousQueue.currentIndex != snapshot.playback.queue.currentIndex
        )
}

internal fun canUseDeviceConnectTrack(
    song: SongItem,
    isFollowing: Boolean,
    requiresReload: Boolean,
): Boolean = !(song.isLocal || song.id.startsWith("local_")) || (!isFollowing && !requiresReload)
