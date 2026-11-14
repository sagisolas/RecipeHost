import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.sagisolas.recipehost.gradle.buildNumber
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Release signing values come from the gitignored local.properties; CI, which has no such file,
// passes them as Gradle properties instead (e.g. the ORG_GRADLE_PROJECT_releaseStorePassword env var).
val localProperties =
    Properties().apply {
        rootProject
            .file("local.properties")
            .takeIf { it.isFile }
            ?.inputStream()
            ?.use { load(it) }
    }

fun signingProperty(name: String): String? = localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.androidDriver)
    implementation(libs.playServices.codeScanner)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "org.sagisolas.recipehost"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "org.sagisolas.recipehost"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionName = providers.gradleProperty("app_version").get()
        versionCode = buildNumber(versionName!!)
    }
    androidResources {
        // The app's strings are English only; drop the translations libraries bring in.
        localeFilters += listOf("en", "ru")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            signingProperty("releaseStoreFile")?.let { storeFile = file(it) }
            storePassword = signingProperty("releaseStorePassword")
            keyAlias = signingProperty("releaseKeyAlias") ?: "recipehost"
            keyPassword = signingProperty("releaseKeyPassword")
        }
    }
    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate code, then drop resources nothing references.
            isMinifyEnabled = true
            isShrinkResources = true
            // Without a release keystore configured, fall back to the debug key so the build still installs locally.
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
