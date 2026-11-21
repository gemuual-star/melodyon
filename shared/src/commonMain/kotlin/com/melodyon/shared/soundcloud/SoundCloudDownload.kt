package com.melodyon.shared.soundcloud

/**
 * Downloads the full audio of a SoundCloud track (progressive file or HLS segments) into [sink].
 * Used by both download managers, because their generic path only accepts direct files.
 * Returns the container mime type of the written bytes.
 */
suspend fun SoundCloudApi.downloadTrackAudio(
    trackId: Long,
    onProgress: (written: Long, total: Long?) -> Unit = { _, _ -> },
    sink: suspend (ByteArray) -> Unit,
): String {
    val stream = streamUrl(trackId)
    var written = 0L
    if (stream.isHls) {
        downloadHls(stream.url) { bytes ->
            sink(bytes)
            written += bytes.size
            onProgress(written, null)
        }
    } else {
        val bytes = rawGet(stream.url)
        sink(bytes)
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }
    return soundCloudAudioMime(stream)
}
