# Mobile app v1 (Android first, iOS-ready)

Status: ready-for-agent

## Problem

The companion mod can stream a real `Snapshot`, but there is no mobile app to receive it. `:shared` is still the Compose Multiplatform template. The player needs an offline viewer that:
- pairs with the game by QR code,
- builds up a `Catalog` from every `Scan`, and
- lets them browse `Item`s by mod version and see each item's recipes, as in JEI.

## Solution

- **Shared code.** A Compose Multiplatform app in `:shared` holds all logic and UI. `androidApp` and `iosApp` are thin wrappers. Only Android ships in v1. Nothing in `commonMain` may use `java.*` or Android APIs. Platform capabilities (QR scanning, the database driver) come in through interfaces that the wrapper supplies.
- **Entry point.** The user scans the QR code shown by the companion mod. They can use the in-app button (the GMS code scanner) or the system camera, which opens the `recipehost://pair` deep link. The app connects and receives the `Snapshot`. Items and Recipes are committed to the `Catalog` in one transaction. The app then opens the item grid while Icons keep arriving in the background.
- **Main screen.** An item grid with search and an `@modid` filter. A **Mods** button opens the mod list, where tapping a mod filters the grid and a dropdown picks that mod's `Mod release`. Tapping an Item opens its **Recipes** and **Uses** tabs.
- **UI.** A custom design system built from GUI textures that the user supplies and the Monocraft font. There is no Material dependency.

Domain rules come from ADR 0010 (additive Catalog), ADR 0011 (recipe attribution) and ADR 0012 (SQLDelight). Vocabulary is in `GLOSSARY.md`.

## Wire changes (mod side)

ADR 0011 needs these protocol changes:
- `Recipe.id = 5`: the namespaced recipe id. Vanilla dispatch uses `RecipeHolder.id()`; EMI uses `EmiRecipe.getId()` later.
- `Handshake.instance_name = 7`. The value is the folder name of `Minecraft.gameDirectory`, except in two cases:
  - If that folder is named `.minecraft` or `minecraft` and its parent contains `instance.cfg` (Prism or MultiMC), use the parent's folder name.
  - Otherwise, a `.minecraft` folder becomes `Minecraft`.
  
  This runs in `:mod:common` with no `Platform` method. Check the rule against real CurseForge, Modrinth App, Prism and vanilla installs.
- `ModInfo` is sent for every namespace that owns an Item **or** a Recipe. A recipe namespace with no loaded mod behind it gets `{id: ns, name: ns, version: "datapack"}`.
- `WireProtocol.PROTOCOL_VERSION` stays at `1`. Nothing has been published yet, so these changes amend v1 instead of bumping the version. The app rejects any version other than `1` with a domain error.

## Implementation decisions

### Layers (packages in `:shared`, `org.sagisolas.recipehost.*`)

- **`data`**:
  - `pairing`: parses `PairingPayload`.
  - `transport`: framing and the session client.
  - `catalog`: the SQLDelight schema and DAOs.
  - `scan`: writes a `Snapshot` into the `Catalog`.
- **`domain`**: use cases where they add something, for example the `Version selection` rules, resolving recipe variants across `Instance`s, and grid filtering. Thin pass-throughs are skipped.
- **`presentation`**: one ViewModel per screen (`org.jetbrains.androidx.lifecycle`), exposing `StateFlow<UiState>`.
- **`ui`**: screens, plus `ui/designsystem` for the components.
- **Wiring.** `AppGraph` is the composition root. The platform wrapper constructs it, passing a `SqlDriver` and a `QrScanner`. The CLAUDE.md rule applies here too: services are instances that receive their collaborators through the constructor, and nothing is static except pure functions and constants.
- **Navigation.** The root screen has bottom navigation with two tabs in v1: **Items** and **Settings**. A **Craft tree** tab comes later, so the tab list must be easy to extend. Each tab keeps its own hand-written back stack (`List<Screen>`) inside one `Navigator` instance, wired to `BackHandler`. The bottom bar stays visible on screens pushed inside a tab.
  - Tapping an ItemStack inside a recipe pushes another item screen onto the current tab's stack, so the user can follow a chain of recipes as in JEI.
  - Back pops the current tab's stack. At a tab's root, back switches to Items, and at the Items root it exits the app.
  - Reselecting the active tab pops it to its root.
  - Welcome and Scan progress are full-screen, outside the bottom navigation.

