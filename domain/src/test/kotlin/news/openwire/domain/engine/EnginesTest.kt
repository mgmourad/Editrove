// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article
import news.openwire.domain.model.DomainRule
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.model.SortMode
import news.openwire.domain.model.Strategy
import news.openwire.domain.model.Topic
import news.openwire.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicCatalogTest {
    @Test fun editionDrivesNationalAndLocalTopics() {
        val t = TopicRegistry.defaultTopics(LocalEdition("London", "England", "GB"))
        assertEquals("UK", t.first { it.id == "US" }.label)
        assertTrue(t.first { it.id == "US" }.liveUrl!!.contains("gl=GB"))
        assertEquals("London", t.first { it.id == "WASHINGTON_DC" }.label)
        assertTrue(t.first { it.id == "WASHINGTON_DC" }.liveUrl!!.contains("q=%22London%22"))
    }

    @Test fun defaultLocalQueryIsWashingtonDc() {
        assertTrue(TopicRegistry.defaultTopics().first { it.id == "WASHINGTON_DC" }.liveUrl!!.contains("q=%22Washington+D.C.%22"))
    }

    @Test fun orderAndVisibilityApplied() {
        val custom = TopicRegistry.newCustomTopic("Gaming", listOf("video games", "esports"), emptyList(), LocalEdition.DEFAULT, 1234L)
        val all = TopicRegistry.buildTopics(LocalEdition.DEFAULT, listOf(custom), hidden = setOf("TOP"), order = listOf(custom.id, "AI"))
        assertEquals(custom.id, all[0].id)
        assertEquals("AI", all[1].id)
        assertFalse(all.first { it.id == "TOP" }.isVisible)
        assertTrue(custom.liveUrl!!.contains("q=%22video+games%22+OR+esports"))
    }

    @Test fun neverHidesEverything() {
        val ids = TopicRegistry.defaultTopics().map { it.id }.toSet()
        assertTrue(TopicRegistry.buildTopics(LocalEdition.DEFAULT, emptyList(), ids, null).first().isVisible)
    }

    @Test fun keywordsQueryFormatting() {
        assertEquals("\"Vancouver Canucks\" OR NHL OR hockey", TopicRegistry.formatKeywordsQuery(TopicRegistry.parseCommaKeywords("Vancouver Canucks, NHL, hockey")))
    }

    @Test fun moveSwapsNeighbours() {
        assertEquals(listOf("B", "A", "C"), TopicRegistry.move(listOf("A", "B", "C"), "B", up = true))
        assertEquals(listOf("A", "B", "C"), TopicRegistry.move(listOf("A", "B", "C"), "A", up = true))
    }
}

class SourceLibraryTest {
    private val catalog = listOf(
        FeedSource("bbc", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", topics = listOf("TOP")),
        FeedSource("verge", "The Verge", "https://www.theverge.com/rss/index.xml", topics = listOf("TECHNOLOGY"), category = "Technology"),
        FeedSource("hn", "Hacker News", "https://news.ycombinator.com/rss", topics = listOf("TECHNOLOGY"), kind = "aggregator", category = "Technology"),
    )
    private val top = Topic("TOP", "Top Stories")

    @Test fun feedsForTopicRespectDisabled() {
        assertEquals(listOf("bbc"), SourceLibrary.feedsForTopic(top, catalog, emptyList(), emptySet()).map { it.id })
        assertTrue(SourceLibrary.feedsForTopic(top, catalog, emptyList(), setOf("https://feeds.bbci.co.uk/news/rss.xml")).isEmpty())
        assertTrue(SourceLibrary.isCustomized(top, catalog, emptyList(), setOf("https://feeds.bbci.co.uk/news/rss.xml")))
        assertFalse(SourceLibrary.isCustomized(top, catalog, emptyList(), emptySet()))
    }

    @Test fun customFeedValidationAndMerge() {
        val one = SourceLibrary.addCustomFeed(emptyList(), "Polygon", "https://www.polygon.com/rss/index.xml", listOf("top"), 99, 1L)
        assertEquals(10, one.single().maxArticles)
        assertEquals(listOf("TOP"), one.single().topics)
        val two = SourceLibrary.addCustomFeed(one, "Polygon", "https://www.polygon.com/rss/index.xml", listOf("TECHNOLOGY"), 3, 2L)
        assertEquals(1, two.size)
        assertEquals(listOf("TOP", "TECHNOLOGY"), two.single().topics)
        assertEquals(7, two.single().maxArticles)
        assertTrue(runCatching { SourceLibrary.addCustomFeed(emptyList(), "x", "ftp://a.b/rss", listOf("TOP"), 8, 1L) }.isFailure)
    }

    @Test fun groupsByCategoryThenKind() {
        val groups = SourceLibrary.groupSources(catalog)
        assertEquals(listOf("News", "Technology"), groups.map { it.category })
        assertEquals(listOf("hn"), groups[1].aggregators.map { it.id })
        assertEquals(listOf("verge"), groups[1].publishers.map { it.id })
        assertEquals(listOf("Technology"), SourceLibrary.groupSources(catalog, "verge").map { it.category })
    }
}

class PreviewMatcherTest {
    @Test fun cleansMarkupAndEntities() {
        assertEquals("Hello & welcome ©", PreviewMatcher.cleanPreviewText("<p>Hello &amp; <b>welcome</b> &#169;</p><script>x()</script><ul><li>menu</li></ul>"))
    }

