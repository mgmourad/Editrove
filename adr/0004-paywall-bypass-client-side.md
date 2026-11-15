# ADR-0004: Paywall routing remains a first-class, client-side feature

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

OpenWire's defining feature is smart paywall routing (direct / bypass readers /
archive fallbacks). Because the app is distributed off-Play-Store, it is not
subject to Google Play's paywall-bypass policy, so the feature can be retained
without compromise.

## Decision

- Port the full strategy engine (`js/domainRegistry.js`) and archive checker
  (`js/archiveService.js`) to Kotlin, preserving the PWA's behaviour including
  the "grey out when no snapshot" fallback.
- Archive availability checks stay **client-side** in v1 (direct HTTP to
  archive.is / Wayback with bounded timeouts).

## Consequences

- Hard-paywall headlines resolve instantly when a snapshot exists and degrade
  gracefully when it does not.
- Client-side archive checks can be rate-limited/Cloudflare-challenged on
  mobile; precomputing availability server-side is a deferred improvement.
- Contributors/distributors must remain aware of the legal landscape around
  bypass tools (documented in `SECURITY.md`).
