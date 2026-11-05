# App-specific R8 rules. Libraries ship their own consumer rules, so add a rule here only for
# code reached by reflection that R8 can't see.

# Keep line numbers so crash stack traces can be retraced with the mapping file,
# and hide the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit discovers its ComponentRegistrars from the manifest and creates them reflectively.
# firebase-components' consumer rule keeps the classes but, in R8 full mode, not their no-arg
# constructors, so the registrars never load and the code scanner client NPEs on creation.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
