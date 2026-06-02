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
}
