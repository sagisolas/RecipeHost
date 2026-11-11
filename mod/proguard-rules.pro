# ProGuard rules for every loader's shipped jar (wired in
# buildSrc/src/main/kotlin/recipehost.loader-jar.gradle.kts). Strips code nothing reaches
# and relocates the bundled libraries under org.sagisolas.recipehost.lib; nothing is obfuscated
# or optimized.

# Our code is the entry point and is kept as written: loader entrypoints, Platform
# implementations, and everything they reach.
-keep class org.sagisolas.recipehost.mod.** { *; }
# The generated protocol messages lose only methods nothing calls; their names and fields stay,
# because the protobuf runtime finds both by name.
-keepnames class org.sagisolas.recipehost.protocol.**
-keepclassmembers class org.sagisolas.recipehost.protocol.** { <fields>; }
# Architectury's generated helper for @ExpectPlatform (ADR 0005), in the default package.
-keep class architectury_inject_** { *; }

# No renaming except the relocation in the generated mapping (applymapping): members keep
# their names, so stack traces and reflection by member name still work.
-keepclassmembernames class ** { *; }
# Class names in string constants (Class.forName) follow the relocation.
-adaptclassstrings

# Not optimized: it saved under 1% of the jar, and HotSpot's JIT does the same at run time.
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable,MethodParameters,Record,PermittedSubclasses,NestHost,NestMembers

# Compile-only in :mod:common, so not on the loaders' classpaths: soft dependencies, present
# only when loaded (EMI, ADR 0008), and Architectury's @ExpectPlatform, resolved at build time
# (ADR 0005).
-dontwarn dev.emi.**
-dontwarn dev.architectury.injectables.**
