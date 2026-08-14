// All plugins used anywhere in this build are declared here (apply false)
// via the version catalog, and referenced without a version in each
// module's own build.gradle.kts. This is the standard, robust pattern for
// Kotlin multi-module builds mixing JVM and Android modules: several of
// these plugin IDs (jvm/android/serialization/kapt) are provided by the
// same underlying kotlin-gradle-plugin artifact, and resolving/applying
// them inconsistently across modules (e.g. a module re-declaring an
// explicit version, or a module applying kotlin-android before
// com.android.application has made its API classes visible on that same
// shared plugin classpath) produces confusing Gradle plugin-resolution
// errors. A single root-level declaration avoids that entirely.
//
// Trade-off: because the root project is always configured, `:domain:test`
// / `:protocol:test` now also require resolving the Android Gradle Plugin
// from google() at configuration time, even though neither module has an
// Android dependency. `--configure-on-demand` (see gradle.properties)
// still prevents `:app`'s own build script from being evaluated for those
// tasks, but the root-level plugin resolution itself needs network access
// to google()/mavenCentral, same as any other Android Gradle build.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.android.application) apply false
}

allprojects {
    group = "com.controlldeck"
    version = "0.1.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
