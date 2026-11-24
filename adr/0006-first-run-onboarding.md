# ADR-0006: First-run setup as a live network graph

- **Status:** Accepted
- **Date:** 2026-11-23

## Context

New installs opened straight onto a Washington, D.C. front page with every topic
and all 117 catalog sources switched on. Changing the edition, topics, sources and
the audio briefing meant finding four separate places in Settings. The reference
mock (`Editrove first-run setup.html`) shows a short setup in which those choices
are built visibly, as a network: edition → topics → sources → briefing.

Constraints: no account, no server, no tracking; existing users must never be sent
back through it; it has to work on folded, unfolded and table-top foldables, with
TalkBack, with animations off, and in all five app languages (Arabic RTL).

## Decision

1. **A pure state machine in `domain/`.** `news.openwire.domain.onboarding.Onboarding`
   holds the steps, guards (an edition before Topics, at least 3 topics, at most 3
   keyword topics), Skip semantics, and `apply()`, which turns the choices into
   `UserSettings` in one write. Nothing is saved until the end, so abandoning setup
   halfway changes nothing. It is JVM-tested like the other ported engines.
2. **State survives anything** by encoding `OnboardingState` as JSON in the
   ViewModel's `SavedStateHandle` (rotation, folding, language switch, process death).
3. **One `Canvas`, one engine.** `GraphEngine` draws the whole network in screen space
   with cached strokes, a pooled effect list and an eased camera. `GraphDirector`
   diffs state into moves (burst-out, fly-out, pulses, blooms, the finale). The
   frame loop sleeps whenever nothing moves. With the system animation scale at 0
   there are no effects and camera moves cut instantly.
4. **Graph and list are the same choices.** Every node has an invisible 48dp
   target with a name and an on/off state. Every panel has chips that mirror the
   graph, which is also the non-visual path through setup.
5. **Foldables via `androidx.window`.** Compact screens get a bottom sheet. Wide
   screens get a docked panel on the far side of a vertical hinge. Table-top
   posture puts the graph above the hinge and the panel below.
6. **Who sees it.** A `done_version` flag in its own preferences. An updated
   install, or settings already changed from the defaults (ignoring the language,
   which is copied from the phone's per-app setting), counts as set up. Importing
   settings counts as set up. Settings › Backup offers "Run setup again", which
   starts from the current choices. On a fresh install setup replaces the welcome
   tour and marks What's new as seen. The delivery check is skipped when no
   briefing time was chosen.

## Consequences

- One new dependency, `androidx.window` (Google; no network, no data collection).
- Bumping `Onboarding.VERSION` shows a redesigned setup to everyone once.
- Setup always uses the dark Black Glass look, whatever the app theme.
- Table-top layout follows `FoldingFeature.State.HALF_OPENED`. The Android
  emulator used for testing only reported `FLAT`, so that layout is verified only
  on a real device.
- For a non-default edition, the national and city topics keep the same ids, so
  the catalog sources tagged `US` / `WASHINGTON_DC` still appear under them. This
  matches what `SourceLibrary.feedsForTopic` already does elsewhere in the app.
