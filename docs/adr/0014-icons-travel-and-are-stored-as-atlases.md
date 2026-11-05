# Icons travel and are stored as atlases, not one PNG per item

Each `IconBatch` is one **Icon atlas**: the whole sheet `IconRenderer` reads back (up to 8×4 cells of 16 px, cut after the last used row), sent as a single PNG with its `cell_size`, `columns` and the `item_ids` of its cells in row-major order. The mobile app stores the atlas as received (`icon_atlas`) and each `Icon` as a pointer to its cell (`icon.atlas_id`, `icon.cell`). The UI decodes each atlas once, off the main thread, and draws an item's Icon as a region of it.

Sending and storing one PNG per item was rejected: the mod cropped and encoded every cell separately, and the item grid decoded one image per visible cell on the main thread, which made scrolling heavy. With atlases the mod encodes once per sheet, the app decodes ~32× fewer images, and item queries return a small region (atlas id + cell) instead of a blob.

Consequences:
- An atlas is immutable and its id is never reused (`AUTOINCREMENT`), so decoded atlases are cached by id with no invalidation.
- One atlas can hold Icons of several `Mod release`s (each cell is filed under its item id's namespace). A later `Scan` repoints some cells to new atlases; an atlas left with no `Icon` pointing at it is deleted after each batch.
- The wire change bumped the protocol version to 2.
