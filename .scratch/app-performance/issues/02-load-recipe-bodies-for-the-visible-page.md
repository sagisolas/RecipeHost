# App: load recipe bodies only for the recipes the Item screen shows

Status: ready-for-agent

## Why

P7 (commit 220326d) made `RecipeVariant.display` decode on first use. But `Recipe.sq`'s `selectedProducing` and `selectedConsuming` still read every row's `body` blob and `layout_slots` from SQLite. For a common Item's Uses (sticks, iron ingots) that means thousands of blobs held in memory, re-read on every refresh, so that a handful can be shown. The lazy decode also runs wherever `display` is first read, which is the ViewModel's `combine` on the main dispatcher, not the I/O context.

## What

- Split the two queries:
  - a list query that returns only what grouping and paging need: release, recipe id, type, body hash, Instance name, layout key;
  - a body query that fetches the bodies and layouts of given recipe rows.
- `ItemRecipes` groups and collapses variants from the list query, as it does today. It loads and decodes on its I/O context only the variant each section shows. It loads another when the pager or variant picker moves there.
- Keep the domain interface free of wire types (`RecipeDisplay`), and keep `ItemViewModel` free of I/O. Its state may show a section as loading for one frame rather than block.
- Restore value equality for `RecipeVariant` (it became a plain class for the lazy decode), so an unchanged refresh doesn't look like new data downstream.

## Acceptance

- Tests at `ItemRecipes` against the in-memory Catalog:
  - groups, order and variants are unchanged;
  - a section's shown variant decodes to the right `RecipeDisplay`;
  - paging a section loads the next recipe's display;
  - a recipe whose layout image is missing still shows generically.
- An `ItemViewModel` test: paging shows the next recipe once its display arrives.
- Manual (user): open Uses of `minecraft:stick` on a large pack and page through it.
