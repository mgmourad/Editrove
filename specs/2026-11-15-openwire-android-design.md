# OpenWire Android — Design Document

- **Date:** 2026-11-15
- **Status:** Approved (pending builder review)
- **Author:** OpenWire maintainer
- **Scope:** Rebuild of the OpenWire PWA as a native Android application

> Note on location: this repository's `docs/` directory is a *generated* GitHub
> Pages build output (`build.js --target=docs` deletes and recreates it). Source
> documentation therefore lives at the repository root and in `specs/` and
> `adr/`. When the legacy PWA build is retired (see §8), `docs/` may be
> repurposed for source documentation.

---

## 1. Intent & Why

OpenWire is a fast, frictionless, advertising- and tracking-free news aggregator.
Its defining feature is **smart paywall routing**: open/free articles link
directly, metered publishers route through reader/bypass tools, and hard
paywalls fall back to archive snapshots — or grey out when no readable copy
exists.

It exists today as a 100% client-side Progressive Web App. The PWA is good, but
it cannot deliver three things a native app can:

1. **Reliable offline storage** and background refresh (a service worker is
   browser-controlled and can be evicted).
2. **Notifications** (the PWA has none; Android has WorkManager and FCM).
3. **A native feel** — Material 3 theming, system navigation, share sheets,
   Custom Tabs, and predictable performance.

This document describes the native rebuild. The goal is not to clone the PWA
pixel-for-pixel, but to preserve its *behaviour* — the article data model, the
paywall strategy engine, the dedup/weaving logic, and the JSON feed pipeline —
while re-expressing the UI in Compose and replacing browser storage with native
storage.

## 2. Goals

- Ship a polished, single-activity Compose app that renders the topic feeds
  (Top, Latest, AI, US, Washington D.C., Business, Tech, International, Science,
  Entertainment, Sports, Politics, Finance, Lifestyle) with images, badges,
  relative time, and sort options.
- Port the **domain/paywall strategy engine** (direct / smry / removepaywall /
  unwall / freedium / reader / archive.is / wayback) as a first-class feature.
- Support **custom topics**, **custom RSS/Atom sources**, the **source library**,
  **local editions**, the **rules editor**, the **strategy switcher**, and the
  **quick-URL opener**, matching the PWA's feature set.
- Implement **settings import/export** using the existing JSON schema.
- Provide **offline caching** and **background refresh**.
- Provide **local notifications** for new headlines in topics the user opts
  into (per-topic opt-in, default off, plus a global master switch).
- Open articles in **Chrome Custom Tabs** (the native equivalent of the PWA's
  link-out), preserving the bypass target URLs.
- Keep the project **open source (AGPL-3.0)**, canonical in this repo, and
  distributable off-Play-Store.

## 3. Non-goals

- **No Play Store distribution** (initially): F-Droid / direct APK. This also
  removes Play policy pressure on the paywall-bypass feature.
- **No account system or cloud sync** in v1.
- **No in-app article reader** in v1 (articles open in Custom Tabs).
- **No iOS** (this is a native Kotlin build, not Compose Multiplatform).
- **No real-time push (FCM)** in v1 (see §5.2).

## 4. Anti-goals

- **No ads, no analytics/tracking.** Any third-party SDK that collects data is
  a failure even if the app technically works.
- **No server to operate.** The project's "zero server-side bloat" identity must
  hold: the Android app reads the same pre-generated JSON that GitHub Actions
  already publishes. Adding a mandatory backend would be a regression.
- **No regression of the paywall engine.** The domain registry and archive
  fallback behaviour must remain as capable as the PWA's.

## 5. Constraints

- **License:** AGPL-3.0. The Android app and all modifications must remain
  AGPL-3.0-compatible and source-available.
- **Distribution:** off-Play-Store. Reproducible builds and a clean F-Droid
  story are valued over Play-specific conveniences.
- **Data backend:** reuse the existing `sync_news.py` → GitHub Actions →
  `data/*.json` pipeline. The app must treat those JSON snapshots as its primary
  source for built-in topics.
- **Identity:** `applicationId = news.openwire`, `minSdk = 26`, target latest
  stable SDK, app name "OpenWire Reader".
