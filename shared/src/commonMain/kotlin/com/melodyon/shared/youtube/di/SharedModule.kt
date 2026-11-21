package com.melodyon.shared.youtube.di

import com.melodyon.shared.soundcloud.GlobalSoundCloud
import com.melodyon.shared.soundcloud.SoundCloudApi
import com.melodyon.shared.soundcloud.SoundCloudStreamResolver
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamExtractor
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.strategy.ClientFallbackStrategy
import com.metrolist.innertubex.extraction.strategy.ClientHealthMonitor
import com.metrolist.innertubex.extraction.strategy.ContentAwareFallbackStrategy
import com.melodyon.shared.BuildConfig
import com.melodyon.shared.api.lyrics.AiModelCatalog
import com.melodyon.shared.api.lyrics.LyprioLyricsProvider
import com.melodyon.shared.api.lyrics.LyricsClient
import com.melodyon.shared.api.lyrics.LyricsFetcher
import com.melodyon.shared.api.lyrics.LyricsTranslationClient
import com.melodyon.shared.api.lyrics.YouTubeLyricsApi
import com.melodyon.shared.api.musicvideo.MusicVideoResolver
import com.melodyon.shared.repository.LyricsRepository
import com.melodyon.shared.state.GlobalYouTube
import com.melodyon.shared.youtube.logging.MelodyonInnerTubeLogger
import com.melodyon.shared.youtube.lyrics.InnerTubeLyricsApi
import com.melodyon.shared.youtube.playback.DefaultStreamResolver
import com.melodyon.shared.youtube.playback.LocalAudioSource
import com.melodyon.shared.youtube.playback.MelodyonPlayerConfigRepository
import com.melodyon.shared.youtube.playback.PlatformTokenProvider
import com.melodyon.shared.youtube.playback.StreamResolver
import com.melodyon.shared.youtube.strategy.ClientHealthTracker
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val sharedModule =
    module {
        single {
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        }
        single { RemotePlayerConfigStore(get(), MelodyonPlayerConfigRepository, MelodyonInnerTubeLogger) }
        single<YtConfigParser> {
            YtConfigParserImpl(
                get(),
                get(),
                get(),
                MelodyonInnerTubeLogger,
                cipherService = get<YouTubeCipherService>(),
            )
        }

        single { ClientHealthTracker() }
        single<ClientHealthMonitor> { get<ClientHealthTracker>() }
        single<ClientFallbackStrategy> { ContentAwareFallbackStrategy(get()) }

        single { YouTubeCipherService(get(), get(), MelodyonInnerTubeLogger) }

        single<TokenProvider> { PlatformTokenProvider() }

        single<StreamExtractor> {
            InnerTubeExtractor(
                configParser = get(),
                cipherService = get<YouTubeCipherService>(),
                innerTube = get<InnerTube>(),
                fallbackStrategy = get(),
                tokenProvider = get<TokenProvider>(),
                clientHealthMonitor = get(),
                logger = MelodyonInnerTubeLogger,
            )
        }

        single { GlobalSoundCloud.api }

        single<StreamResolver> {
            val soundCloud = get<SoundCloudApi>()
            SoundCloudStreamResolver(
                delegate =
                    DefaultStreamResolver(
                        extractor = get(),
                        innerTube = get(),
                        tokenProvider = getOrNull(),
                        clientHealthTracker = get(),
                        playerConfigRefresh = { get<YouTubeCipherService>().refreshAfterStreamRejection() },
                        musicVideoResolver = get(),
                        localAudioSource = getOrNull<LocalAudioSource>(),
                    ),
                streamLookup = { trackId -> soundCloud.streamUrl(trackId) },
            )
        }

        single<MusicVideoResolver> { MusicVideoResolver() }

        single { GlobalYouTube.repository }
        single<YouTubeLyricsApi> { InnerTubeLyricsApi(get()) }
        single {
            LyricsClient(
                httpClient = get(),
                youtubeLyricsApi = get(),
                lyprioLyricsProvider =
                    LyprioLyricsProvider.createOrNull(
                        httpClient = get(),
                        baseUrl = BuildConfig.LYPRIO_BASE_URL,
                        signingKeyBase64 = BuildConfig.LYPRIO_HMAC_KEY,
                        allowInsecureHttp = BuildConfig.DEBUG,
                    ),
            )
        }
        single<LyricsFetcher> { get<LyricsClient>() }
        single { LyricsTranslationClient(get(), get()) }
        single { AiModelCatalog(get()) }
        single {
            LyricsRepository(
                lyricsClient = get(),
                lyricsTranslationClient = get(),
                aiModelCatalog = get(),
                databaseDao = getOrNull(),
                youtubeLyricsApi = get(),
            )
        }
    }
