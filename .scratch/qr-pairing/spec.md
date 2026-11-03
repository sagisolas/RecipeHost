# QR screen + listener lifecycle (replaces DevHarness)

Status: ready-for-agent

## Problem

Pairing today goes through `DevHarness`: a fixed port (25566) and an all-zero token, bound on every world join. ADR 0001 says otherwise. The listener should bind only while the player is looking at the **QR screen**, use a fresh 256-bit **Token** each time, and show the addresses, port and token as a QR code.

## Solution

- Add a 20×20 vanilla `ImageButton` (icon only, no button background) with the pairing icon to the survival `InventoryScreen`, in the top-left corner of the screen.
- Clicking it opens `PairingScreen` and binds the `SessionListener`. The listener gets a fresh token and an ephemeral port.
- `PairingScreen` renders the QR code with the caption `Scan this code with "Recipehost" app` below it.
- The listener lifecycle follows ADR 0001.
- Everything is built from standard vanilla GUI elements. Strings are English literals for v1. The button icon is a GUI-atlas sprite in the conventional `assets/` directory; Fabric loads it through fabric-api (ADR 0009).

## Background: inventory buttons in other mods

Vanilla has no API for this. Mods hook screen init and position their widgets against `leftPos`/`topPos` themselves. The "row" is a convention: mods share the strip at `topPos + 61`, to the right of the recipe-book button at `leftPos + 104`.

- Fabric API `ScreenEvents.AFTER_INIT`: the standard Fabric hook. fabric-api is now allowed (ADR 0009), but the existing `InventoryScreen#init` mixin stays until `.scratch/fabric-api-adoption` replaces the mixins.
- "Inventory Button Bar" library: rejected, because it must be installed on both client and server, and this mod is client-only (ADR 0001).
- Instead, Fabric uses a mixin on `InventoryScreen#init()` and NeoForge uses its built-in `ScreenEvent.Init.Post`.

## Implementation decisions

### Platform (ADR 0005): two new `@ExpectPlatform` methods

- `onInventoryInit(BiConsumer<InventoryScreen, Consumer<AbstractWidget>> cb)`. The callback receives the screen and an "add widget" sink.
  - Fabric: a mixin on `InventoryScreen#init()` TAIL dispatches through a static sink, like `WorldJoinDispatcher`. The mixin class extends `AbstractContainerScreen` so it can call the protected `addRenderableWidget`.
  - NeoForge: `NeoForge.EVENT_BUS` `ScreenEvent.Init.Post`, filtered to `InventoryScreen`, using `event::addListener`.
- `onWorldLeave(Runnable cb)`.
  - Fabric: fabric-api `ClientPlayConnectionEvents.DISCONNECT`. A mixin on `Minecraft#clearClientLevel` was tried first; in-game it did not fire on singleplayer exit.
  - NeoForge: `ClientPlayerNetworkEvent.LoggingOut`.
- `onWorldJoin` stays, but `RecipeHost.init` no longer uses it.

### UI (`:mod:common/ui/`)

- `PairingButtonInstaller` (an instance, constructed with the `PairingController` by `RecipeHost.init`) places the button in the **top-left corner of the whole screen**, at (4, 4), not inside the inventory window.
  - If an existing widget overlaps that spot, it steps right 22 px to the first free slot. This uses Minecraft's widget API (`screen.children()`) and runs once, at init. Resizing re-runs init.
- The button is a vanilla `ImageButton` (20×20). It draws only the sprite, with no button background; the icon's own transparent background shows the screen behind it. Its sprite is `recipehost:pairing_button`, from `common/src/main/resources/assets/recipehost/textures/gui/sprites/pairing_button.png`. The label `Pair with RecipeHost app` is used for narration and the tooltip.
- No unit tests; the in-game check covers it.
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

