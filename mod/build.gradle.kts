// Parent project for mod subprojects: holds the Architectury setup they share. What the loader
// modules share beyond that is the recipehost.loader-jar convention plugin in buildSrc.
plugins {
    alias(libs.plugins.architecturyPlugin)
}

architectury {
    minecraft = providers.gradleProperty("mod_minecraft_version").get()
}
