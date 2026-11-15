# CLAUDE.md

Guidance for AI agents (and humans) working in this repository.

## Project

Editrove is a native Android news aggregator (Kotlin + Jetpack Compose) with a
smart paywall-routing engine, rebuilt from the original OpenWire PWA. It is
**ad-free, tracking-free, and serverless** (built-in topics read pre-generated
JSON; custom feeds and non-default local editions use live RSS). Distributed
off-Play-Store (F-Droid / APK) under **AGPL-3.0**.

Read these before making changes:

- [`SPEC.md`](SPEC.md) — the authoritative "what/why".
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — module layout and data flow.
- [`adr/`](adr/) — recorded decisions and their rationale.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — workflow and style.

## Status

v0.2.0: the SPEC §6 v1 feature set is implemented across `app/`, `data/`,
`domain/` and `notifications/`. The UI follows the reference prototype
(`pulse_news_2026_adaptive_liquid_glass_prototype.html`, branded **Editrove** —
never "Pulse" or "Editrove+"): Liquid Glass / Black Glass + Editorial Paper,
cover-screen Bento feed with floating pill nav and slide-up reader, and an
unfolded rail + 50/50 feed/reader layout (owner preference over the mock's 38/62).

Snapshot host: `BuildConfig.DATA_BASE_URL` (Gradle property `openwire.dataUrl`).
`openwire.news` is currently a parked page; the default points at the original
project's GitHub Pages feed until this fork publishes its own.

Release signing reads `signing/keystore.properties` or
`~/.openwire/keystore.properties` (never committed).

## Commands

```bash
# Build and run
./gradlew assembleDebug
./gradlew installDebug
./gradlew assembleRelease          # release AAB/APK (needs signing config)

# Tests
./gradlew test                     # JVM unit tests (domain logic)
./gradlew connectedAndroidTest     # instrumented tests (needs device/emulator)

# Static analysis
./gradlew lint
./gradlew ktlintCheck              # if ktlint is configured

# Single test / class
./gradlew test --tests "engine.DomainRegistryTest"
```

## Architecture (summary)

Single-activity, multi-module, MVVM + Repository. See `ARCHITECTURE.md` for the
full picture.

```
app/            Compose UI, navigation, theme, DI wiring
data/           Repositories, Room, DataStore, Retrofit/OkHttp
domain/         Pure Kotlin — models + ported engine logic (no Android deps)
notifications/  WorkManager refresh worker + local NotificationManager alerts
```

- **`domain/` must stay free of Android framework imports.** All ported PWA
  logic lives here and is unit-tested on the JVM.
- **UI → ViewModel → Repository → (Room | remote).** UI never talks to the
  network or database directly.
- Data flows down; state flows up (`StateFlow`).

## Key invariants (do not break)

1. **No analytics, no tracking, no ads.** Reject any dependency that phones home
   or collects user data.
2. **No mandatory server.** Built-in topics read `https://openwire.news/` JSON
   snapshots. Do not introduce a runtime backend requirement.
3. **Paywall routing is core.** `domain/engine/DomainRegistry` and the archive
   checker must stay as capable as the PWA's `js/domainRegistry.js` and
   `js/archiveService.js`.
4. **Default edition = US / Washington, D.C.**; the World topic is labeled
   **International**. Non-default local editions use live Google News RSS.
5. **License stays AGPL-3.0.** Every new source file needs the SPDX header.
6. **Notifications are local-first and per-topic opt-in (default off).**

## File map (source of truth — the legacy PWA files are the reference)

| PWA (legacy) | Android (target) |
|---|---|
| `js/domainRegistry.js` | `domain/engine/DomainRegistry.kt` |
| `js/newsService.js` | `domain/engine/NewsProcessor.kt` |
| `js/feedWeaver.js` | `domain/engine/FeedWeaver.kt` |
| `js/feedCatalog.js`, `js/feedRegistry.js`, `js/sourceGroups.js` | `domain/engine/FeedCatalog.kt`, `FeedRegistry.kt` |
| `js/topicRegistry.js` | `domain/engine/TopicRegistry.kt` |
| `js/archiveService.js` | `domain/engine/ArchiveChecker.kt` |
| `js/imageService.js` | `domain/engine/ImageExtractor.kt` |
| `js/publisherUrlService.js` | `domain/engine/PublisherUrlResolver.kt` |
| `js/previewService.js` | `domain/engine/PreviewService.kt` |
| `js/settingsExport.js` | `domain/engine/SettingsCodec.kt` |
| `js/fetchProxy.js` | *(dropped — native HTTP has no CORS)* |
| `js/app.js` | Compose screens + ViewModels in `app/` |
| `sync_news.py` | *(retained — feed pipeline, not part of the app)* |

The article JSON shape is documented in `SPEC.md` §7.

## Conventions

- **Kotlin**, Jetpack Compose, Material 3, coroutines + `Flow`.
- `applicationId = news.editrove` (code packages stay `news.openwire`), `minSdk = 26`, target latest stable.
- Package root: `news.openwire`.
- Prefer **immutable data classes** in `domain/model`.
- Ported logic should be **pure functions** wherever possible, mirroring the
  PWA's test isolation; port the corresponding `.test.mjs` cases to Kotlin.
- DI via Hilt (recommended; manual DI acceptable if a good reason exists).
- Format with ktlint/spotless once configured.

## Common tasks

- **Port a PWA module** → read the legacy `js/*.js` and its `tests/*.test.mjs`,
  write `domain/engine/*.kt` + a matching unit test, then delete/mark the JS as
  ported in the tracking table.
- **Add a topic** → update the default topic list (mirrors
  `js/topicRegistry.js` `DEFAULT_TOPICS`) and the feed pipeline if a snapshot is
  required.
- **Change a paywall strategy** → edit `DomainRegistry` and update `SPEC.md`
  §6.2 and the ADR if the behaviour is architecturally significant.
