pluginManagement {
    repositories {
        gradlePluginPortal {
            content {
                excludeGroup("org.apache.logging.log4j")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.7.0")
    id("org.ajoberstar.grgit") version("5.2.2") apply(false)
    id("com.github.breadmoirai.github-release") version("2.4.1") apply(false)
}

rootProject.name = "zson"

