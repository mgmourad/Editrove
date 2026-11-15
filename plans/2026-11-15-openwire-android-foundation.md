# OpenWire Android — Foundation & Core Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A buildable, testable Android app that fetches a built-in topic's JSON snapshot, parses it into the domain model, deduplicates/sorts it, and renders a scrollable feed with a topic switcher, opening articles in Chrome Custom Tabs.

**Architecture:** Multi-module Gradle project — `domain` (pure Kotlin/JVM, no Android deps), `data` (Room + Retrofit + DataStore), `app` (Compose UI + Hilt). MVVM + Repository; UI → ViewModel → Repository → (Room cache | remote).

**Tech Stack:** Kotlin 2.x, Jetpack Compose + Material 3, Hilt (KSP), Room, Retrofit + kotlinx.serialization, Coil, Navigation Compose, kotlinx-coroutines, JUnit5.

**Spec:** [`SPEC.md`](../SPEC.md), [`ARCHITECTURE.md`](../ARCHITECTURE.md), [`specs/2026-11-15-openwire-android-design.md`](../specs/2026-11-15-openwire-android-design.md)

> Note on location: this repo's `docs/` is a generated GitHub Pages build output
> (`build.js --target=docs` wipes it), so plans live in `plans/` at the root.

---

## Global Constraints

- Kotlin, Jetpack Compose, Material 3; single-activity; MVVM + Repository.
- `applicationId = news.openwire`, `minSdk = 26`, target/compile latest stable.
- Package root: `news.openwire`.
- The `domain` module **must not import any `android.*` / `androidx.*` (non-test)
  class**; it is a pure JVM module.
- Every new source file starts with `// SPDX-License-Identifier: AGPL-3.0-only`.
- No analytics, ads, or tracking SDKs.
- Built-in topics read `https://openwire.news/` snapshots; base URL is
  configurable via build config.
- Default edition = US / Washington D.C.; the World topic is labeled
  **International** (snapshot file is still `news_world.json`).
- Notifications are not built in this plan (later phase).

## Phasing (this plan is Phase 1)

| Plan | Scope |
|---|---|
| **1 (this)** | Scaffold + domain engine core (DomainRegistry, NewsProcessor) + topic catalog + JSON ingestion + minimal feed UI. |
| 2 | Paywall routing end-to-end (ArchiveChecker, PublisherUrlResolver, ImageExtractor, PreviewService, FeedWeaver) + live Google News RSS (enables US/Washington live fetch) + Custom Tabs routing. |
| 3 | Customization & settings (topics/sources/rules/quick-URL/share-target/local-edition UI, DataStore persistence, import/export). |
| 4 | Offline refresh + notifications (WorkManager, per-topic notify, NotificationManager). |

## Review Focus

The spec's five failure modes most likely to bite a user, and the test that
pins each:

1. **A snapshot article missing `imageUrl`/`archiveUrl`/`badge`, or `pubDate` in
   an unexpected format** (`"Wed, 16 Sep 2026 18:39:36 EDT"`, ISO, or null).
   Expected: the card renders in compact layout, no crash. → Task 5 (DTO mapper).
2. **The same story appearing twice in one snapshot** (two publishers). Expected:
   dedup collapses it. → Task 3 (NewsProcessor).
3. **A publisher domain absent from the registry.** Expected: defaults to
   `DIRECT`, no badge, still tappable. → Task 2 (DomainRegistry).
4. **First launch offline / network error with empty cache.** Expected: explicit
   error state + retry, never a crash or infinite spinner. → Task 6 (ViewModel).
5. **An empty snapshot (topic with zero articles).** Expected: explicit empty
   state, not a blank screen. → Task 6 (UI).

---

### Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle.properties`,
  `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `domain/build.gradle.kts`, `data/build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`,
  `app/src/main/java/news/openwire/MainActivity.kt`,
  `app/src/main/java/news/openwire/OpenWireApp.kt`,
  `app/src/main/java/news/openwire/ui/theme/{Theme.kt,Color.kt,Type.kt}`,
  `app/src/main/res/values/{strings.xml,themes.xml}`

**Interfaces:**
- Produces: three Gradle modules with names `:app`, `:data`, `:domain`; a version
  catalog with keys used by later tasks (`kotlin`, `compose-bom`, `hilt`,
  `room`, `retrofit`, `kotlinx-serialization`, `coil`); `OpenWireApp` annotated
  `@HiltAndroidApp`; a Material 3 `OpenWireTheme`.

- [ ] **Step 1: Write version catalog** with known-good versions (bump to latest stable if newer): AGP 8.7.x, Kotlin 2.1.x, Compose BOM 2025.01.x, Hilt 2.53.x, Room 2.6.x, Retrofit 2.11.x, kotlinx.serialization 1.7.x, Coil 2.7.x, coroutines 1.9.x, okhttp 4.12.x. Enable KSP.

- [ ] **Step 2: Configure the three modules** — `domain` = `kotlin("jvm")`; `data` and `app` = `com.android.library` / `com.android.application` with `minSdk = 26`, `targetSdk`/`compileSdk` latest stable, `applicationId = "news.openwire"` (app only). Wire Hilt (KSP) into `app` and `data`.

- [ ] **Step 3: Add manifest + empty Compose activity** — `MainActivity` sets `OpenWireTheme { Text("OpenWire") }`; theme colors mirror the PWA (`#10b981` primary, dark mode).

- [ ] **Step 4: Verify the build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, an installable `app-debug.apk`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: scaffold multi-module Android project"
```

---

### Task 2: Domain model + DomainRegistry (port `js/domainRegistry.js`)

**Files:**
- Create: `domain/src/main/kotlin/news/openwire/domain/model/{Strategy.kt,DomainRule.kt,LocalEdition.kt}`
- Create: `domain/src/main/kotlin/news/openwire/domain/engine/DomainRegistry.kt`
- Create: `domain/src/main/kotlin/news/openwire/domain/engine/DomainRuleStore.kt`
- Test: `domain/src/test/kotlin/news/openwire/domain/engine/DomainRegistryTest.kt`

**Interfaces:**
- Consumes: nothing (foundation).
- Produces (used by Task 5 and later plans):
  - `enum class Strategy { DIRECT, SMRY, REMOVEPAYWALL, UNWALL, FREEDIUM, READER, ARCHIVE_IS, WAYBACK }`
  - `data class DomainRule(val domain: String, val strategy: Strategy, val paywall: String? = null, val label: String? = null, val isCustom: Boolean = false, val matchedDomain: String? = null)`
  - `interface DomainRuleStore { fun load(): Map<String, DomainRule>; fun save(rules: Map<String, DomainRule>) }`
  - `class DomainRegistry(private val store: DomainRuleStore = InMemoryDomainRuleStore())`
    - `fun getRule(domainInput: String): DomainRule`
    - `fun normalizeDomain(input: String): String`
    - `fun resolvePublisherDomain(input: String): String`
    - `fun getAllRules(): Map<String, DomainRule>`
    - `fun setDomainRule(domainInput: String, strategy: Strategy, paywall: String = "custom", label: String = ""): Boolean`
    - `fun deleteDomainRule(domainInput: String): Boolean`
    - `fun resetToDefaults()`

- [ ] **Step 1: Port the default rules and `KNOWN_PUBLISHER_DOMAINS` maps** verbatim from `js/domainRegistry.js` (`DEFAULT_RULES`, `FREEDIUM_SUPPORTED_DOMAINS`, `KNOWN_PUBLISHER_DOMAINS`).

- [ ] **Step 2: Write the failing tests** — port the cases in `tests/domainRegistry.test.mjs`. Canonical assertions:

```kotlin
@Test fun `hard paywall maps to archive_is`() =
    assertEquals(Strategy.ARCHIVE_IS, registry.getRule("wsj.com").strategy)
