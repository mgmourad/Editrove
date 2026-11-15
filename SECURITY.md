# Security Policy

## Reporting a vulnerability

If you discover a security vulnerability in OpenWire, please **do not open a
public issue**. Report it privately to the maintainer. Include:

- A description of the vulnerability and its impact.
- Steps to reproduce.
- Any suggested remediation.

We will acknowledge receipt, investigate, and coordinate a fix.

## Supported versions

Only the latest release on the `master` branch is supported with security
fixes.

## Design & threat model

OpenWire is designed to minimize attack surface and user exposure:

- **No accounts, no personal data** — the app stores only local preferences and
  cached headlines on the device.
- **No analytics or tracking SDKs** — nothing is sent to a first-party server.
- **Outbound network only** — the app fetches news JSON and RSS; it does not
  host user content.

## Considerations for contributors

- **Paywall bypass.** OpenWire's paywall-routing engine is an intentional
  feature. It is distributed off-Play-Store and, as a result, is not subject to
  Google Play policy, but contributors and downstream distributors should be
  aware of the evolving legal landscape around paywall-bypass and article-access
  tools in their jurisdiction. This is a policy matter, not a technical
  vulnerability.
- **Third-party services.** The bypass/archive services (smry.ai, removepaywall,
  unwall.app, Freedium mirrors, archive.is, Wayback) are external and may change
  or fail. The app must fail gracefully (grey out headlines) rather than break.
- **Dependency hygiene.** Keep dependencies minimal and pinned; prefer
  well-maintained, license-compatible (AGPL-3.0-compatible) libraries.
