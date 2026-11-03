# 01 — Allow fabric-api and replace custom mixins with fabric-api calls

Status: claimed
Type: task

## Why

`CLAUDE.md` currently says: "Use at least dependencies as possible. Do not use farbic-api if there are builtin methods."
In practice this forces us to write and maintain our own Mixins for basic lifecycle hooks. Each one has to be
re-checked on every Minecraft update, and they duplicate what fabric-api already provides and tests. We are
dropping the no-fabric-api rule and using fabric-api for loader hooks.

## Scope

### 1. Update `CLAUDE.md`
- Remove the "Do not use farbic-api if there are builtin methods" clause.
- Keep the "as few dependencies as possible" intent. Reword it so fabric-api counts as the accepted
  base dependency for the Fabric loader, for example: "Keep dependencies minimal. On Fabric, prefer
  fabric-api modules over custom Mixins."
- Write a short ADR in `docs/adr/` (next number: `0009`) recording why fabric-api is now allowed.

### 2. Add fabric-api to the Fabric module
- `mod/fabric/build.gradle.kts`: add `modImplementation("net.fabricmc.fabric-api:fabric-api:<ver>")`
  for MC `1.21.1`. Put the version in `gradle.properties` as `mod_fabric_api_version`, next to `mod_loader_version`.
- `mod/fabric/src/main/resources/fabric.mod.json`: add `"fabric-api": "*"` (or a minimum version) to `depends`.
- Keep fabric-api out of `:mod:common`. Common code must stay loader-agnostic and go through `Platform`
  (`@ExpectPlatform`), so another loader can be added later.

### 3. Replace the existing mixin
`mod/fabric/src/main/java/org/sagisolas/recipehost/mod/fabric/mixin/MinecraftSetLevelMixin.java` injects at the
TAIL of `Minecraft#setLevel` and calls `WorldJoinDispatcher.dispatch(level)`.

- Implement `PlatformImpl.onWorldJoin(Consumer<ClientLevel>)` with a fabric-api event, then delete:
  - `mixin/MinecraftSetLevelMixin.java`
  - `WorldJoinDispatcher.java`
  - `recipehost.mixins.json` and the `"mixins"` entry in `fabric.mod.json` (if no mixins are left)
- **Behaviour to check:** `setLevel` fires on every level change, including dimension changes and respawns.
  `ClientPlayConnectionEvents.JOIN` fires once per connection. Pick the event that matches what
  `RecipeHost` expects from "world join":
  - `ClientPlayConnectionEvents.JOIN` (networking API): read the level from `client.level`. It may still be null
    at JOIN time. Check this before relying on it.
  - A client world-change event in lifecycle-events, if the fabric-api build for 1.21.1 has one. Check this
    against the fabric-api sources before using it.
  - Record which one was chosen, and why, in this ticket's Comments.

### 4. Implement the stubs that were waiting on mixins
`PlatformImpl` has two methods that throw `UnsupportedOperationException`, with TODOs that say to write Mixins:
- `onClientStarted(Runnable)` → `ClientLifecycleEvents.CLIENT_STARTED.register(client -> callback.run())`
- `registerKeybinding(KeyMapping)` → `KeyBindingHelper.registerKeyBinding(keyMapping)`
Remove the TODO comments that say "without fabric-api".

## Acceptance criteria
- [x] `CLAUDE.md` no longer bans fabric-api, and ADR 0009 explains the change.
- [x] `:mod:fabric` depends on fabric-api, and `fabric.mod.json` declares it.
- [ ] No custom Mixins are left in `mod/fabric`, unless a hook has no fabric-api equivalent (note any exceptions here).
- [ ] `onWorldJoin`, `onClientStarted` and `registerKeybinding` all work on Fabric. None of them throw.
- [ ] `:mod:common` has no fabric-api imports.
- [ ] The dev harness (world-join listener and probe CLI) still triggers on joining a singleplayer world and a server.

## Comments

- Scope 1–2 done: `fabric-api` `0.116.18+1.21.1`, ADR 0009, and the CLAUDE.md rule. Mixin replacement (scope 3–4) is deferred on purpose. The mixins include the new `InventoryScreenInitMixin` and `MinecraftClearLevelMixin` from the qr-pairing work; `ScreenEvents.AFTER_INIT` and `ClientPlayConnectionEvents.DISCONNECT` are their likely replacements.
