import org.objectweb.asm.tools.Retrofitter
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import java.time.ZonedDateTime
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.Deflater

plugins {
    id("idea")
    id("java")
    id("maven-publish")
    id("org.ajoberstar.grgit")
    id("com.github.breadmoirai.github-release")
    id("xyz.wagyourtail.jvmdowngrader")
}

operator fun String.invoke(): String = rootProject.properties[this] as? String ?: error("Property $this not found")

group = "maven_group"()
base.archivesName = "project_name"()

//region Git
enum class ReleaseChannel(val suffix: String? = null) {
    DEV_BUILD("dev"),
    RELEASE,
}

val headDateTime: ZonedDateTime = grgit.head().dateTime

val branchName = grgit.branch.current().name!!
val releaseTagPrefix = "release/"

val releaseTags = grgit.tag.list()
    .filter { tag -> tag.name.startsWith(releaseTagPrefix) }
    .sortedWith { tag1, tag2 ->
        if (tag1.commit.dateTime == tag2.commit.dateTime)
            if (tag1.name.length != tag2.name.length)
                return@sortedWith tag1.name.length.compareTo(tag2.name.length)
            else
                return@sortedWith tag1.name.compareTo(tag2.name)
        else
            return@sortedWith tag2.commit.dateTime.compareTo(tag1.commit.dateTime)
    }
    .dropWhile { tag -> tag.commit.dateTime > headDateTime }

val isExternalCI = (rootProject.properties["external_publish"] as String?).toBoolean()
val isRelease = rootProject.hasProperty("release_channel") || isExternalCI
val releaseIncrement = if (isExternalCI) 0 else 1
val releaseChannel: ReleaseChannel =
    if (isExternalCI) {
        val tagName = releaseTags.first().name
        val suffix = """-(\w+)\.\d+$""".toRegex().find(tagName)?.groupValues?.get(1)
        if (suffix != null)
            ReleaseChannel.values().find { channel -> channel.suffix == suffix }!!
        else
            ReleaseChannel.RELEASE
    } else {
        if (isRelease)
            ReleaseChannel.valueOf("release_channel"())
        else
            ReleaseChannel.DEV_BUILD
    }

println("Release Channel: $releaseChannel")

val minorVersion = "project_version"()
val minorTagPrefix = "${releaseTagPrefix}${minorVersion}."

val patchHistory = releaseTags
    .map { tag -> tag.name }
    .filter { name -> name.startsWith(minorTagPrefix) }
    .map { name -> name.substring(minorTagPrefix.length) }

val maxPatch = patchHistory.maxOfOrNull { it.substringBefore('-').toInt() }
val patch =
    maxPatch?.plus(
        if (patchHistory.contains(maxPatch.toString()))
            releaseIncrement
        else
            0
    ) ?: 0
var patchAndSuffix = patch.toString()

if (releaseChannel.suffix != null) {
    patchAndSuffix += "-${releaseChannel.suffix}"
}

val versionString = "${minorVersion}.${patchAndSuffix}"
val versionTagName = "${releaseTagPrefix}${versionString}"

//endregion

version = versionString
println("ZSON Version: $versionString")

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
    compileOnly("org.jetbrains:annotations:${"jetbrains_annotations_version"()}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0-M1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.javadoc {
    val options = options as StandardJavadocDocletOptions
    options.addBooleanOption("Xdoclint:none", true)
}

tasks.downgradeJar {
    dependsOn(tasks.jar)
    downgradeTo = JavaVersion.VERSION_1_8
    archiveClassifier = "downgraded-8"
}

val downgradeJar17 = tasks.register<DowngradeJar>("downgradeJar17") {
    dependsOn(tasks.jar)
    downgradeTo = JavaVersion.VERSION_17
    inputFile = tasks.jar.get().archiveFile
    archiveClassifier = "downgraded-17"
}

val downgradeJar5 = tasks.register<Jar>("downgradeJar5") {
    // JVMDG doesn't support java 5, so we have to do it manually using an internal ASM tool
    dependsOn(tasks.downgradeJar)
    group = "jvmdowngrader"
    archiveClassifier = "downgraded-5"
    outputs.upToDateWhen { false }

    doLast {
        val jar = tasks.downgradeJar.get().archiveFile
        val dir = temporaryDir.resolve("downgradeJar5")
        dir.mkdirs()

        copy {
            from(zipTree(jar))
            into(dir)
        }

        Retrofitter().retrofit(dir.toPath())

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

tasks.jar {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }

    finalizedBy(tasks.downgradeJar, downgradeJar17)
}

val sourcesJar: Jar = tasks.withType<Jar>()["sourcesJar"].apply {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }
}

tasks.assemble {
    dependsOn(tasks.jar, sourcesJar, downgradeJar17, downgradeJar5)
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

githubRelease {
    setToken(providers.environmentVariable("GITHUB_TOKEN"))
    setTagName(versionTagName)
    setTargetCommitish("master")
    setReleaseName(versionString)
    setReleaseAssets(tasks.jar.get().archiveFile, sourcesJar.archiveFile)
}

tasks.githubRelease {
    dependsOn(tasks.assemble, tasks.check)
}

publishing {
    repositories {
        if (!System.getenv("local_maven_url").isNullOrEmpty())
            maven(System.getenv("local_maven_url"))
    }

    publications {
        create<MavenPublication>("project_name"()) {
            artifact(tasks.jar) // java 21
            artifact(downgradeJar17) // java 17
            artifact(tasks.downgradeJar) // java 8
            artifact(downgradeJar5) // java 5
            artifact(sourcesJar) // java 21 sources
            artifact(tasks["javadocJar"])
        }
    }
}

tasks.withType<AbstractPublishToMaven> {
    dependsOn(tasks.assemble, tasks.check)
}