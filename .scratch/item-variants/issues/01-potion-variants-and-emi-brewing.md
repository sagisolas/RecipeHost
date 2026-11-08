# Potion variants and brewing recipes (EMI)

Status: needs-triage
Blocked by: ../../recipe-layouts/issues/04-mod-emi-recipe-source.md, ../../recipe-layouts/issues/05-mod-emi-layout-capture.md

## Problem

The Snapshot has no brewing recipes and only one entry for each potion item.

- **Potions.** In 1.21.1, Swiftness, Healing and the other potions are all the same registry item (`minecraft:potion`, `splash_potion`, `lingering_potion`, `tipped_arrow`), told apart by the `POTION_CONTENTS` data component. `ItemMapper` emits one `Item` per registry entry, and its name and Icon come from the bare item (`getDescriptionId()`, `new ItemStack(item)`). The wire `ItemStack` is `{ item_id, count }` and can't name a variant (GLOSSARY: "Carries no NBT in v1").
- **Brewing.** Brewing mixes aren't `Recipe`s and aren't in `RecipeManager`, which is the only place the vanilla dispatch reads recipes from. They live in the level's `PotionBrewing`.

With EMI loaded, issue `recipe-layouts/04` brings in EMI's brewing recipes and `recipe-layouts/05` captures the brewing stand's layout. But both map stacks to the bare item id, so every brewing recipe comes out as `minecraft:potion + X → minecraft:potion`, and every potion slot shows the same default bottle.

## What

Add an item **variant** to the model, list potion variants as their own Items when EMI is loaded, and keep the variant on the stacks of EMI recipes.

### Wire

- `Item` and `ItemStack` get an optional `variant` string. Empty means the bare item, so every existing Item and stack stays as it is.
  - The variant is a stable key for the stack's component changes. Default: the component patch encoded with `DataComponentPatch.CODEC`, canonicalised (sorted keys), then hashed and truncated, like layout keys. It has to stay the same across Scans for the same content (ADR 0010).
  - `item_id` stays the base item, so namespace attribution is unchanged (ADR 0011).
- `IconBatch` cells name a `(item_id, variant)` pair, not just an item id.
- Nothing is published yet, so this amends protocol version 2, as ADR 0015 did. If v2 has shipped by then, bump it instead.

### Mod (EMI loaded)

- **Items.** As well as the registry items, add one Item for each stack in EMI's index (`EmiApi.getIndexStacks()` or equivalent) whose item is in the potion family above and whose component patch isn't empty. Dedupe by `(item_id, variant)`, keeping the index's order after the registry items.
  - `display_name` is `stack.getHoverName().getString()` ("Potion of Swiftness"), in the game's locale (ADR 0004).
  - `Handshake.total_items` counts the variants.
- **Icons.** Icons are rendered from the actual stack, components included, so potions get their tint. `IconBatchIterator` walks stacks, not ids.
- **Recipe stacks.** When issue 04 maps an `EmiStack` to a wire `ItemStack`, it keeps the variant, computed from the stack's component changes the same way as for Items. This covers `inputs`, `results` and `layout_stacks`, so brewing (and EMI's tipped-arrow crafting) recipes name the right potions.
  - A variant that isn't in the item list (EMI shows it in a recipe but not in its index) is still added as an Item, so every stack the app sees has a name and an Icon.
- Brewing recipes themselves need no new code: they arrive through issue 04 (type id from EMI's brewing category), with their layout from issue 05.

### Mobile app

- Catalog: `item` is unique on `(release_id, item_id, variant)`. Icons and recipe stacks are keyed the same way. Migrate existing rows to an empty variant.
- Item grid, search, Item screen, Recipes and Uses all treat `(item_id, variant)` as the Item's identity. Swiftness and Healing are separate grid entries, and "Uses" of Awkward Potion lists the brewing recipes that take it.
- A stack whose variant the Catalog doesn't know falls back to the bare item's name and Icon.

### Docs

- New ADR: "Items have variants keyed by their component patch". It explains why the key is a hash of the patch rather than a per-component field (generic for any mod), and why variants come from EMI's index. It amends the GLOSSARY entries for `Item`, `ItemStack` and `Icon` ("One icon per Item", "Carries no NBT in v1").

## Out of scope

- **Brewing without EMI.** Reading `PotionBrewing` (for example, every potion variant × every item through its public `hasMix`/`mix`) and a vanilla brewing-stand layout. This is a follow-up issue.
- **Variants without EMI,** for example listing items from the creative tabs.
- **Other component variants** (enchanted books, suspicious stew, goat horns, ominous bottles, modded variants). The wire and Catalog changes are generic, so widening the potion-family filter is the only change needed. Triage decides whether to widen it now.
- Enchantment glint.

## Decide in triage

- Variant key: a hash of the encoded patch (default) or a readable form (for example, the potion id for potions). The hash is generic. The readable form is easier to debug but needs a rule per component.
- Whether to list only the potion family or every variant in EMI's index. All of them is more complete, but some packs add thousands of variants (Icon volume).
- Potions a mod adds to `minecraft:potion` are attributed to `minecraft` (the item's namespace), not to the mod that added the potion. Acceptable for now?

## Acceptance

- Pure tests:
  - The variant key is the same for equal patches built in different orders, different for different patches, and empty for an empty patch.
  - Mapping an `EmiStack` keeps its variant (with a fake stack, where the EMI API allows it).
  - Catalog: Items with the same `item_id` and different variants are stored separately, and a re-Scan with the same content changes nothing (ADR 0010).
- Manual (user), vanilla + EMI:
  - the app's grid shows Potion of Swiftness, Splash Potion of Healing and so on, each with its own name and tinted Icon;
  - Awkward Potion's Uses list its brewing recipes, drawn in the captured brewing-stand layout, with the right potions in the slots;
  - a tipped arrow recipe shows the matching potion and arrow.
