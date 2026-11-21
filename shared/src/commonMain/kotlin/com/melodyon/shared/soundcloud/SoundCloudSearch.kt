package com.melodyon.shared.soundcloud

import com.melodyon.shared.model.SearchResultPage

/** SoundCloud results reuse the filtered-results list; `next_href` plays the role of the continuation token. */
fun ScSearchPage.toSearchResultPage(): SearchResultPage =
    SearchResultPage(items = collection.map { it.toSongItem() }, continuation = nextHref)

/** Appends [next] dropping tracks already shown (SoundCloud pages can overlap). */
fun mergeSoundCloudPages(current: SearchResultPage, next: SearchResultPage): SearchResultPage {
    val seen = current.items.mapTo(HashSet()) { it.id }
    return SearchResultPage(items = current.items + next.items.filter { seen.add(it.id) }, continuation = next.continuation)
}
