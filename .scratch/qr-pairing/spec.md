# QR screen + listener lifecycle (replaces DevHarness)

Status: ready-for-agent

## Problem

Pairing today goes through `DevHarness`: a fixed port (25566) and an all-zero token, bound on every world join. ADR 0001 says otherwise. The listener should bind only while the player is looking at the **QR screen**, use a fresh 256-bit **Token** each time, and show the addresses, port and token as a QR code.

## Solution

- Add a 20×18 vanilla `Button` labelled `QR` to the survival `InventoryScreen`, in the row to the right of the recipe-book button.
- Clicking it opens `PairingScreen` and binds the `SessionListener`. The listener gets a fresh token and an ephemeral port.
- `PairingScreen` renders the QR code with the caption `Scan this code with "Recipehost" app` below it.
- The listener lifecycle follows ADR 0001.
- Everything is built from standard vanilla GUI elements. The mod ships **no asset files**: no lang JSON and no textures. On Fabric, mod assets only load with fabric-api's resource loader, which CLAUDE.md rules out.

## Background: inventory buttons in other mods

Vanilla has no API for this. Mods hook screen init and position their widgets against `leftPos`/`topPos` themselves. The "row" is a convention: mods share the strip at `topPos + 61`, to the right of the recipe-book button at `leftPos + 104`.

- Fabric API `ScreenEvents.AFTER_INIT`: rejected, because CLAUDE.md forbids fabric-api where builtins work.
- "Inventory Button Bar" library: rejected, because it must be installed on both client and server, and this mod is client-only (ADR 0001).
- Instead, Fabric uses a mixin on `InventoryScreen#init()` and NeoForge uses its built-in `ScreenEvent.Init.Post`.

## Implementation decisions

### Platform (ADR 0005): two new `@ExpectPlatform` methods

- `onInventoryInit(BiConsumer<InventoryScreen, Consumer<AbstractWidget>> cb)`. The callback receives the screen and an "add widget" sink.
  - Fabric: a mixin on `InventoryScreen#init()` TAIL dispatches through a static sink, like `WorldJoinDispatcher`. The mixin class extends `AbstractContainerScreen` so it can call the protected `addRenderableWidget`.
  - NeoForge: `NeoForge.EVENT_BUS` `ScreenEvent.Init.Post`, filtered to `InventoryScreen`, using `event::addListener`.
- `onWorldLeave(Runnable cb)`.
  - Fabric: a mixin on `Minecraft#disconnect` HEAD. Verify the 1.21.1 signature.
  - NeoForge: `ClientPlayerNetworkEvent.LoggingOut`.
- `onWorldJoin` stays, but `RecipeHost.init` no longer uses it.

### Common mixin (`:mod:common`)

- `AbstractContainerScreenAccessor` provides `@Accessor` for `leftPos` and `topPos`.
- It is declared in `recipehost-common.mixins.json`, which both `fabric.mod.json` and `neoforge.mods.toml` list.

### UI (`:mod:common/ui/`)

- `PairingButton extends Button`:
  - Size 20×18, label `Component.literal("QR")`, tooltip `Tooltip.create(Component.literal("Pair with RecipeHost app"))`.
  - In `renderWidget`, every frame and before calling super:
    - read `leftPos`/`topPos` through the accessor;
    - collect the bounds of every `AbstractWidget` in `screen.children()` except itself;
    - call `setX(ButtonRowLayout.slotX(...))` and `setY(topPos + 61)`.
  - The check runs every frame, not once at init, for three reasons:
    - Buttons that other mods add after our init hook (fabric-api `AFTER_INIT`, NeoForge event order) are still seen.
    - The button follows the recipe-book toggle, which shifts `leftPos` without re-running `init`.
    - It follows window resizes.
  - The cost is a few dozen rectangle tests per frame.
- `ButtonRowLayout` (pure): `static int slotX(int startX, int y, int w, int h, List<Rect> occupied)`.
  - It starts at `leftPos + 126` and moves 22 px right until the candidate rectangle overlaps nothing.
  - When the panel row is full, it keeps going past the panel's right edge (no wrapping).
  - Its result is the same whatever order the widgets come in, so two mods using the same strategy settle within one frame.
  - Known limit: mods that draw buttons without registering a widget can't be detected.
- `PairingScreen extends Screen`:
  - Title `Component.literal("RecipeHost pairing")`. Takes `parent` and a `QrCode`.
  - The QR side length is `min(width, height) * 0.6`, snapped to whole pixels per module and centred.
  - `GuiGraphics.fill` draws a white quiet zone (4 modules), then one black fill per dark module.
  - `drawCenteredString(font, Component.literal("Scan this code with \"Recipehost\" app"), …)` puts the caption under the code, in white.
  - A vanilla `Done` button (`CommonComponents.GUI_DONE`, which is already translated by vanilla) sits below the caption.
  - `onClose` calls `PairingController.screenClosed()` and does `minecraft.setScreen(parent)`.
  - `isPauseScreen()` returns `false`, so singleplayer keeps ticking while the phone pulls the snapshot.
