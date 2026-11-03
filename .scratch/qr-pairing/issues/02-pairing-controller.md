# PairingController + session-end callback; remove DevHarness

Status: resolved
Blocked by: 01
Spec: ../spec.md

## What

- `SessionListener` gets an `onSnapshotDelivered` callback. It fires only after `Done` is written.
- `PairingController` has injected collaborators: listener factory, scheduler, token source, address source. It implements:
  - `open() → String` pairing URI (`MinecraftListenerFactory` captures the level);
  - `screenClosed()`, which unbinds after 60 s;
  - `snapshotDelivered()`;
  - `worldLeft()`.
- `open` binds `0.0.0.0:0` with a fresh `SecureRandom` 32-byte token and logs the URI at INFO.
- Delete `DevHarness`.
- `tools/probe` takes a pairing URI argument.

## Acceptance

- `PairingControllerTest` (fake scheduler and fake factory) covers:
  - open binds with a fresh token;
  - the 60 s grace unbind;
  - reopening inside the grace period cancels the unbind and rotates the token;
  - snapshot delivered unbinds;
  - world leave unbinds.
- `SessionListenerTest` has a new case: the callback fires after `Done` and not after UNAUTHORIZED, BUSY or a mid-stream source failure.

## Comments

- Added `SessionListener.onTerminated` (not in the original spec). Without it, a grace-period unbind during an active download would close `MinecraftSnapshotSource` and free the icon framebuffer mid-stream.
- Added a test for a stale delivery callback from a previous pairing (generation guard).
- No BUSY test for `onSnapshotDelivered`: BUSY connections never reach `handleClient`.
- `open()` throws `IOException` when there is no client level.
