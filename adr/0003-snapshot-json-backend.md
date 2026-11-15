# ADR-0003: Pre-generated JSON snapshots as the built-in feed source

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

The PWA has two modes: client-side live RSS (through CORS proxies) and
Jamstack pre-generated JSON published by a scheduled `sync_news.py` pipeline.
The native app has no CORS restrictions but should stay serverless and instant.

## Decision

- **Built-in topics** read the pre-generated JSON snapshots published at
  `https://openwire.news/` (`news_*.json`, `news.json`, plus
  `publisher_urls.json` and `preview_articles.json`). The retained
  `sync_news.py` + GitHub Actions pipeline remains the data backend.
- **Custom RSS/Atom feeds** and **non-default local editions / custom keyword
  topics** are fetched live over native HTTP (no CORS proxy).

## Consequences

- Instant, offline-capable, zero-proxy loading for built-in topics.
- The CORS proxy waterfall (`fetchProxy.js`) is dropped entirely.
- Snapshot freshness is bounded by the pipeline cadence (~30 min).
- The default US/Washington edition currently has no snapshot and is fetched
  live; a snapshot for it is a deferred enhancement.
