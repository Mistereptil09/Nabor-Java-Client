plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "Nabor_Services_Java_Client"

// main app parts
include("core-api")
include("core-impl")
include("app-desktop")

// plugins
include("plugins:sync")
include("plugins:test-plugin")
include("plugins:resolver")
include("plugins:viewer")
include("plugins:export-csv")