- **Technology:** Kotlin + Jetpack Compose, single-activity, Material 3.

## 6. Approaches considered

| Approach | Trade-offs | Verdict |
|---|---|---|
| **A. Trusted Web Activity / WebView wrapper** | Days of work, keeps 100% of PWA code; but no reliable push, no native polish, poor background behaviour, and it's still "a web app". | Rejected — does not meet "full fledged". |
| **B. Native Kotlin + Jetpack Compose** | Best polish, first-class notifications, native networking removes the CORS-proxy layer, best long-term maintainability; most work, logic must be ported to Kotlin. | **Chosen.** |
| **C. Compose Multiplatform (KMP)** | Same as B but shares logic with iOS; extra toolchain overhead. | Rejected — no iOS requirement. |
| **D. Flutter / React Native** | Cross-platform, but poor reuse of existing JS logic and weaker Compose-ecosystem fit. | Rejected. |

**Chosen: B.** The PWA's logic is already cleanly factored into small,
unit-tested, mostly-pure modules, which makes porting to Kotlin tractable and
preserves the domain model without a rewrite-by-guesswork.

## 7. Decisions (as decided with the maintainer)

### 7.1 v1 scope
Full reader: the topic lineup (Top, Latest, AI, US, Washington D.C., Business,
Tech, International, Science, Entertainment, Sports, Politics, Finance,
Lifestyle), feed cards (image/badge/relative time), sort
(curated/newest/oldest), paywall routing + archive checks, custom topics, custom
RSS/Atom sources, source library, local editions, rules editor, strategy
switcher, quick-URL opener, settings import/export, dark mode, offline cache
(Room), background refresh (WorkManager), and **local notifications**. Articles
open in **Chrome Custom Tabs**. The quick-URL feature is **both** an in-app
"paste a URL" box and an Android **share target** (`ACTION_SEND` /
`ACTION_PROCESS_TEXT`), so users can share an article from another app into
OpenWire to have it paywall-routed.

### 7.2 Notifications — local-first, per-topic opt-in
v1 uses **WorkManager + NotificationManager** only. A periodic worker polls the
`openwire.news/data/*.json` snapshots, diffs against the last-seen article IDs
for each topic, and posts local notifications for genuinely new headlines — but
only for topics the user has opted into. Each topic carries a **notify** flag
(default **off**), and a global master switch controls notifications overall.
This is serverless and preserves the project's identity. **FCM push** (instant,
but requiring a sender backend) is a documented future phase (§7.4).

### 7.3 Data source
- **Base URL:** `https://openwire.news/` by default, overridable via a build
  config field (the pipeline already publishes `data/*.json` and `news.json`
  there). A native app has no same-origin `./data/` path; it uses this absolute
  URL.
- **Built-in topics** read the pre-generated JSON snapshots (no CORS in native).
  "Refresh" re-fetches the current snapshot (the pipeline regenerates them every
  ~30 minutes). **No client-side Google News RSS fetch for built-in topics.**
- **Editions & renaming:** the default edition is **US** (national) with
  **Washington, D.C.** as the local city, replacing the PWA's Canada/Vancouver
  default; the **World** topic is renamed **International**. Local editions for
  other places are supported via **live Google News RSS** (see below).
- **Snapshot artifacts in the v1 data contract:** `news_*.json` / `news.json`,
  plus `data/publisher_urls.json` (Google News wrapper → publisher URL
  resolution) and `data/preview_articles.json` (keyword/preview enrichment).
  All three are consumed to preserve the PWA's behaviour.
- **Custom/user-added feeds** are fetched natively over RSS/Atom and parsed with
  a Kotlin XML parser.
- **Local editions (non-default) and custom keyword topics** fetch **live Google
  News RSS** with the appropriate locale (`hl`/`gl`/`ceid`), native HTTP, no
  CORS. This is how Seattle, New York, London, or any free-text city/region
  works, matching the PWA's behaviour for non-snapshot locations.

### 7.4 Deferred (documented future phases)
- FCM push notifications (requires a small sender backend — a Cloudflare Worker
  or Function that polls feeds and pushes).
