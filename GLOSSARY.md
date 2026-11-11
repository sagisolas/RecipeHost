# RecipeHost

An offline mobile viewer for Minecraft recipes. A Fabric & NeoForge **companion mod** extracts recipes and icons from the player's running Minecraft client and ships them to the mobile app over the LAN. 

## Language

### Roles

**Companion mod**:
The Fabric or NeoForge mod running inside the player's Minecraft client. Produces recipe and icon data; hosts the TCP listener.
_Avoid_: Server, plugin, sidecar

**Mobile app**:
The Kotlin Multiplatform client that receives snapshots from the companion mod and renders them offline.
_Avoid_: Client (ambiguous — Minecraft itself is a "client"), viewer

### Pairing & transport

**QR screen**:
The in-game Minecraft UI that displays pairing data (addresses + port + token) as a QR code. Opening it is the only way to bind the TCP listener.

**Token**:
A 256-bit random value generated when the QR screen opens and embedded in the QR payload. Authenticates exactly one `Session`; dies with it.
_Avoid_: Secret, key, password

**Pairing payload**:
The `recipehost://pair?v=1&h=<hosts>&p=<port>&t=<token>` URI encoded in the QR code. `h` lists the usable host addresses of the player's machine (loopback, link-local and tunnel interfaces excluded), IPv4 first — the mobile app picks the reachable one. `t` is the `Token`, base64url without padding.
_Avoid_: QR data, connection string

**Session**:
A single mobile ↔ companion-mod TCP connection, authenticated by one `Token`. Only one `Session` is active at a time; concurrent connects are refused.
_Avoid_: Connection (used only for the raw TCP layer)

**Envelope**:
The top-level protobuf message exchanged over a `Session`. Wraps a `oneof payload` whose variants are `Handshake`, `ItemBatch`, `RecipeBatch`, `IconBatch`, `Done`, `Error`.

**Handshake**:
The first `Envelope` the companion mod sends after accepting a `Token`. Carries session-scoped context the mobile app needs before it can interpret the rest of the `Snapshot`: wire protocol version, Minecraft game version, the locale the mod used to render display names, the `Instance` name, the `ModInfo` list, and the total `Item`/`Recipe` counts used to drive progress indicators.

**Snapshot**:
The full dataset the companion mod streams to the mobile app on connect: handshake, then every `Item`, every `Recipe`, every icon, terminated by `Done`. No deltas, no resumption — a mid-stream drop forces a new `Snapshot`.
_Avoid_: Sync, dump, export

### Content

**Item**:
One entry in the companion mod's enumeration of `BuiltInRegistries.ITEM`. Carries namespaced id and localized display name; mod attribution is derived from the id's namespace.

**ModInfo**:
Metadata about a namespace that owns at least one `Item` or `Recipe`. Listed in the `Handshake`. Mods that own neither (performance mods, API shims) are omitted. A namespace with no loaded mod behind it (a datapack) is listed under its namespace with version `datapack`.

**Recipe**:
The generic wire representation of any crafting operation — vanilla, modded, or datapack. Carries its recipe `id` (e.g. `minecraft:oak_planks`) and its `type_id`; type-specific metadata lives in a free-form `extras` map rather than per-type schemas. In the `Catalog` a recipe belongs to the `Mod release` of its id's namespace, and is kept per `Instance` it was seen in.

