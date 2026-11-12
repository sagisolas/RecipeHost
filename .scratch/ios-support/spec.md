# Full iOS support

Status: ready-for-agent

## Problem

The iOS wrapper only has to keep compiling (`shared/src/iosMain/.../MainViewController.kt`): `QrScanner { null }`, no `recipehost://pair` routing. Almost everything else is already shared in `commonMain` (screens, ViewModels, the Ktor raw-TCP `SessionClient`, the SQLDelight `Catalog`, Compose resources), so what's missing is the iOS edge of the app plus the places where Android behaviour is assumed:

- **Pairing**: no scanner and no deep link, so an iOS phone can't pair at all.
- **Local Network privacy** (iOS 14+): the first TCP connect to a LAN address shows a system prompt, and connections fail until the user allows it. ADR 0001's transport is exactly that, so this can't be avoided, only explained.
- **Back**: pushed screens (`Item`, `ModList`, `ModVersions`) are left with the Android system back. iOS has none.
- **Backgrounding**: iOS suspends the app a few seconds after it leaves the foreground, which drops a running `Scan`'s socket.

## Solution

- The Swift `iOSApp` owns one `AppGraph` per process (like `MainActivity`), built by a Kotlin factory in `iosMain`, and hands it to `MainViewController(graph)`.
- `recipehost://pair` is a registered URL scheme; SwiftUI `.onOpenURL` routes it to `graph.pairing.start`. The iOS system Camera recognises the in-game QR and offers to open the app, so pairing works even without the in-app scanner.
- An in-app scanner on AVFoundation, written in Kotlin in `iosMain`.
- A Local Network hint on the Scan progress network failure, shown on iOS only.
- An edge-swipe back gesture plus a visible back button on pushed screens, on both platforms. The button lives in a shared `McHeader` (centred title, icon back button on the left) that every screen header uses.
- A UIKit background task held while a Scan runs, so a short app switch doesn't kill it. A longer one still drops the socket; the mod keeps the listener up after a mid-stream drop (ADR 0001), so Scan again with the same QR works.
- Platform polish: portrait only, light status bar, catalog excluded from backup, `MARKETING_VERSION` from `app_version`, template leftovers removed.

## Implementation decisions

### Composition root (iOS)

- `iosMain` exposes `fun createAppGraph(): AppGraph` and `fun MainViewController(graph: AppGraph): UIViewController`. Both are top-level functions because Swift calls them; `createAppGraph` is the iOS half of the composition root and builds the driver, the scanner and the platform hooks.
- Swift keeps the graph in the `@main` `App` struct (a `let`), so a SwiftUI re-render never rebuilds it.
- `PairingEntry.progressRequests` is already conflated, so a cold-start URL that arrives before `App` collects still lands.

### QR scanner

- `AvFoundationQrScanner` (`iosMain`, internal) implements `QrScanner`: an `AVCaptureSession` with an `AVCaptureMetadataOutput` limited to `AVMetadataObjectTypeQRCode`, shown as a modal `UIViewController` with a Cancel button, presented from the top view controller.
- It's bridged with `suspendCancellableCoroutine`: the first code read → its text; Cancel or swipe-to-dismiss → `null`; coroutine cancellation dismisses the modal and stops the session.
- Camera permission goes through `AVCaptureDevice.requestAccessForMediaType`. Denied or restricted → it throws `CameraAccessDenied`. `tryScanQr` today turns any throw into `welcome_no_scanner` ("use your camera app instead"), which is still true on iOS, but a denied permission gets its own message pointing to Settings.
- Kotlin rather than Swift: a Swift class can't cleanly implement a Kotlin `suspend fun interface`, and keeping it in `iosMain` keeps every `QrScanner` next to `AppGraph`'s other collaborators.
- Rejected: VisionKit `DataScannerViewController`, which is Swift-only (not reachable from Kotlin/Native interop) and needs iOS 16 with A12+.

### Local Network permission

- `Info.plist` gets `NSLocalNetworkUsageDescription`. No `NSBonjourServices`: the app doesn't browse Bonjour.
- There's no API to ask for the permission up front or read its state. The first `connect` while the prompt is up fails, and the Scan ends as `ScanFailure.Network.Unreachable` → `FailureReason.Network`.
- `AppGraph` takes a `localNetworkHint: Boolean` (false on Android, true on iOS) and passes it to `ScanProgressViewModel`; `ScanProgressUiState.Failed` carries it for `FailureReason.Network`, and the screen adds "Allow Local Network for RecipeHost in Settings, then scan again." Compose resources have no platform qualifiers, so the flag comes from the composition root, not from the resource system.

