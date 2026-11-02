# Minecraft-backed Snapshot source (vanilla baseline)

Status: ready-for-agent

## Problem Statement

Right now the companion mod can accept a paired `Session` and stream a well-formed `Snapshot` over TCP, but the only `SnapshotSource` available is `StubSnapshotSource`, which emits an empty `Handshake` and no `Item`s, `Recipe`s, or `Icon`s. The mobile app therefore has nothing to render. A player who opens the QR screen (once built) and pairs a device will see an empty catalog.

## Solution

Replace the stub with a Minecraft-backed `SnapshotSource` that walks the live `BuiltInRegistries.ITEM`, the player's `RecipeManager`, and the GUI render path to produce a real `Snapshot`. The source is constructed on world-join (recipes are only present once the client has a world), does its eager item/recipe pass off the render thread, and streams `Icon`s lazily in render-thread tasks so no visible hitch is introduced. Recipe inputs are extracted via vanilla `instanceof` dispatch (see ADR 0008) — modded types we don't recognize ship as shell entries with just their `type_id` and result. EMI enrichment is deferred to a follow-up.

## User Stories

1. As a player, I want the mobile app to show every item my client knows about, so that I can browse the full catalog of my modpack while AFK.
2. As a player, I want each item's name to match the language I play Minecraft in, so that the mobile app feels consistent with the game (ADR 0004).
3. As a player, I want every mod that contributes items to appear in the mod list with its name and version, so that I know which mods the catalog came from.
4. As a player, I want mods that register no items (API shims, performance mods) to be omitted from the mod list, so that the list stays meaningful.
5. As a player, I want each item to be accompanied by a 16×16 icon that matches what I see in-game — including 3D block items, shields, tridents, bundles, and modded custom-renderer items (ADR 0003) — so that the mobile app looks like the game.
6. As a player, I want icon rendering to not cause visible frame hitches, so that I can keep playing while the first snapshot is being produced.
7. As a player, I want every vanilla crafting recipe (shaped, shapeless, smelting, blasting, smoking, campfire, smithing, stonecutting) to be rendered richly on the mobile app, so that the primary recipe surface is complete.
8. As a player, I want shaped crafting recipes to carry their grid dimensions, so that the mobile app can reproduce the correct layout.
9. As a player, I want smelting-family recipes to carry their cook time and XP reward, so that the mobile app can display the full recipe context.
10. As a player, I want ingredient slots to list every item that satisfies them (tag expansion handled on the mod side), so that the mobile app never has to resolve a tag.
11. As a player, I want a modded recipe type the mod doesn't recognize to still appear in the catalog with its `type_id` and result item, so that the recipe is discoverable even if its inputs aren't yet richly modeled.
12. As the mobile app, I want the `Handshake` to carry the mod's locale, the Minecraft version, the full `ModInfo` list, and accurate `total_items` / `total_recipes` counts, so that I can drive progress indicators and interpret the stream correctly.
13. As a developer, I want the loader-specific pieces (Minecraft version string, mod list) to live behind `Platform`, so that NeoForge can be added later without touching the common source (ADR 0005).
14. As a developer, I want the Minecraft-backed `SnapshotSource` to live under `:mod:common/snapshot/` and do nothing on construction except hold the live `ClientLevel`, so that worlds that never pair don't pay any CPU cost for a snapshot.
15. As a developer, I want the already-tested seams (`EnvelopeFraming`, `SnapshotSender`, `SessionListener`) to remain unchanged, so that the suite keeps running without a Minecraft runtime and existing green tests stay green.
16. As a developer, I want `StubSnapshotSource` deleted once the real source compiles, so that there is one way to build a snapshot and no dead code.
17. As a future contributor, I want recipe-type mapping concentrated in a single `RecipeMapper` with a flat `instanceof` cascade, so that adding a vanilla type the mod missed (or later wiring EMI) is a one-place change.
18. As a future contributor, I want icon rendering concentrated in a single `IconRenderer` that owns its offscreen render target and `NativeImage` buffers, so that the GL-resource lifecycle is obvious and disposable with the `Session`.

## Implementation Decisions

- The Minecraft-backed `SnapshotSource` lives in `:mod:common` under a new `snapshot` subpackage. All Minecraft-coupled mapping code lives there; `:mod:common/net/` continues to hold only transport/framing/session code with no Minecraft imports in signatures.
- `MinecraftSnapshotSource` is constructed with the live `ClientLevel` and does the eager item + recipe + mod-list pass on its first `handshake()` call, caching batches of size 256. The handshake return value is derived from the cached counts.
- Items come from `BuiltInRegistries.ITEM.entrySet()`. Each entry becomes an `Item { id = key.location().toString(), display_name = I18n.get(item.getDescriptionId()) }`. Locale follows the game (ADR 0004); the `Handshake.locale` field is `Minecraft.getInstance().options.languageCode`.
- `ModInfo`s are the subset of `Platform.listMods()` whose ids match a namespace actually used by at least one item. Mods with zero items are dropped (GLOSSARY).
- Recipes come from `Minecraft.getInstance().level.recipeManager.getRecipes()`. The `RecipeMapper` dispatches via `instanceof`:
  - `ShapedRecipe` → `extras = {width, height}`, slots from `pattern().ingredients()`.
  - `ShapelessRecipe` → slots from `ingredients()`, no extras.
  - `AbstractCookingRecipe` → `extras = {cook_time, xp}`, single slot from `input()`.
  - `SmithingTransformRecipe` → three slots in template/base/addition order.
  - `StonecutterRecipe` → single slot from `input()`.
  - Anything else → `{type_id, inputs = [], results = [recipe.getResultItem(registryAccess)]}` (shell — ADR 0008).
