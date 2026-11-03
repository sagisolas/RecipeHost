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

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("mod_minecraft_version").get()}")
    mappings(loom.officialMojangMappings())
    implementation(project(":protocol"))
    implementation(libs.qrcodegen)

    testImplementation(libs.junit)
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
