plugins {
    `java-library`
    alias(libs.plugins.wire)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

wire {
    sourcePath {
        srcDir("src/main/proto")
    }
    java {
        android = false
    }
}

dependencies {
    api(libs.wire.runtime)
}
