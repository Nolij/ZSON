plugins {
    idea
    java
}

operator fun String.invoke(): String = rootProject.properties[this] as? String ?: error("Property $this not found")

group = "maven_group"()
version = "project_version"()
base.archivesName = "zson"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:${"jetbrains_annotations_version"()}")
    "com.pkware.jabel:jabel-javac-plugin:${"jabel_version"()}".also {
        annotationProcessor(it)
        compileOnly(it)
    }
}

tasks.register<JavaExec>("testRun") {
    mainClass = "Main"
    classpath(sourceSets["test"].runtimeClasspath)
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