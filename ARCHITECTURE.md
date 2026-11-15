# OpenWire Android — Architecture

- **Last updated:** 2026-11-15
- **Companion to:** [`SPEC.md`](SPEC.md), [`adr/`](adr/)

This document describes how OpenWire for Android is structured and how data
flows through it.

## 1. Principles

1. **Clean layering** — `domain` has no Android dependencies; `data` depends on
   `domain`; `app` depends on `data` and `domain`.
2. **Single-activity** — one `MainActivity`, Compose Navigation for screens.
3. **Unidirectional data flow** — UI observes `StateFlow` from ViewModels;
   ViewModels call repositories; repositories coordinate cache + remote.
4. **Serverless** — built-in topics read pre-generated JSON over HTTPS; there is
   no app-owned backend.

## 2. Module layout

```
OpenWire-Android/
├── app/                         # Android application module
│   ├── MainActivity.kt
│   ├── OpenWireApp.kt           # Application class, Hilt entry
│   ├── ui/
│   │   ├── theme/               # Material 3 theme, color, typography, dark mode
│   │   ├── home/                # topic bar, feed list, article cards
│   │   ├── topics/              # topic/source manager screens
│   │   ├── rules/               # domain strategy registry editor
│   │   ├── quickurl/            # paste-a-URL + shared-URL confirmation
│   │   ├── settings/            # notifications, local edition, import/export
│   │   └── navigation/          # NavGraph, routes, deep links
│   └── di/                      # Hilt modules
├── data/
│   ├── local/                   # Room DB, entities, DAOs; DataStore
│   ├── remote/                  # Retrofit API, RSS/Atom fetcher, DTOs
│   └── repository/              # repository implementations
├── domain/
│   ├── model/                   # Article, Topic, Source, DomainRule, Strategy, LocalEdition
│   └── engine/                  # ported logic (pure Kotlin)
├── notifications/               # WorkManager workers, notification posting
└── (legacy PWA source — to be relocated)
```

### 2.1 `domain/engine` (ported logic)

| Kotlin | Ported from | Responsibility |
|---|---|---|
| `DomainRegistry.kt` | `js/domainRegistry.js` | strategy map, `KNOWN_PUBLISHER_DOMAINS`, domain normalization, rule persistence |
| `NewsProcessor.kt` | `js/newsService.js` | title cleaning, Dice-similarity dedup, date parsing/formatting, sorting |
| `FeedWeaver.kt` | `js/feedWeaver.js` | fair-share quotas, anti-clustering, interleaving |
| `FeedCatalog.kt` / `FeedRegistry.kt` | `js/feedCatalog.js`, `js/feedRegistry.js`, `js/sourceGroups.js` | publisher catalog, source grouping, enabled/disabled state |
| `TopicRegistry.kt` | `js/topicRegistry.js` | default/custom topics, ordering, visibility, local edition |
| `ArchiveChecker.kt` | `js/archiveService.js` | archive.is / Wayback availability (non-blocking, bounded timeouts) |
| `ImageExtractor.kt` | `js/imageService.js` | image candidate extraction / logo-filtering |
| `PublisherUrlResolver.kt` | `js/publisherUrlService.js` | Google News wrapper → publisher URL |
| `PreviewService.kt` | `js/previewService.js` | keyword/preview enrichment from `preview_articles.json` |
| `SettingsCodec.kt` | `js/settingsExport.js` | settings import/export (PWA-compatible schema) |

> `js/fetchProxy.js` is **not ported** — native HTTP has no CORS, so the proxy
> waterfall is unnecessary.

## 3. Data flow

### 3.1 Reading a topic

```
FeedScreen (Compose)
  └─ FeedViewModel (StateFlow<FeedUiState>)
       └─ FeedRepository
            ├─ ArticleDao (Room cache — return immediately)
            └─ NewsApi (fetch snapshot/news_*.json) → upsert into Room
```

- On open, the repository emits the cached articles instantly, then refreshes
  from the network and re-emits.
- Built-in topics: `GET {base}/data/news_{topic}.json`.
- Custom feeds: native RSS/Atom fetch + parse.
- Non-default local editions / custom keyword topics: live Google News RSS with
  `hl`/`gl`/`ceid`.

### 3.2 Paywall routing (tap an article)

```
ArticleCard.onClick
  └─ DomainRegistry.getRule(domain)            // pure
       ├─ direct → Custom Tabs(originalUrl)
       ├─ smry/removepaywall/unwall/freedium/reader → Custom Tabs(strategy URL)
       └─ archive_is/wayback → ArchiveChecker.check(url)
            ├─ snapshot found → Custom Tabs(archiveUrl)
            └─ none → grey out headline (locked)
```

- Archive checks are asynchronous, non-blocking, with bounded timeouts, matching
  the PWA's "UI never hangs" behaviour.

### 3.3 Notifications

```
NotificationSyncWorker (WorkManager, periodic ≥ 15 min)
  └─ for each topic with notify == true (and global master on):
       fetch snapshot
       diff new article IDs vs. last-seen set (persisted)
       post NotificationManager notification(s)
       update last-seen set
```

- Per-topic `notify` flag stored in DataStore; default off.
- See ADR-0002.

## 4. Storage

| Concern | Mechanism |
|---|---|
| Articles cache | Room (`articles`, `sources`, `last_seen_ids`) |
| User preferences (topics, rules, edition, notify flags) | DataStore (Preferences + Proto for structured data) |
| Settings export/import | JSON file, PWA-compatible schema |

## 5. Networking

- **JSON snapshots** — Retrofit/OkHttp + kotlinx.serialization.
- **RSS/Atom** — OkHttp + XmlPullParser (or Jsoup for lenient HTML-heavy feeds).
- **Images** — Coil (memory + disk cache).
- **Timeout/retry policy** — bounded timeouts; a failing primary feed never
  blocks the remaining publishers (ported behaviour).

## 6. Notifications module

- `NotificationPermissionHelper` — POST_NOTIFICATIONS runtime permission
  (Android 13+) and notification-channel creation (Android 8+).
- `NotificationSyncWorker` — the periodic diff-and-post worker.
- `NotificationPoster` — builds and posts notifications, deep-links into the
  relevant topic feed.

## 7. Build & dependencies

- **Build:** Gradle (Kotlin DSL), version catalog (`libs.versions.toml`).
- **Key libraries:** Jetpack Compose + Material 3, Navigation Compose, Hilt,
  Room, DataStore, Retrofit/OkHttp, kotlinx.serialization, Coil, WorkManager,
  kotlinx-coroutines.
- **Configuration:** `applicationId = news.openwire`, `minSdk = 26`, target
  latest stable.

## 8. Testing strategy

- `domain/engine` logic is pure and unit-tested on the JVM (ports of the PWA's
  `tests/*.test.mjs`).
- `data` repositories tested with fake remote + in-memory Room.
- `app` UI covered by Compose UI tests for critical flows.
- `notifications` worker tested with WorkManager's `TestListenableWorkerBuilder`.
