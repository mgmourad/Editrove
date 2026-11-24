# Changelog

All notable changes to Editrove for Android are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
