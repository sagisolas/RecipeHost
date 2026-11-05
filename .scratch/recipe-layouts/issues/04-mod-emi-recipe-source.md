# Mod: EMI as the recipe source (soft dependency)

Status: ready-for-agent
Blocked by: 01
Spec: ../spec.md

## What

- Add the EMI API as `compileOnly` in `:mod:common` (the xplat Mojmap artifact). EMI classes are only reached behind a check that EMI is loaded, using the mod list from `Platform`. The mod still loads and works without EMI.
- **Readiness:** if EMI is loaded but `EmiReloadManager.isLoaded()` is false, the QR screen doesn't open and the chat shows "Recipes are still loading, try again in a moment". `EmiReloadManager` is internal API, so access is guarded and treated as "ready" if the class or method is missing.
- When EMI is loaded, recipes come from `EmiApi.getRecipeManager().getRecipes()`, and the vanilla dispatch isn't used:
  - `type_id` is the category id;
  - `inputs` come from `getInputs()`, item stacks only, with tags expanded;
  - `results` come from `getOutputs()`;
  - `id` is `getId()`, and synthetic `ns:/path` ids are kept;
  - a null id becomes `<category ns>:<hash of the encoded body>`.
- `ModInfos` picks up the namespaces as usual (ADR 0011).

## Acceptance

- Pure tests for the null-id fallback (the same body gives the same id) and the item-only stack filter.
- Manual (user): a pack with EMI shows modded and JEI-only recipes in the app. Opening the QR screen right after F3+T shows the chat message.