    @Test fun matchesWholeWordsInTitleOrSummary() {
        val a = Article("1", "OpenAI ships a model", summary = "Machine learning news")
        assertEquals("OpenAI", PreviewMatcher.matchKeyword(a, listOf("OpenAI")))
        assertEquals("machine learning", PreviewMatcher.matchKeyword(a, listOf("machine learning")))
        assertNull(PreviewMatcher.matchKeyword(Article("2", "Painting ideas"), listOf("AI")))
        assertEquals(1, PreviewMatcher.filterKeywordArticles(listOf(a, Article("3", "Sports")), listOf("OpenAI")).size)
    }

    @Test fun advancedOperatorsAreNotMatchedLocally() {
        assertTrue(PreviewMatcher.literalKeywords(listOf("site:bbc.com", "-war", "a AND b")).isEmpty())
        assertEquals(listOf("cats", "dogs"), PreviewMatcher.literalKeywords(listOf("cats OR dogs")))
    }
}

class ImageExtractorTest {
    @Test fun rejectsLogosAndSquares() {
        assertFalse(ImageExtractor.isEditorialImage("https://x.com/static/logo.png"))
        assertFalse(ImageExtractor.isEditorialImage("https://x.com/a.svg"))
        assertFalse(ImageExtractor.isEditorialImage("https://x.com/photo.jpg", 300, 300))
        assertTrue(ImageExtractor.isEditorialImage("https://x.com/photo.jpg", 1200, 675))
    }

    @Test fun prefersOpenGraphThenArticleImages() {
        val html = """
            <meta content="https://cdn.x.com/og.jpg" property="og:image">
            <article><img src="/img/lead.jpg" width="800" height="450"><img src="/img/logo.png"></article>
        """
        assertEquals(
            listOf("https://cdn.x.com/og.jpg", "https://x.com/img/lead.jpg"),
            ImageExtractor.candidatesFromHtml(html, "https://x.com/story"),
        )
    }
}

class SharedUrlTest {
    @Test fun extractsFromSharedText() {
        assertEquals("https://www.wsj.com/articles/x-123", SharedUrl.extract("Read this https://www.wsj.com/articles/x-123."))
        assertEquals("https://nytimes.com/2026/a.html", SharedUrl.extract("nytimes.com/2026/a.html"))
        assertEquals("https://ft.com/content/abc", SharedUrl.extract("Check ft.com/content/abc now"))
        assertEquals("", SharedUrl.extract("no links here"))
        assertEquals("https://bbc.com/x", SharedUrl.extract("https%3A%2F%2Fbbc.com%2Fx"))
    }
}

class FeedOrderingTest {
    private val a = Article("a", "A", originalUrl = "https://x.com/a", publishedAt = 1)
    private val b = Article("b", "B", originalUrl = "https://x.com/b", publishedAt = 3)
    private val c = Article("c", "C", originalUrl = "https://x.com/c", publishedAt = 2)

    @Test fun sortModes() {
        assertEquals(listOf("a", "b", "c"), FeedOrdering.apply(listOf(a, b, c), SortMode.CURATED).map { it.id })
        assertEquals(listOf("b", "c", "a"), FeedOrdering.apply(listOf(a, b, c), SortMode.NEWEST).map { it.id })
        assertEquals(listOf("a", "c", "b"), FeedOrdering.apply(listOf(a, b, c), SortMode.OLDEST).map { it.id })
    }

