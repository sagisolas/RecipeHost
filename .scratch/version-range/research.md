# Supported Minecraft version range for the companion mod

Question: the mod targets Minecraft 1.21.1 (`gradle.properties`: `mod_minecraft_version=1.21.1`, Fabric Loader 0.20.1, fabric-api 0.116.18+1.21.1, NeoForge 21.1.77, EMI 1.1.24+1.21.1). Starting at 1.21.1, what is the highest version we can declare without big codebase changes, meaning one jar per loader from the same source with at most tiny compat shims?

Researched 2026-11-12. Tags used below: **[verified]** means a primary source or repo file says so. **[inference]** means it is my reasoning from those sources and has not been checked by building or running.

Primer links point at the NeoForged migration primers (`github.com/neoforged/.github/blob/main/primers/<ver>/index.md`). They are written against vanilla Mojang names, so they hold for both loaders. Line anchors (`#Lnnn`) refer to the file as fetched on 2026-11-12.

---

## TL;DR

- **Recommended max: 1.21.1. The declared range is exactly `1.21.1`.** Nothing after 1.21.1 works without big changes. 1.21.2 breaks every layer the mod has:
  - Recipe access: the client no longer receives recipes.
  - The recipe classes we dispatch on.
  - The offscreen render path (`TextureTarget`, `RenderTarget.clear`, projection matrix, `applyModelViewMatrix`).
  - The `GuiGraphics.blit` signatures.
  - The `Registry.get` signature.
  - EMI: it has **no release for any version after 1.21.1** at all.
  - None of these are small shims. They are source-incompatible API changes, so each needs its own build.
- **A singleplayer-only fallback does not help.** I considered declaring 1.21.2–1.21.3 or higher and reading recipes from the integrated server in singleplayer. That only fixes the recipe *source*. The render, recipe-class, blit and registry breaks still need a separate 1.21.2+ build, so it is the same cost as a real port (see "What each extra step costs"). There is **no fallback that extends the range without a second build.**
- **The current metadata over-declares, and that is a bug today:**
  - `fabric.mod.json` says `"minecraft": "~1.21"`, which means `>=1.21 <1.22-` (`mod/fabric/src/main/resources/fabric.mod.json:16`).
  - `neoforge.mods.toml` says `[1.21,1.22)` (`mod/neoforge/src/main/resources/META-INF/neoforge.mods.toml:21`).
  - So both loaders will load the jar on 1.21.2–1.21.11, where it will fail with `NoSuchMethodError`/`NoSuchFieldError` once its code runs **[inference]**.
- Optional: **1.21 (1.21.0) as the lower bound** is likely free on Fabric, because the 1.21→1.21.1 delta doesn't touch anything we use. On NeoForge it is already excluded by `neoforge [21.1,)`. The request was to start at 1.21.1, so this is only noted here.

---

## 1. What the mod touches (repo inventory)

There are no Mixins and no access wideners: no `*.mixins.json` under `mod/*/src`. Every touch point is a direct call against Mojang-named Minecraft or loader API.

