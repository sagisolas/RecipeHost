# Recipes are spooled to a temp file during a Scan

A `Scan` commits its `Item`s and `Recipe`s in one transaction after the last `RecipeBatch`, so a dropped stream writes nothing. Until then the Recipes used to wait in memory, tag expansions included, and a 616k-recipe modpack ran the app out of memory. A sample `Catalog` averaged about 770 bytes per stored recipe body, which is roughly 470 MB of encoded bytes at that size and several times that as Wire objects. So each `RecipeBatch` is now appended to a file in the platform's temp directory as it arrives, re-encoded as it travelled with its `shared_slots` still unexpanded (ADR 0016) behind a 4-byte length. The commit reads the file back one batch at a time inside its transaction. Memory stays at about one batch, and the commit is unchanged: one transaction, one insert per row.

`Item`s stay in memory. They are an id and a display name each, a few tens of MB even for a very large pack.

What the commit used to check before its transaction opened is now checked as each `RecipeBatch` arrives, without expanding it: a recipe id whose namespace has no `ModInfo`, and a Slot pointing past its batch's `shared_slots`. A broken batch still fails the `Scan` before anything is written, but sooner, so a stream that sends one and then drops reports the broken contract rather than the drop.

The file is truncated on every `Handshake`, so a retry starts empty, and deleted after the commit and when the `Scan` ends for any reason. A file left by a crash is overwritten by the next `Scan`.

## Considered options

- **Staging tables in the `Catalog`.** Batches would be inserted into `staged_recipe` tables and moved with `INSERT … SELECT` at commit. That commit would be faster, but it changes the schema, writes every expanded body and index row twice, and needs the release mapping redone in SQL. This was rejected for now.
- **Writing recipes straight to the `Catalog` under a generation, then flipping a pointer.** This is the usual way to avoid a long transaction. It was rejected because every recipe query would have to filter on the generation, and the long write lock costs nothing here: the app shows the Scan progress screen until the commit ends, and `clearCatalog` refuses to run during a `Scan`.

## Consequences

- One large commit transaction grows the SQLite journal on disk to roughly the size of the recipes until it commits. RAM is unaffected.
- The spool needs free space in the temp directory of about the compact wire size of the recipes.
