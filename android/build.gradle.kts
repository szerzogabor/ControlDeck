// NOTE: the Android Gradle Plugin / Kotlin-Android plugins are deliberately
// NOT declared here (even with apply false). Declaring them in the root
// project's plugins{} block forces Gradle to resolve them from
// google() during configuration of *every* task, including
// `:domain:test`/`:protocol:test` which have no Android dependency at all.
// Since :domain and :protocol are plain Kotlin/JVM modules (see their
// build.gradle.kts), keeping the Android plugin declarations local to
// `app/build.gradle.kts` (with an explicit version) lets those two modules
// build and test with zero Android SDK / no google() connectivity, using
// `gradle :domain:test :protocol:test --configure-on-demand`.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    group = "com.controlldeck"
    version = "0.1.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
