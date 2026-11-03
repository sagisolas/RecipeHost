# Settings tab: Clear Catalog

Status: ready-for-agent
Blocked by: 05, 06, 07
Spec: ../spec.md

## What

- The Settings tab's root screen has one action, **Clear Catalog**, behind a confirmation dialog built from design-system components.
- Confirming runs `ClearCatalog` and leaves the bottom-navigation root for the full-screen Welcome route, discarding every tab's stack.
- If a Scan is running, the button is disabled.

## Acceptance

- ViewModel tests check that confirming clears the Catalog and navigates to Welcome, that cancelling does nothing, and that the button is disabled while a Scan runs.
