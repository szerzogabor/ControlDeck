import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // See :domain's build.gradle.kts comment — must match :app's JVM target.
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter.api)
    testImplementation(libs.junit5.jupiter.params)
    testRuntimeOnly(libs.junit5.jupiter.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    timeout.set(Duration.ofMinutes(5))
}
