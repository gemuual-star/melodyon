package com.melodyon.shared.soundcloud

import com.melodyon.shared.network.createHttpClient

/** Single anonymous SoundCloud client shared by search UI and the stream resolver (one cached client_id). */
object GlobalSoundCloud {
    val api: SoundCloudApi by lazy { SoundCloudApi(createHttpClient()) }
}
