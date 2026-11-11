# Architectury Loom for loader abstraction (not Architectury API, not hand-rolled)

The mod is split into `:mod:common`, `:mod:fabric`, and `:mod:neoforge` using the **Architectury Loom** Gradle plugin. `common` holds all loader-agnostic code against Mojmap-named Minecraft APIs. The handful of operations that genuinely differ per loader (mod list, config dir, client-started callback, keybinding registration) are declared in `common` as static methods on a `Platform` class annotated `@ExpectPlatform`; each loader module provides `PlatformImpl` with the matching statics. Architectury's annotation processor wires them at compile time — no runtime indirection.

The **Architectury API** runtime jar was rejected. Its event/registry/networking helpers are convenient but are themselves a dependency shim, and the mod's surface against Minecraft is narrow enough that direct Mojmap calls are clearer and dependency-free. `@ExpectPlatform` codegen does not require the API jar.

A **fully hand-rolled** common+platform split (plain Gradle subprojects with a `PlatformHooks` interface injected at startup) was rejected for the opposite reason: Architectury Loom's source-set wiring, mapping unification, and mod-jar packaging are non-trivial to reproduce correctly, and reinventing them earns nothing. The loom plugin pulls in no runtime dependency on the API jar.

The trade-off: adding a future loader (Forge, Quilt) means one more source set and a `PlatformImpl` with four static methods — not a rewrite. NeoForge was the second loader and needed only that.

**Amendment:** `Platform` holds only what `:mod:common` calls (mod list, Minecraft version, screen-init and world-leave events). Unused methods (config dir, client-started callback, keybinding, world join) were dropped so a new loader implements only those; add one back when a feature needs it.

**Amendment:** NeoForge has landed as `:mod:neoforge`. Its `PlatformImpl` uses NeoForge's built-in mod list and event bus (`ScreenEvent.Init.Post`, `ClientPlayerNetworkEvent.LoggingOut`), so it needs no extra dependency and no Mixins. One packaging difference: NeoForge's dev game doesn't see plain classpath jars, so the bundled libraries (`:protocol`, protobuf-javalite, qrcodegen) are also declared as `forgeRuntimeLibrary` for dev runs; the shipped jar carries them jar-in-jar, as on Fabric.
