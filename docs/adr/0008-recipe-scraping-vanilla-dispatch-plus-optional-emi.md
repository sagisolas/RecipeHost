# Recipe scraping: vanilla instanceof dispatch, with optional EMI enrichment

The companion mod extracts `Recipe`s in two layers. The **baseline** is an `instanceof` dispatch inside `:mod:common` over the vanilla recipe types — `ShapedRecipe`, `ShapelessRecipe`, `AbstractCookingRecipe` (smelting/blasting/smoking/campfire), `SmithingTransformRecipe`, `StonecutterRecipe` — mapping each to the generic wire `Recipe` (ADR 0002) with type-specific metadata under `extras`. Recipe classes not covered by the dispatch emit a shell: `{type_id, inputs=[], results=[recipe.getResultItem(...)]}`. The **optional layer**, added in a later slice, soft-depends on **EMI**: if `emi` is loaded, we query EMI's `EmiRecipe` API to fill `inputs` for unknown types. Without EMI, the shell is what ships.

A **generic `Recipe.getIngredients()`** accessor was not available: in 1.21.x the `Recipe` interface no longer exposes a uniform `NonNullList<Ingredient>`; each type stores its ingredients in its own fields. `instanceof` dispatch per type is the only honest way to read them without reflection.

**Reflecting over private `Ingredient` fields** on unknown recipe classes was rejected. It would catch many modded item-only recipes, but it is fragile against obfuscation and field renames, and it still can't model fluid, gas, or chemical inputs that mods like Mekanism and Create use.

**Depending directly on JEI** was rejected. JEI's recipe data is only reachable by implementing its `IRecipeLayoutBuilder` as a capturing fake across ~20 methods, and the plugin API changes faster than EMI's data-shaped `EmiRecipe` API. More importantly, **EMI already ships that fake** as its "Jemi" compatibility layer: JEI-only mods register against what they think is JEI, Jemi records the slot calls, and EMI exposes the result through `EmiRecipe.getInputs()`. Targeting EMI gets us JEI-only recipes for free, without us maintaining a JEI builder emulator.

**Requiring JEI or EMI as a hard dependency** was rejected — the mod's install story is zero-fuss, and roughly half of modpacks will have neither in a configuration we can rely on. The baseline must stand on its own.

The trade-off: recipes that neither vanilla dispatch nor EMI covers ship as `{type_id, result}` shells. The mobile app can still surface "this recipe exists and produces X" for discovery, which is strictly better than dropping the entry, and the type is preserved so richer rendering can be added later without a snapshot schema change.