| Area | API used | Where |
|---|---|---|
| Recipe source (vanilla) | `ClientLevel#getRecipeManager().getRecipes()`, `RecipeHolder#id()/value()`, `RegistryAccess` | `mod/common/src/main/java/org/sagisolas/recipehost/mod/snapshot/vanilla/VanillaRecipeSource.java:25-27,51,56` |
| Recipe dispatch | `ShapedRecipe#getWidth/getHeight/getIngredients/getResultItem(RegistryAccess)`, `ShapelessRecipe`, `AbstractCookingRecipe#getCookingTime/getExperience/getIngredients`, `SmithingTransformRecipe#getIngredients`, `StonecutterRecipe#getIngredients`, `Recipe#getResultItem`, `BuiltInRegistries.RECIPE_TYPE.getKey` | `.../snapshot/vanilla/RecipeMapper.java:46-124,168-174` |
| Ingredients | `Ingredient#getItems()` → `ItemStack[]` | `.../snapshot/vanilla/IngredientMapper.java:15` |
| Vanilla layouts | `CraftingMenu/FurnaceMenu/BlastFurnaceMenu/SmokerMenu/SmithingMenu(int, Inventory)`, subclassing `CraftingScreen`, `FurnaceScreen`, …, `renderBg(GuiGraphics, float, int, int)`, `SmithingScreen#subInit`, `GuiGraphics#blit(ResourceLocation,int×6)`, `blitSprite(RL,int×4)`, `drawString`, `pose().pushPose/translate(x,y,z)` | `.../snapshot/vanilla/VanillaLayouts.java:78-113,194-199,250-255,267-342` |
| Icon raster | `new TextureTarget(w,h,true,Minecraft.ON_OSX)`, `setClearColor`, `clear(boolean)`, `bindWrite/unbindWrite`, `RenderSystem.backupProjectionMatrix/setProjectionMatrix(Matrix4f, VertexSorting)/getModelViewStack/applyModelViewMatrix/restoreProjectionMatrix/bindTexture`, `Lighting.setupFor3DItems`, `getColorTextureId`, `NativeImage#downloadTexture`, `new GuiGraphics(mc, bufferSource)`, `renderItem(stack,x,y)`, `flush()`, `destroyBuffers` | `.../snapshot/icon/IconRenderer.java:68-149` |
| Layout raster | Same render-target and projection calls; `JobGuiGraphics extends GuiGraphics` overriding `enableScissor/disableScissor`, `RenderSystem.enableScissor/disableScissor`, `flush()` | `.../snapshot/layout/LayoutRenderer.java:40-133`, `.../snapshot/layout/JobGuiGraphics.java:17-60` |
| Read-back | `NativeImage#getPixelsRGBA()` | `.../snapshot/image/NativeImages.java:16` |
| Items and registries | `BuiltInRegistries.ITEM.entrySet()/getKey/get(ResourceLocation)`, `ResourceLocation.parse/withDefaultNamespace/fromNamespaceAndPath`, `I18n.get`, `Item#getDescriptionId` | `.../snapshot/item/ItemMapper.java:25-33`, `.../snapshot/item/Ids.java:11`, `.../snapshot/MinecraftSnapshotSource.java:83` |
| EMI layer | `EmiApi.getRecipeManager().getRecipes()`, `EmiRecipe#getId/getCategory/getInputs/getOutputs/getCatalysts/addWidgets/getDisplayWidth/Height`, `WidgetHolder`, `SlotWidget#drawBackground/drawStack/getBounds/getStack/getRecipe`, `FillingArrowWidget`, `AnimatedTextureWidget`, `GeneratedSlotWidget`, `RenderSystem.setShaderColor`, `GuiGraphics#blit` | `.../snapshot/emi/EmiRecipeSource.java`, `.../snapshot/emi/CapturingWidgetHolder.java`, `.../snapshot/emi/EmiLayoutDrawer.java:75-93` |
| UI | `Screen` (`init`, `render`, `addRenderableWidget`, `removed`, `onClose`, `isPauseScreen`), `Button.builder`, `Tooltip.create`, `GuiGraphics#fill/drawCenteredString`, `InventoryScreen`/`CreativeModeInventoryScreen` instanceof | `.../ui/PairingScreen.java`, `.../ui/PairingButtonInstaller.java` |
| Threading | `Minecraft#execute` | `.../snapshot/RenderThreadScheduler.java` |
| Fabric | `ClientModInitializer`, `FabricLoader#getAllMods`, fabric-api `ScreenEvents.AFTER_INIT`, `Screens.getButtons`, `ClientPlayConnectionEvents.DISCONNECT`, `SharedConstants.getCurrentVersion().getName()` | `mod/fabric/src/main/java/org/sagisolas/recipehost/mod/fabric/PlatformImpl.java` |
| NeoForge | `@Mod(dist = Dist.CLIENT)`, `ModList`, `ScreenEvent.Init.Post`, `ClientPlayerNetworkEvent.LoggingOut`, `NeoForge.EVENT_BUS` | `mod/neoforge/src/main/java/org/sagisolas/recipehost/mod/neoforge/PlatformImpl.java`, `RecipeHostNeoForge.java` |

---

## 2. Version → breaking changes that hit this codebase

Releases after 1.21.1, with dates from Mojang's version manifest (`https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`) **[verified]**:

- 1.21.2 (2024-10-22) and 1.21.3 (10-23)
- 1.21.4 (12-03)
- 1.21.5 (2025-03-25)
- 1.21.6 (06-17), 1.21.7 (06-30) and 1.21.8 (07-17)
- 1.21.9 (09-30) and 1.21.10 (10-07)
- 1.21.11 (12-09)
- 26.1 (2026-03-24), 26.1.1 and 26.1.2
- 26.2 (06-16)
- 26.3 (09-15), the latest release.

