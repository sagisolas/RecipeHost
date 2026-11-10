# Navigation 3 for app navigation

The mobile app navigates with **Navigation 3** (`org.jetbrains.androidx.navigation3:navigation3-ui`, the version Compose Multiplatform pins), plus `lifecycle-viewmodel-navigation3` so each entry gets its own ViewModel store. The first version used a hand-written `Navigator` that also rendered screens and handled back. That code was replaced so the library owns rendering, transitions, back handling and per-entry saved state.

Navigation 3 leaves the back stacks to the app, so a small `Navigator` state holder remains. It keeps one stack per tab, and `NavDisplay` shows only the selected tab's stack. Back pops within that tab and, at its root, exits the app: the bottom bar is never part of back. (This replaced the official "multiple back stacks" recipe, under which back from any tab's root landed on Items, once the start tab could be Bookmarks.) Full-screen routes (Welcome, Scan progress) are a single-entry outer `NavDisplay`.

Navigation 3 and Compose save the navigation state themselves. Each tab's stack is a `rememberNavBackStack`, and the selected tab and full-screen route are `rememberSerializable`. So the state survives activity recreation and process death without app code. Keys are `@Serializable`, which needs the kotlinx-serialization plugin. On iOS the stacks are saved through a `SavedStateConfiguration` that registers the one `NavKey` subclass polymorphically.

Because that state lives in composition, `rememberNavigator` builds the `Navigator` in `App`, not in `AppGraph`. Non-UI code doesn't navigate. `PairingEntry` emits a conflated `progressRequests` event when a pairing URI arrives, and `App` turns it into `showScanProgress()`. Being conflated, the event also covers a cold-start deep link that arrives before the UI collects.

Each back-stack key is a `StackEntry(screen, id)` with a random UUID. Navigation 3's default content key is the key's `toString()`, which includes that id. The same Item pushed twice, in one tab or in two, is therefore two entries, each with its own saved state and ViewModels.

## Considered options

- **Navigation Compose 2.x** (`NavHost`/`NavController`). It has per-tab stacks built in through `saveState`/`restoreState`, so less app code. It was rejected because Navigation 3 is the actively developed API, and Navigation Compose is only maintained.
- **Decompose.** It is stable, but it moves navigation into a component tree outside Compose and would replace the ViewModel layer. That is too much rework for the gain.
- **Keeping the hand-written navigator.** Rejected: it reimplemented rendering, back handling and state saving that the library provides.
- **Keeping the stacks in `AppGraph` and saving them through the `SavedStateRegistry` by hand.** Rejected: the library already saves its own back stacks.

## Consequences

- Navigation 3 ships with Compose Multiplatform 1.12.2 as a release candidate (1.2.0-rc01). Move to the stable version when Compose Multiplatform pins one.
- A restored Scan progress route has no Scan behind it, because the process that ran it is gone. The Scan progress screen (issue 08) must treat that as an interrupted Scan.
