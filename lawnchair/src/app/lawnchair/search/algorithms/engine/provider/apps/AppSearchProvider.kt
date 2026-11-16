package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.filterHiddenApps
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.StringMatcherUtility
import java.text.Normalizer
import java.util.Locale

object AppSearchProvider {

    private val DIACRITICS_REMOVE_PATTERN = "\\p{M}+".toRegex()
    private val WHITESPACE_REGEX = Regex("\\s+")

    // NFKD normalization + regex replacement is the most expensive part of search and was
    // previously recomputed for every app on every keystroke. Cache it by the *content* of the
    // title (an immutable String), so the entry self-invalidates when a label changes and no
    // manual invalidation on app install/remove/update is needed. Access-order + bound keeps
    // memory bounded even if many dynamic shortcuts with unique titles pass through here.
    private val normalizedTitleCache = object : java.util.LinkedHashMap<String, String>(
        256,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 512
        }
    }

    private fun normalizedTitle(title: CharSequence?): String = synchronized(normalizedTitleCache) {
        if (title.isNullOrEmpty()) return ""
        val raw = title.toString()
        normalizedTitleCache[raw] ?: stripDiacritics(raw).also { normalizedTitleCache[raw] = it }
    }

    fun search(context: Context, query: String, allApps: AllAppsList): List<SearchResult.App> {
        val prefs = PreferenceManager2.getInstance(context)
        val hiddenApps = prefs.hiddenApps.firstCached()
        val hiddenAppsInSearch = prefs.hiddenAppsInSearch.firstCached()
        val maxAppResults = prefs.maxAppSearchResultCount.firstCached()
        val enableFuzzySearch = prefs.enableFuzzySearch.firstCached()

        val queryNormalized = stripDiacritics(query).lowercase(Locale.getDefault())

        val appResults = if (enableFuzzySearch) {
            fuzzySearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        } else {
            normalSearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        }

        return appResults.map { SearchResult.App(data = it) }
    }

    private fun normalSearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter { StringMatcherUtility.matches(query, normalizedTitle(it.title), matcher) }
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .take(maxResultsCount)
            .toList()
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        val filteredApps = apps.asSequence()
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .toList()

        // Precompute query tokens once for all apps to avoid redundant splits per item
        val qTokens = query.split(WHITESPACE_REGEX).filter { it.isNotBlank() }

        return filteredApps
            .mapNotNull { app ->
                val matchResult = AppMatcher.match(normalizedTitle(app.title), query, qTokens)
                if (matchResult.type == MatchType.NO_MATCH) null else Pair(app, matchResult)
            }
            .sortedWith(
                compareBy(
                    { it.second.type.priority },
                    { -it.second.score },
                ),
            )
            .map { it.first }
            .take(maxResultsCount)
    }

    private fun stripDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKD)
            .replace(DIACRITICS_REMOVE_PATTERN, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }
}
