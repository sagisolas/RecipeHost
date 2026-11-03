plugins {
    alias(libs.plugins.architecturyLoom)
    alias(libs.plugins.architecturyPlugin)
}

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()
}

val common = configurations.create("common") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val shadowBundle = configurations.create("shadowBundle") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations.named("compileClasspath").configure { extendsFrom(common) }
configurations.named("runtimeClasspath").configure { extendsFrom(common) }
configurations.matching { it.name == "developmentFabric" }.configureEach {
    extendsFrom(common)
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("mod_minecraft_version").get()}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("mod_loader_version").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("mod_fabric_api_version").get()}")

    common(project(path = ":mod:common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":mod:common", configuration = "transformProductionFabric"))

    implementation(project(":protocol"))
    "include"(project(":protocol"))
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
