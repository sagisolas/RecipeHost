# App: captured RecipeLayout rendering on the item screen

Status: ready-for-agent
Blocked by: 06, mobile-app/09
Spec: ../spec.md

## What

- Add a captured implementation behind the `RecipeLayout` seam from mobile-app issue 09. It is chosen when the recipe has a stored layout; otherwise the generic layout is used.
- The image is drawn at the largest whole-number scale that fits the section width, with nearest-neighbour filtering.
- Each `RecipeSlot` rectangle (scaled the same way) shows an `ItemIcon` and its count, with no `McSlot` chrome. Alternatives cycle on the screen's shared clock, and tapping a slot opens that Item.
- `LayoutImageCache` is app-scoped and owned by `AppGraph`. It decodes images off the main thread and caches them by key. Keys are not content-addressed: a vanilla key (e.g. `minecraft:crafting`) is replaced by a later Scan with another resource pack or locale (the latest Scan wins, issue 06), so the cache must drop or refresh a key whose stored image changed, e.g. after each Scan.

## Acceptance

- A pure test for the scale: the factor chosen for given widths, and scaled slot rectangles.
- `ItemViewModel` test: the captured layout is picked when present and generic otherwise, and `layout_stacks` are mapped to slots by index.
- Manual (user): check the layouts on phones of different densities.
