package com.melodyon.shared.api.musicvideo

import com.melodyon.shared.api.YouTubeRepository
import com.melodyon.shared.model.ArtistTiny
import com.melodyon.shared.model.SongItem
import com.melodyon.shared.youtube.innertube.LiveInnerTubeSuite
import com.melodyon.shared.youtube.innertube.withLiveInnerTubeClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs

class LiveMusicVideoResolutionProbeTest {
    @Test
    fun resolvesOfficialVideoEndToEnd() =
        runBlocking {
            if (!LiveInnerTubeSuite.liveClientTestsEnabled()) return@runBlocking

            withLiveInnerTubeClient { _, innerTube ->
                val resolver = MusicVideoResolver()
                val samples =
                    listOf(
                        SongItem(
                            id = "dQw4w9WgXcQ",
                            title = "Never Gonna Give You Up",
                            thumbnail = null,
                            artists = listOf(ArtistTiny(id = null, name = "Rick Astley")),
                            album = null,
                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
                        ),
                        SongItem(
                            id = "9bZkp7q19f0",
                            title = "Gangnam Style",
                            thumbnail = null,
                            artists = listOf(ArtistTiny(id = null, name = "PSY")),
                            album = null,
                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
                        ),
                    )
                for (sample in samples) {
                    val result = resolver.resolveMusicVideo(sample)
                    LiveInnerTubeSuite.log("RESOLVE '${sample.title}' -> $result")
                    assertIs<MusicVideoResult.Found>(result)
                }
            }
        }
}
