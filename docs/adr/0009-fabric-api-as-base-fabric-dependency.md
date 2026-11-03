# fabric-api as the base dependency on Fabric

`:mod:fabric` depends on **fabric-api** (`mod_fabric_api_version` in `gradle.properties`), and `fabric.mod.json` declares it. This replaces the earlier rule of avoiding fabric-api wherever a builtin existed.

Without fabric-api, Fabric Loader doesn't load a mod's `assets/` into the game's resources. That forced workarounds for something as basic as a button icon: classpath PNGs uploaded as `DynamicTexture`s, with no lang files and no GUI-atlas sprites. It also forced hand-written Mixins for lifecycle hooks, keybindings and screen events, each of which has to be re-checked on every Minecraft update and duplicates what fabric-api already provides and tests. Nearly every Fabric modpack already ships fabric-api, so the added install friction for players is close to zero.

fabric-api stays out of `:mod:common`. Loader-specific behaviour still goes through `Platform` (`@ExpectPlatform`, ADR 0005), so NeoForge and any later loader are unaffected.

Considered and rejected: keeping the no-fabric-api rule and re-implementing the resource loader's pack injection with our own Mixin. That costs about 30–50 lines per hook of Minecraft-internal Mixin code to maintain, in exchange for avoiding a dependency players already have.