@Test fun `subdomain matches parent domain`() =
    assertEquals("ft.com", registry.getRule("markets.ft.com").matchedDomain)
@Test fun `normalizes url to bare domain`() =
    assertEquals("cnn.com", registry.normalizeDomain("https://edition.cnn.com/world"))
@Test fun `unknown domain defaults to direct`() =
    assertEquals(Strategy.DIRECT, registry.getRule("someblog.example").strategy)
@Test fun `publisher name resolves to domain`() =
    assertEquals("nytimes.com", registry.resolvePublisherDomain("The New York Times"))
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests "news.openwire.domain.engine.DomainRegistryTest"`
Expected: FAIL (classes not defined).

- [ ] **Step 4: Implement `DomainRegistry`** — match order: exact match → subdomain
  suffix match → `DIRECT` fallback; merge `DEFAULT_RULES` with `store.load()`
  overrides.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests "news.openwire.domain.engine.DomainRegistryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add domain
git commit -m "feat(domain): port domain registry paywall engine"
```

---

### Task 3: NewsProcessor (port `js/newsService.js` core)

**Files:**
- Create: `domain/src/main/kotlin/news/openwire/domain/model/Article.kt`
- Create: `domain/src/main/kotlin/news/openwire/domain/engine/NewsProcessor.kt`
- Test: `domain/src/test/kotlin/news/openwire/domain/engine/NewsProcessorTest.kt`

**Interfaces:**
- Produces:
  - `data class Article(val id: String, val title: String, val source: String? = null, val domain: String? = null, val originalUrl: String? = null, val publishedAt: Long? = null, val status: String? = null, val badge: String? = null, val targetUrl: String? = null, val archiveUrl: String? = null, val imageUrl: String? = null)`
  - `enum class SortDirection { NEWEST, OLDEST }`
  - `object NewsProcessor`:
    - `fun cleanTitle(title: String): String`
    - `fun titleSimilarity(a: String, b: String): Double`
    - `fun isDuplicate(a: Article, b: Article): Boolean`
    - `fun deduplicate(articles: List<Article>): List<Article>`
    - `fun sortByDate(articles: List<Article>, direction: SortDirection = SortDirection.NEWEST): List<Article>`
    - `fun parseDate(input: String?): Long?`
    - `fun formatRelativeTime(epochMillis: Long?, now: Long = System.currentTimeMillis()): String`

- [ ] **Step 1: Write the failing tests** — port `tests/newsService.test.mjs` date/formatting/dedup/sort cases. Canonical assertions:

```kotlin
@Test fun `duplicate urls collapse`() =
    assertTrue(NewsProcessor.isDuplicate(article(url = "https://x.com/a"), article(url = "https://x.com/a/")))
@Test fun `similar titles collapse above threshold`() =
    assertTrue(NewsProcessor.titleSimilarity("Breaking news today", "Breaking news today!") >= 0.68)
@Test fun `sorts newest first`() =
    assertEquals(listOf(3L, 1L), NewsProcessor.sortByDate(listOf(a(1L), a(3L))).map { it.publishedAt })
@Test fun `parses rfc1123 date`() =
    assertNotNull(NewsProcessor.parseDate("Wed, 16 Sep 2026 23:05:16 GMT"))
@Test fun `relative time formats`() =
    assertEquals("15m ago", NewsProcessor.formatRelativeTime(now - 15 * 60_000, now))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests "news.openwire.domain.engine.NewsProcessorTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `NewsProcessor`** — Dice coefficient (words > 2 chars), URL normalization (`split('?')`, trim trailing `/`, lowercase); date parsing must accept RFC-1123 and the `"EDT"/"EST"`-suffixed variant seen in `data/news_top.json`, falling back to ISO and returning null on unparseable.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests "news.openwire.domain.engine.NewsProcessorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain
git commit -m "feat(domain): port news processor (dedup, sort, dates)"
```