| Version | Change (source) | Repo files hit |
|---|---|---|
| **1.21.2** | **Recipes are server-only.** "Recipe and the recipe ID is now server-side only. It cannot be accessed from the client." Clients get display-entry objects instead ([Fabric 1.21.2 blog](https://fabricmc.net/2024/10/14/1212.html)) **[verified]**. `Level#getRecipeManager` → `recipeAccess`, which "returns `RecipeAccess` on level but `RecipeManager` on `ServerLevel`" ([primer 1.21.2#L1855](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1855)). `getSynchronizedRecipes` → `getSynchronizedItemProperties`, `getSynchronizedStonecutterRecipes` ([#L1832](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1832)). `ClientPacketListener#getRecipeManager` → `recipes` returning `RecipeAccess` ([#L1749](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1749)) **[verified]** | `VanillaRecipeSource.java:27` (the whole vanilla source) |
| 1.21.2 | `RecipeHolder` takes a `ResourceKey` ([#L1822](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1822)). `Recipe#getIngredients` → `placementInfo` ([#L1614](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1614)). `getResultItem` is replaced by `RecipeDisplay` via `display` ([#L1625](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1625)). `Ingredient#getItems` → `items` (HolderSet-based) ([#L1271](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1271)). `ShapedRecipePattern` now holds `List<Optional<Ingredient>>`, and smithing takes `Optional<Ingredient>` ([#L1618-1621](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1618)) **[verified]** | `RecipeMapper.java:46-124`, `IngredientMapper.java:15` |
| 1.21.2 | `TextureTarget(int,int,boolean,boolean)` → `TextureTarget(int,int,boolean)`, and `RenderTarget#clear(boolean)` → `clear()` ([#L1888-1890](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1888)). `RenderSystem#setProjectionMatrix` takes a `ProjectionType` instead of `VertexSorting` ([#L349](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L349)). `RenderSystem#applyModelViewMatrix` is removed ([#L3128](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L3128)). `NativeImage#getPixelsRGBA` → `getPixels` ([#L2639](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L2639)) **[verified]** | `IconRenderer.java:68,82,91-110`, `LayoutRenderer.java:40,54,57-99`, `NativeImages.java:16` |
| 1.21.2 | `GuiGraphics` blit methods take a `Function<ResourceLocation, RenderType>` and the PNG size. `setColor` is removed, `bufferSource` → `drawSpecial` ([#L66-90](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L66)) **[verified]**. Our `new GuiGraphics(mc, mc.renderBuffers().bufferSource())` constructor is not listed as changed **[inference: likely still compiles]** | `VanillaLayouts.java:252-254`, `EmiLayoutDrawer.java:89` |
| 1.21.2 | `Registry#get` → `getValue`, and `getHolder` → `get` ([#L2783-2786](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L2783)) **[verified]**. The same name now has a new return type, so it is a binary break | `MinecraftSnapshotSource.java:83` |
| 1.21.2 | **EMI**: Modrinth lists EMI builds only for `1.18.2 … 1.21, 1.21.1`. The newest is `1.1.24+1.21.1` (2026-05-13) ([Modrinth API](https://api.modrinth.com/v2/project/emi/version)). The [GitHub repo](https://github.com/emilyploszaj/emi) has no branch past `1.21` **[verified, as of 2026-11-12]** | Whole `snapshot/emi/` package (ADR 0008 and 0015 layer) |
| 1.21.3 | Hotfix of 1.21.2. The NeoForged primers repo has no `1.21.3` primer (directory listing: [primers/](https://github.com/neoforged/.github/tree/main/primers)), and Fabric's 1.21.2 post is titled "1.21.2 & 1.21.3" **[verified]**. One jar for 1.21.2–1.21.3 is plausible **[inference]** | — |
| 1.21.4 | Item rendering moves to **Client Items** (`assets/<ns>/items/*.json`) ([primer 1.21.4#L13-17](https://github.com/neoforged/.github/blob/main/primers/1.21.4/index.md#L13)) **[verified]**. `GuiGraphics#renderItem(ItemStack,int,int)` is not listed as changed, so this is internal to the call we make **[inference]** | Probably none at source level, beyond the 1.21.2 port |
| **1.21.5** | Render pipeline rework (`GpuDevice`, `GpuTexture`, `RenderPipeline`) ([primer 1.21.5#L1362](https://github.com/neoforged/.github/blob/main/primers/1.21.5/index.md#L1362)). `RenderTarget`: `bindWrite`, `unbindWrite` and `setClearColor` are removed, and `getColorTextureId` → `getColorTexture` ([#L1741-1749](https://github.com/neoforged/.github/blob/main/primers/1.21.5/index.md#L1741)). `NativeImage#downloadTexture` is removed ([#L1807](https://github.com/neoforged/.github/blob/main/primers/1.21.5/index.md#L1807)). `RenderSystem#bindTexture` is removed ([#L1872](https://github.com/neoforged/.github/blob/main/primers/1.21.5/index.md#L1872)) **[verified]** | `IconRenderer.java:69,82-83,113-120`, `LayoutRenderer.java:41,54-55,102-109`: the readback has to be rewritten on `CommandEncoder` |
| **1.21.6** | GUI is split into prepare and render phases. `renderBg` and friends now *submit* to `GuiRenderState`, and `GuiRenderer` draws later ([primer 1.21.6#L18-28](https://github.com/neoforged/.github/blob/main/primers/1.21.6/index.md#L18)). `GuiGraphics#pose` returns `Matrix3x2fStack`, `flush` is removed, and `drawSpecial` is removed ([#L468-475](https://github.com/neoforged/.github/blob/main/primers/1.21.6/index.md#L468)). `RenderSystem#enableScissor` is removed ([#L1200](https://github.com/neoforged/.github/blob/main/primers/1.21.6/index.md#L1200)). `RenderSystem#setShaderColor` is removed ([#L1216](https://github.com/neoforged/.github/blob/main/primers/1.21.6/index.md#L1216)) **[verified]** | The whole offscreen design: `IconRenderer.java:130-144`, `LayoutRenderer.java:118-129`, `JobGuiGraphics.java` (scissor and flush overrides), `VanillaLayouts.java:195-198`, `EmiLayoutDrawer.java:75-93` |
| 1.21.7 / 1.21.8 | Small primers. 1.21.7 changes `GuiItemRenderState` and `ItemStackRenderState` internals ([primer 1.21.7](https://github.com/neoforged/.github/blob/main/primers/1.21.7/index.md)). 1.21.8 adds `GraphicsWorkarounds` ([primer 1.21.8](https://github.com/neoforged/.github/blob/main/primers/1.21.8/index.md)) **[verified]**. One jar for 1.21.6–1.21.8 is plausible **[inference]** | — |
| 1.21.9 / 1.21.10 | "Feature Submissions", the font glyph pipeline, and input-handling consolidation ([primer 1.21.9](https://github.com/neoforged/.github/blob/main/primers/1.21.9/index.md)). The 1.21.10 primer is tiny ([primer 1.21.10](https://github.com/neoforged/.github/blob/main/primers/1.21.10/index.md)) **[verified]**. Hits on our code are not checked line by line | UI and layout code likely affected **[inference]** |
| 1.21.11 | "All references to `ResourceLocation` … have been replaced with `Identifier`" ([primer 1.21.11#L23-25](https://github.com/neoforged/.github/blob/main/primers/1.21.11/index.md#L23)). There is also "another rendering rewrite" (#L295) **[verified]** | Every file importing `ResourceLocation` (7 files) |
| 26.1+ | Java 25, and vanilla ships deobfuscated ([primer 26.1#L20-28](https://github.com/neoforged/.github/blob/main/primers/26.1/index.md#L20)). Intermediary is discontinued, and the game uses Mojang names at runtime ([Fabric: Removing Obfuscation](https://fabricmc.net/2025/10/31/obfuscation.html)) **[verified]** | Build toolchain (Arch Loom 1.9, `java >=21`) |

---

## 3. What each extra step of range would cost

None of these steps is a "tiny shim". Each one is a separate compile target.

1. **1.21.1 → 1.21.3 (a 1.21.2/1.21.3 jar).** Costs:
   - A second build per loader. In practice that means a multi-version setup, such as a Stonecutter-style preprocessor or a version branch, and Arch Loom against 1.21.3 mappings.
   - Rewriting `VanillaRecipeSource`, `RecipeMapper`, `IngredientMapper`, the blit calls in `VanillaLayouts` and `EmiLayoutDrawer`, the render-target and projection code in `IconRenderer` and `LayoutRenderer`, `NativeImages`, and `MinecraftSnapshotSource:83`.
   - **Recipe availability changes in kind:**
     - In **singleplayer and on a LAN host**, the integrated server's `RecipeManager` is reachable in the same JVM. `ServerLevel` still returns a `RecipeManager` ([primer 1.21.2#L1855](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1855)). So the vanilla dispatch could keep working off `Minecraft#getSingleplayerServer()` **[inference; threading between the server and render threads would need care]**.
     - In **multiplayer**, the client only has what the server sends: item property sets, stonecutter recipes, and recipe-book display entries for *unlocked* recipes ([#L1832](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md#L1832), [Fabric blog](https://fabricmc.net/2024/10/14/1212.html)) **[verified]**. A full catalog is impossible without a server-side component. That conflicts with ADR 0001 (client-side only).
   - **The EMI layer is gone**, because no EMI build exists. ADR 0008 and 0015 enrichment would fall back to vanilla only.
2. **→ 1.21.4.** Probably cheap on top of step 1 **[inference]**. Client Items stays behind `renderItem`.
3. **→ 1.21.5.** The icon and layout readback has to be rewritten on `GpuDevice`/`CommandEncoder`, because `bindWrite` and `downloadTexture` are gone.
4. **→ 1.21.6–1.21.8.** Offscreen capture has to be redesigned. `GuiGraphics` no longer draws immediately; it submits render state that `GuiRenderer` draws into the main target. `flush()` and the scissor overrides in `JobGuiGraphics` have no direct equivalent. ADR 0003 and 0015 need re-deciding (for example, driving a `GuiRenderer` into our own target, or picture-in-picture renderers) **[inference on approach]**.
5. **→ 1.21.9–1.21.11, 26.x.** More rendering rewrites, the `Identifier` rename, then Java 25 and a new toolchain.

---

## 4. Metadata range strings

Fabric (`fabric.mod.json`). Version predicates: a bare version is an exact match, space-separated predicates are ANDed, an array is ORed, and `~1.2.3` means `>=1.2.3 <1.3.0-` ([Fabric mod json spec](https://wiki.fabricmc.net/documentation:fabric_mod_json_spec)) **[verified]**.

```json
"minecraft": "1.21.1"
```

If you want 1.21 too, use `">=1.21 <=1.21.1"`. The current `"~1.21"` also matches 1.21.2–1.21.11, so it is too wide.

NeoForge (`neoforge.mods.toml`). `versionRange` is "a Maven Version Range" ([NeoForge docs: mod files](https://docs.neoforged.net/docs/gettingstarted/modfiles)). `[x]` pins exactly one version, and `[a,b)` is inclusive-exclusive ([Maven version range spec](https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html)) **[verified]**.

```toml
[[dependencies.recipehost]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1]"     # or "[1.21.1,1.21.2)"
ordering = "NONE"
side = "CLIENT"
```

Also consider capping NeoForge itself, `versionRange = "[21.1,21.2)"`. NeoForge's major.minor tracks the Minecraft minor.patch: 21.1 = 1.21.1 and 21.3 = 1.21.3, per the version list at [maven.neoforged.net](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) **[verified: listing; mapping convention inference from names]**. The current `[21.1,)` (`neoforge.mods.toml:14`) would accept any later NeoForge.

The EMI dependency is not declared in either file. That is fine, since it is detected at runtime (`RecipeHost.java`, `emiLoaded`).

---

## 5. Hotfix binary compatibility

- **1.21 ↔ 1.21.1**: the whole 1.21.1 primer adds `EntitySelectorParser#allowSelectors` and `BlockEntity#isValidBlockState`, and removes one `EntitySelectorParser` constructor ([primer 1.21.1](https://github.com/neoforged/.github/blob/main/primers/1.21.1/index.md)) **[verified]**. The mod uses none of these, so a 1.21.1 jar should run on 1.21 under Fabric **[inference; untested]**.
- **1.21.2 ↔ 1.21.3, 1.21.6 ↔ 1.21.7 ↔ 1.21.8, 1.21.9 ↔ 1.21.10**: the primers are empty or tiny (see the table), so one jar per group is plausible **[inference]**. That only matters once a 1.21.2+ build exists.
- On Fabric, a jar is remapped to Intermediary, whose names are stable across versions for unchanged members. So binary compatibility comes down to whether the members we call still exist with the same descriptors **[inference; Fabric's obfuscation post confirms Intermediary is the runtime namespace until 26.1]**.

---

## 6. Open questions

- I did not verify that fabric-api `ScreenEvents.AFTER_INIT`, `Screens.getButtons` and `ClientPlayConnectionEvents.DISCONNECT` still exist in each later fabric-api, or that NeoForge `ScreenEvent.Init.Post` and `ClientPlayerNetworkEvent.LoggingOut` do. They are moot while vanilla already breaks at 1.21.2.
- The official minecraft.net 1.21.2 changelog timed out on fetch. The recipe-sync claim rests on Fabric's 1.21.2 post and the NeoForged primer.
- Could EMI ship a 1.21.2+ build later? Today none exists (Modrinth, GitHub). JEI and REI were out of scope (ADR 0008 rejects JEI).
- If a 1.21.2+ port is ever wanted, the bigger decision is product, not code: accept singleplayer-only recipes, or add an optional server-side component. Either one changes ADR 0001.
- Whether to widen downward to 1.21 on Fabric. It is likely free, but untested.
