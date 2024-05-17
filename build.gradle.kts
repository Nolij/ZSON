plugins {
    idea
    java
    `maven-publish`
}

operator fun String.invoke(): String = rootProject.properties[this] as? String ?: error("Property $this not found")

group = "maven_group"()
version = "project_version"()
base.archivesName = rootProject.name.lowercase()

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
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.jar)
            artifactId = base.archivesName.get()
        }
    }
    repositories {
        mavenLocal()
    }
}