- Server-side archive-availability precompute (ship archive status in the
  snapshot instead of checking per-article from the device).
- Snapshot generation for the default US/Washington edition (so the default
  local edition loads instantly and offline like the old Vancouver/BC did; until
  then it is fetched live).
- In-app article reader (full-text extraction).
- Account/cloud sync of settings.
- iOS via Compose Multiplatform (if ever needed).

### 7.5 Resolved during builder review (maintainer's calls)
- **Settings import is net-new** and uses the same JSON schema as the PWA's
  `settingsExport.js` export (version 1: `topics` / `rssFeeds` /
  `paywallRules`), so users can migrate from the browser app. Unknown or
  missing fields are handled defensively on import.
- **Archive availability checks stay client-side** in v1 (port
  `archiveService.js` → `ArchiveChecker`, direct HTTP to archive.is/Wayback with
  bounded timeouts). Precomputing availability server-side in `sync_news.py` is
  a noted future improvement (§7.4).

## 8. Architecture (high-level)

Single-activity, multi-module, MVVM + Repository pattern.

```
app/                 # UI layer — Compose screens, navigation, theme
  ui/theme, ui/home, ui/topics, ui/rules, ui/quickurl, ui/settings
data/                # Repository layer — Room, DataStore, Retrofit/OkHttp
  local/  (Room: articles, sources, rules)
  remote/ (JSON snapshot API, RSS/Atom fetcher)
domain/              # Pure Kotlin — ported logic, no Android deps
  model/  (Article, Topic, Source, DomainRule, Strategy)
  engine/ (DomainRegistry, FeedWeaver, NewsProcessor, ArchiveChecker)
notifications/       # WorkManager + NotificationManager
```

**Key libraries:** Retrofit/OkHttp + kotlinx.serialization (networking),
Room (cache), DataStore (preferences), Coil (images), WorkManager (background),
Hilt (DI), Navigation Compose, kotlinx-coroutines, Jsoup or XmlPullParser (RSS).

**Data flow:** UI → ViewModel → Repository → (Room cache | remote). Domain
modules are pure Kotlin and unit-tested independently of Android, mirroring the
PWA's test isolation.

**Ported modules (JS → Kotlin):**
- `domainRegistry.js` → `DomainRegistry` (strategies, `KNOWN_PUBLISHER_DOMAINS`,
  domain normalization, rule persistence).
- `newsService.js` → `NewsProcessor` (title cleaning, Dice-similarity dedup,
  date parsing/formatting, sorting).
- `feedWeaver.js` → `FeedWeaver` (fair-share quotas, anti-clustering).
- `feedCatalog.js` / `feedRegistry.js` / `sourceGroups.js` / `topicRegistry.js`
  → `FeedCatalog`, `FeedRegistry`, `TopicRegistry`.
- `archiveService.js` / `imageService.js` / `publisherUrlService.js` →
  `ArchiveChecker`, `ImageExtractor`, `PublisherUrlResolver`.
- `fetchProxy.js` → **dropped** (native networking has no CORS); replaced by
  direct HTTP.

## 9. Repo restructuring (transition)

The current repository mixes the PWA source and its build pipeline. As part of
this work:

1. The PWA source (`index.html`, `css/`, `js/`, `sw.js`, `manifest.json`,
   `build.js`) will be moved under `legacy-pwa/` or a separate repository.
2. The **feed pipeline** (`sync_news.py`, `article_metadata.py`,
   `preview_text.py`, `scripts/audit_feeds.py`, `requirements.txt`, and the
   GitHub Actions workflow) will be retained, but its published output
   (`data/*.json`, `news.json`) must continue to be served at a stable URL the
   app consumes. This may mean a dedicated `feeds/` repo or a static host.
3. The Android source becomes the root of the repository (Gradle project at
   `./`), and `docs/` is freed for source documentation.

This restructuring is tracked as a separate task from the app build itself.

## 10. What's left to the builder

The following are deliberately left to the implementing engineer and are not
specified here: exact package/module names, DI library choice (Hilt is
recommended but manual DI is acceptable), RSS parsing library, exact screen
hierarchy, animation details, and test-file layout. These are low-risk,
reversible choices consistent with the constraints in §5.
