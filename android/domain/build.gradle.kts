import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // Must match :app's JVM target (compileOptions/kotlinOptions = 17) since
    // :app compiles against this module's jar directly (project dependency).
    // A mismatch here produces "class file has wrong version" errors from
    // kapt/javac when :app is on an older JVM target than this module.
    jvmToolchain(17)
}

dependencies {
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
