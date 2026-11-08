plugins {
    alias(libs.plugins.architecturyLoom)
    alias(libs.plugins.architecturyPlugin)
}

architectury {
    common("fabric", "neoforge")
}

loom {
    silentMojangMappingsLicense()
}

// Loom declares project repositories, so the ones in settings are ignored here.
repositories {
    maven {
        name = "Sleeping Town (EMI)"
        url = uri("https://repo.sleeping.town/")
        content { includeGroup("dev.emi") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("mod_minecraft_version").get()}")
    mappings(loom.officialMojangMappings())
    implementation(project(":protocol"))
    implementation(libs.qrcodegen)
    // Soft dependency (ADR 0008): EMI classes are only reached when EMI is loaded.
    compileOnly("dev.emi:emi-xplat-mojmap:${providers.gradleProperty("mod_emi_version").get()}:api")

    testImplementation(libs.junit)
    testImplementation("dev.emi:emi-xplat-mojmap:${providers.gradleProperty("mod_emi_version").get()}:api")
}

tasks.test {
    useJUnit()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}
