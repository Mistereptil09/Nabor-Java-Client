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

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-impl"))
    runtimeOnly(project(":plugins:test-plugin"))
    // runtimeOnly(project(":plugins:sync")) // core plugin
}