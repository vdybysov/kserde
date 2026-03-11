plugins {
    kotlin("jvm") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    if (name in listOf("example", "ktor")) {
        apply(plugin = "com.google.devtools.ksp")
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }

    if (name != "example") {
        apply(plugin = "maven-publish")
        afterEvaluate {
            extensions.configure<org.gradle.api.publish.PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        groupId = project.group.toString()
                        artifactId = project.name
                        version = project.version.toString()
                        from(components["java"])
                    }
                }
            }
        }
    }
}
