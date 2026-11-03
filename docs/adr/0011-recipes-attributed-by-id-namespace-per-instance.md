# Recipes attributed to Mod release by id namespace, kept per Instance

In the `Catalog`, each `Recipe` belongs to the `Mod release` of its recipe id's namespace. For example, `create:crushing/iron_ore` belongs to the selected Create release, and `minecraft:iron_pickaxe` belongs to the minecraft release. Recipes are stored per `Instance`, which is the launcher installation's game-directory folder name. A `Scan` from an `Instance` replaces that `Instance`'s recipe set for every `Mod release` the `Scan` carries, so recipes a pack has since removed disappear as well. The recipe view for an `Item` shows the recipes owned by the currently selected releases whose results include that `Item`, which is what JEI would show with all selected mods installed together. When one `(Mod release, recipe id)` has different bodies in different `Instance`s, the view offers "From <instance name>" to switch between them. Bodies that are identical are merged, and the default is the body from the most recently scanned `Instance`.

This required three wire changes. `Recipe` gains `id`. The `Handshake` gains `instance_name`. The companion mod sends a `ModInfo` for every namespace that owns an `Item` **or** a `Recipe`, and a namespace with no loaded mod behind it (a datapack) is listed with version `datapack`.

## Considered options

- **Union of every recipe ever seen, deduplicated by content.** This was rejected because a pack that overrides a vanilla recipe would show both versions with nothing to tell them apart.
- **Attribute by the output item's namespace.** This was rejected because recipes are added by mods other than the one that owns the output. For example, Create adds crushing recipes whose outputs are vanilla items, so they would follow the minecraft release instead of Create's.
- **Keep every `Scan` as its own source.** Nothing would collide, but rescanning would pile up variants and removed recipes would never disappear.
- **Per world or server, or a mod-generated UUID per world.** This was rejected as more identity than the problem needs. Different recipes on different servers inside one `Instance` is an edge case. Those servers simply replace each other's recipes, last `Scan` wins.

## Consequences

- Two installations whose game directories have the same folder name collide. Two vanilla-launcher installs that both use `.minecraft` are the likely case. The same pack installed on two PCs also collides, which is arguably correct. This is accepted.
- A recipe whose id namespace belongs to one mod but whose results belong to another only shows when the owning mod's release is selected.
