plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation(project(":core-api"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    // E2E tests need the real HTTP client (core-impl)
    testImplementation(project(":core-impl"))
}

// ── E2E tests ───────────────────────────────────────────────────────────────
// Run with: ./gradlew :plugins:sync:e2eTest -Dnabor.test.token=<jwt> -Dnabor.test.baseUrl=<url>
tasks.register<Test>("e2eTest") {
    description = "Runs sync plugin E2E tests against a running Nabor API"
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
