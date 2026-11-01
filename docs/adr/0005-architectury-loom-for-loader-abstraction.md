# Architectury Loom for loader abstraction (not Architectury API, not hand-rolled)

The mod is split into `:mod:common`, `:mod:fabric`, and (later) `:mod:forge` using the **Architectury Loom** Gradle plugin. `common` holds all loader-agnostic code against Mojmap-named Minecraft APIs. The handful of operations that genuinely differ per loader (mod list, config dir, client-started callback, keybinding registration) are declared in `common` as static methods on a `Platform` class annotated `@ExpectPlatform`; each loader module provides `PlatformImpl` with the matching statics. Architectury's annotation processor wires them at compile time — no runtime indirection.

The **Architectury API** runtime jar was rejected. Its event/registry/networking helpers are convenient but are themselves a dependency shim, and the mod's surface against Minecraft is narrow enough that direct Mojmap calls are clearer and dependency-free. `@ExpectPlatform` codegen does not require the API jar.

A **fully hand-rolled** common+platform split (plain Gradle subprojects with a `PlatformHooks` interface injected at startup) was rejected for the opposite reason: Architectury Loom's source-set wiring, mapping unification, and mod-jar packaging are non-trivial to reproduce correctly, and reinventing them earns nothing. The loom plugin pulls in no runtime dependency on the API jar.

The trade-off: adding a future loader (NeoForge, Quilt) means one more source set and a `PlatformImpl` with four static methods — not a rewrite.
