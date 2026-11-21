package com.melodyon.shared.soundcloud

import com.melodyon.shared.model.SearchResultPage
import com.melodyon.shared.model.SongItem
import com.melodyon.shared.viewmodels.SearchViewModel
import com.melodyon.shared.viewmodels.canLoadMoreFilteredResults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundCloudSearchTest {
    private fun song(id: String) = SongItem(id = id, title = id, thumbnail = null, artists = emptyList(), album = null)

    @Test
    fun searchPageBecomesFilteredResults() {
        val page = parseSearchPage("""{"collection":[{"id":3,"title":"A","user":{"username":"u"},
            "media":{"transcodings":[{"url":"https://api-v2.soundcloud.com/m","format":{"protocol":"progressive","mime_type":"audio/mpeg"}}]}}],
            "next_href":"https://api-v2.soundcloud.com/search/tracks?offset=30"}""")
        val result = page.toSearchResultPage()
        assertEquals(listOf("sc:3"), result.items.map { it.id })
        assertEquals("https://api-v2.soundcloud.com/search/tracks?offset=30", result.continuation)
    }

    @Test
    fun mergeDropsDuplicatesAndKeepsNewContinuation() {
        val merged = mergeSoundCloudPages(
            SearchResultPage(listOf(song("sc:1"), song("sc:2")), "next1"),
            SearchResultPage(listOf(song("sc:2"), song("sc:3")), null),
        )
        assertEquals(listOf("sc:1", "sc:2", "sc:3"), merged.items.map { it.id })
        assertEquals(null, merged.continuation)
    }

    @Test
    fun soundCloudModeAllowsLoadMore() {
        val state = SearchViewModel.SearchUiState(query = "q", soundCloudSelected = true, filteredResults = SearchResultPage(listOf(song("sc:1")), "next"))
        assertTrue(canLoadMoreFilteredResults(state, loadingContinuation = null))
        assertFalse(canLoadMoreFilteredResults(state.copy(soundCloudSelected = false), loadingContinuation = null))
    }
}
