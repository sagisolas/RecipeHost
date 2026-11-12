# Local Network permission and hint

Status: ready-for-agent
Blocked by: 01
Spec: ../spec.md

## What

- `Info.plist`: `NSLocalNetworkUsageDescription` ("RecipeHost connects to Minecraft on your local network to load your recipes.").
- `AppGraph` takes `localNetworkHint: Boolean`; the Android wrapper passes `false`, `createAppGraph` passes `true`.
- `ScanProgressViewModel` takes it, and `ScanProgressUiState.Failed` carries `localNetworkHint = true` only for `FailureReason.Network` when the flag is on.
- `ScanProgressScreen` shows an extra line under the network failure text: "Allow Local Network for RecipeHost in Settings, then scan again." (new string resource).

## Acceptance

- `ScanProgressViewModelTest`: the hint is set for a `Network` failure when the flag is on, and not set for other failures or when the flag is off.
- On a device (user): the first pairing shows the system prompt; while it's up (or after Don't Allow) the Scan fails with the hint; after allowing, Scan again with the same QR succeeds.
