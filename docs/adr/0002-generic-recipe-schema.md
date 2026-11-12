# Generic Recipe schema with type_id + extras map

`Recipe` on the wire is a single generic shape: `{ type_id, inputs: repeated Slot, results: repeated ItemStack, extras: map<string, string> }`. `type_id` is Minecraft's `RecipeType` id (e.g. `minecraft:crafting_shaped`, `minecraft:smelting`); type-specific metadata — shaped `width`/`height`, smelting `cooktime`/`xp`, smithing template slot — lives in `extras` under documented keys.

Per-type protobuf messages were rejected because modded and datapack recipes (KubeJS, Create, Mekanism, Thermal) define arbitrary `RecipeType`s that the mod cannot anticipate. A typed schema would silently drop them; the generic schema enumerates them with their metadata preserved, and the mobile app can render known types richly while still showing something useful for unknown types.

The trade-off: the mobile app must know the `extras` key conventions per `type_id` to render shaped-grid layouts or smelting times. This is accepted as the cost of modded-recipe support.

## Pinned `extras` keys (v1)

The vanilla-dispatch mapper emits exactly these keys; additions require an ADR amendment.

- `minecraft:crafting_shaped` → `width`, `height` (both stringified ints).
- `minecraft:smelting` / `minecraft:blasting` / `minecraft:smoking` / `minecraft:campfire_cooking` → `cook_time` (ticks, int), `xp` (float).
- All other baseline types (shapeless, smithing, stonecutter) → no `extras`.
- Shell entries for unknown types → no `extras`; `type_id` and `results` only.

## Amendment: `extras` removed

`extras` is gone from `Recipe` (field 4 and its name are reserved). Nothing read it: the app draws every recipe from its `Recipe layout` (ADR 0015), which bakes the grid shape and type-specific text (cook time, XP, heat) into the image, and the EMI layer never filled it. Type-specific metadata the app needs later goes into the layout or a typed field. Nothing is published, so the protocol version stays.

## Amendment: recipe id

`Recipe` also carries `id`, the namespaced id of the recipe (`RecipeHolder.id()` for vanilla dispatch). The mobile app uses it to attribute each recipe to a `Mod release` (ADR 0011).
