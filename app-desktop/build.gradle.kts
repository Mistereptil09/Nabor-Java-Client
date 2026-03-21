// app-desktop/build.gradle.kts
plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation(project(":core-impl"))
    // runtimeOnly(project(":plugins:sync")) // core plugin
}