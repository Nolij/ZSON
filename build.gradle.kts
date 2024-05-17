import java.time.ZonedDateTime

plugins {
    id("idea")
    id("java")
    id("maven-publish")
    id("org.ajoberstar.grgit")
    id("com.github.breadmoirai.github-release")
}

operator fun String.invoke(): String = rootProject.properties[this] as? String ?: error("Property $this not found")

group = "maven_group"()
base.archivesName = "project_name"()

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
        val suffix = """\-(\w+)\.\d+$""".toRegex().find(tagName)?.groupValues?.get(1)
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

version = versionString
println("ZSON Version: $versionString")

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:${"jetbrains_annotations_version"()}")
    compileOnly("com.pkware.jabel:jabel-javac-plugin:${"jabel_version"()}", ::annotationProcessor)

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0-M1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    group = "build"

    archiveClassifier = "sources"
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }

    from(sourceSets.main.get().allSource) { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
}

tasks.assemble {
    dependsOn(tasks.jar, sourcesJar)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "17"
    options.release = 8
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

githubRelease {
    setToken(providers.environmentVariable("GITHUB_TOKEN"))
    setTagName(versionTagName)
    setTargetCommitish("master")
    setReleaseName(versionString)
    setReleaseAssets(tasks.jar.get().archiveFile, sourcesJar.get().archiveFile)
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
            artifact(tasks.jar)
            artifact(sourcesJar)
        }
    }
}

tasks.withType<AbstractPublishToMaven> {
    dependsOn(tasks.assemble, tasks.check)
}