### Pairing and transport

- **`PairingPayload.parse(uri)`** (pure) reads `v`, `h`, `p` and `t`:
  - Hosts keep their order, and percent-encoded IPv6 scope ids are decoded.
  - The token is base64url without padding and must decode to 32 bytes.
  - A `v` other than `1` is rejected.
- **`EnvelopeFraming` (Kotlin, `commonMain`)** is a varint length prefix followed by the `Envelope` bytes, the same format as the Java `EnvelopeFraming`. Byte-for-byte compatibility is checked by shared golden vectors (see Testing).
- **`SessionClient`** uses `ktor-network` raw sockets on `Dispatchers.IO`:
  - It tries the hosts **one at a time, in payload order**, with a 1.5 s connect timeout each. The mod claims the session when it accepts the TCP connection, before Auth arrives (`SessionListener`), so racing the hosts in parallel can lose to its own `BUSY`. Leave a code comment there explaining this.
  - Once connected, it sends `Auth{token}` and exposes the incoming envelopes as a `Flow<Envelope>`.
- **Errors:**
  - A network failure (I/O error, timeout, EOF before `Done`) gets **one automatic retry** with the same payload. The listener stays up after a mid-stream drop (ADR 0001).
  - Domain errors get no retry and show a message: `Error{UNAUTHORIZED}`, `Error{BUSY}`, `Error{SOURCE_FAILURE}`, a protocol version other than 1, or an invalid payload.

### Scan

`ScanIngest` consumes the envelope flow and reports progress as `ScanProgress`:
1. **Handshake.** It records the `Instance` (by name) and the Mod releases `(mod id, version, minecraft version)`, and reads the item and recipe totals used for progress.
2. **ItemBatch / RecipeBatch.** Batches are staged in memory, or in staging tables if memory turns out to be a problem.
3. **After the last RecipeBatch.** One transaction does all of this:
   - Upserts Items by `(release, item id)`, keeping each item's registry position.
   - For every Mod release in the Handshake, replaces this Instance's recipes.
   - Sets the `Version selection` to the Scan's releases. Mods not in the Scan keep their choice.
   - Updates each release's `last_scanned_at`.

   If the connection drops before this commit, the Scan adds nothing.
4. **IconBatch.** Each batch is upserted as it arrives, matched to `(release, item id)`. A drop during Icons keeps everything already committed.

The scan runs in an app-scoped `CoroutineScope` owned by `AppGraph`, not in a screen's ViewModel, so it keeps going while the user browses. There is no Android foreground service: an OS kill or backgrounding counts as a network drop.

### Catalog (SQLDelight, ADR 0012)

This is a sketch; the implementer finalises it.

- `mod_release(id PK, mod_id, version, mc_version, name, last_scanned_at, UNIQUE(mod_id, version, mc_version))`
- `item(release_id, item_id, display_name, ordinal, icon BLOB NULL, PK(release_id, item_id))`, plus an FTS table over `display_name`.
- `instance(id PK, name UNIQUE, last_scanned_at)`
- `recipe(id PK, release_id, recipe_id, instance_id, type_id, body BLOB, body_hash, UNIQUE(release_id, recipe_id, instance_id))`. `body` is the encoded wire `Recipe`, and `body_hash` is used to merge identical variants.
- `recipe_output(recipe_pk, item_id)` and `recipe_input(recipe_pk, item_id)`, each indexed on `item_id`. Inputs cover every alternative in every Slot.
- `version_selection(mod_id PK, release_id)`

