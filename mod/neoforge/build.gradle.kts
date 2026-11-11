plugins {
    alias(libs.plugins.architecturyLoom)
    alias(libs.plugins.architecturyPlugin)
    alias(libs.plugins.recipehostLoaderJar)
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

// The shipped jar: recipehost-neoforge-<mod version>+<Minecraft version>.jar
version = "${providers.gradleProperty("mod_version").get()}+${providers.gradleProperty("mod_minecraft_version").get()}"
// Set on the archive tasks, not base.archivesName: a plugin overrides that one.
tasks.withType<AbstractArchiveTask>().configureEach {
    archiveBaseName = "recipehost-neoforge"
}

repositories {
    maven {
        name = "NeoForged"
        url = uri("https://maven.neoforged.net/releases/")
    }
    maven {
        name = "Sleeping Town (EMI)"
        url = uri("https://repo.sleeping.town/")
        content { includeGroup("dev.emi") }
    }
}

loom {
    silentMojangMappingsLicense()
}

configurations.matching { it.name == "developmentNeoForge" }.configureEach {
    extendsFrom(configurations["common"])
}
// Dev runs only: the game layer doesn't see plain classpath jars; the shipped jar carries the
// libraries inside it instead.
configurations.named("forgeRuntimeLibrary") { extendsFrom(configurations["bundled"]) }

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("mod_minecraft_version").get()}")
    mappings(loom.officialMojangMappings())

    neoForge("net.neoforged:neoforge:${providers.gradleProperty("neoforge_version").get()}")

    common(project(path = ":mod:common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":mod:common", configuration = "transformProductionNeoForge"))

    // Packed into the shipped jar, stripped and relocated (recipehost.loader-jar).
    // :protocol brings its runtime, pure Java, so the mod needs no Kotlin stdlib (ADR 0006, ADR 0007).
    bundled(project(":protocol"))
    bundled(libs.qrcodegen)

    // Dev client only, never shipped: `-Pmod_dev_emi=true` runs it with EMI, so both
    // recipe sources (ADR 0008) can be tried.
    if (providers.gradleProperty("mod_dev_emi").orNull.toBoolean()) {
        modLocalRuntime("dev.emi:emi-neoforge:${providers.gradleProperty("mod_emi_version").get()}")
    }
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
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}
