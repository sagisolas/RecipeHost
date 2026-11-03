# App shell: AppGraph, Navigator with bottom-navigation tabs, Android wrapper (GMS scanner, deep link, driver)

Status: ready-for-agent
Blocked by: 02, 04
Spec: ../spec.md

## What

- **`AppGraph`** (commonMain) is the composition root. It is constructed with `SqlDriver` and `QrScanner`, and creates:
  - the database;
  - `SessionClient`;
  - `ScanIngest` and `ScanController`, in an app-scoped `CoroutineScope`;
  - the domain queries;
  - `Navigator`;
  - the ViewModel factories.
  
  Nothing in it is static except pure functions and constants (CLAUDE.md).
- **`QrScanner`** interface: `suspend fun scan(): String?`, returning the raw text, or `null` if the user cancels.
- **`Navigator`**: holds either a full-screen route (`Welcome`, `ScanProgress`) or the **bottom-navigation root**, which has two tabs in v1: `Items` and `Settings`. A `CraftTree` tab comes later, so adding a tab must be just a new enum entry, its first screen and a bar item.
  - Each tab has its own back stack of `Screen` (a sealed type: `Grid`, `ModList`, `Settings`, `Item(itemId)`). It supports `push` (onto the current tab), `pop`, `selectTab(tab)` and `showRoot(tab)` (leave the full-screen route).
  - Wired to `BackHandler`: back pops the current tab's stack. At a tab's root, back switches to Items, and at the Items root it exits the app.
  - Reselecting the active tab pops it to its root.
  - The start route is Welcome when the Catalog is empty, and the root with Items otherwise.
- **`RootScaffold`**: the tab's current screen above an `McBottomBar` (from issue 06). The bar stays visible on pushed screens.
- **`PairingEntry.start(uri)`** parses the URI with `PairingPayload.parse` and runs `ScanController.start`, then navigates to ScanProgress. A parse failure shows a domain error.
- **androidApp**:
  - `MainActivity` builds `AppGraph(AndroidSqliteDriver(...), GmsQrScanner(this))`. `GmsQrScanner` uses `play-services-code-scanner` with QR format only.
  - The manifest declares an intent filter for `recipehost` / `pair` (`VIEW`, `BROWSABLE`, `DEFAULT`). `onCreate` and `onNewIntent` route `intent.data` to `PairingEntry`.
  - Lock the orientation to portrait.
- **iosApp**: `MainViewController` keeps compiling. A `TODO` stub `QrScanner` and `NativeSqliteDriver` are enough here.

## Acceptance

- The app launches to Welcome when the Catalog is empty. `adb shell am start -d 'recipehost://pair?…'` starts a Scan against the game.
- The in-app scanner reads the in-game QR code and starts a Scan.
- Switching tabs keeps each tab's stack. Back follows the rules above.
- `NavigatorTest` covers:
  - push and pop within a tab;
  - each tab keeping its own stack;
  - back at a tab's root going to Items, and back at the Items root exiting;
  - reselecting a tab popping it to its root;
  - choosing the start route;
  - moving from a full-screen route to the root.
- The iOS framework still compiles.
