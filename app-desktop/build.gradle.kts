// app-desktop/build.gradle.kts
plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("tech.nabor.Main")
}

// Enable native access for SQLite JDBC
tasks.named<JavaExec>("run") {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// ── Versioning ─────────────────────────────────────────────────────────────
val generateVersionProperties by tasks.registering {
    val outputFile = layout.buildDirectory.file("resources/main/version.properties")
    outputs.file(outputFile)
    inputs.property("version", project.version.toString())
    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("version=${project.version}\n")
    }
}

tasks.processResources {
    dependsOn(generateVersionProperties)
}

// ── App bundle: fat JAR + plugins/ + nabor.db ─────────────────────────────
fun isPlugin(file: java.io.File) = file.name.let { n ->
    n.startsWith("sync") || n.startsWith("viewer") || n.startsWith("resolver")
    || n.startsWith("export-csv") || n.startsWith("social") || n.startsWith("calendar")
    || n.startsWith("test-plugin")
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a fat JAR containing all dependencies except plugins"
    archiveBaseName.set("nabor-desktop")
    archiveClassifier.set("all")

    manifest {
        attributes(
            "Main-Class" to "tech.nabor.Main",
            "Implementation-Version" to project.version.toString()
        )
    }

    from(sourceSets.main.get().output)

    // Lazy collection to avoid resolving the configuration during gradle configuration phase
    from(provider {
        configurations.runtimeClasspath.get()
            .filter { file -> file.name.endsWith(".jar") && !isPlugin(file) }
            .map { file -> if (file.isDirectory) file else zipTree(file) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register("bundle") {
    group = "build"
    description = "Assembles the executable fat JAR, copies external plugins, and sets up the database file."

    dependsOn("fatJar")

    // Ensure all plugin JARs are compiled first
    val pluginProjects = listOf(
        ":plugins:sync",
        ":plugins:resolver",
        ":plugins:viewer",
        ":plugins:export-csv",
        ":plugins:social",
        ":plugins:calendar"
    )
    pluginProjects.forEach { dependsOn("$it:jar") }

    doLast {
        val bundleDir = rootProject.layout.buildDirectory.dir("bundle").get().asFile
        // Clean the bundle directory first to start fresh
        bundleDir.deleteRecursively()
        bundleDir.mkdirs()

        val pluginsDir = File(bundleDir, "plugins")
        pluginsDir.mkdirs()

        // 1. Copy the fat JAR and rename it to a clean name
        val fatJarFile = tasks.named<Jar>("fatJar").get().archiveFile.get().asFile
        if (fatJarFile.exists()) {
            fatJarFile.copyTo(File(bundleDir, "nabor-desktop.jar"), overwrite = true)
            println("[Bundle] Copied executable fat JAR to bundle directory.")
        } else {
            throw GradleException("Fat JAR was not found at ${fatJarFile.absolutePath}")
        }

        // 2. Copy the plugin JARs and rename them to clean names (without version/classifier)
        val pluginPrefixes = listOf(
            "sync", "viewer", "resolver", "export-csv", "social", "calendar", "test-plugin"
        )
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") && isPlugin(it) }
            .forEach { file ->
                val matchedPrefix = pluginPrefixes.firstOrNull { file.name.startsWith(it) }
                val cleanName = if (matchedPrefix != null) {
                    "$matchedPrefix.jar"
                } else {
                    file.name
                }
                file.copyTo(File(pluginsDir, cleanName), overwrite = true)
                println("[Bundle] Copied plugin: $cleanName")
            }

        // 3. Copy the database file if it exists in the root project directory
        val rootDbFile = File(rootProject.projectDir, "nabor.db")
        if (rootDbFile.exists()) {
            rootDbFile.copyTo(File(bundleDir, "nabor.db"), overwrite = true)
            println("[Bundle] Copied existing nabor.db to bundle.")
        } else {
            println("[Bundle] No existing nabor.db found in root project directory. A new database will be created on application launch.")
        }

        println("[Bundle] Successfully created clean run directory at: ${bundleDir.absolutePath}")
    }
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-impl"))
    // Core plugins — on classpath during dev, loaded as JARs in production.
    // External plugins: build JAR, drop in plugins/ folder, restart.
    runtimeOnly(project(":plugins:sync"))
    runtimeOnly(project(":plugins:resolver"))
    runtimeOnly(project(":plugins:viewer"))
    runtimeOnly(project(":plugins:export-csv"))
    runtimeOnly(project(":plugins:social"))
    runtimeOnly(project(":plugins:calendar"))

    // Génération des QR codes SSO (QRCodeUtil) — rendu local en WritableImage.
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}

// ── E2E tests ───────────────────────────────────────────────────────────────
// Run with: ./gradlew :app-desktop:e2eTest -Dnabor.test.token=<jwt> -Dnabor.test.baseUrl=<url>
tasks.register<Test>("e2eTest") {
    description = "Runs end-to-end tests against a running Nabor API"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    // Forward system properties to the test JVM (evaluated at config time)
    systemProperty("nabor.test.token", System.getProperty("nabor.test.token", ""))
    systemProperty("nabor.test.baseUrl", System.getProperty("nabor.test.baseUrl", ""))
    useJUnitPlatform { includeTags("e2e") }
    shouldRunAfter("test")
}

tasks.test {
    useJUnitPlatform { excludeTags("e2e") }
}