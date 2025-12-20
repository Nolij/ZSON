import org.objectweb.asm.tools.Retrofitter
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.Deflater

plugins {
    id("idea")
    id("java")
    id("maven-publish")
    id("org.taumc.gradle.versioning")
    id("org.taumc.gradle.publishing")
    id("xyz.wagyourtail.jvmdowngrader")
}

fun property(name: String): String = rootProject.properties[name] as? String ?: error("Property $this not found")

group = property("maven_group")
base {
    archivesName = property("project_name")
}

val isRebuild = (rootProject.properties["external_publish"] as String?).toBoolean()
version = tau.versioning.version(property("project_version"), project.properties["release_channel"], isRebuild = isRebuild)
println("ZSON Version: ${tau.versioning.version}")

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:${property("jetbrains_annotations_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${property("junit_version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.javadoc {
    val options = options as StandardJavadocDocletOptions
    options.addBooleanOption("Xdoclint:none", true)
}

jvmdg.defaultTask {
    dependsOn(tasks.jar)
    downgradeTo = JavaVersion.VERSION_1_8
    archiveClassifier = "downgraded-8"

    doLast {
        val jar = archiveFile.get().asFile
        val dir = temporaryDir.resolve("downgradeJar5")
        dir.mkdirs()

        copy {
            from(zipTree(jar))
            into(dir)
        }

        Retrofitter().run {
            retrofit(dir.toPath())
            //verify(dir.toPath())
        }

        JarOutputStream(archiveFile.get().asFile.outputStream()).use { jos ->
            jos.setLevel(Deflater.BEST_COMPRESSION)
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    jos.putNextEntry(JarEntry(file.relativeTo(dir).toPath().toString()))
                    file.inputStream().use { it.copyTo(jos) }
                    jos.closeEntry()
                }
            }
            jos.flush()
            jos.finish()
        }
    }
}

jvmdg.defaultShadeTask {
    enabled = false
}

val downgradeJar8 = jvmdg.defaultTask

val downgradeJar17 = tasks.register<DowngradeJar>("downgradeJar17") {
    dependsOn(tasks.jar)
    downgradeTo = JavaVersion.VERSION_17
    inputFile = tasks.jar.get().archiveFile
    archiveClassifier = "downgraded-17"
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }

    finalizedBy(downgradeJar17, downgradeJar8)
}

val sourcesJar = tasks.getByName<Jar>("sourcesJar") {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }
}

tasks.assemble {
    dependsOn(tasks.jar, sourcesJar, tasks.check)
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "21"
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val tauPublishTask = tau.publishing.publish {
    dependsOn(tasks.assemble)

    useTauGradleVersioning()
    changelog = tau.versioning.commitChangeLog

    artifact {
        files(
            provider { tasks.jar.get().archiveFile }, 
            provider { downgradeJar17.get().archiveFile }, 
            provider { downgradeJar8.get().archiveFile }, 
            sourcesJar.archiveFile,
            provider {
                @Suppress("UNCHECKED_CAST")
                (tasks["javadocJar"] as TaskProvider<AbstractArchiveTask>).get().archiveFile
            }
        )
    }
    
    github {
        supportAllChannels()

        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "Nolij/ZSON"
        tagName = tau.versioning.releaseTag
    }
}

if (!isRebuild) {
    tasks.publish {
        dependsOn(tauPublishTask)
    }
}

publishing {
    repositories {
        if (!System.getenv("local_maven_url").isNullOrEmpty())
            maven(System.getenv("local_maven_url"))
        
        if (!isRebuild) {
            maven("https://maven.taumc.org/releases") {
                credentials {
                    username = System.getenv("MAVEN_USERNAME")
                    password = System.getenv("MAVEN_SECRET")
                }
            }
        }
    }

    publications {
        create<MavenPublication>(property("project_name")) {
            artifact(tasks.jar)
            artifact(downgradeJar17) // java 17
            artifact(downgradeJar8) // java 8
            artifact(sourcesJar)
            artifact(tasks["javadocJar"])
        }
    }
}

tasks.withType<AbstractPublishToMaven> {
    dependsOn(tasks.assemble, tasks.check)
}