- Production collaborators are instances wired in `RecipeHost.init`: `MinecraftListenerFactory`, `ExecutorScheduler`, `SecureTokens`, `HostAddresses::all`.
- `PairingController` owns the listener lifecycle. It has injected collaborators (listener factory, `ScheduledExecutorService`, token source, address source), so it can be unit-tested.
  - `open(ClientLevel) → PairingPayload`:
    - Cancels any pending grace unbind.
    - Closes any existing listener and source.
    - Creates a new token with `SecureRandom` (32 bytes).
    - Binds `0.0.0.0:0` (an ephemeral port) with a fresh `MinecraftSnapshotSource`.
    - Logs the payload URI at INFO for the probe.
  - `screenClosed()`: schedules unbind after 60 s. An active session runs to completion, because closing the server socket leaves accepted sockets alone.
  - `snapshotDelivered()`: unbinds immediately. A mid-stream drop does not unbind, so the app can retry with the same QR within the grace period.
  - `worldLeft()`: unbinds immediately.
- `HostAddresses` is a thin, untested adapter: every address of every interface from `NetworkInterface.getNetworkInterfaces()`, unfiltered and in enumeration order. **The mobile app filters**: it knows its own local IP and subnet, so it can pick the reachable host better than the mod can.
- `PairingPayload` (pure):
  - Format: `recipehost://pair?v=1&h=192.168.1.5,10.0.0.2&p=51234&t=<base64url-no-pad token>`.
  - Hosts keep their input order. IPv6 scope ids (`fe80::1%en0`) are percent-encoded (`%25`).
  - Because nothing is filtered, the URI grows with the number of interfaces: roughly 15 chars per IPv4 host and up to ~40 per IPv6 host. A typical machine is still well within the QR's capacity, though the code gets denser.
  - It can also serve as a mobile deep link later.

### Net

- `SessionListener` gets an `onSnapshotDelivered` callback. It fires only when `SnapshotSender.send` returns normally (`Done` written), so UNAUTHORIZED, BUSY and mid-stream drops don't end pairing.
- `SessionListener` also gets `onTerminated`. It fires exactly once, after `close()` and once no session is streaming. The Minecraft adapter closes `MinecraftSnapshotSource` there, so a grace-period unbind never frees the icon framebuffer under an active `Snapshot`.

### QR encoding

- `io.nayuki:qrcodegen:1.8.0` (MIT, about 30 KB, no transitive dependencies), bundled jar-in-jar like `:protocol`: `include` on Fabric, `jarJar` on NeoForge. Use ECC level M.
- Rejected: ZXing core (about 550 KB, mostly decoders) and hand-rolling Reed–Solomon plus masking.

### Removals and updates

- Delete `DevHarness`. `RecipeHost.init` now registers `onInventoryInit` (which adds the pairing button via `PairingButtonInstaller`) and `onWorldLeave` (which calls `PairingController.worldLeft`).
- `tools/probe` drops the dev port and token. It takes a pairing URI as its only argument (copied from the game log).
- GLOSSARY: add **Pairing payload** (the `recipehost://pair` URI encoded in the QR).

## Out of scope (v1)

- The creative inventory (`CreativeModeInventoryScreen`).
- A keybinding to open the QR screen.
- Localized UI strings, and any asset files.
- Mobile-side scanning and parsing.

## Testing (TDD at pure seams)

- `PairingPayloadTest`: checks `toUri` against hand-written URIs: single host, multiple hosts keep their order, token is base64url without padding, IPv6 and scope-id encoding.
- `PairingControllerTest` uses a fake scheduler and a fake listener factory. It checks that:
  - open binds with a fresh token;
  - close followed by 60 s unbinds;
  - reopening inside the grace period cancels the unbind and rotates the token;
  - snapshot delivered unbinds;
  - world leave unbinds.
- `SessionListenerTest`: new cases checking that `onSnapshotDelivered` fires after `Done` and not after UNAUTHORIZED, BUSY or a mid-stream source failure.
- In-game smoke test, on both loaders:
  - The button appears.
  - It sits in the screen's top-left corner and shows the icon.
  - It steps aside for another mod's button.
  - The QR scans to the logged URI.
  - The probe pulls a snapshot.
  - The port is closed 60 s after leaving the screen.

## Issues

1. `PairingPayload` (pure) + `HostAddresses` adapter.
2. `PairingController` + the `SessionListener` end callback; delete DevHarness; update the probe.
3. `Platform.onInventoryInit`/`onWorldLeave` + mixins/events + pairing button.
4. `PairingScreen` + qrcodegen bundling.
