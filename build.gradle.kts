plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.wire) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.spotless)
}

// One formatting setup for every module, configured from the root. Style lives in .editorconfig.
// spotlessCheck verifies, spotlessApply fixes; not wired into `check` (runs on demand and from .githooks).
spotless {
    isEnforceCheck = false
    val editorConfig = rootProject.file(".editorconfig")
    val excludes = listOf("**/build/**", "**/.gradle/**", "**/.kotlin/**", "mod/*/run/**")
    // MPL-2.0 Exhibit A notice, placed above `package` (or `@file:` annotations) in every source file.
    val mplHeader =
        """
        |/*
        | * This Source Code Form is subject to the terms of the Mozilla Public
        | * License, v. 2.0. If a copy of the MPL was not distributed with this
        | * file, You can obtain one at https://mozilla.org/MPL/2.0/.
        | */
        |
        """.trimMargin()
    kotlin {
        target("**/src/**/*.kt")
        targetExclude(excludes)
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(editorConfig)
        licenseHeader(mplHeader)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(excludes)
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(editorConfig)
    }
    java {
        target("**/src/**/*.java")
        targetExclude(excludes)
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
        removeUnusedImports()
        licenseHeader(mplHeader)
    }
}

allprojects {
    group = "org.sagisolas.recipehost"
}
