# Contributing to Editrove

Thank you for your interest in contributing. Editrove is open source (AGPL-3.0)
and welcomes forks, issues, and pull requests.

## Getting started

1. Read [`SPEC.md`](SPEC.md) and [`ARCHITECTURE.md`](ARCHITECTURE.md).
2. Read [`CLAUDE.md`](CLAUDE.md) for the working file map and invariants.
3. Set up the project (once the Gradle scaffold exists):
   ```bash
   ./gradlew assembleDebug
   ./gradlew test
   ```

## Workflow

1. **Open an issue** for bugs or feature proposals first (unless the change is
   trivial). Use the issue templates.
2. **Fork and branch** off `master` with a short, descriptive name
   (e.g. `feat/fcm-push`, `fix/archive-timeout`).
3. **Keep changes focused.** One logical change per PR.
4. **Add/update tests.** Ported engine logic must carry unit tests (ports of the
   PWA's `tests/*.test.mjs` where applicable).
5. **Run the checks** before pushing: `./gradlew test lint`.
6. **Open a PR** using the pull-request template, referencing the issue.

## Code style

- Kotlin, Jetpack Compose, Material 3.
- `domain/` must remain free of Android framework imports.
- Prefer immutable data classes and pure functions for ported logic.
- Format with ktlint/spotless (once configured).

## Licensing

By contributing you agree your contribution is licensed under **AGPL-3.0**.
Every new source file must include the SPDX header:

```kotlin
// SPDX-License-Identifier: AGPL-3.0-only
```

## Code of conduct

All contributors are expected to follow the
[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).
