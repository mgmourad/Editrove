# ADR-0007: Per-source refresh progress and a stage-gated reader reveal

- **Status:** Accepted
- **Date:** 2026-11-23

## Context

Loading looked the same everywhere: Material's spinner on pull-to-refresh and a
sweep bar in the reader. The motion prototype (`Editrove loading motion.html`)
replaces both with Editrove's own language, "the rule and the wire" and "type on
a press". Its key promise is honesty: the wire lists sources as they *actually*
answer, and each reader stage starts when its content is *actually* ready, never
on a timer.

## Decision

1. **Refresh progress is reported by the fetch layer, without changing the fetch.**
   - `RefreshReporter` in `data/repository/RefreshProgress.kt` emits
     `RefreshEvent`s (`Started`, `Returned`, `Failed`, `Finished`) on a
     `SharedFlow` with a 64-event buffer that drops the oldest.
   - Events are sent with `tryEmit`, so the fetch never waits on a listener.
   - Each source's time is measured from its request to its answer (or
     failure). A `SocketTimeoutException` is reported as a timeout.
   - Sources are a `SourceRef` (`FEED`, `SEARCH`, `EDITION`). A Google News
     search is one "News search" line, and the published snapshot is one
     "Editrove edition" line.
   - `RefreshTicker` folds events for a single refresh id; events from older
     refreshes are ignored.
   - Only refreshes the reader asked for drive the wire.
2. **The pull is a custom nested-scroll connection, not `PullToRefreshBox`.**
   Material's indicator applies its own drag multiplier and curve. The spec
   needs `140 × (1 − e^(−d/160)) dp` exactly, plus a 72 dp ready line with one
   haptic per crossing and a 64 dp hold on a no-bounce spring
   (`dampingRatio 1, stiffness 300`). The phases (idle → pulling → ready →
   refreshing → finishing → idle, plus cancel) are a pure reducer in
   `domain/motion/PullRefresh.kt`, unit-tested. The band's height, rule and
   glint are read only in layout and draw, so the feed list never recomposes
   per frame.
3. **"New since" uses the normalised headline** (`NewSince.key`), because
   article ids are positional and links are rewritten after load. Nothing is
   "new" on a first load or when every story changed.
4. **The reader reveal builds on the existing shared-element transition.**
   - The bounds transform becomes `tween(620, emphasized decelerate)` to open
     and `tween(520, emphasized accelerate)` to close.
   - The sheet only rises a little and fades, so the photo is what grows.
   - The feed behind sinks to 93%, dims and (API 31+) blurs on the same flag.
   - Stages and their triggers:
     - Headline and kicker: when the sheet's transition settles.
     - Image develop: on Coil's `Success` for the full image. If that image
       came from the memory cache, it shows at once.
     - Key points: on `BodyState.Loaded`. The summary is timed on
       `Dispatchers.Default`, and its real duration is shown.
     - Body reveal: on `BodyState.Loaded`.
   - The summary is extractive and arrives all at once, so the letter-by-letter
     reveal plays over the finished text.
5. **Variable type is stepped and cached.** `VariableType.newsreader(wght,
   opsz)` builds each axis combination once. Animations snap to about a dozen
   steps, so a headline uses around 12 typefaces rather than one per frame.

## Consequences

- No new dependencies and no new colour tokens. The key-point writing glow
  uses the app's rose accent (owner's choice over the prototype's indigo).
- Fallbacks:
  - Below API 31, there is no `RenderEffect` blur. The headline, preview and
    feed-behind use alpha and dimming instead.
  - Glows are drawn as translucent wider strokes, so they look the same on
    every version.
  - Haptics use `SEGMENT_TICK` on API 34+ and `CLOCK_TICK` below.
- At system animation scale 0, end states are kept and every transition, glint
  and blur is cut.
- On the unfolded 50/50 layout there is no container transform (nothing flies
  between panes). The content stages still play there.
