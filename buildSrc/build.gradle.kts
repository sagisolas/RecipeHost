plugins {
    `kotlin-dsl`
}

dependencies {
    // The loader convention plugin wires Loom's remapJar by its task type, so Loom is loaded from
    // here for the whole build; plugin requests elsewhere name it without a version.
    val loomId =
        libs.plugins.architecturyLoom
            .get()
            .pluginId
    implementation("$loomId:$loomId.gradle.plugin:${libs.versions.architecturyLoom.get()}")
    implementation(libs.proguard.gradle)
}
