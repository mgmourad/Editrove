# OpenWire — Android

A fast, frictionless, **ad-free and tracking-free** news aggregator for Android,
with a smart paywall-routing engine. Read the news without barriers.

OpenWire classifies every publisher by its paywall, routes open articles
directly, routes metered ones through reader/bypass tools, and falls back to
archive snapshots for hard paywalls — greying out headlines that have no
readable copy.

> **Status:** v0.2.0 — the v1 feature set (SPEC §6) is implemented and in
> preview. This repository is the canonical source for the native Android app,
> a fork and rebuild of the original OpenWire PWA by Anson Lai.

---

## Features

- **Topic feeds** — Top, Latest, AI, US, Washington D.C., Business, Tech,
  International, Science, Entertainment, Sports, Politics, Finance, Lifestyle.
- **Smart paywall routing** — direct / smry.ai / removepaywall / unwall /
  Freedium / reader / archive.is / Wayback, with a per-domain rules editor.
- **Customization** — custom keyword topics, custom RSS/Atom sources, a grouped
  source library, and local editions (any city via live Google News RSS).
- **Offline-first** — Room caching + background refresh (WorkManager).
- **Notifications** — local, per-topic opt-in, default off.
- **Quick-URL** — paste any article URL, or share one from another app, and
  OpenWire routes it through the paywall engine.
- **Privacy** — no ads, no analytics, no tracking.

## Technology

Kotlin · Jetpack Compose · Material 3 · MVVM · Room · DataStore · Retrofit/OkHttp
· Coil · WorkManager · Hilt. `minSdk 26`, target latest stable.

## Documentation

| Document | Purpose |
|---|---|
| [`SPEC.md`](SPEC.md) | Authoritative what/why + feature + data-model spec. |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Module layout and data flow. |
| [`CLAUDE.md`](CLAUDE.md) | Working guidance for contributors and AI agents. |
| [`adr/`](adr/) | Architecture decision records. |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to contribute. |
| [`SECURITY.md`](SECURITY.md) | Security policy and reporting. |
| [`specs/`](specs/) | Original design document. |

## Building

> The Gradle project is not scaffolded yet. Once it lands:

```bash
./gradlew assembleDebug     # debug APK
./gradlew test              # unit tests
./gradlew lint              # static analysis
```

## Distribution

F-Droid and direct APK (off-Play-Store).

## License

GNU AGPL-3.0 — see [`LICENSE`](LICENSE).

## Legacy PWA & feed pipeline

The original PWA source (`index.html`, `css/`, `js/`, `sw.js`, `manifest.json`,
`build.js`) and the scheduled feed pipeline (`sync_news.py`,
`article_metadata.py`, `preview_text.py`, `scripts/`, GitHub Actions) are being
relocated per the design document's restructuring note. The feed pipeline is
retained as the app's data backend, publishing `data/*.json` to
`https://openwire.news/`.
