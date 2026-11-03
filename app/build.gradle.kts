// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.winteriscoming.vpn"
    // 34 matches what is installed on the development machine (platforms;android-34,
    // build-tools;34.0.0), so a local build and a CI build target the same thing.
    // Bump both together, and update the SDK install step in ci-android.yml to match.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.winteriscoming.vpn"
        // wireguard-android supports API 21+; 24 keeps us clear of pre-N TLS quirks.
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The tunnel library ships native code for four ABIs (~25 MB debug APK);
            // a phone needs two. Debug keeps all of them so an x86_64 emulator works.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // The tunnel library uses Java 8+ APIs that need desugaring on older runtimes.
    // Documented requirement of com.wireguard.android:tunnel.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Where the app talks to the control plane. Override per build type or with
    // -PapiBaseUrl; production must be an https:// URL terminated by nginx/Caddy
    // (ADR-0009), because the server refuses plaintext when require_https is on.
    defaultConfig {
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${project.findProperty("apiBaseUrl") ?: "https://vpn.example.com"}\"",
        )
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // The official tunnel: reference-correct WireGuard, Apache-2.0. Embedding it
    // means every session also proves our server interoperates with the official
    // implementation - the project's biggest open risk (ADR-0012).
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    // NotificationCompat for the mandatory foreground-service notification.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Explicit, not transitive: TunnelEngine and the onboarding repository use
    // coroutines/Flow directly, so relying on lifecycle-runtime-ktx to drag them in
    // would break the moment that dependency changes.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")  // android org.json is stubbed on the JVM

    // Instrumented UI tests. They cannot run on the development machine (no device,
    // and the emulator needs a hypervisor this box does not have), but they compile
    // here and run on any real device or CI emulator with connectedAndroidTest.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