    @Test fun firstObservationIsBaselineOnly() {
        assertTrue(FeedOrdering.newStories(listOf(a, b), null).isEmpty())
        val seen = listOf(a).map(FeedOrdering::storyKey).toSet()
        assertEquals(listOf("b"), FeedOrdering.newStories(listOf(a, b), seen).map { it.id })
    }
}

class PublisherUrlResolverTest {
    @Test fun resolvesFromPublishedMap() {
        val map = PublisherUrlResolver.sanitizeMap(mapOf("CBMiAbc" to "https://www.reuters.com/x", "bad" to "https://www.google.com/x"))
        assertEquals("https://www.reuters.com/x", PublisherUrlResolver.resolve("https://news.google.com/rss/articles/CBMiAbc?oc=5", map))
        assertEquals("", PublisherUrlResolver.resolve("https://news.google.com/rss/articles/unknown", map))
        assertEquals("https://bbc.com/a", PublisherUrlResolver.resolve("https://bbc.com/a", map))
        assertFalse("bad" in map)
    }
}

class SettingsCodecTest {
    @Test fun roundTripsAndireDefensive() {
        val topic = TopicRegistry.newCustomTopic("Gaming", listOf("esports"), emptyList(), LocalEdition.DEFAULT, 99L)
        val feed = SourceLibrary.addCustomFeed(emptyList(), "Polygon", "https://www.polygon.com/rss/index.xml", listOf(topic.id), 8, 1L)
        val settings = UserSettings(
            edition = LocalEdition("Seattle", "Washington", "US"), customTopics = listOf(topic), hiddenTopics = setOf("SPORTS"),
            customFeeds = feed, disabledSources = setOf("https://feeds.bbci.co.uk/news/rss.xml"), sortMode = SortMode.NEWEST,
            notificationsEnabled = true, notifyTopics = setOf("TOP"),
        )
        val all = TopicRegistry.buildTopics(settings.edition, settings.customTopics, settings.hiddenTopics, null)
        val text = SettingsCodec.export(settings, mapOf("wsj.com" to DomainRule("wsj.com", Strategy.WAYBACK, isCustom = true)), all, "2026-11-15T00:00:00Z")
        val back = SettingsCodec.import(text)
        assertEquals(settings.edition, back.settings.edition)
        assertEquals(listOf(topic.id), back.settings.customTopics.map { it.id })
        assertEquals(setOf("SPORTS"), back.settings.hiddenTopics)
        assertEquals(feed.map { it.url }, back.settings.customFeeds.map { it.url })
        assertEquals(SortMode.NEWEST, back.settings.sortMode)
        assertEquals(setOf("TOP"), back.settings.notifyTopics)
        assertEquals(Strategy.WAYBACK, back.paywallRules["wsj.com"]?.strategy)
    }

    @Test fun importsPwaBackupAndMigratesIds() {
        val pwa = """{"version":1,"topics":{"items":[{"id":"WORLD","label":"World","isVisible":false},{"id":"CANADA","label":"Canada"},
            {"id":"CUSTOM_X_1","label":"X","query":"cats, dogs","isCustom":true,"isVisible":true},{"id":"bad"}],
            "order":["CUSTOM_X_1","WORLD","CANADA"],"localEdition":{"city":"Vancouver","region":"British Columbia"}},
            "rssFeeds":{"customFeeds":[{"id":"f1","name":"Feed","url":"https://a.com/rss","topics":["WORLD"]},{"id":"f2","name":"Bad","url":"javascript:x"}],
            "disabledSources":["https://news.google.com/rss"]},
            "paywallRules":{"nytimes.com":{"strategy":"12ft","isCustom":true},"bbc.com":{"strategy":"direct"}}}"""
        val r = SettingsCodec.import(pwa)
        assertEquals("CA", r.settings.edition.country)
        assertEquals(setOf("INTERNATIONAL"), r.settings.hiddenTopics)
        assertEquals(listOf("CUSTOM_X_1", "INTERNATIONAL"), r.settings.topicOrder)
        assertEquals(listOf("cats", "dogs"), r.settings.customTopics.single().keywords)
        assertEquals(listOf("INTERNATIONAL"), r.settings.customFeeds.single().topics)
        assertEquals(setOf("google_news"), r.settings.disabledSources)
        assertEquals(mapOf("nytimes.com" to Strategy.READER), r.paywallRules.mapValues { it.value.strategy })
        assertTrue(r.notes.single().contains("CANADA"))
    }

    @Test fun rejectsNonBackups() {
        assertTrue(runCatching { SettingsCodec.import("{\"hello\":1}") }.isFailure)
        assertTrue(runCatching { SettingsCodec.import("not json") }.isFailure)
    }
}
