package com.melodyon.shared.ui.mobile

import com.melodyon.shared.model.AlbumItem
import com.melodyon.shared.model.ArtistItem
import com.melodyon.shared.model.BuiltinPlaylist
import com.melodyon.shared.model.HomeSection
import com.melodyon.shared.model.PlaylistItem
import com.melodyon.shared.model.PodcastItem
import com.melodyon.shared.model.YTItem
import com.melodyon.shared.ui.components.CollectionPreviews
import com.melodyon.shared.ui.navigation.Screen
import com.melodyon.shared.ui.settings.SettingsPageId
import io.ktor.http.URLProtocol
import io.ktor.http.Url

internal sealed interface MobileRoute {
    data object Home : MobileRoute

    data object Search : MobileRoute

    data object Library : MobileRoute

    data object EasterEgg : MobileRoute

    data class Settings(
        val initialPage: SettingsPageId? = null,
    ) : MobileRoute

    data object History : MobileRoute

    data object Statistics : MobileRoute

    data object Integrations : MobileRoute

    data class Login(
        val isSwitchingChannel: Boolean,
    ) : MobileRoute

    data object TokenLogin : MobileRoute

    data object About : MobileRoute

    data class Album(
        val id: String,
        val title: String,
    ) : MobileRoute

    data class Artist(
        val id: String,
        val name: String,
    ) : MobileRoute

    data class AboutArtist(
        val artist: ArtistItem,
        val catalogTitles: List<String>,
    ) : MobileRoute

    data class Playlist(
        val id: String,
        val title: String,
    ) : MobileRoute

    data class Podcast(
        val id: String,
        val title: String = "",
    ) : MobileRoute

    data class Browse(
        val destination: MobileBrowseDestination,
    ) : MobileRoute

    data class LocalShelf(
        val key: String,
        val section: HomeSection,
    ) : MobileRoute

    data class SongCollection(
        val type: String,
        val title: String,
        val localPlaylistId: String? = null,
    ) : MobileRoute
}

internal sealed interface MobileDeepLink {
    data class Tab(
        val route: MobileRoute,
        val query: String? = null,
    ) : MobileDeepLink

    data class Destination(
        val route: MobileRoute,
    ) : MobileDeepLink

    data class Video(
        val id: String,
    ) : MobileDeepLink

    data object Player : MobileDeepLink
}

internal fun routeForMobileItem(item: YTItem): MobileRoute? =
    when (item) {
        is PodcastItem -> {
            MobileRoute.Podcast(item.id, item.title)
        }

        is PlaylistItem -> {
            val builtin = BuiltinPlaylist.fromId(item.id)
            when {
                builtin == BuiltinPlaylist.HISTORY -> MobileRoute.History
                builtin?.collectionType != null -> MobileRoute.SongCollection(builtin.collectionType, item.title)
                item.id.startsWith("MPSP") -> MobileRoute.Podcast(item.id, item.title)
                item.isLocal -> MobileRoute.SongCollection("local", item.title, item.id)
                item.id.isLikedMusicPlaylistId() -> MobileRoute.SongCollection("liked", item.title)
                else -> MobileRoute.Playlist(item.id, item.title)
            }
        }

        is AlbumItem -> {
            MobileRoute.Album(item.id, item.title)
        }

        is ArtistItem -> {
            CollectionPreviews.record(item)
            MobileRoute.Artist(item.id, item.title)
        }

        else -> {
            item.mobileBrowseDestination()?.let { destination ->
                when {
                    destination.id.startsWith("MPREb_") -> {
                        MobileRoute.Album(destination.id, destination.title)
                    }

                    destination.id.startsWith("UC") || destination.id.startsWith("FEmusic_library_privately_owned_artist") -> {
                        MobileRoute.Artist(destination.id, destination.title)
                    }

                    destination.id.startsWith("MPSP") -> {
                        MobileRoute.Podcast(destination.id, destination.title)
                    }

                    destination.id.startsWith("VL") || destination.id.startsWith("PL") -> {
                        MobileRoute.Playlist(destination.id, destination.title)
                    }

                    else -> {
                        MobileRoute.Browse(destination)
                    }
                }
            }
        }
    }

internal fun String.isLikedMusicPlaylistId(): Boolean = this == "LM" || this == "VLLM" || startsWith("LM:")

