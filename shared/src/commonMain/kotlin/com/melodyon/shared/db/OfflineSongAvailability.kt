package com.melodyon.shared.db

fun interface OfflineSongAvailability {
    suspend fun songIds(): Set<String>
}
