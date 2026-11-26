package com.clibeats.data.home

import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeShelf

/**
 * Search's playlist row: playlists on your Home page that match the search come first (charts, Top 50s,
 * your languages' playlists), marked "On your Home", then YouTube Music's results without repeats.
 */
internal fun rankPlaylistsWithHome(
    query: String,
    found: List<HomeItem.Collection>,
    home: List<HomeItem.Collection>,
): List<HomeItem.Collection> {
    val words = searchWords(query)
    if (words.isEmpty()) return found
    val foundIds = found.mapTo(HashSet()) { it.browseId }
    val onHome =
        home
            .distinctBy { it.browseId }
            .filter { it.browseId in foundIds || matches(it, words) }
            .map { it.copy(subtitle = listOf(ON_HOME, it.subtitle).filter(String::isNotBlank).joinToString(" · ")) }
    val homeIds = onHome.mapTo(HashSet()) { it.browseId }
    return onHome + found.filterNot { it.browseId in homeIds }
}

/** The playlists among Home's rows, in the order they're shown. */
internal fun homePlaylists(shelves: List<HomeShelf>): List<HomeItem.Collection> =
    shelves
        .flatMap { it.items }
        .filterIsInstance<HomeItem.Collection>()
        .filter { it.kind == CollectionKind.PLAYLIST }

/** Every word of the search appears in the playlist's name or subtitle ("top 50 india", "tamil hits"). */
private fun matches(
    item: HomeItem.Collection,
    words: List<String>,
): Boolean {
    val text = "${item.title} ${item.subtitle}".lowercase()
    return words.all { it in text }
}

private fun searchWords(query: String): List<String> =
    query
        .lowercase()
        .split(Regex("""[\s\-–—·•,]+"""))
        .filter { it.length >= MIN_WORD }

internal const val ON_HOME = "On your Home"
private const val MIN_WORD = 2
