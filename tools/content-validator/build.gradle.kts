plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test-junit5"))
}

application {
    mainClass.set("com.vibecheck.validator.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    args(
        rootProject.layout.projectDirectory.dir("content/puzzles").asFile.absolutePath,
        "2026-01-01",
        "90"
    )
}
