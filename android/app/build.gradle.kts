// All versions are declared once, in the root build.gradle.kts (apply
// false), via the version catalog. See the comment there for why.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
}

// CI (release.yml) passes -PversionName=<x.y.z> -PversionCode=<n>; local/dev builds fall back to defaults.
val releaseVersionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
val releaseVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// Release signing is injected via Gradle properties from GitHub Secrets (see release.yml /
// README "Android signing"). No keystore/password/key is ever committed. When these are absent
// (e.g. a plain PR build), the release build type is simply left unsigned.
val signingStoreFile = (project.findProperty("android.injected.signing.store.file") as String?)
val signingStorePassword = (project.findProperty("android.injected.signing.store.password") as String?)
val signingKeyAlias = (project.findProperty("android.injected.signing.key.alias") as String?)
val signingKeyPassword = (project.findProperty("android.injected.signing.key.password") as String?)

android {
    namespace = "com.controlldeck.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.controlldeck.app"
        minSdk = 26
        targetSdk = 34
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":protocol"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)

    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(libs.coil.compose)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter.api)
    testImplementation(libs.junit5.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit5.jupiter.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
