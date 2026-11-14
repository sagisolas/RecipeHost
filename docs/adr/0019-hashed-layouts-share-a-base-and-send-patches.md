# Hashed layouts share a base and send patches

An EMI `Recipe layout` used to be keyed by a hash of its pixels (ADR 0015), so any recipe that drew one pixel differently got its own full PNG. In a large modpack, 27,904 layout keys fell into just 864 shapes (type, size and `Recipe slot`s), and none of them was pixel-identical to its shape's most-used layout. Two causes account for this:

- **Animation frames.** Create's machines draw their cogs, blaze and fire from their own tick counter, which ADR 0015's static-texture rule doesn't catch. Images that look the same differ in 1–2% of their pixels.
- **Real per-recipe content.** Some types draw each recipe's rate, burn time or fluid into the image.

Keying layouts by shape alone would have shown every recipe of a shape with the first recipe's text and fluid, so that was rejected.

Instead, `LayoutBatches` makes the first hashed job of each shape its **base**. The base is sent under its content hash as before. Each later job of that shape is compared with the base in 8×8 px tiles:

- If no tile differs, the job is sent as the base's key alone.
- Otherwise it is sent as the base's key plus a `LayoutPatch`: the tiles that differ, as one PNG strip 8 px tall, with each tile's row-major index. A patch's key is a hash of its tiles and pixels. It is sent once per Snapshot, and later references carry only the key.

Each recipe's image is rebuilt exactly, so nothing a recipe draws is lost. Layouts with parameter keys (vanilla) are unchanged.

The base is still keyed by its pixels rather than by its shape, so two Instances whose bases differ keep separate layouts, and a patch is never applied to a base it wasn't diffed against.

## Storage and drawing

The app stores patches in `layout_patch (key, png, tiles)`, and `recipe.patch_id` points at one. Orphaned patches are deleted after a Scan, like layouts. The Item screen draws the layout, then each patch tile scaled into place with `BlendMode.Src` in an offscreen layer, so a transparent patch pixel replaces the layout's pixel. A patched recipe waits for its patch before drawing, so it never briefly shows another recipe's text.

In the measured modpack this takes the images from 56.5 MB of PNGs to 1.5 MB of bases plus 24.2 MB of patches. Of the 27,040 non-base layouts, 961 differ across more than half their area and cost about as much as before.

This bumps the protocol version to 4.

## Consequences

- The mod keeps one base's pixels per shape in memory during a Snapshot. That's about 60 KB per shape, roughly 50 MB for 864 shapes.
- Patches that come only from animation frames are pure waste. Drawing every layout at a frozen animation clock would remove most of them, but whether that can be done across mods hasn't been investigated.
- Fluids, text and other per-recipe pixels are still drawn exactly; they are just stored as tiles.
