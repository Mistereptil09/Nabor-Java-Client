// core-impl/build.gradle.kts
plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation(project(":core-api"))
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("org.jdbi:jdbi3-core:3.45.0")
    implementation("org.jdbi:jdbi3-sqlobject:3.45.0")  // SQL annotations
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}