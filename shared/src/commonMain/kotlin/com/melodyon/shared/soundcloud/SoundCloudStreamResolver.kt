package com.melodyon.shared.soundcloud

import com.melodyon.shared.model.SongItem
import com.melodyon.shared.youtube.playback.AudioQuality
import com.melodyon.shared.youtube.playback.ContentHints
import com.melodyon.shared.youtube.playback.PlaybackFailureCategory
import com.melodyon.shared.youtube.playback.PlaybackNetworkState
import com.melodyon.shared.youtube.playback.PlaybackResource
import com.melodyon.shared.youtube.playback.StreamResolver
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Routes `sc:<trackId>` ids to SoundCloud and everything else to the YouTube resolver.
 * Player, queue and downloader keep using one [StreamResolver], so SoundCloud tracks
 * get queue/download/cache support without separate code paths.
 */
class SoundCloudStreamResolver(
    private val delegate: StreamResolver,
    private val streamLookup: suspend (Long) -> ResolvedStream,
) : StreamResolver {
    override suspend fun resolve(videoId: String, hints: ContentHints, audioQuality: AudioQuality, sourceSong: SongItem?): PlaybackResource =
        soundCloudTrackId(videoId)?.let { resolveSoundCloud(videoId, it) } ?: delegate.resolve(videoId, hints, audioQuality, sourceSong)

    override suspend fun refresh(videoId: String, hints: ContentHints, audioQuality: AudioQuality, sourceSong: SongItem?): PlaybackResource =
        soundCloudTrackId(videoId)?.let { resolveSoundCloud(videoId, it) } ?: delegate.refresh(videoId, hints, audioQuality, sourceSong)

    override suspend fun resolveRemote(
        videoId: String,
        hints: ContentHints,
        audioQuality: AudioQuality,
        sourceSong: SongItem?,
        refresh: Boolean,
        excludedProfiles: Set<String>,
    ): PlaybackResource =
        soundCloudTrackId(videoId)?.let { resolveSoundCloud(videoId, it) }
            ?: delegate.resolveRemote(videoId, hints, audioQuality, sourceSong, refresh, excludedProfiles)

    private suspend fun resolveSoundCloud(songId: String, trackId: Long): PlaybackResource =
        streamLookup(trackId).toPlaybackResource(songId)

    override suspend fun prewarm() = delegate.prewarm()

    override suspend fun clearPlaybackFailures(videoId: String) {
        if (!isSoundCloudSongId(videoId)) delegate.clearPlaybackFailures(videoId)
    }

    override suspend fun invalidateNetworkScope(state: PlaybackNetworkState) = delegate.invalidateNetworkScope(state)

    override suspend fun resetExtractionBackoff() = delegate.resetExtractionBackoff()

    override suspend fun invalidateAttestation() = delegate.invalidateAttestation()

    override suspend fun refreshPlayerConfigAfterStreamRejection(): Boolean = delegate.refreshPlayerConfigAfterStreamRejection()

    override suspend fun close() = delegate.close()

    override suspend fun reportPlaybackFailure(
        videoId: String,
        clientName: String?,
        responseCode: Int?,
        profileId: String?,
        hints: ContentHints,
        category: PlaybackFailureCategory,
    ) {
        if (!isSoundCloudSongId(videoId)) delegate.reportPlaybackFailure(videoId, clientName, responseCode, profileId, hints, category)
    }

    override suspend fun reportPlaybackSuccess(profileId: String?, hints: ContentHints) {
        if (profileId != SOUNDCLOUD_PROFILE) delegate.reportPlaybackSuccess(profileId, hints)
    }

    companion object {
        const val SOUNDCLOUD_PROFILE = "soundcloud"
    }
}

/** Direct SoundCloud media URLs are signed for a short time; treat them as valid for 10 minutes. */
fun ResolvedStream.toPlaybackResource(songId: String): PlaybackResource =
    PlaybackResource(
        videoId = songId,
        url = url,
        headers = emptyMap(),
        loudnessDb = null,
        expiresAt = Clock.System.now() + 10.minutes,
        mimeType = if (isHls) "application/x-mpegurl" else mimeType.ifBlank { "audio/mpeg" },
        clientName = SoundCloudStreamResolver.SOUNDCLOUD_PROFILE,
        profileId = SoundCloudStreamResolver.SOUNDCLOUD_PROFILE,
        requireBoundedRange = false,
    )
