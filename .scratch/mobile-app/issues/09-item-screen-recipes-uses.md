# Item screen: Recipes/Uses tabs, pager, "From <instance>" selector, generic recipe layout

Status: ready-for-agent
Blocked by: 05, 06, 07
Spec: ../spec.md

## What

- `ItemViewModel(itemId)` uses `RecipesFor` / `UsesOf` and `ResolveStack`.
- The header shows the item's icon, display name and id.
- `McTabs` switches between **Recipes** and **Uses**. Each tab is a vertical list with **one section per recipe type** (`type_id`), and each section:
  - shows one recipe at a time, with an `McPager` (◀ ▶ and "3 / 12") that counts recipes, not variants;
  - shows a `McDropdown` "From <instance name>" only when the current recipe has two or more variants, listing each variant's Instance names, with the newest variant as the default.
- **Generic recipe layout** (the fallback for recipes without a captured `Recipe layout`; captured layouts come in `.scratch/recipe-layouts/` issue 07, ADR 0015):
  - the input Slots in a wrapped row, then an arrow, then the result Slots;
  - a Slot with several alternatives cycles through them every 1 s, using one shared clock for the whole screen;
  - the `count` is drawn bottom-right when it's greater than 1;
  - an unresolved stack shows its id with a placeholder Icon.
- Tapping any ItemStack pushes `Item(thatId)` onto the current tab's stack, normally Items. The bottom bar stays visible.
- Keep the layout behind a `RecipeLayout` seam that is chosen per recipe, not per `type_id`. There is a single generic implementation for now. The captured-layout implementation (recipe-layouts issue 07) plugs in here whenever a recipe has a stored layout. The planned craft tree builds on `RecipesFor`.
- An empty tab shows "No recipes".

## Acceptance

- ViewModel tests with fakes check that:
  - sections follow the type groups;
  - the pager moves within a section;
  - the variant selector appears only when there are variants, and switching variants changes the body shown;
  - the alternative cycling moves forward under the test clock.
- Manual check:
  - follow a chain of recipes three items deep and press back through it;
  - switching to Settings and back keeps the chain;
  - reselecting Items pops to the grid.