- `IngredientMapper` turns an `Ingredient` into a wire `Slot` by walking `Ingredient.getItems()` and mapping each `ItemStack` to `{item_id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), count = stack.getCount()}`. Tag expansion is Mojang's job, not ours.
- `IconRenderer` owns one 16×16 offscreen `RenderTarget` for the lifetime of the `MinecraftSnapshotSource`. For each `ItemStack`: bind the target, clear, construct a `GuiGraphics`, call `renderItem(stack, 0, 0)`, end the pose stack, flush the buffer source, read the color attachment into a `NativeImage`, encode via `NativeImage.asByteArray()`. Returns PNG bytes for one `Icon`.
- Icon batching is lazy. The `Iterator<IconBatch>` returned from `iconBatches()` holds the remaining `Item` ids. Each `next()` call posts a single task to `Minecraft.getInstance().execute(...)` that renders up to **32 icons** and completes a `CompletableFuture<IconBatch>` the IO thread is awaiting. `hasNext()` returns true until the id list is drained.
- The eager item / recipe pass runs on the IO thread (the thread `SnapshotSender.send` is called on). Registry reads and `RecipeManager.getRecipes()` are safe off the render thread; display-name `I18n.get` is also safe (reads a `Language` instance).
- One new `Platform` method: `String minecraftVersion()`. Fabric implementation uses `SharedConstants.getCurrentVersion().getName()`; NeoForge gets a matching impl when its loader lands.
- `StubSnapshotSource` is deleted in the same change.
- No schema changes.

## Testing Decisions

- A good test here verifies behavior at the transport/framing boundary, not the Minecraft mapping layer. Mapping code is a thin adapter over Mojang APIs; unit-testing it would require a Minecraft runtime (mocks or Fabric's test harness), and both carry more maintenance cost than a mapping bug would.
- **Unchanged tested seams**: `EnvelopeFramingTest`, `SnapshotSenderTest`, `SessionListenerTest` already cover framing, streaming order (Handshake → batches → Done / Error), token auth, and single-session enforcement. All three continue to run without a Minecraft runtime. This feature introduces no new tests at those seams and must not break any of them.
- **`MinecraftSnapshotSource`, `RecipeMapper`, `IngredientMapper`, `IconRenderer`**: explicitly **not unit-tested**. They are Minecraft-coupled adapters. Correctness is verified by in-game smoke test: launch the client with the companion mod, join a world, connect a mobile client through the existing `SessionListener`, confirm the mobile app renders a non-empty catalog with correct names, icons, and shaped-crafting layouts for a few spot-checked recipes.
- Prior art: no mapping code in the repo today has unit tests; the only tests live under `:mod:common/src/test/java/.../net/`. This spec stays consistent with that pattern.

## Out of Scope

- The QR screen that generates the token, binds the listener on screen-open, and unbinds on screen-close + 30 s grace. The source will exist but is not yet wired to a `SessionListener` lifecycle in-game. A separate ticket.
- EMI-based enrichment of unknown recipe types (ADR 0008's optional layer). Will land as a follow-up slice once the baseline is proven.
- The NeoForge `PlatformImpl.minecraftVersion()` (NeoForge wiring is tracked separately; stubbing it to throw is acceptable until that work starts).
- Icon atlas fast-path for flat-texture items (ADR 0003 explicitly defers this).
- Enchantment glint / potion variants / NBT-variant icons. One icon per `Item` (GLOSSARY).
- Any `Snapshot` resumption, delta, or caching across sessions. Each `Session` gets a fresh full stream.
- Mobile-side changes.

## Further Notes

- Threading contract to keep in mind during implementation: the IO thread blocks inside `iconBatches().next()` while a render-thread task completes. This is intentional — it keeps `SnapshotSender`'s "pull from iterator" model intact without introducing reactive wiring. If the render thread stalls (debugger, pause), the mobile app simply waits on its next frame.
- Memory: a 16×16 PNG is on the order of 200–400 bytes. Even a 5000-item modpack sits under 2 MB of icon payload in-flight, comfortably below any buffer concern.
- The eager item/recipe pass is bounded by registry size and recipe count. For vanilla + a dozen large mods this is tens of ms; worth measuring once but not optimizing pre-emptively.
- Follow-ups unlocked by this: QR screen, EMI integration, NeoForge port, icon atlas fast-path.
