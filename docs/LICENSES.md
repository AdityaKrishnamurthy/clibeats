# CLIBeats — Open Source Licenses & Attributions

CLIBeats is built using open source software libraries. Below is the inventory of third-party dependencies, license types, and attributions:

## 1. AndroidX & Jetpack Libraries
- **AndroidX Core KTX / Lifecycle / Media3 / Room / DataStore**: Apache License 2.0
  - Copyright (c) The Android Open Source Project

## 2. Kotlin & Coroutines
- **Kotlin Standard Library / KotlinX Coroutines / Serialization**: Apache License 2.0
  - Copyright 2010-2024 JetBrains s.r.o.

## 3. Dependency Injection & Networking
- **Google Hilt / Dagger**: Apache License 2.0
  - Copyright 2012 The Dagger Authors
- **Square OkHttp / Retrofit**: Apache License 2.0
  - Copyright 2019 Square, Inc.

## 4. UI & Image Loading
- **Coil**: Apache License 2.0
  - Copyright 2023 Coil Contributors

## 5. Media & Extraction
- **NewPipeExtractor** (YouTube stream extraction): GNU General Public License v3.0
  - Copyright (c) Team NewPipe — https://github.com/TeamNewPipe/NewPipeExtractor
  - Note: because the release APK bundles this GPLv3 library, the distributed APK as a whole is subject to
    the GPLv3. CLIBeats' own source code remains MIT-licensed (MIT is GPL-compatible).
- **Guava** (used by Media3): Apache License 2.0

## 6. Fonts
- **JetBrains Mono**: SIL Open Font License 1.1
  - Copyright 2020 The JetBrains Mono Project Authors

## 7. Testing
- **Google Truth / JUnit 4 / Mockito / Paparazzi / Timber**: Apache License 2.0 / EPL 1.0 / MIT