Queries:
- **Grid.** Items of the selected releases, optionally filtered by mod and by search text (FTS on the name, plus `@modid`). Order: minecraft first, then by the mods' order in the latest Scan, then `ordinal`.
- **Mod list.** Mods that have at least one release, each with its releases ordered by `last_scanned_at` descending.
- **Recipes(item) / Uses(item).** Recipes owned by the selected releases whose output (or input) is the item's id, grouped by `type_id`. Variants of the same `(release, recipe_id)` from different Instances collapse by `body_hash`. Each group carries its Instance names, and the default is the group from the most recently scanned Instance.
- **Resolve ItemStack.** Takes the id's namespace, finds the selected release for that mod and looks up the Item. If none is found, the ItemStack shows its id with a placeholder Icon.
- **Clear Catalog.** Deletes all rows.

### Screens

1. **Welcome** (full screen, no bottom bar). Shown when the Catalog is empty. It has a "Scan QR" button and a hint to open the pairing screen in-game.
2. **Scan progress** (full screen, no bottom bar). Shows the steps connecting → receiving items (n/N) → receiving recipes (m/M), using the Handshake totals. Domain errors appear as messages, each with a "Scan again" button. When the commit finishes, the app moves to the bottom-navigation root with the Items tab selected.
3. **Items tab: item grid** (the Items tab's first screen). The column count follows the screen width (fixed-size Slots, as many as fit). The top bar has a search field, a **Mods** button that opens the mod list, and a Scan button. While a Scan's Icons are still arriving, a thin progress bar shows. Items whose Icon hasn't arrived yet show a placeholder.
4. **Mod list** (pushed onto the Items tab's stack by the Mods button). Mods with their names. Tapping a mod filters the grid and goes back to it. A dropdown per mod lists its Mod releases ("6.0.1 · MC 1.21.1"), and changing it updates the `Version selection`, which is saved.
5. **Item** (pushed onto the Items tab's stack). Has **Recipes** and **Uses** tabs. Each tab is a vertical list with one section per recipe type. A section shows one recipe at a time, with ◀ ▶ and a counter like "3 / 12". When a recipe has several different bodies across Instances, the section shows a "From <instance name>" selector. How each recipe type looks is **deferred** (the user has a design). Until then, every type uses a generic layout: input Slots in a row, an arrow, then the results, with alternatives in a Slot cycling every second. Tapping an ItemStack opens that Item.
6. **Settings tab.** "Clear Catalog", which asks for confirmation.

The app is portrait-only on phones in v1. UI strings are English and go through Compose `stringResource` from the start, so translating them later is cheap.

### Design system (`ui/designsystem`)

- **Textures.** The user supplies the GUI textures as PNGs in `shared/src/commonMain/composeResources/drawable/`, named after Minecraft's sprite paths with `/` replaced by `_` (for example `container_slot.png`, `widget_button.png`, `widget_button_highlighted.png`). Each component has a fixed aspect ratio taken from its texture and a fixed size in dp, like any mobile UI. The texture is the component's background `Painter`, and Compose scales it to fill. There is **no nine-slice in v1**. We'll check in practice whether this is enough and add nine-slice only if a component really needs to stretch.
- **No GUI scale.** Components don't scale with the screen. Larger screens get different layouts, not bigger components.
- **Font.** Monocraft (OFL), bundled in `composeResources/font`, with its licence file next to it. Text sizes are ordinary `sp`.
- **Components:** `McPanel`, `McButton`, `McTextField`, `McSlot`, `ItemIcon` (fixed size, nearest-neighbour, with a placeholder), `McDropdown`, `McTabs`, `McPager` (◀ ▶ and the counter), `McProgressBar`, `McBottomBar` (the tab bar), and `McText`.
- No `material3`, so remove it from `shared/build.gradle.kts` and the version catalog. Use foundation, ui and runtime only.

### Platform wrappers

- **androidApp:**
  - `MainActivity` builds `AppGraph(driver = AndroidSqliteDriver(...), qrScanner = GmsQrScanner(activity))`.
  - The manifest declares an intent filter for `recipehost://pair`, and deep links from `onCreate` / `onNewIntent` go to `AppGraph.pairing.start(uri)`.
  - `GmsQrScanner` uses `play-services-code-scanner`. This needs Google Play Services; devices without it can still use the deep link from any camera app.
- **iosApp (not in v1):** `MainViewController` will build `AppGraph` with `NativeSqliteDriver` and an AVFoundation scanner, and handle the URL scheme. Shared code must not block this.

### New dependencies

- `kotlinx-coroutines-core`
- `ktor-network`
- SQLDelight: the plugin, runtime and coroutines extensions; `android-driver` for Android and `native-driver` for iOS later.
- `play-services-code-scanner`, in `androidApp` only.
- Tests: `kotlinx-coroutines-test`, plus SQLDelight's `sqlite-driver` for JVM and Android host tests.

## Out of scope (v1)

- The iOS wrapper. It only has to keep compiling with the shared module.
- Detailed rendering for each recipe type.
- Craft trees down to raw materials. Later, these can be built on the Recipes(item) query against the `Version selection`.
- Landscape and tablet layouts.
- Deleting individual Mod releases or Instances.
- A "browse as Instance" filter.
- Icon variants (NBT, enchantment).
- Resuming a Snapshot or fetching only Icons.
- Nine-slice textures.

## Testing (TDD at pure and fake seams)

- **`PairingPayloadTest`:** single and multiple hosts, IPv6 scope ids, token length and base64url, a bad `v`, missing parameters.
- **Framing golden vectors:** one hard-coded set of `Envelope` byte frames (empty `Done`, a frame whose length needs a two-byte varint, `Auth`). `EnvelopeFramingTest` in `:protocol` encodes them on the Java side and checks the bytes match; the Kotlin test does the same in `commonTest`.
- **`SessionClientTest`:** uses a fake socket connector. It checks that:
  - hosts are tried in order, with a timeout before moving to the next;
  - `Auth` is sent first;
  - one retry happens on a network drop, and none on `UNAUTHORIZED`, `BUSY` or `SOURCE_FAILURE`;
  - a protocol version mismatch is reported as a domain error.
- **`ScanIngestTest`:** uses an in-memory SQLDelight driver and a scripted envelope flow. It checks that:
  - nothing is committed before the last RecipeBatch;
  - an Instance's recipes for a release are replaced, including deletions;
  - recipes from another Instance are kept;
  - `Version selection` is set to the Scan's releases, and other mods keep theirs;
  - Icons are filled in after the commit, and a drop during Icons keeps what was committed;
  - a later Scan overwrites display names.
- **Catalog query tests:**
  - the grid honours the version selection and the `@mod` and name search;
  - Recipes and Uses are grouped by type;
  - variants collapse by hash and carry their Instance names;
  - an ItemStack from an unknown mod resolves to a placeholder.
- **ViewModel tests** with fake use cases: the scan progress states, and switching the version dropdown updating the grid.
- **Manual on device:**
  - in-app scan, and a deep link from the system camera;
  - a large modpack: memory use and the time to commit;
  - turning Wi-Fi off during Icons;
  - checking the textures on phones of different densities.

## Issues

1. Wire schema changes (mod side): `Recipe.id`, `Handshake.instance_name` with the instance folder rule, `ModInfo` for namespaces that only own recipes.
2. Kotlin `EnvelopeFraming` plus golden vectors shared with `:protocol`; `PairingPayload.parse`.
3. `SessionClient` on ktor-network: hosts tried in turn, Auth, the retry and error rules.
4. Catalog schema on SQLDelight, and `ScanIngest`, including the app-scoped scan scope.
5. Catalog queries and domain logic: grid and search, mod list and `Version selection`, Recipes and Uses with Instance variants, ItemStack resolution.
6. Design system foundations: Monocraft, core components with flat-colour backgrounds.
7. App shell: `AppGraph`, `Navigator` with bottom-navigation tabs, Android wrapper (`GmsQrScanner`, deep link, driver); iOS stub kept compiling.
8. Screens: Welcome, Scan progress, Items tab (grid and mod list).
9. Item screen: Recipes and Uses tabs, pager, "From <instance>" selector, generic recipe layout.
10. Settings tab: Clear Catalog.
11. Design system textures: the real GUI textures and component sizes replace the flat colours.
