# `AvFoundationQrScanner`

Status: ready-for-agent
Blocked by: 01
Spec: ../spec.md

Decided: a native UIKit modal (spec question 1).

## What

- `iosMain`: `internal class AvFoundationQrScanner : QrScanner`.
  - Asks for camera access with `AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo)` when it hasn't been decided; denied or restricted throws `CameraAccessDenied`.
  - Presents a modal `UIViewController` from the key window's top view controller: an `AVCaptureVideoPreviewLayer` filling the view, plus a Cancel button.
  - `AVCaptureMetadataOutput` with `metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)`; the first `stringValue` resumes the coroutine and dismisses.
  - Cancel, or a swipe-to-dismiss (`presentationControllerDidDismiss`), resumes with `null`.
  - `invokeOnCancellation` stops the session and dismisses the modal on the main thread.
  - The capture session starts and stops off the main thread, as Apple requires.
- `createAppGraph` passes it instead of the stub.
- `Info.plist`: `NSCameraUsageDescription` ("RecipeHost uses the camera to scan the pairing code shown in Minecraft.").
- `CameraAccessDenied` gets its own message on the screens that call `tryScanQr`: "Allow camera access for RecipeHost in Settings, or scan the code with your Camera app." This means `tryScanQr` returns a small result type (scanned, no scanner, camera denied) instead of `Boolean`. Android never produces camera denied.

## Acceptance

- Tests (TDD): the ViewModels that use `tryScanQr` show the camera-denied message for `CameraAccessDenied` and keep the no-scanner message for other failures. The scanner itself has no unit test.
- On a device (user): the scanner reads the in-game QR and starts a Scan; Cancel and swipe-down return to the screen without an error; a denied permission shows the Settings message; scanning twice in a row works.
