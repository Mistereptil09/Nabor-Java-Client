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


tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assemble un JAR exécutable autonome (JavaFX inclus)"

    archiveBaseName.set("nabor-desktop")
    archiveClassifier.set("all")

    manifest {
        attributes["Main-Class"] = "tech.nabor.Main"
        attributes["Implementation-Title"] = "Nabor Services Desktop"
        attributes["Implementation-Version"] = project.version.toString()
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

    // Les signatures de jars tiers casseraient un jar fusionné.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-impl"))
    runtimeOnly(project(":plugins:test-plugin"))
    runtimeOnly(project(":plugins:sync")) // core plugin
    runtimeOnly(project(":plugins:resolver"))

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