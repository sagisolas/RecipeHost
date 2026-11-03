# Catalog schema (SQLDelight) and ScanIngest with an app-scoped scan

Status: ready-for-agent
Blocked by: 01, 03
Spec: ../spec.md

## What

- Add the SQLDelight plugin and runtime, plus coroutines extensions, to `:shared` (ADR 0012). Use `android-driver` in androidMain, and declare `native-driver` for iosMain so the iOS build keeps compiling.
- Schema: start from the sketch in the spec (`mod_release`, `item` plus FTS, `instance`, `recipe` with `body` and `body_hash`, `recipe_output`, `recipe_input`, `version_selection`). `body` is `Recipe.ADAPTER.encode`, and `body_hash` is a stable hash of `body` (SHA-256 via okio `ByteString.sha256()`).
- `data/scan/ScanIngest` is an instance taking the database and a clock. It consumes `Flow<Envelope>` and emits `ScanProgress` (Connecting, Items n/N, Recipes m/M, Committed, Icons k/N, Done, Failed):
  1. **Handshake**: holds the Instance name, the Mod releases `(mod id, version, minecraft version)` and the totals.
  2. **Item and recipe batches**: staged. Keep them in memory first, and measure on a large pack.
  3. **After the last RecipeBatch**: one transaction that
     - upserts releases, setting `last_scanned_at`;
     - upserts Items (keeping `ordinal` = registry position, and the display name from the last Scan);
     - for each release in the Handshake, deletes this Instance's recipes and inserts the new set, assigning each recipe to the release of its id's namespace;
     - sets `version_selection` for the mods in the Scan, leaving other mods alone;
     - upserts the Instance with `last_scanned_at`.
  4. **IconBatch**: upsert `icon` by `(release of the namespace, item_id)` for each batch.
  - If the stream fails before step 3, nothing is written. If it fails afterwards, the committed data stays.
- `data/scan/ScanController` (app-scoped `CoroutineScope`, owned by `AppGraph` later):
  - `start(payload)` runs `SessionClient` into `ScanIngest`;
  - it exposes `StateFlow<ScanProgress>`;
  - only one Scan runs at a time.

## Acceptance

`ScanIngestTest` uses `JdbcSqliteDriver` in memory and scripted envelopes. It checks that:
- a drop before the last RecipeBatch writes nothing;
- rescanning an Instance replaces its recipes for a release, so a recipe that's been removed disappears;
- another Instance's recipes are kept;
- identical bodies across Instances have the same `body_hash`;
- `Version selection` is set to the Scan's releases, and a mod missing from the Scan keeps its previous selection;
- Icons are filled in after the commit, and a drop during Icons keeps the committed Items and Recipes;
- a later Scan overwrites display names;
- a recipe whose namespace has no `ModInfo` is rejected loudly (the mod contract from issue 01).
