pluginManagement {
    repositories {
        mavenCentral()
        maven("https://maven.taumc.org/releases")
        maven("https://maven.wagyourtail.xyz/releases")
        gradlePluginPortal {
            content {
                excludeGroup("org.apache.logging.log4j")
            }
        }
    }

    plugins {
        fun property(name: String) = settings.extra.properties[name] as? String ?: error("Property `${name}` is not defined in gradle.properties!")

        id("org.gradle.toolchains.foojay-resolver-convention") version(property("foojay_resolver_convention_version"))
        id("org.taumc.gradle.versioning") version(property("taugradle_version"))
        id("org.taumc.gradle.publishing") version(property("taugradle_version"))
        id("xyz.wagyourtail.jvmdowngrader") version(property("jvmdg_version"))
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "zson"

