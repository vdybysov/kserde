pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kserde"

include(":annotations")
include(":core")
include(":processor")
include(":mongo")
include(":ktor")
include(":example")