### Back

- Turn on Compose Multiplatform's iOS back gesture in the `ComposeUIViewController` configuration so the edge swipe reaches the inner `NavDisplay`'s `onBack = navigator::pop`. Check the exact flag in CMP 1.12.2.
- A visible back button on both platforms, in one shared design-system component, `McHeader`. As a general rule every screen header centres its title on the full width and has an icon back button on the left (none at a root, with the title still centred). ModList, ModVersions and Item move to it; ModVersions' `OK` button on the right is replaced by the back icon on the left. See issue 04.

### Background Scan

- `ScanBackgroundGrant` (`iosMain`, an instance built by `createAppGraph`) collects `scans.progress` on the app scope: `beginBackgroundTaskWithExpirationHandler` when a Scan starts, `endBackgroundTask` on `Done`/`Failed` or on expiry. It holds at most one task.
- The decision logic (when to begin, when to end) is a pure `ScanActivity` function over the progress flow in `commonMain`, so it's tested in `commonTest` against a fake grant.
- `AppGraph` gets no UIKit types: `createAppGraph` builds the graph, then the grant from `graph.scans.progress` (already `internal`, and `iosMain` is the same module).

### Platform polish

- `Info.plist`: `UISupportedInterfaceOrientations` portrait only (matches Android's `screenOrientation="portrait"`); `UIStatusBarStyleLightContent` with `UIViewControllerBasedStatusBarAppearance = NO`.
- `catalog.db` is marked `NSURLIsExcludedFromBackupKey`, mirroring `allowBackup="false"`.
- Check that the soft keyboard doesn't cover the Items search `McTextField` and that the `McBottomBar` clears the home indicator (`WindowInsets.navigationBars` maps to it on iOS).
- `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig` is still hard-coded to `1.0`, while Android's `versionName` and the Settings footer's version (`AppInfo.VERSION`) read `app_version` from `gradle.properties`. Make iOS read it too, so the version lives in one place. One option is a Gradle task that writes a generated `Version.xcconfig`, which `Config.xcconfig` `#include`s. Until then, keep the two values in sync by hand.
- Delete the template's `Greeting.kt`, `GreetingUtil.kt`, `Platform.kt` and both `Platform.*.kt` (only `Greeting` uses them).

## Decisions

1. ~~**Scanner UI**~~: decided, a native UIKit modal. A Minecraft-styled Compose scanner through `UIKitView` was rejected as more interop and lifecycle work for a screen seen for a few seconds.
2. ~~**Back button**~~: decided, the swipe plus a visible button on both platforms, through `McHeader`. The swipe alone was rejected: it's hard to discover and doesn't help VoiceOver or Switch Control users.
3. ~~**Minimum iOS version**~~: decided, iOS 16 (`IPHONEOS_DEPLOYMENT_TARGET` lowered from 18.2). Nothing in this spec needs more than iOS 14. Issue 06.

## Out of scope

- App Store release: bundle ID, team ID, signing, icon, listing, privacy manifest (`RELEASE_PLAN.md`).
- Universal links (`https://` pairing links). The custom scheme is enough for a QR.
- iPad-specific layouts and landscape.

## Testing

- TDD at pure seams, in `commonTest` so the iOS test target runs them too:
  - `ScanActivity`: begins on the first non-idle progress, ends once on `Done`/`Failed`, holds at most one task, begins again for the next Scan.
  - `ScanProgressViewModelTest`: the Local Network hint is set for `Network` failures only, and only when `localNetworkHint` is true.
  - The camera-denied message in `WelcomeViewModelTest` (and wherever else `tryScanQr` is used).
  - URL routing is already covered by `PairingEntryTest`.
- No simulator runs by the agent. The user runs `./gradlew :shared:iosSimulatorArm64Test` and the device checks:
  - the system Camera opens the app and starts a Scan, both warm and cold;
  - the in-app scanner reads the in-game QR; Cancel and a denied permission behave;
  - the Local Network prompt appears on first pairing, the hint shows, and Scan again works after allowing;
  - an app switch of a few seconds mid-Scan doesn't fail the Scan;
  - edge swipe and the back button pop pushed screens;
  - portrait lock, the status bar, the keyboard and the home indicator.

## Issues

1. Composition root and `recipehost://pair` deep link.
2. `AvFoundationQrScanner`.
3. Local Network permission and hint.
4. Back gesture and back button.
5. Keep a Scan alive in the background.
6. Platform polish.
7. ADR and release-plan update.
