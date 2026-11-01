# Wire for protobuf codegen on both mod and app

The `:protocol` Gradle module owns the `.proto` schema and runs **Square Wire** codegen twice: Java output consumed by `:mod:common` and (via `:mod:fabric`) packaged into the mod jar; Kotlin Multiplatform output consumed by `:shared`. Both sides depend on Wire's lean runtime — no reflection, no descriptor pool, no dynamic message support.

**`protobuf-javalite` on the mod + `pbandk` on the app** was rejected. It would mean two generator plugins, two generated-API surfaces, and two sets of conventions to keep in sync. Wire generates both targets from one plugin invocation with the same schema; a protocol change breaks both sides in one build, which is the whole reason the schema lives in a shared module.

**Full `protobuf-java`** on the mod was rejected as too heavy — it carries reflection and descriptor infrastructure the mod never uses.

The trade-off: Wire's Java output is slightly less conventional than javalite's (builder patterns differ) and its KMP support, while mature, moves faster than Google's canonical library. These are accepted in exchange for a single codegen path across the repo.
