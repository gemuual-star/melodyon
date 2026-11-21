package com.melodyon.shared.api

class PlaylistNotFoundException(
    val playlistId: String,
) : Exception()