private val youtubeVideoId = Regex("^[A-Za-z0-9_-]{11}$")
internal fun resolveMobileDeepLink(callback: String): MobileDeepLink? {
    val uri = runCatching { Url(callback.replace("melodyon://", "https://app.melodyon/")) }.getOrNull()
    val path = uri?.encodedPath?.trim('/') ?: callback.removePrefix("melodyon://").substringBefore('?').trim('/')
    if (!callback.startsWith("melodyon://")) {
        return null
    }
    return when (path) {
        "watch" -> {
            val videoId = uri?.parameters?.get("v") ?: path.removePrefix("watch/").substringBefore('/')
            videoId.takeIf(String::isNotBlank)?.let { MobileDeepLink.Video(it) }
        }

        "party" -> {
            val code = uri?.parameters?.get("r") ?: uri?.parameters?.get("code") ?: path.removePrefix("party/").substringBefore('/')
            partyInvitation(code)
        }

        "home" -> {
            MobileDeepLink.Tab(MobileRoute.Home)
        }

        "library" -> {
            MobileDeepLink.Tab(MobileRoute.Library)
        }

        "search" -> {
            MobileDeepLink.Tab(
                MobileRoute.Search,
                uri
                    ?.parameters
                    ?.get("q")
                    ?.trim()
                    ?.takeIf(String::isNotBlank),
            )
        }

        "player" -> {
            MobileDeepLink.Player
        }

        "history" -> {
            MobileDeepLink.Destination(MobileRoute.History)
        }

        "statistics" -> {
            MobileDeepLink.Destination(MobileRoute.Statistics)
        }

        "integrations" -> {
            MobileDeepLink.Destination(MobileRoute.Integrations)
        }

        "settings" -> {
            MobileDeepLink.Destination(MobileRoute.Settings())
        }

        "about" -> {
            MobileDeepLink.Destination(MobileRoute.About)
        }

        "login" -> {
            MobileDeepLink.Destination(MobileRoute.Login(isSwitchingChannel = false))
        }

        "token-login" -> {
            MobileDeepLink.Destination(MobileRoute.TokenLogin)
        }

        else -> {
            fun id(
                prefix: String,
                parameter: String = "id",
            ): String? =
                uri
                    ?.parameters
                    ?.get(parameter)
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: path
                        .takeIf { it.startsWith(prefix) }
                        ?.removePrefix(prefix)
                        ?.substringBefore('/')
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
            when {
                path.startsWith("party/") || path == "party" -> {
                    val code = uri?.parameters?.get("r") ?: uri?.parameters?.get("code") ?: path.removePrefix("party/").substringBefore('/')
                    partyInvitation(code)
                }

                path.startsWith("watch/") || path == "watch" -> {
                    val videoId = uri?.parameters?.get("v") ?: path.removePrefix("watch/").substringBefore('/')
                    videoId.takeIf(String::isNotBlank)?.let { MobileDeepLink.Video(it) }
                }

                path.startsWith("playlist/") || path == "playlist" -> {
                    id("playlist/")?.let { MobileDeepLink.Destination(MobileRoute.Playlist(it, "")) }
                }

                path.startsWith("album/") || path == "album" -> {
                    id("album/")?.let { MobileDeepLink.Destination(MobileRoute.Album(it, "")) }
                }

                path.startsWith("artist/") || path == "artist" -> {
                    id("artist/")?.let { MobileDeepLink.Destination(MobileRoute.Artist(it, "")) }
                }

                path.startsWith("podcast/") || path == "podcast" -> {
                    id("podcast/")?.let { MobileDeepLink.Destination(MobileRoute.Podcast(it, "")) }
                }

                path.startsWith("auto-playlist/") || path == "auto-playlist" -> {
                    id("auto-playlist/", "type")?.let { MobileDeepLink.Destination(MobileRoute.SongCollection(it, "")) }
                }

                path.startsWith("browse/") || path == "browse" -> {
                    id("browse/")?.let {
                        MobileDeepLink.Destination(MobileRoute.Browse(MobileBrowseDestination(id = it, params = null, title = "")))
                    }
                }

                youtubeVideoId.matches(path) -> {
                    MobileDeepLink.Video(path)
                }

                else -> {
                    null
                }
            }
        }
    }
}

internal val MobileRoute.motionScreen: Screen
    get() =
        when (this) {
            MobileRoute.Home -> Screen.Home
            MobileRoute.Search -> Screen.Search
            MobileRoute.Library -> Screen.Library
            MobileRoute.EasterEgg -> Screen.EasterEgg
            is MobileRoute.Settings -> Screen.Settings
            MobileRoute.History -> Screen.History
            MobileRoute.Statistics -> Screen.Statistics
            MobileRoute.Integrations -> Screen.Integrations
            is MobileRoute.Login -> if (isSwitchingChannel) Screen.SwitchChannel else Screen.Login
            MobileRoute.TokenLogin -> Screen.TokenLogin
            MobileRoute.About -> Screen.About
            is MobileRoute.Album -> Screen.Album(id)
            is MobileRoute.Artist -> Screen.Artist(id)
            is MobileRoute.AboutArtist -> Screen.AboutArtist(artist.id, artist.title, artist)
            is MobileRoute.Playlist -> Screen.Playlist(id)
            is MobileRoute.Podcast -> Screen.Podcast(id)
            is MobileRoute.Browse -> Screen.Browse(destination.id, destination.params, destination.title)
            is MobileRoute.LocalShelf -> Screen.HomeSection(key, section.title, section)
            is MobileRoute.SongCollection -> Screen.AutoPlaylist(type)
        }
