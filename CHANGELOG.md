# Changelog

All notable changes to Editrove for Android are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed (0.9.2.1)

- Article photos are sharp: the full-page reader no longer stretches the feed's small
  thumbnail; it uses the publisher's large photo or the page's own (BBC at 1536 px).
- Every article leads with its main photo at the top, including stories that had no
  photo in the feed.
- The headline sets smoothly and the article cascades in, section by section.
- "New since" stories show as a compact 2 × 2 grid of headlines with a "Show more" cue.

### Added

- Loading motion, the Editrove way (ADR-0007):
  - Pull to refresh opens a band above the masthead. A hairline rule grows
    from the centre and the wordmark inks slightly heavier as you pull. At the
    ready line the rule turns solid rose and the phone ticks.
  - While refreshing, a glint runs along the rule and a one-line wire lists
    each source as it actually answers (with its response time), plus a live
    count. Sources that didn't respond show in amber.
  - When it lands, "18 min ago" rolls to "just now". Stories that are really
    new appear under an "N new since …" divider.
  - Opening a story:
    - The photo grows into the page while the feed sinks behind it.
    - The photo develops from its thumbnail.
    - The headline sets word by word on Newsreader's weight and optical-size
      axes.
    - Key points are written letter by letter in rose, with the real time the
      summary took on the phone.
    - Paragraphs reveal from the top down.
    - A comet runs along the top while loading, then becomes the
      reading-progress line.
  - Respects the system animation scale, TalkBack and right-to-left.

- First-run setup: pick your edition (your location or a typed city), topics
  (including up to three of your own), the sources you trust, and who reads your
  audio briefing and when. Each choice is drawn live as a network graph. It saves
  everything in one go, Skip keeps today's defaults, and it can be run again from
  Settings › Backup. Works folded and unfolded, in book and table-top posture,
  with TalkBack, with animations off, and in all five languages. Existing users
  and imported settings never see it (ADR-0006).

- Project documentation set: `SPEC.md`, `CLAUDE.md`, `ARCHITECTURE.md`,
  `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, ADRs, and the design
  document.
- Design decisions locked: native Kotlin + Jetpack Compose, local-first
  notifications (per-topic opt-in), snapshot JSON backend + native RSS for
  custom feeds, and a US/International default edition.

## [0.1.0] - 2026-11-15

### Added

- Initial planning and specification for the Editrove Android rebuild (no
  application code yet).
