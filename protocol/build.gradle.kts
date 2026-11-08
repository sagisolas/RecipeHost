plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// The mod's side of the schema: protoc's lite Java output on protobuf-javalite, a pure-Java
// runtime (ADR 0007). The app generates its own Kotlin types from the same .proto with Wire.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    api(libs.protobuf.javalite)

    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
