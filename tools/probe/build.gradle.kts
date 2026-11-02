plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":protocol"))
}

application {
    mainClass.set("org.sagisolas.recipehost.tools.probe.MainKt")
}

kotlin {
    jvmToolchain(21)
}
