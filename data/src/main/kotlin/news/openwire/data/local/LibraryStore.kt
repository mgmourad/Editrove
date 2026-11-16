// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import news.openwire.domain.model.Article

/** Saved (bookmarked) stories and liked ids. */
interface Library {
    val saved: StateFlow<List<Article>>
    val liked: StateFlow<Set<String>>
    fun toggleSaved(article: Article): Boolean
    fun toggleLiked(article: Article): Boolean
    /** Outlets of liked and saved stories, one entry per story: the on-device interest signal. */
    fun interestDomains(): List<String>
}

/** In-memory library for tests. */
class InMemoryLibrary : Library {
    private val _saved = MutableStateFlow<List<Article>>(emptyList())
    override val saved: StateFlow<List<Article>> = _saved.asStateFlow()
    private val _liked = MutableStateFlow<Set<String>>(emptySet())
    override val liked: StateFlow<Set<String>> = _liked.asStateFlow()
    override fun toggleSaved(article: Article): Boolean {
        val now = _saved.value.none { it.id == article.id }
        _saved.value = if (now) listOf(article) + _saved.value else _saved.value.filterNot { it.id == article.id }
        return now
    }
    private val likedDomains = mutableListOf<String>()
    override fun toggleLiked(article: Article): Boolean {
        val now = article.id !in _liked.value
        _liked.value = if (now) _liked.value + article.id else _liked.value - article.id
        article.domain?.let { if (now) likedDomains += it else likedDomains.remove(it) }
        return now
    }
    override fun interestDomains(): List<String> = likedDomains + _saved.value.mapNotNull { it.domain }
}

/**
 * On-device reading library: bookmarked (saved) stories and liked story ids.
 * Purely local — nothing ever leaves the device. Saved stories keep a full copy
 * of the article so the Saved tab works offline and after the feed rotates.
 */
class LibraryStore(context: Context) : Library {

    private val prefs = context.getSharedPreferences("openwire_library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _saved = MutableStateFlow(loadSaved())
    /** Saved stories, most recently saved first. */
    override val saved: StateFlow<List<Article>> = _saved.asStateFlow()

    private val _liked = MutableStateFlow(prefs.getStringSet(KEY_LIKED, emptySet()).orEmpty().toSet())
    override val liked: StateFlow<Set<String>> = _liked.asStateFlow()

    override fun toggleSaved(article: Article): Boolean {
        var nowSaved = false
        _saved.update { current ->
            if (current.any { it.id == article.id }) {
                current.filterNot { it.id == article.id }
            } else {
                nowSaved = true
                listOf(article) + current
            }
        }
        persistSaved()
        return nowSaved
    }

    override fun toggleLiked(article: Article): Boolean {
        var nowLiked = false
        _liked.update { current ->
            if (article.id in current) current - article.id else { nowLiked = true; current + article.id }
        }
        // Remember the outlet of each like (most recent 200) for Curated tuning.
        val domains = likedDomains().toMutableList()
        article.domain?.takeIf { it.isNotEmpty() }?.let { d -> if (nowLiked) domains += d else domains.remove(d) }
        prefs.edit().putStringSet(KEY_LIKED, _liked.value).putString(KEY_LIKED_DOMAINS, domains.takeLast(200).joinToString("\n")).apply()
        return nowLiked
    }

    private fun likedDomains(): List<String> = prefs.getString(KEY_LIKED_DOMAINS, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    override fun interestDomains(): List<String> = likedDomains() + _saved.value.mapNotNull { it.domain }

    private fun loadSaved(): List<Article> = try {
        prefs.getString(KEY_SAVED, null)
            ?.let { json.decodeFromString(ListSerializer(SavedArticle.serializer()), it) }
            ?.map { it.toArticle() }
            .orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    private fun persistSaved() {
        val encoded = json.encodeToString(
            ListSerializer(SavedArticle.serializer()),
            _saved.value.map { SavedArticle.from(it) },
        )
        prefs.edit().putString(KEY_SAVED, encoded).apply()
    }

    @Serializable
    private data class SavedArticle(
        val id: String,
        val title: String,
        val source: String? = null,
        val domain: String? = null,
        val originalUrl: String? = null,
        val publishedAt: Long? = null,
        val status: String? = null,
        val badge: String? = null,
        val targetUrl: String? = null,
        val archiveUrl: String? = null,
        val imageUrl: String? = null,
    ) {
        fun toArticle() = Article(id, title, source, domain, originalUrl, publishedAt, status, badge, targetUrl, archiveUrl, imageUrl)

        companion object {
            fun from(a: Article) = SavedArticle(
                a.id, a.title, a.source, a.domain, a.originalUrl, a.publishedAt,
                a.status, a.badge, a.targetUrl, a.archiveUrl, a.imageUrl,
            )
        }
    }

    private companion object {
        const val KEY_SAVED = "saved_articles"
        const val KEY_LIKED = "liked_ids"
        const val KEY_LIKED_DOMAINS = "liked_domains"
    }
}
