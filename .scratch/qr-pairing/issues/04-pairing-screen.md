# PairingScreen + qrcodegen bundling

Status: ready-for-agent
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
