# Editrove Android — Specification (SPEC)

- **Version:** 1.0
- **Last updated:** 2026-11-15
- **Status:** Approved — pre-implementation
- **License:** GNU AGPL-3.0

> This SPEC is the authoritative description of **what** Editrove for Android is
> and how it behaves. Architecture decisions live in
> [`ARCHITECTURE.md`](ARCHITECTURE.md) and
> [`adr/`](adr/). Working guidance for contributors and AI agents lives in
> [`CLAUDE.md`](CLAUDE.md). The original design rationale is in
> [`specs/2026-11-15-openwire-android-design.md`](specs/2026-11-15-openwire-android-design.md).

---

## 1. Overview

Editrove is a fast, frictionless, advertising- and tracking-free news
aggregator. Its defining capability is **smart paywall routing**: open/free
articles open directly, metered publishers route through reader/bypass tools,
and hard paywalls fall back to archive snapshots — or grey out when no readable
copy exists.

This repository is the canonical source for the **native Android application**,
a rebuild of the original OpenWire Progressive Web App in **Kotlin + Jetpack
Compose**. The app preserves the PWA's behaviour and data model while replacing
browser storage with native storage, browser networking with native HTTP, and
adding the capabilities a PWA cannot provide: reliable offline storage,
background refresh, and notifications.

## 2. Goals

1. A polished, single-activity Compose app rendering the Editrove topic feeds
   with images, badges, relative time, and sort options.
2. A first-class **paywall strategy engine** (direct / smry / removepaywall /
   unwall / freedium / reader / archive.is / wayback) ported from the PWA.
3. Full parity of the PWA's customization: custom topics, custom RSS/Atom
   sources, the source library, local editions, the rules editor, the strategy
   switcher, and the quick-URL opener.
4. **Settings import/export** compatible with the PWA's JSON schema.
5. **Offline caching** (Room) and **background refresh** (WorkManager).
6. **Local notifications** for new headlines, opt-in per topic.
7. Open articles in **Chrome Custom Tabs**, preserving bypass target URLs.
8. Open source (AGPL-3.0), distributable off-Play-Store.

## 3. Non-goals

- **No Play Store distribution** initially (F-Droid / direct APK).
- **No account system or cloud sync** in v1.
- **No in-app article reader** in v1 (articles open in Custom Tabs).
- **No iOS** (native Kotlin, not Compose Multiplatform).
- **No real-time push (FCM)** in v1.

## 4. Anti-goals

- **No ads, no analytics, no tracking.** Any third-party SDK that collects
  user data is a failure even if the app technically works.
- **No server the user must operate.** The project's "zero server-side bloat"
  identity must hold: built-in topics read pre-generated JSON published by an
  existing scheduled pipeline. A mandatory backend would be a regression.
- **No regression of the paywall engine** relative to the PWA.

## 5. Users and primary flows

The target user wants headlines with minimal friction, no ads, and working
links for paywalled sources.

- **Read a topic** — open the app, pick a topic, scroll the feed, tap an article
  to open it (routed per its paywall strategy).
- **Personalize** — reorder/hide topics, add custom topics by keyword, add
  custom RSS/Atom feeds, choose a local edition.
- **Tune the paywall engine** — view/edit per-domain strategies, override the
  bypass method for a domain, or bypass an arbitrary URL.
- **Migrate** — import settings exported from the browser PWA.
- **Stay informed** — opt selected topics into notifications; receive local
  notifications for genuinely new headlines.

## 6. Features

### 6.1 Topic feeds
The default topic lineup is:

`Top · Latest · AI · US · Washington D.C. · Business · Tech · International ·
Science · Entertainment · Sports · Politics · Finance · Lifestyle`

- Each feed renders cards with: headline, source, domain badge, paywall/access
  badge, relative publication time, and (when available) a photo.
- Sort modes: **curated** (fair-share weaving), **newest**, **oldest**.
- Pull-to-refresh and a menu **Refresh** action re-fetch the current snapshot
  (built-in topics) or live feed (local editions / custom topics).

### 6.2 Paywall routing engine
Port of the PWA's domain-strategy registry. Each publisher domain maps to a
strategy:

| Strategy | Behaviour |
|---|---|
| `direct` | Open the article URL directly (free/open access). |
| `smry` | Route through smry.ai reader. |
| `removepaywall` | Route through removepaywall. |
| `unwall` | Route through unwall.app. |
| `freedium` | Route through a Freedium mirror (Medium, NYT, WaPo, Bloomberg, Reuters, Economist, FT). |
| `reader` | Reader mode / no-JS / bot. |
| `archive_is` | Check archive.is for a snapshot; link if found, else grey out. |
| `wayback` | Check the Wayback Machine; link if found, else grey out. |

