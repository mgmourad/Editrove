# ADR-0001: Native Kotlin + Jetpack Compose

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

OpenWire exists as a vanilla-JS Progressive Web App. The maintainer wants a
"full fledged" Android application with notifications. Four delivery options
were considered.

## Decision

Rebuild the app natively in **Kotlin + Jetpack Compose**, single-activity,
Material 3.

## Alternatives considered

| Option | Rationale for rejection |
|---|---|
| Trusted Web Activity / WebView wrapper | Fast, but no reliable push, no native polish, poor background behaviour; still "a web app". |
| Compose Multiplatform (KMP) | Shares logic with iOS; rejected because iOS is not a goal. |
| Flutter / React Native | Poor reuse of existing JS logic; weaker Compose-ecosystem fit. |

## Consequences

- Best polish, first-class notifications, and native networking removes the
  PWA's entire CORS-proxy layer.
- The PWA's domain logic must be ported to Kotlin (tractable because it is
  already factored into small, unit-tested, mostly-pure modules).
