# Catalog queries and domain: grid, mod list, Version selection, Recipes/Uses with Instance variants

Status: ready-for-agent
Blocked by: 04
Spec: ../spec.md

## What

Expose everything as `Flow`s so the UI updates live while Icons arrive.

- **`domain/catalog/ItemGridQuery`**:
  - Returns the Items of the selected releases.
  - Optional mod filter. The search text is parsed so that `@modid` tokens filter by mod and the rest matches names through FTS.
  - Order: `minecraft` first, then the mods' order in their latest Scan, then `ordinal`.
- **`domain/catalog/ModList`**:
  - Lists the mods that have at least one release, each with its releases sorted by `last_scanned_at` descending, and marks the selected one.
  - `select(modId, releaseId)` saves the choice.
- **`domain/recipes/RecipesFor(item)` and `UsesOf(item)`**:
  - Only recipes owned by the selected releases count, matched through `recipe_output` / `recipe_input`.
  - Results are grouped by `type_id`.
  - Within a group, the rows for one `(release, recipe_id)` collapse by `body_hash` into **variants**, each carrying its Instance names.
  - The default variant is the one from the most recently scanned Instance.
  - Recipe bodies are decoded with `Recipe.ADAPTER`.
- **`domain/catalog/ResolveStack`**:
  - Maps an `item_id` to the selected release of its namespace's mod, returning the display name and Icon.
  - Returns `Unresolved(id)` when the mod has no release in the Catalog or the selected release lacks the Item.
- **`ClearCatalog`**: deletes all rows in one transaction.

## Acceptance

Query tests on an in-memory driver, seeded through `ScanIngest` or direct inserts, check that:
- the grid follows the selection, and switching the release changes the Items shown;
- `@create` and name search both work;
- the mod list ordering is correct;
- Recipes and Uses are grouped by type;
- two Instances with identical bodies give one variant listing both names, and different bodies give two variants with the newest as default;
- an unresolved stack comes back as `Unresolved`;
- clearing leaves an empty Catalog.