- Unknown domains default to `direct`.
- Hard-paywall domains run a non-blocking archive availability check; if no
  snapshot exists, the headline is **greyed out and locked**.
- The rules editor lists all rules (defaults + user overrides) and allows
  adding/editing/resetting per-domain rules, persisted on device.

### 6.3 Customization
- **Custom topics** — create a topic from keywords/phrases; matches headlines
  (and short previews, via the snapshot preview lookup) within the topic's
  assigned sources.
- **Custom RSS/Atom sources** — add any direct feed URL; fetched natively.
- **Source library** — grouped catalog (categories, aggregators vs. individual
  feeds) with per-source enable/disable and a "check enabled feeds" audit.
- **Local editions** — a configurable city/region (see §6.4).
- **Settings export/import** — single JSON file; import is defensive about
  unknown/missing fields.

### 6.4 Editions
- The **default edition is US** (national) with **Washington, D.C.** as the
  local city, replacing the PWA's Canada/Vancouver default.
- The **World** topic is named **International**.
- Any other local place (Seattle, New York, London, or a free-text city/region)
  is supported via **live Google News RSS** using the correct locale
  (`hl`/`gl`/`ceid`) — native HTTP, no CORS.

### 6.5 Quick-URL opener
- An in-app "paste a URL" dialog.
- An Android **share target** (`ACTION_SEND` / `ACTION_PROCESS_TEXT`), so a user
  can share an article from any app into Editrove and have it routed by the
  paywall engine.

### 6.6 Offline and background
- Room caches articles per topic so the app opens instantly and works offline.
- A WorkManager periodic worker refreshes snapshots in the background.

### 6.7 Notifications
- **Local notifications** only (WorkManager + NotificationManager).
- Each topic has a **notify** flag, **default off**; a global master switch
  gates notifications overall.
- The worker diffs new article IDs against the last-seen set per opted-in topic
  and posts a notification for genuinely new headlines.
- See ADR-0002 for the local-first rationale.

### 6.8 Article viewing
Articles open in **Chrome Custom Tabs** at the resolved target URL (which may be
a bypass/archive URL).

## 7. Data model

The canonical article shape (identical to the PWA's JSON):

```json
{
  "id": "art-top-0",
  "title": "…",
  "source": "CBC News",
  "domain": "cbc.ca",
  "originalUrl": "https://…",
  "pubDate": "Wed, 16 Sep 2026 23:05:16 GMT",
  "status": "free",
  "badge": "Free",
  "targetUrl": "https://…",
  "archiveUrl": "https://archive.is/newest/…",
  "imageUrl": "https://…"
}
```

Additional fields used for keyword/preview enrichment: `summary` and a keyword
match indicator (from `data/preview_articles.json`).

Kotlin domain model (`domain/model`): `Article`, `Topic`, `Source`,
`DomainRule`, `Strategy`, `LocalEdition`.

## 8. Data sources and backend contract

- **Base URL:** `https://openwire.news/` by default, overridable via build
  config.
- **Snapshot artifacts (built-in topics):**
  - `news_*.json` / `news.json` — per-topic headline arrays.
  - `data/publisher_urls.json` — Google News wrapper → publisher URL resolution.
  - `data/preview_articles.json` — keyword/preview enrichment.
- These are regenerated every ~30 minutes by the retained feed pipeline
  (`sync_news.py` + GitHub Actions).
- **Custom feeds and non-default local editions** use live RSS/Atom and live
  Google News RSS respectively, fetched directly (no CORS in native).

## 9. Security, privacy, and legal

- No analytics, no tracking, no third-party data collection.
- Article fetches are outbound HTTP only; no user account or personal data is
  stored.
- **Paywall bypass is an intentional, first-class feature.** Because the app is
  distributed off-Play-Store (F-Droid / direct APK), it is not subject to Play
  policy restrictions, but contributors and distributors should be aware of the
  legal landscape around bypass tools. See [`SECURITY.md`](SECURITY.md).
- Report security issues privately; see [`SECURITY.md`](SECURITY.md).

## 10. Distribution and licensing

- **License:** AGPL-3.0. All source must remain AGPL-3.0-compatible.
- **Channels:** F-Droid, direct APK. Reproducible builds are a goal.

## 11. Roadmap

| Phase | Scope |
|---|---|
| **v1** | Everything in §6 (reader, paywall engine, customization, editions, notifications, offline). |
| **Later** | FCM push; server-side archive precompute; snapshot for the default US/Washington edition; in-app reader; account/cloud sync; iOS (if ever). |

## 12. Repository structure and documentation map

See [`CLAUDE.md`](CLAUDE.md) for the working file map and
[`ARCHITECTURE.md`](ARCHITECTURE.md) for module-level detail. The legacy PWA
source and its build pipeline are being relocated per the design document's
restructuring note.
