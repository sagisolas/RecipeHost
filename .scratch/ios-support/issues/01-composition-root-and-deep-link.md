# Composition root and `recipehost://pair` deep link

Status: done
Blocked by: —
Spec: ../spec.md

## What

- `iosMain`: replace `MainViewController()` with:
  - `fun createAppGraph(): AppGraph`, which builds `NativeSqliteDriver(CatalogDatabase.Schema, "catalog.db")` and the scanner (still the `QrScanner { null }` stub until issue 02);
  - `fun MainViewController(graph: AppGraph): UIViewController`, which is `ComposeUIViewController { App(graph) }`.
- `iosApp`:
  - `iOSApp` holds `let graph = MainViewControllerKt.createAppGraph()` and passes it to `ContentView`, which passes it to `ComposeView`.
  - `ContentView` adds `.onOpenURL { url in graph.pairing.start(uri: url.absoluteString) }`, filtered to scheme `recipehost`.
  - `Info.plist`: `CFBundleURLTypes` with the `recipehost` scheme.
- Remove the `TODO(iOS)` about URL routing.

## Acceptance

- The iOS framework compiles (`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`) and `spotlessCheck` passes.
- On a device (user): scanning the in-game QR with the system Camera opens the app and starts a Scan, both when the app is running and from a cold start. An invalid `recipehost://pair?…` link shows the Invalid code error.
