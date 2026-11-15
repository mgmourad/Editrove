# ADR-0005: US/International default edition (de-Canada the app)

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

The PWA defaults to a Canada/Vancouver local edition and labels its global feed
"World". The maintainer wants the Android app to be US-centric and
internationally oriented.

## Decision

- The default edition is **US** (national) with **Washington, D.C.** as the
  local city, replacing Canada/Vancouver.
- The **World** topic is renamed **International**.
- Other local places (Seattle, New York, London, or a free-text city/region)
  remain supported via **live Google News RSS** with the correct
  `hl`/`gl`/`ceid` locale.

## Consequences

- Topic lineup: `Top · Latest · AI · US · Washington D.C. · Business · Tech ·
  International · Science · Entertainment · Sports · Politics · Finance ·
  Lifestyle`.
- The default US/Washington edition has no snapshot yet, so it is fetched live
  (snapshot for it is a deferred enhancement).
