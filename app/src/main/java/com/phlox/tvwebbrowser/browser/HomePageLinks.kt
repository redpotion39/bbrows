package com.phlox.tvwebbrowser.browser

import android.util.Log
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.model.FavoriteItem
import com.phlox.tvwebbrowser.model.HomePageLink
import com.phlox.tvwebbrowser.data.FavoritesRepository
import com.phlox.tvwebbrowser.data.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Links shown on the built-in home page: bookmarks, latest or most visited history items.
 * The bookmarks are the ones right in the folder of [Config.homePageBookmarksFolder] (the Home page folder by
 * default, and when the chosen one is gone), in its order: a link's order is its place on the page, and the
 * page leaves out those it has no room for.
 */
class HomePageLinks(
    private val config: Config,
    private val history: HistoryRepository,
    private val favorites: FavoritesRepository
) {
    companion object {
        private const val TAG = "HomePageLinks"
    }

    private val _links = MutableStateFlow<List<HomePageLink>>(emptyList())
    /** Can be read from any thread (home page scripts ask for it on a background thread) */
    val links: StateFlow<List<HomePageLink>> = _links.asStateFlow()

    suspend fun load() {
        Log.d(TAG, "load")
        if (config.homePageMode != Config.HomePageMode.HOME_PAGE) return
        when (config.homePageLinksMode) {
            Config.HomePageLinksMode.MOST_VISITED -> {
                _links.value = history.mostVisited()
                    .map { HomePageLink.fromHistoryItem(it) }
            }
            Config.HomePageLinksMode.LATEST_HISTORY -> {
                _links.value = history.last(8)
                    .map { HomePageLink.fromHistoryItem(it) }
            }
            Config.HomePageLinksMode.BOOKMARKS -> {
                val folder = favorites.existingFolder(config.homePageBookmarksFolder)
                if (!config.homePageCleanedV2) {
                    val legacyUrls = setOf(
                        "https://www.wikipedia.org",
                        "https://www.reddit.com",
                        "https://mytuner-radio.com/",
                        "https://www.instagram.com",
                        "https://www.imdb.com/",
                        "https://www.ebay.com/",
                        "https://itch.io/games/input-gamepad/platform-web",
                        "https://www.bbc.com/news"
                    )
                    val current = favorites.homePageLinkBookmarks(FavoritesRepository.HOME_PAGE)
                    var removedLegacy = false
                    for (item in current) {
                        if (item.url in legacyUrls) {
                            favorites.delete(item.id)
                            removedLegacy = true
                        }
                    }
                    if (removedLegacy) {
                        config.initialBookmarksSuggestionsLoaded = false
                    }
                    config.homePageCleanedV2 = true
                }
                val bookmarks = favorites.homePageLinkBookmarks(folder)
                //the first start fills the Home page folder only, a chosen folder stays as the user left it
                if (folder == FavoritesRepository.HOME_PAGE && bookmarks.isEmpty() &&
                    !config.initialBookmarksSuggestionsLoaded) {
                    val suggestions = initialBookmarksSuggestions() ?: return
                    for (s in suggestions) {
                        favorites.save(s)
                    }
                    config.initialBookmarksSuggestionsLoaded = true
                    _links.value = suggestions.mapIndexed { i, item -> HomePageLink.fromBookmarkItem(item, i) }
                } else {
                    _links.value = bookmarks.mapIndexed { i, item -> HomePageLink.fromBookmarkItem(item, i) }
                }
            }
        }
    }

    /** The links that follow the removed one move up a place */
    suspend fun remove(link: HomePageLink) {
        _links.update { it - link }
        link.favoriteId?.let {
            favorites.delete(it)
        }
        load()
    }

    /**
     * Saves the bookmark edited (or created, if its id is 0: it goes after the others of the folder the page
     * shows) by the user and updates the links.
     */
    suspend fun onBookmarkEdited(item: FavoriteItem) {
        if (item.id == 0L) {
            favorites.add(item, favorites.existingFolder(config.homePageBookmarksFolder), last = true)
        } else {
            favorites.save(item)
        }
        load()
    }

    suspend fun markRecommendationAsUseful(bookmarkOrder: Int) {
        val link = _links.value.find { it.order == bookmarkOrder } ?: return
        val favoriteId = link.favoriteId ?: return
        if (link.validUntil == null) return
        favorites.markAsUseful(favoriteId)
    }

    private fun initialBookmarksSuggestions(): List<FavoriteItem>? {
        //There was loading of default bookmarks from app's home page (with affiliate links).
        // Now its just hardcoded. I left them as json array just for convenience to copy the same
        // data used in corresponding home html page where them useful for debug
        return try {
            val jsonArray = JSONArray("""[
        {"title": "Google", "url":"https://www.google.com", "favicon": "favicons/google.png"},
        {"title": "YouTube", "url":"https://www.youtube.com", "favicon": "https://www.youtube.com/favicon.ico"}
    ]""")
            val result = mutableListOf<FavoriteItem>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val favorite = FavoriteItem()
                favorite.title = jsonObject.getString("title")
                favorite.url = jsonObject.getString("url")
                favorite.favicon = jsonObject.opt("favicon") as String?
                favorite.destUrl = jsonObject.opt("dest_url") as String?
                favorite.description = jsonObject.opt("description") as String?
                favorite.order = i
                favorite.homePageBookmark = true
                val validUntil = jsonObject.opt("valid_until") as String?
                if (validUntil != null) {
                    favorite.validUntil = dateFormat.parse(validUntil)
                }
                result.add(favorite)
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
