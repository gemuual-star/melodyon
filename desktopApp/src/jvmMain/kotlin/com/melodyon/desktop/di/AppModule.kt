/**
 * Melodyon Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.melodyon.desktop.di

import com.melodyon.desktop.state.playback.PlayerState
import com.melodyon.desktop.storage.DownloadManager
import com.melodyon.desktop.storage.SongCacheManager
import com.melodyon.desktop.storage.desktopSongOfflineAvailability
import com.melodyon.desktop.youtube.DesktopPageBoundTokenRuntime
import com.metrolist.innertubex.extraction.TokenProvider
import com.melodyon.shared.db.MusicDatabase
import com.melodyon.shared.db.OfflineSongAvailability
import com.melodyon.shared.db.daos.DatabaseDao
import com.melodyon.shared.db.getDatabaseBuilder
import com.melodyon.shared.youtube.playback.DesktopTokenProvider
import com.melodyon.shared.youtube.playback.LocalAudioSource
import org.koin.dsl.module

val composeStateModule =
    module {
        single<MusicDatabase> { getDatabaseBuilder().build() }
        single<LocalAudioSource> {
            LocalAudioSource { id, song ->
                DownloadManager.getLocalPlaybackResource(id, song) ?: SongCacheManager.getCachedPlaybackResource(id, song)
            }
        }
        single<DatabaseDao> { get<MusicDatabase>().dao() }
        single<OfflineSongAvailability> { desktopSongOfflineAvailability(get()) }
        single { DesktopPageBoundTokenRuntime() }
        single<TokenProvider> {
            DesktopTokenProvider(get<DesktopPageBoundTokenRuntime>()) {
                val queue = PlayerState.queue.snapshots.value
                queue.displayedItems
                    .asSequence()
                    .drop(queue.currentIndex + 1)
                    .take(2)
                    .map { it.id }
                    .toList()
            }
        }
    }
