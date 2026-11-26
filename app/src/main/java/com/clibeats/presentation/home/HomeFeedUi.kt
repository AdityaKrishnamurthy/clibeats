@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "ForbiddenImport")

package com.clibeats.presentation.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeMood
import com.clibeats.domain.model.Track

internal class LanguageActions(
    val edit: () -> Unit = {},
    val skip: () -> Unit = {},
)

internal class FeedActions(
    val playSong: (String, List<Track>, Int) -> Unit,
    val songMenu: (Track) -> Unit,
    val open: (HomeItem.Collection) -> Unit,
    val selectMood: (HomeMood) -> Unit,
    val language: LanguageActions = LanguageActions(),
    val rowState: ((String) -> LazyListState)? = null,
)

@Suppress("LongParameterList")
internal fun LazyListScope.homeFeed(
    state: FeedState,
    moods: List<HomeMood>,
    selectedMood: HomeMood?,
    playingTrackId: String?,
    actions: FeedActions,
    emptyText: String? = "Nothing to show yet.",
    languagesLabel: String? = null,
    suggestLanguages: Boolean = false,
) {
    if (suggestLanguages) item(key = "languages_hint") { LanguageHintCard(actions) }
    if (moods.isNotEmpty() || languagesLabel != null) {
        item(key = "moods") { LanguageChipsRow(moods, selectedMood, actions, languagesLabel) }
    }
    when (state) {
        FeedState.Loading -> item(key = "feed_loading") { FeedMessage("Loading your home page…", busy = true) }
        FeedState.MoodFailed ->
            item(key = "feed_mood_failed") {
                val mood = selectedMood?.title ?: "this mood"
                FeedMessage("Couldn't load $mood. Check the connection or pick another.")
            }
        is FeedState.Loaded -> {
            if (state.feed.shelves.isEmpty() && emptyText != null) item(key = "feed_empty") { FeedMessage(emptyText) }
            state.feed.shelves.forEachIndexed { index, shelf ->
                item(key = "shelf_${index}_${shelf.title}") {
                    ShelfTitle(shelf)
                    if (shelf.items.all { it is HomeItem.Song }) {
                        QuickResumeSection(shelf.title, shelf.songs, playingTrackId, actions)
                    } else {
                        ChartsSection(shelf, playingTrackId, actions)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
