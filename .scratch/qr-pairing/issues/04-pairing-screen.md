# PairingScreen + qrcodegen bundling

Status: resolved
Blocked by: 03
Spec: ../spec.md

## What

- Add the dependency `io.nayuki:qrcodegen:1.8.0`: `implementation` in common, `include` on Fabric, `jarJar` on NeoForge.
- `PairingScreen extends Screen`:
  - The QR is drawn with `GuiGraphics.fill`: a white quiet zone of 4 modules, side length `min(w,h)*0.6` snapped to whole pixels, ECC level M.
  - The caption `Scan this code with "Recipehost" app` goes below it.
  - A `CommonComponents.GUI_DONE` button.
  - `isPauseScreen` returns `false`.
  - `onClose` calls `controller.screenClosed()` and returns to `parent`.
- The pairing button (`PairingButtonInstaller`) opens it via `PairingController.open`.

## Acceptance

- Smoke test on both loaders:
  - the QR scans to the logged URI;
  - the probe pulls a snapshot;
  - the port is closed 60 s after leaving the screen.

## Comments

- Implemented. qrcodegen 1.8.0 is in `libs.versions.toml`: `implementation` in common, and `implementation` + `include` on both loaders, the same way as `:protocol`.
- `PairingScreen` takes `(parent, QrCode, onRemoved)`. The grace timer starts from `removed()`, so every way of leaving the screen counts: Done, Esc, or disconnecting. `onClose` returns to the inventory.
- If `open()` or the QR encoding fails (for example, too many host addresses for one code), the error is logged, `screenClosed()` releases any listener that was already bound, and no screen opens.
- In-game check passed on Fabric (user confirmed).