---

### Task 4: Topic catalog (default topics)

**Files:**
- Create: `domain/src/main/kotlin/news/openwire/domain/model/Topic.kt`
- Create: `domain/src/main/kotlin/news/openwire/domain/engine/TopicRegistry.kt`
- Test: `domain/src/test/kotlin/news/openwire/domain/engine/TopicRegistryTest.kt`

**Interfaces:**
- Produces:
  - `data class Topic(val id: String, val label: String, val snapshotFile: String? = null, val liveUrl: String? = null, val liveOnly: Boolean = false)`
  - `object TopicRegistry { fun defaultTopics(): List<Topic>; fun snapshotTopics(): List<Topic> }`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `world topic is labeled International`() =
    assertEquals("International", TopicRegistry.defaultTopics().first { it.id == "INTERNATIONAL" }.label)
@Test fun `us and washington are liveOnly in phase 1`() =
    assertTrue(TopicRegistry.defaultTopics().first { it.id == "US" }.liveOnly)
@Test fun `snapshot topics exclude liveOnly topics`() =
    assertEquals(0, TopicRegistry.snapshotTopics().count { it.liveOnly })
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests "news.openwire.domain.engine.TopicRegistryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `TopicRegistry.defaultTopics()`** — the target lineup: `TOP, LATEST, AI, US (liveOnly), WASHINGTON_DC (liveOnly), BUSINESS, TECHNOLOGY, INTERNATIONAL (file news_world.json), SCIENCE, ENTERTAINMENT, SPORTS, POLITICS, FINANCE, LIFESTYLE`. `US`/`WASHINGTON_DC` carry `liveUrl` Google News RSS URLs and `liveOnly = true` (their live fetch lands in Plan 2).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests "news.openwire.domain.engine.TopicRegistryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain
git commit -m "feat(domain): add topic catalog with US/International edition"
```

---

### Task 5: Data layer (JSON ingestion + Room cache)

**Files:**
- Create: `data/src/main/kotlin/news/openwire/data/remote/{ArticleDto.kt,NewsApi.kt}`
- Create: `data/src/main/kotlin/news/openwire/data/local/{ArticleEntity.kt,ArticleDao.kt,OpenWireDatabase.kt}`
- Create: `data/src/main/kotlin/news/openwire/data/repository/{FeedRepository.kt,Mappers.kt}`
- Create: `data/src/main/kotlin/news/openwire/data/di/DataModule.kt`
- Test: `data/src/test/kotlin/news/openwire/data/repository/FeedRepositoryTest.kt`

**Interfaces:**
- Consumes: `Article`, `Topic` (domain); `NewsProcessor` (Task 3).
- Produces (used by Task 6):
  - `@Serializable data class ArticleDto(val id: String, val title: String, val source: String? = null, val domain: String? = null, val originalUrl: String? = null, val pubDate: String? = null, val status: String? = null, val badge: String? = null, val targetUrl: String? = null, val archiveUrl: String? = null, val imageUrl: String? = null)`
  - `interface NewsApi { @GET("data/{file}") suspend fun articles(@Path("file") file: String): List<ArticleDto> }`
  - `fun ArticleDto.toArticle(): Article` (in `Mappers.kt`)
  - `class FeedRepository(api, dao, baseUrl)` with
    `fun observeTopic(topic: Topic): Flow<FeedResult>` where
    `sealed interface FeedResult { object Loading; data class Success(val articles: List<Article>, val fromCache: Boolean); data class Error(val message: String); data class Empty(val topicId: String) }`

- [ ] **Step 1: Write the failing test** using a fake `NewsApi` (returns fixed JSON) and in-memory Room:

```kotlin
@Test fun `missing optional fields map to nulls without crashing`() =
    assertEquals(null, ArticleDto(id = "a", title = "t").toArticle().imageUrl)
