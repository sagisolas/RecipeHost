# Item screen: human-readable recipe type names

Status: needs-triage
Blocked by: 09
Spec: ../spec.md

## Problem

Each section on the Item screen (issue 09) is titled with its raw `type_id`, for example `minecraft:crafting_shaped` or `create:mixing`. The player expects names like "Crafting" or "Mixing", as in JEI and EMI.

## What

- Show a human-readable name for each recipe type in the section header, where the `type_id` shows today. Keep the pager at the header's end.
- Names follow the game's language, like Item display names (ADR 0004). So the companion mod should supply them rather than the app hard-coding English names. Possible sources:
  - EMI's recipe category names, when EMI is loaded;
  - the companion mod's own vanilla layouts (`.scratch/recipe-layouts/`), for example the container titles "Crafting", "Furnace" and "Stonecutter".
- Proposed wire shape, to confirm during triage: a `RecipeType { id, name }` list in the `Handshake`, or a name on each `Recipe layout`. The Catalog stores the names per `Mod release` of the type id's namespace, like Items (ADR 0010, 0011).
- When a type has no name, fall back to the `type_id`, so older Catalogs and unknown types still show something.

## Open questions

- Where the name lives: per type id, or per layout or category. EMI can show one `type_id` under several categories.
- Should shaped and shapeless crafting merge into one "Crafting" section, or stay as two sections with the same name?

## Acceptance

- Sections show the type's name in the game's language, and the `type_id` when the name is missing.
- A test covers the fallback, and the Catalog stores the names and returns them with the recipe groups.
