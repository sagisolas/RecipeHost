plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.architecturyLoom) apply false
    alias(libs.plugins.architecturyPlugin)
    alias(libs.plugins.wire) apply false
}

architectury {
    minecraft = providers.gradleProperty("mod_minecraft_version").get()
}

allprojects {
    group = "org.sagisolas.recipehost"
    version = providers.gradleProperty("mod_version").orElse("0.0.0").get()
}
