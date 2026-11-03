// Copyright 2026 The vpn authors. SPDX-License-Identifier: Apache-2.0
plugins {
    // 8.5.2 pairs with Gradle 8.7, which is the distribution already cached on the
    // development machine - so a local build and a CI build use identical versions
    // and no download is needed to reproduce a failure.
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
