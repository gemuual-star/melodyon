package com.melodyon.shared.db

import com.melodyon.shared.db.entities.Event

suspend fun MusicDatabase.deleteHistoryEvents(
    events: List<Event>,
    removeRemote: (suspend (List<String>) -> Unit)? = null,
) {
    if (events.isEmpty()) return
    val tokens = events.mapNotNull { it.remoteFeedbackToken?.takeIf(String::isNotBlank) }.distinct()
    if (tokens.isNotEmpty()) removeRemote?.invoke(tokens)
    withDatabaseTransaction {
        events.forEach { dao().delete(it) }
    }
}
