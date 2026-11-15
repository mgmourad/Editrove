# ADR-0002: Local notifications first (no FCM in v1)

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

The app must notify users of new headlines, but the project's identity is
"zero server-side bloat" — no server to operate. Real-time push (FCM) requires
a sender backend.

## Decision

v1 ships **local notifications only**: a WorkManager periodic worker polls the
existing JSON snapshots, diffs new article IDs per topic, and posts
`NotificationManager` notifications. Notification opt-in is **per topic**,
**default off**, with a global master switch.

## Consequences

- Serverless; no new infrastructure.
- Notifications are delayed (WorkManager min period ~15 min) and subject to
  Android Doze/battery throttling — acceptable for a news reader.
- FCM push is a documented future phase (requires a small sender backend).
