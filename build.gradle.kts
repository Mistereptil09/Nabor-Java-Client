plugins {
    java
    id("org.openjfx.javafxplugin") version "0.1.0" apply false
}

allprojects {
    group = "tech.nabor"
    val customVersion = findProperty("version")?.toString()
    version = if (customVersion != null && customVersion != "unspecified") customVersion else "0.9.8-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
    }
}