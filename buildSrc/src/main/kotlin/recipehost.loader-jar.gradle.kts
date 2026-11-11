import net.fabricmc.loom.task.RemapJarTask
import proguard.gradle.ProGuardTask

// How every loader module builds its shipped jar:
//   jar + :mod:common's production classes -> bundledJar
//   bundledJar + bundled libraries -> shrinkJar (ProGuard: unused code stripped, libraries
//   relocated under org.sagisolas.recipehost.lib, nothing obfuscated)
//   shrinkJar -> remapJar (Loom) -> the shipped jar
// A loader declares its libraries as `bundled` and its :mod:common output as `common` and
// `shadowBundle`; dev runs use the libraries as they are.

val ownPackage = "org.sagisolas.recipehost."
val relocatedPackage = "org.sagisolas.recipehost.lib"

pluginManager.withPlugin("dev.architectury.loom") {
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
    val bundled =
        configurations.create("bundled") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            }
        }
    configurations.named("compileClasspath") { extendsFrom(common) }
    configurations.named("runtimeClasspath") { extendsFrom(common) }
    configurations.named("implementation") { extendsFrom(bundled) }

    // The shipped jar must hold :mod:common's classes too. Architectury's template packs them
    // with the Shadow plugin; a plain Jar does the same: the loader's classes plus common's
    // production output (shadowBundle).
    val jar = tasks.named<Jar>("jar")
    val bundledJar =
        tasks.register<Jar>("bundledJar") {
            archiveClassifier = "dev-bundled"
            from(zipTree(jar.flatMap { it.archiveFile }))
            from(shadowBundle.elements.map { files -> files.map { zipTree(it) } })
            exclude("architectury.common.json", "META-INF/MANIFEST.MF")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

    // One line per class of the bundled libraries: original name -> the same name under
    // org.sagisolas.recipehost.lib. ProGuard applies it, so libraries move without being
    // renamed. Our own classes (:protocol) stay where they are.
    val relocationMapping =
        tasks.register("relocationMapping") {
            val output = layout.buildDirectory.file("proguard/relocation.txt")
            inputs.files(bundled)
            outputs.file(output)
            doLast {
                val lines =
                    bundled.files
                        .asSequence()
                        .flatMap { jarFile ->
                            val classes = zipTree(jarFile).matching { include("**/*.class").exclude("META-INF/**") }
                            classes.toClassNames()
                        }.filterNot { it.endsWith("module-info") || it.startsWith(ownPackage) }
                        .distinct()
                        .sorted()
                        .map { "$it -> $relocatedPackage.$it:" }
                        .toList()
                output.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
            }
        }

    // Minecraft, the loader and the JDK: read to resolve references, never packed.
    val jdkModules =
        extensions
            .getByType<JavaToolchainService>()
            .launcherFor { languageVersion = JavaLanguageVersion.of(21) }
            .map { it.metadata.installationPath.dir("jmods") }
    // Leaves out what is packed by component, not by file: a classpath may resolve a packed
    // project to its classes directory instead of the jar that `bundled` resolves.
    val packedComponents by lazy {
        listOf(bundled, common).flatMapTo(mutableSetOf()) { packed ->
            packed.incoming.resolutionResult.allComponents
                .map { it.id }
        }
    }
    val libraries =
        files(
            listOf("compileClasspath", "runtimeClasspath").map { name ->
                configurations.named(name).map { classpath ->
                    classpath.incoming.artifactView { componentFilter { it !in packedComponents } }.files
                }
            },
        )

    val shrunkJar = layout.buildDirectory.file("proguard/shrunk.jar")
    val relocation = relocationMapping.map { it.outputs.files.singleFile }
    val usageReport = layout.buildDirectory.file("proguard/usage.txt")
    val mappingReport = layout.buildDirectory.file("proguard/mapping.txt")
    val shrinkJar =
        tasks.register<ProGuardTask>("shrinkJar") {
            // ProGuardTask doesn't track these as inputs/outputs itself: without them a changed
            // mapping would leave the shrunk jar up to date.
            inputs.file(relocation)
            outputs.files(usageReport, mappingReport)
            injars(bundledJar.flatMap { it.archiveFile })
            // Classes only: a library's manifest, module-info and resources stay out.
            injars(mapOf("filter" to "!META-INF/**,!**module-info.class,**.class"), bundled)
            libraryjars(libraries)
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), jdkModules)
            outjars(shrunkJar)
            configuration(rootProject.file("mod/proguard-rules.pro"))
            applymapping(relocation)
            // What was stripped and how classes moved, for checking a build by hand.
            printusage(usageReport)
            printmapping(mappingReport)
        }

    tasks.named<RemapJarTask>("remapJar") {
        dependsOn(shrinkJar)
        inputFile = shrunkJar
    }
}

/** `a/b/C$D.class` -> `a.b.C$D` for every class file in the tree. */
private fun FileTree.toClassNames(): List<String> {
    val names = mutableListOf<String>()
    visit {
        if (!isDirectory) names += relativePath.pathString.removeSuffix(".class").replace('/', '.')
    }
    return names
}
