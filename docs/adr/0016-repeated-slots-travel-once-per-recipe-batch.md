# Repeated Slots travel once per RecipeBatch

The companion mod expands every ingredient tag into its items (the mobile app never resolves tags), so a tag such as `#planks` repeats its full list in every recipe that uses it, in `inputs` and again in `layout_stacks`. Each `RecipeBatch` now carries `shared_slots`: every Slot with two or more alternatives that appears more than once in the batch is sent there once, and each occurrence becomes a Slot with only `shared` set, a 1-based index into that list. The app expands them as each batch arrives, so the Catalog, its queries and the stored recipe bodies are unchanged.

A table per batch rather than one for the whole Snapshot keeps each batch decodable on its own: a dropped stream still commits nothing half-resolved, and no state carries between frames. Interning in the Catalog too (stack lists as rows, `recipe_input` over lists) was rejected for now: it changes every recipe query and the stored body format for a storage gain nobody has measured.

Nothing is published yet, so this amends protocol version 2 rather than bumping it.
