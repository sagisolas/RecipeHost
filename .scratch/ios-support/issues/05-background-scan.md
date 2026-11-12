# Keep a Scan alive in the background

Status: ready-for-agent
Blocked by: 01
Spec: ../spec.md

## What

- `commonMain`: a pure `ScanActivity` mapping `Flow<ScanProgress>` to `Flow<Boolean>` (a Scan is running), with `distinctUntilChanged`.
- `iosMain`: `internal class ScanBackgroundGrant(progress: Flow<ScanProgress>, scope: CoroutineScope, tasks: BackgroundTasks)`.
  - `BackgroundTasks` is a small interface over `UIApplication.beginBackgroundTaskWithExpirationHandler` / `endBackgroundTask`; the real one calls UIKit on the main thread.
  - On true it begins a task (if none is held); on false it ends it. The expiration handler ends the task too, so iOS never kills the app for overrunning.
- `createAppGraph` builds it from `graph.scans.progress` after building the graph.

## Acceptance

- Tests (TDD, `commonTest`): `ScanActivity` turns idle → running → `Done` into false → true → false, and `Failed` ends it too; `ScanBackgroundGrant` with a fake `BackgroundTasks` begins once per Scan, ends once, and ends on expiry without a double end.
- On a device (user): switching to another app for ~10 s during a Scan of a large pack, then returning, finds the Scan still running or finished, not failed.
