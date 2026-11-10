plugins {
    alias(libs.plugins.architecturyLoom)
    alias(libs.plugins.architecturyPlugin)
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
}

loom {
    silentMojangMappingsLicense()
}

val common =
    configurations.create("common") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
val shadowBundle =
    configurations.create("shadowBundle") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

configurations.named("compileClasspath").configure { extendsFrom(common) }
configurations.named("runtimeClasspath").configure { extendsFrom(common) }
configurations.matching { it.name == "developmentNeoForge" }.configureEach {
    extendsFrom(common)
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("mod_minecraft_version").get()}")
    mappings(loom.officialMojangMappings())

    "neoForge"("net.neoforged:neoforge:${providers.gradleProperty("neoforge_version").get()}")

    common(project(path = ":mod:common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":mod:common", configuration = "transformProductionNeoForge"))

    implementation(project(":protocol"))
    "include"(project(":protocol"))
    // :protocol's runtime: pure Java, so the mod needs no Kotlin stdlib (ADR 0006, ADR 0007).
    "include"(libs.protobuf.javalite)
    implementation(libs.qrcodegen)
    "include"(libs.qrcodegen)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

// The shipped jar must hold :mod:common's classes too. Architectury's template packs them with
// the Shadow plugin; a plain Jar does the same: this module's classes plus common's production
// output (shadowBundle), which remapJar then remaps into the shipped jar.
val bundledJar =
    tasks.register<Jar>("bundledJar") {
        archiveClassifier = "dev-bundled"
        from(zipTree(tasks.jar.flatMap { it.archiveFile }))
        from(shadowBundle.elements.map { files -> files.map { zipTree(it) } })
        exclude("architectury.common.json", "META-INF/MANIFEST.MF")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
tasks.remapJar {
    inputFile = bundledJar.flatMap { it.archiveFile }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}
