# Mod: EMI as the recipe source (soft dependency)

Status: done
Blocked by: 01
Spec: ../spec.md

## What

- Add the EMI API as `compileOnly` in `:mod:common` (the xplat Mojmap artifact). EMI classes are only reached behind a check that EMI is loaded, using the mod list from `Platform`. The mod still loads and works without EMI.
- ~~**Readiness**~~: dropped. `EmiReloadManager` is internal and not in EMI's API jar, so checking it would need reflection, which the feature isn't worth. Pairing while EMI is still loading snapshots what EMI has so far.
- When EMI is loaded, recipes come from `EmiApi.getRecipeManager().getRecipes()`, and the vanilla dispatch isn't used:
  - `type_id` is the category id;
  - `inputs` come from `getInputs()`, item stacks only, with tags expanded;
  - `results` come from `getOutputs()`;
  - `id` is `getId()`, and synthetic `ns:/path` ids are kept;
  - a null id becomes `<category ns>:<hash of the encoded body>`;
  - recipes of EMI's `emi:tag` category are left out: they only list a tag's members, which Slots already expand.
- `ModInfos` picks up the namespaces as usual (ADR 0011).

## Acceptance

- Pure tests for the null-id fallback (the same body gives the same id) and the item-only stack filter.
- Manual (user): a pack with EMI shows modded and JEI-only recipes in the app.