@Test fun `repository dedups and sorts the fetched articles`() { /* fake returns duplicate + unsorted */ }
@Test fun `unparseable pubDate yields null publishedAt`() =
    assertEquals(null, ArticleDto(id = "a", title = "t", pubDate = "garbage").toArticle().publishedAt)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :data:testDebugUnitTest --tests "news.openwire.data.repository.FeedRepositoryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement DTO, mapper, `NewsApi`, Room entity/DAO/DB, `FeedRepository`** — the repository emits cached articles immediately, then refreshes from the network, dedups/sorts via `NewsProcessor`, and upserts into Room. Live-only topics (`topic.liveOnly`) return `Empty` with a "live fetch coming soon" message in this phase. Base URL from build config (default `https://openwire.news/`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :data:testDebugUnitTest --tests "news.openwire.data.repository.FeedRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data
git commit -m "feat(data): JSON snapshot ingestion and Room cache"
```

---

### Task 6: UI home feed

**Files:**
- Create: `app/src/main/java/news/openwire/ui/home/{HomeScreen.kt,TopicBar.kt,ArticleCard.kt,FeedViewModel.kt}`
- Create: `app/src/main/java/news/openwire/di/AppModule.kt`
- Modify: `app/src/main/java/news/openwire/MainActivity.kt`
- Test: `app/src/test/kotlin/news/openwire/ui/home/FeedViewModelTest.kt`

**Interfaces:**
- Consumes: `TopicRegistry`, `FeedRepository` (Tasks 4–5).
- Produces: `class FeedViewModel(repository) : ViewModel()` exposing
  `val uiState: StateFlow<FeedUiState>` and `fun selectTopic(id: String)` where
  `data class FeedUiState(val topics: List<Topic>, val selected: Topic, val result: FeedResult)`.

- [ ] **Step 1: Write the failing ViewModel test** (fake repository):

```kotlin
@Test fun `network error surfaces Error state not Loading`() { /* fake returns Error */ }
@Test fun `empty topic surfaces Empty state`() { /* fake returns Empty */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "news.openwire.ui.home.FeedViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Implement the screen** — `TopicBar` (horizontally scrollable pills), `ArticleCard` (Coil image with graceful fallback, title, source, badge, `NewsProcessor.formatRelativeTime`), and `HomeScreen` rendering `Loading`/`Success`/`Error`(+retry)/`Empty` states. Tap opens the article `targetUrl` (fallback `originalUrl`) in a Chrome Custom Tab.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "news.openwire.ui.home.FeedViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): home feed with topic bar and article cards"
```

---

### Task 7: Integration & device verification

- [ ] **Step 1: Build and install on an emulator/device**

Run: `./gradlew installDebug`
Expected: app installs and launches.

- [ ] **Step 2: Smoke test manually** — verify: topics switch; a snapshot topic renders headlines with images/badges/relative time; tapping opens the article in Custom Tabs; airplane-mode first launch shows the error + retry; a live-only topic (US) shows the "live fetch coming soon" empty state.

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew test`
Expected: all `:domain:test`, `:data:testDebugUnitTest`, `:app:testDebugUnitTest` pass.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: integration verification of foundation reader"
```

---

## Self-Review Notes

- **Spec coverage:** Phase 1 covers SPEC §6.1 (feed rendering), part of §6.2
  (DomainRegistry), §7 (data model), §8 (snapshot ingestion). Paywall routing
  end-to-end, customization, and notifications are Phases 2–4.
- **Live-only topics:** US/Washington D.C. show an explicit empty state in Phase
  1 because their live Google News RSS fetch is Phase 2 and their snapshots are
  a deferred pipeline change (ADR-0003/0005). This is intentional and honest.
- **Types:** `FeedResult`, `FeedUiState`, `Article`, `Topic` signatures above are
  the single source of truth for cross-task references.
