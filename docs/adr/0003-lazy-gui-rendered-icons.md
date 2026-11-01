# Icons rendered lazily via GuiGraphics, not read from texture atlas

Icons are produced by rendering each `ItemStack` through `GuiGraphics.renderItem` into an offscreen 16×16 framebuffer on Minecraft's render thread, encoded as PNG, and cached in-memory for the lifetime of the `Session`. Rendering is lazy and budgeted — a small number of icons per tick, driven by the `IconBatch` stream — so no visible hitch is introduced even for heavy modpacks.

Reading sprites directly from the stitched texture atlas was rejected despite being much faster. The atlas is the pre-render input and does not represent: 3D block-item renders (chests, beds, banners), items with custom `BlockEntityWithoutLevelRenderer` (shields, tridents, bundles), model overrides (`pulling_bow`, `filled_bundle`), enchantment glint, or modded items with custom renderers. Covering all of these correctly requires the full GUI render path, matching what JEI, REI, and EMI all do.

An atlas fast-path for items whose model is a single flat texture with no overrides is a possible future optimisation if snapshot time becomes the bottleneck; it is deliberately not designed in now.
