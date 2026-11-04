import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.wire)
    alias(libs.plugins.sqldelight)
}

wire {
    sourcePath {
        srcDir(rootProject.layout.projectDirectory.dir("protocol/src/main/proto"))
    }
    kotlin {
        android = false
    }
}

sqldelight {
    databases {
        create("CatalogDatabase") {
            packageName.set("org.sagisolas.recipehost.data.catalog")
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    android {
       namespace = "org.sagisolas.recipehost.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.wire.runtime)
            implementation(libs.ktor.http)
            implementation(libs.ktor.network)
            implementation(libs.kotlinx.coroutines.core)
            // api: AppGraph takes a SqlDriver, built by the wrappers.
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}