- Strings are English `Component.literal`s for v1. Localizing them is out of scope until there's a decision on asset loading.

### Pairing (`:mod:common/pairing/`)

- `PairingController` owns the listener lifecycle. It has injected collaborators (listener factory, `ScheduledExecutorService`, token source, address source), so it can be unit-tested.
  - `open(ClientLevel) → PairingPayload`:
    - Cancels any pending grace unbind.
    - Closes any existing listener and source.
    - Creates a new token with `SecureRandom` (32 bytes).
    - Binds `0.0.0.0:0` (an ephemeral port) with a fresh `MinecraftSnapshotSource`.
    - Logs the payload URI at INFO for the probe.
  - `screenClosed()`: schedules unbind after 30 s. An active session runs to completion, because closing the server socket leaves accepted sockets alone.
  - `sessionEnded()`: unbinds immediately.
  - `worldLeft()`: unbinds immediately.
- `HostAddresses` is a thin, untested adapter: every address of every interface from `NetworkInterface.getNetworkInterfaces()`, unfiltered and in enumeration order. **The mobile app filters**: it knows its own local IP and subnet, so it can pick the reachable host better than the mod can.
- `PairingPayload` (pure):
  - Format: `recipehost://pair?v=1&h=192.168.1.5,10.0.0.2&p=51234&t=<base64url-no-pad token>`.
  - Hosts keep their input order. IPv6 scope ids (`fe80::1%en0`) are percent-encoded (`%25`).
  - Because nothing is filtered, the URI grows with the number of interfaces: roughly 15 chars per IPv4 host and up to ~40 per IPv6 host. A typical machine is still well within the QR's capacity, though the code gets denser.
  - It can also serve as a mobile deep link later.

### Net

- `SessionListener` gets an `onSessionEnd` callback. It is fired from `handleClient`'s `finally` only after a successful auth, so UNAUTHORIZED and BUSY attempts don't end pairing.

### QR encoding

- `io.nayuki:qrcodegen:1.8.0` (MIT, about 30 KB, no transitive dependencies), bundled jar-in-jar like `:protocol`: `include` on Fabric, `jarJar` on NeoForge. Use ECC level M.
- Rejected: ZXing core (about 550 KB, mostly decoders) and hand-rolling Reed–Solomon plus masking.

### Removals and updates

- Delete `DevHarness`. `RecipeHost.init` now registers `onInventoryInit` (which adds a `PairingButton`) and `onWorldLeave` (which calls `PairingController.worldLeft`).
- `tools/probe` drops the dev port and token. It takes a pairing URI as its only argument (copied from the game log).
- GLOSSARY: add **Pairing payload** (the `recipehost://pair` URI encoded in the QR).

## Out of scope (v1)

- The creative inventory (`CreativeModeInventoryScreen`).
- A keybinding to open the QR screen.
- Localized UI strings, and any asset files.
- Mobile-side scanning and parsing.

## Testing (TDD at pure seams)

- `PairingPayloadTest`: checks `toUri` against hand-written URIs: single host, multiple hosts keep their order, token is base64url without padding, IPv6 and scope-id encoding.
- `ButtonRowLayoutTest` covers:
  - an empty row;
  - the first slot taken;
  - a gap between two taken slots;
  - a partial overlap counting as taken;
  - overflow past the panel edge;
  - the same result when the occupied list is shuffled.
- `PairingControllerTest` uses a fake scheduler and a fake listener factory. It checks that:
  - open binds with a fresh token;
  - close followed by 30 s unbinds;
  - reopening inside the grace period cancels the unbind and rotates the token;
  - session end unbinds;
  - world leave unbinds.
- `SessionListenerTest`: one new case checking that `onSessionEnd` fires after an authenticated session and not after UNAUTHORIZED or BUSY.
- In-game smoke test, on both loaders:
  - The button appears.
  - It follows the recipe-book toggle.
  - It steps aside for another mod's button.
  - The QR scans to the logged URI.
  - The probe pulls a snapshot.
  - The port is closed 30 s after leaving the screen.

## Issues

1. `PairingPayload` (pure) + `HostAddresses` adapter.
2. `PairingController` + the `SessionListener` end callback; delete DevHarness; update the probe.
3. `Platform.onInventoryInit`/`onWorldLeave` + mixins/events + `PairingButton` + `ButtonRowLayout`.
4. `PairingScreen` + qrcodegen bundling.
