# PairingController + session-end callback; remove DevHarness

Status: ready-for-agent
Blocked by: 01
Spec: ../spec.md

## What

- `SessionListener` gets an `onSessionEnd` callback. It fires only after an authenticated session ends.
- `PairingController` has injected collaborators: listener factory, scheduler, token source, address source. It implements:
  - `open(ClientLevel) → PairingPayload`;
  - `screenClosed()`, which unbinds after 30 s;
  - `sessionEnded()`;
  - `worldLeft()`.
- `open` binds `0.0.0.0:0` with a fresh `SecureRandom` 32-byte token and logs the URI at INFO.
- Delete `DevHarness`.
- `tools/probe` takes a pairing URI argument.

## Acceptance

- `PairingControllerTest` (fake scheduler and fake factory) covers:
  - open binds with a fresh token;
  - the 30 s grace unbind;
  - reopening inside the grace period cancels the unbind and rotates the token;
  - session end unbinds;
  - world leave unbinds.
- `SessionListenerTest` has a new case: the callback fires after an authenticated session and not after UNAUTHORIZED or BUSY.
