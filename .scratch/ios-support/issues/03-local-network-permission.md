# Local Network permission and error

Status: done
Blocked by: 01
Spec: ../spec.md

## What

- `Info.plist`: `NSLocalNetworkUsageDescription` ("RecipeHost connects to Minecraft on your local network to load your recipes.").
- `platform/LocalNetworkAccess` (public `fun interface`, `suspend fun isDenied(host): Boolean`), which `AppGraph` takes and passes to `SessionClient`. The Android wrapper passes `LocalNetworkAccess { false }`; `createAppGraph` passes `NetworkFrameworkLocalNetworkAccess` (`iosMain`), a UDP `nw_connection` probe to an unused port that reads `nw_path_unsatisfied_reason_local_network_denied`, giving up (not denied) after a second.
- `SessionClient`: when no host accepts a connection and the hook says the first host is denied, it fails with `ScanFailure.Network.LocalNetworkDenied` instead of `Unreachable`.
- `ScanProgressViewModel` maps it to `FailureReason.LocalNetworkDenied`, and `ScanProgressScreen` shows "Allow Local Network for RecipeHost in Settings, then scan again." (new string resource) for it. Other network failures keep the Wi-Fi message.

## Acceptance

- `SessionClientTest`: no host reachable while the hook denies → `LocalNetworkDenied`; while it allows → `Unreachable`.
- `ScanProgressViewModelTest`: `LocalNetworkDenied` → `FailureReason.LocalNetworkDenied`; `Unreachable` stays `Network`.
- On a device (user): the first pairing shows the system prompt; after Don't Allow the Scan fails with the Local Network message; with the permission allowed but the pairing screen closed it shows the Wi-Fi message; after allowing, Scan again with the same QR succeeds. Also note which message shows when the Scan fails while the prompt is still up.
