# Additive Catalog keyed by Mod release

The mobile app keeps a single offline `Catalog` that every `Scan` merges into. Nothing is replaced per scan. `Item`s and `Icon`s are keyed by `(Mod release, item id)`, where a `Mod release` is `(mod id, version, minecraft version)`. A later `Scan` that brings the same `Mod release` overwrites the display names and Icons for that release, last `Scan` wins, and fills in Icons that are missing. What the user browses is chosen by the `Version selection`: one `Mod release` per mod. A `Scan` sets it to the releases it carries. The user can then change it on the `Mod versions` screen in Settings, and mods absent from that `Scan` keep their previous choice.

This does not change the wire. A `Snapshot` is still a full dump with no deltas (ADR 0001). "Additive" describes only how the app stores `Snapshot`s, so a reader who sees "full Snapshot, no resumption" should not expect each scan to replace the app's data.

## Considered options

- **One `Snapshot` per device, replaced by each scan.** This was the simplest option, with no `Version selection`, no release keys and no merge rules. It was rejected because the user wants to browse several versions of a mod side by side and switch between them, which a single stored `Snapshot` cannot offer.
- **A library of independent `Snapshot`s, browsed one at a time.** This was rejected because the unit the user selects is a mod version, not a whole pack. Combining Create 6.0 from one scan with vanilla 1.21.1 from another has to work.

## Consequences

- The minecraft version is part of a `Mod release`'s key, because many mods' version strings omit it (`jei 19.0.0` may exist for several game versions).
- A `Mod release` can be referenced by a `Recipe` while it is not the selected release, or before the `Catalog` contains it at all. The UI shows such Items by their id with a placeholder Icon.
- A drop during the Icon part of a `Snapshot` loses nothing that was committed, and another `Scan` of the same game fills the missing Icons. "Incomplete Snapshot" is therefore not a concept the app needs.
- Item display names come in the game's locale (ADR 0004). Releases scanned from games set to different languages can therefore show names in mixed languages, which is accepted.
