# Wire schema: Recipe.id, Handshake.instance_name, ModInfo for namespaces that only own recipes

Status: ready-for-agent
Spec: ../spec.md

## What

All of this is on the mod side, in `:protocol` and `:mod:common`. Per ADR 0011, these changes amend v1, and `PROTOCOL_VERSION` stays at `1`.

- `recipehost.proto`:
  - Add `string id = 5;` to `Recipe`.
  - Add `string instance_name = 7;` to `Handshake`.
  - Update the `ModInfo` comment: it is sent for namespaces that own items **or** recipes.
- Recipe mapping (ADR 0008) fills `id` with `RecipeHolder.id().toString()`. Shell entries carry `id` too.
- Add a pure `InstanceName.of(Path gameDir, Predicate<Path> exists)` function that returns:
  - the parent's folder name, when the folder is named `.minecraft` or `minecraft` and `../instance.cfg` exists (Prism or MultiMC);
  - `"Minecraft"`, for any other folder named `.minecraft`;
  - the folder name, in every other case.
  
  `MinecraftSnapshotSource.handshake()` sets the field from `Minecraft.gameDirectory`. No `Platform` method is needed.
- `ModInfo` list:
  - Collect the namespaces of every Item id and every recipe id.
  - A namespace with a loaded mod gets that mod's id, name and version.
  - A namespace with no loaded mod (a datapack) gets `{id: ns, name: ns, version: "datapack"}`.
  - The `minecraft` namespace keeps its entry.
- Update the glossary entries for **ModInfo** and **Recipe** if the wording drifts (they were already updated during the design).

## Acceptance

- `InstanceNameTest` covers:
  - a CurseForge path (`…/Instances/ATM9`) gives `ATM9`;
  - a Modrinth path (`…/profiles/Fabulously Optimized`) gives its folder name;
  - a Prism path (`…/instances/foo/.minecraft` with `instance.cfg`) gives `foo`;
  - a vanilla `.minecraft` gives `Minecraft`;
  - a custom directory gives its own name.
- `ModInfo` assembly is tested as a pure function with fake mod lists and namespaces: a namespace with a mod, a datapack namespace, and a mod that owns nothing (left out).
- In-game check: `tools/probe` shows recipe ids and the instance name on Fabric. Record the instance name actually observed on real CurseForge, Modrinth App, Prism and vanilla installs in the Comments section.
