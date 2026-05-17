plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation(project(":core-api"))
}

// Ensure the plugin is included in the classpath when running the app
// You can add this project as a dependency to app-desktop if you want it to be automatically loaded
