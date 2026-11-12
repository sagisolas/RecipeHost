# ADR and release-plan update

Status: ready-for-agent
Blocked by: 02, 03
Spec: ../spec.md

## What

- `docs/adr/0017-ios-wrapper.md`: the Kotlin AVFoundation scanner in `iosMain` (and why not Swift or VisionKit); the system Camera plus `recipehost://pair` as the backup pairing route; Local Network permission as part of ADR 0001's threat model (the user grants it once, and the app can't read its state).
- `RELEASE_PLAN.md`: replace the iOS "Decisions to make" item with what's left for an App Store release (signing, team ID, icon, privacy manifest, listing).
- `GLOSSARY.md`: nothing new unless a term comes up in review.

## Acceptance

- The ADR follows the format of the existing ones and is linked from the spec.