**Slot**:
A single position in a `Recipe`'s input grid, carrying the list of `ItemStack`s that satisfy it. Ingredient tags are expanded into concrete items by the companion mod — the mobile app never resolves tags.
On the wire a repeated Slot may travel once per `RecipeBatch` and be referred to by index (ADR 0016); the app expands it on receipt.
_Avoid_: Ingredient (ambiguous with Minecraft's `Ingredient` class)

**Recipe layout**:
How one `Recipe` looks: a background image drawn by the companion mod with the game's own GUI (EMI's display of the recipe when EMI is loaded, the companion mod's own vanilla layouts otherwise), with `Recipe slot`s placed on it. Everything the game draws is in the image, text and fluids included, except the `Item`s inside the `Recipe slot`s; the mobile app draws those. Optional — a `Recipe` without one is shown in a generic layout. Many `Recipe`s share one layout.
_Avoid_: Screenshot, recipe GUI, recipe view (the app screen)

**Recipe type**:
What kind of `Recipe` it is, by its `type_id`: the vanilla `RecipeType` id (shaped and shapeless crafting are both `minecraft:crafting`), or the EMI category id when EMI is loaded. It is owned by the `Mod release` of its id's namespace, and may carry a name in the game's language; the Item screen shows one section per type, headed by that name or, without one, the `type_id`.
_Avoid_: Category (except when speaking of EMI), recipe kind

**Recipe slot**:
A rectangle on a `Recipe layout` with a role (input, output or catalyst): a position each `Recipe` using that layout fills with its own `ItemStack`s. Presentation only: what a `Recipe` consumes and produces is still its `Slot`s and results.
_Avoid_: Placed slot, Slot (the semantic input)

**ItemStack** (wire shape):
`{ item_id, count }`. A `count` of 0 is interpreted as 1. Carries no NBT in v1.

**Icon**:
The 32×32 image of an `Item` (its 16 GUI units rendered at 2×), produced on the render thread via `GuiGraphics.renderItem` into an offscreen framebuffer. It travels and is stored as one cell of an `Icon atlas`, never as an image of its own. One icon per `Item` — enchantment/potion/NBT variants are out of scope for v1.

**Icon atlas**:
One PNG sheet of `Icon`s in square cells, row-major, as carried by one `IconBatch`: the companion mod's render sheet sent whole, with its cell size, columns and the item id of each cell. The `Catalog` keeps atlases as received and points each `Icon` at its cell; the mobile app decodes an atlas once and draws Icons as regions of it (ADR 0014).
_Avoid_: Sprite sheet, texture atlas (Minecraft's own atlas, which ADR 0003 rejects)

### Catalog (mobile app)

**Catalog**:
The mobile app's offline store of everything it has ever received. Grows with every `Scan`; nothing a `Scan` brings replaces data from a different `Mod release`.
_Avoid_: Database, library

**Scan**:
One QR pairing plus the `Snapshot` received over it, merged additively into the `Catalog`. Scanning the same `Instance` again is still a `Scan` — it refreshes the `Mod release`s it carries and fills in missing `Icon`s.
_Avoid_: Rescan, import, sync

**Mod release**:
One mod at one version for one Minecraft version, as reported by a `Handshake`'s `ModInfo`. The `minecraft` namespace's release is the Minecraft version itself. `Item`s in the `Catalog` belong to a `Mod release`.
_Avoid_: Mod version (ambiguous without the Minecraft version)

**Instance**:
The launcher installation a `Scan` was taken from, identified and shown by its game-directory folder name (e.g. a CurseForge or Prism instance). Worlds and servers inside one `Instance` are not distinguished — a later `Scan` of the same `Instance` replaces its recipes. When the same recipe differs between `Instance`s (a pack override), the recipe view offers "From <instance name>" to choose between them.
_Avoid_: World, modpack, profile

**Version selection**:
The one `Mod release` currently chosen for each mod; what the item grid and recipe views show. Set to the releases of the latest `Scan`, then changed by the user picking a version on the `Mod versions` screen. Mods absent from a `Scan` keep their previous choice.

**Mod versions**:
The screen, opened from Settings ("Change mod versions"), where the user changes the `Version selection`: one dropdown of `Mod release`s per mod, each pick saved at once. The Items tab's mod list only filters the grid, and shows no versions.
_Avoid_: Mod list (that's the filter)

**Bookmark**:
A recipe id the user saved for quick access while crafting, listed in the Bookmarks tab newest first. It shows the recipe under the current `Version selection`, and is hidden (but kept) while no selected release has that recipe. Short-lived: cleared together with the `Catalog`.
_Avoid_: Favorite, star

### Mod internals

**Platform (class)**:
The thin Java class in `:mod:common` whose static methods are annotated `@ExpectPlatform` and resolved at compile time to per-loader implementations in `:mod:fabric` and `:mod:neoforge`. Exposes only the operations the companion mod uses and can't do portably against Mojmap-named Minecraft APIs: mod list, Minecraft version, screen-init and world-leave events. Everything else — registry walk, recipe assembly, icon rendering, TCP listener, QR screen — lives in `:mod:common` calling Minecraft directly.
_Avoid_: PlatformHooks, PlatformBridge

**Recipe candidate**:
One recipe as a recipe source (the vanilla dispatch, or EMI) finds it, before it is read. It only knows how to read its wire `Recipe` and lay out its `Recipe layout`; whether it ships is the `Recipe assembly`'s call.

**Recipe assembly**:
The module that turns `Recipe candidate`s into a `Snapshot`'s recipes and owns the rules every source shares: tag listings and fuel are skipped, a recipe that fails to read is left out, one whose layout fails ships without it, an id is sent once, and the id's namespace owns the recipe.
