# Java mod with no Kotlin stdlib shim

The mod is written in **Java**. No Kotlin on the mod side — not via Fabric Language Kotlin, not via a shaded/relocated stdlib. The Kotlin Multiplatform half of this repo (`:shared`, `:androidApp`, iOS) is unaffected; the boundary between the two worlds is the `:protocol` module's generated wire types.

Fabric Language Kotlin was rejected because it is an end-user dependency: players would need to install FLK alongside the mod, raising the install friction for a mod whose whole point is zero-fuss pairing. **Shading Kotlin's stdlib** into the mod jar was rejected because it adds ~1.5 MB of relocated bytecode and Kotlin-metadata noise for a mod whose logic (enumerate registry, render icons, serve TCP frames) is small, imperative, and doesn't benefit meaningfully from Kotlin idioms.

The trade-off: the mod loses `data class`, null-safety, and coroutines. This is accepted because the mod's logic is a handful of classes — the IO thread, snapshot assembly, the QR screen, and `PlatformImpl` — none of which are complex enough for Kotlin's ergonomics to move the needle. KMP code quality on the mobile side is unaffected.
