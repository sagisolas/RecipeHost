# RecipeHost

An offline mobile viewer for Minecraft recipes. A Fabric & Forge **companion mod** extracts recipes and icons from the player's running Minecraft client and ships them to the mobile app over the LAN. 

## Language

### Roles

**Companion mod**:
The Fabric mod running inside the player's Minecraft client. Produces recipe and icon data; hosts the TCP listener.
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

**Session**:
A single mobile ↔ companion-mod TCP connection, authenticated by one `Token`. Only one `Session` is active at a time; concurrent connects are refused.
_Avoid_: Connection (used only for the raw TCP layer)

**Envelope**:
The top-level protobuf message exchanged over a `Session`. Wraps a `oneof payload` whose variants are `Handshake`, `ItemBatch`, `RecipeBatch`, `IconBatch`, `Done`, `Error`.

**Handshake**:
The first `Envelope` the companion mod sends after accepting a `Token`. Carries session-scoped context the mobile app needs before it can interpret the rest of the `Snapshot`: wire protocol version, Minecraft game version, the locale the mod used to render display names, the `ModInfo` list, and the total `Item`/`Recipe` counts used to drive progress indicators.

**Snapshot**:
The full dataset the companion mod streams to the mobile app on connect: handshake, then every `Item`, every `Recipe`, every icon, terminated by `Done`. No deltas, no resumption — a mid-stream drop forces a new `Snapshot`.
_Avoid_: Sync, dump, export

### Content

**Item**:
One entry in the companion mod's enumeration of `BuiltInRegistries.ITEM`. Carries namespaced id and localized display name; mod attribution is derived from the id's namespace.

**ModInfo**:
Metadata about a mod that owns at least one `Item`. Listed in the `Handshake`. Mods that register no items (performance mods, API shims) are omitted.

**Recipe**:
The generic wire representation of any crafting operation — vanilla, modded, or datapack. Identified by `type_id`; type-specific metadata lives in a free-form `extras` map rather than per-type schemas.

**Slot**:
A single position in a `Recipe`'s input grid, carrying the list of `ItemStack`s that satisfy it. Ingredient tags are expanded into concrete items by the companion mod — the mobile app never resolves tags.
_Avoid_: Ingredient (ambiguous with Minecraft's `Ingredient` class)

**ItemStack** (wire shape):
`{ item_id, count }`. A `count` of 0 is interpreted as 1. Carries no NBT in v1.

**Icon**:
A 16×16 PNG of an `Item`, produced on the render thread via `GuiGraphics.renderItem` into an offscreen framebuffer. One icon per `Item` — enchantment/potion/NBT variants are out of scope for v1.
