plugins {
    alias(libs.plugins.architecturyLoom)
    alias(libs.plugins.architecturyPlugin)
    id("recipehost.loader-jar")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

// The shipped jar: recipehost-fabric-<mod version>+<Minecraft version>.jar
version = "${providers.gradleProperty("mod_version").get()}+${providers.gradleProperty("mod_minecraft_version").get()}"
// Set on the archive tasks, not base.archivesName: a plugin overrides that one.
tasks.withType<AbstractArchiveTask>().configureEach {
    archiveBaseName = "recipehost-fabric"
}

loom {
    silentMojangMappingsLicense()
}

configurations.matching { it.name == "developmentFabric" }.configureEach {
    extendsFrom(configurations["common"])
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("mod_minecraft_version").get()}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("mod_loader_version").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("mod_fabric_api_version").get()}")

    common(project(path = ":mod:common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":mod:common", configuration = "transformProductionFabric"))

    // Packed into the shipped jar, stripped and relocated (recipehost.loader-jar).
    // :protocol brings its runtime, pure Java, so the mod needs no Kotlin stdlib (ADR 0006, ADR 0007).
    bundled(project(":protocol"))
    bundled(libs.qrcodegen)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
