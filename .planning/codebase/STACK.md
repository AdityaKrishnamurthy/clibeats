---
last_mapped_commit: 916a56379cbb0bf07eb302edd4489c7633418417
last_mapped_at: 2026-11-17
---
# Technology Stack

**Analysis Date:** 2026-11-17

## Languages

**Primary:**
- Kotlin 2.0.21 — 100% of application source (131 `.kt` files under `app/src/main/java/com/clibeats/`); zero Java sources

**Secondary:**
- Kotlin DSL for build scripts — `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`
- XML — `app/src/main/AndroidManifest.xml`, resources under `app/src/main/res/` (`xml/data_extraction_rules.xml`, drawables, mipmaps)
- Shell — `scripts/check-quality-gates.sh`; YAML — `.github/workflows/ci.yml`; YAML — `config/detekt/detekt.yml`

## Runtime

**Environment:**
- Android, `minSdk = 26` (Android 8.0), `compileSdk = 34`, `targetSdk = 34` — declared in `app/build.gradle.kts:17-22`
- JDK 17 (`sourceCompatibility`/`targetCompatibility = VERSION_17`, `jvmTarget = JVM_17`)
- Gradle 8.9 (wrapper) — `gradle/wrapper/gradle-wrapper.properties`
- Android Gradle Plugin 8.5.2 — `gradle/libs.versions.toml`

**Package Manager:**
- Gradle with version catalog at `gradle/libs.versions.toml` (single source of truth for all versions)
- Wrapper committed: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Lockfile: not present (no `gradle.lockfile`, no dependency locking configured)
- Repositories: `google()`, `mavenCentral()`, `jitpack.io` (JitPack required for NewPipeExtractor) — `settings.gradle.kts`

## Frameworks

**Core:**
- Jetpack Compose (BOM `2024.09.03`) — all UI; `app/src/main/java/com/clibeats/presentation/`
- Compose Material 3 — `compose-material3` via BOM; `material3-adaptive-navigation-suite` 1.3.1 for adaptive navigation (`app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt`)
- Kotlin Compose Compiler plugin 2.0.21 (`org.jetbrains.kotlin.plugin.compose`)
- Hilt 2.51.1 / Dagger — DI, via KSP; modules in `app/src/main/java/com/clibeats/di/` (11 modules)
- KSP 2.0.21-1.0.27 — annotation processing for Hilt and Room (no KAPT)
- Room 2.6.1 — local SQLite persistence, schema export to `app/schemas/com.clibeats.data.local.CliBeatsDatabase/` (versions 1-3)
- DataStore Preferences 1.1.1 — non-sensitive user settings (`clibeats_prefs`)
- AndroidX Security Crypto 1.1.0-alpha06 — EncryptedSharedPreferences for sensitive values (`clibeats_secure_prefs`); legacy API is deprecated, migration to Tink noted in `app/src/main/java/com/clibeats/di/StorageModule.kt`
- AndroidX Media3 1.4.1 (`media3-exoplayer`, `media3-session`, `media3-common`) + `androidx.media` 1.7.0 — playback engine, `MediaSessionService`, notification controls
- Retrofit 2.11.0 + OkHttp 4.12.0 — HTTP clients for all music providers
- kotlinx.serialization JSON 1.7.1 with `retrofit2-kotlinx-serialization-converter` 1.0.0
- Coil 2.7.0 (`coil-compose`) — artwork loading with custom `ImageLoader` (memory 25%, disk 2%)
- NewPipeExtractor `v0.26.4` (JitPack `com.github.TeamNewPipe:NewPipeExtractor`) — YouTube stream extraction fallback

**Testing:**
- JUnit 4.13.2 — all unit tests
- Mockito Kotlin 5.4.0 + mockito-core 5.12.0
- kotlinx-coroutines-test 1.8.1
- OkHttp MockWebServer 4.12.0
- Room testing 2.6.1 (JVM + instrumented)
- Compose UI Test JUnit4 (via Compose BOM) + `ui-test-manifest` (debug)
- Espresso 3.6.1, AndroidX Test Ext JUnit 1.2.1 (instrumented, `app/src/androidTest/`)
- Paparazzi 1.3.4 (`app.cash.paparazzi`) — screenshot regression tests, snapshots in `app/src/test/snapshots/images/`
- Media3 test-utils 1.4.1
- No Truth, no Robolectric, no Turbine, no JUnit 5 (despite `docs/LICENSES.md` mentioning Truth)

**Build/Dev:**
- Detekt 1.23.6 with `detekt-formatting` plugin; config `config/detekt/detekt.yml` (custom `ForbiddenImport` rule blocking `com.clibeats.data.*` imports from presentation)
- ktlint Gradle plugin 12.1.1 (`org.jlleitschuh.gradle.ktlint`)
- Android Lint with `abortOnError = true`, `checkDependencies = true`, `warningsAsErrors = false`
- GitHub Actions CI — `.github/workflows/ci.yml` (JDK 17 Temurin, ktlintCheck, detekt, lintDebug, assembleDebug, testDebugUnitTest, artifact upload)
- Local gate script — `scripts/check-quality-gates.sh`

## Key Dependencies

**Critical:**
- `androidx.media3:media3-exoplayer:1.4.1` — single `ExoPlayer` singleton built in `app/src/main/java/com/clibeats/di/PlaybackModule.kt`
- `androidx.media3:media3-session:1.4.1` — `PlaybackService` extends `MediaSessionService` (`app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`)
- `androidx.room:room-runtime:2.6.1` + `room-compiler` (KSP) — 9 entities, 8 DAOs in `app/src/main/java/com/clibeats/data/local/`
- `com.google.dagger:hilt-android:2.51.1` + `hilt-compiler` (KSP) — every ViewModel/repository uses `@Inject`/`@HiltViewModel`
- `com.github.TeamNewPipe:NewPipeExtractor:v0.26.4` — used only by `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`; NewPipe is initialized lazily with an OkHttp-backed `Downloader`

**Infrastructure:**
- `okhttp3:okhttp:4.12.0` + `logging-interceptor` (debug-only BODY logging in `NetworkModule`)
- `retrofit2:retrofit:2.11.0` + `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0`
- `androidx.datastore:datastore-preferences:1.1.1`
- `androidx.security:security-crypto:1.1.0-alpha06`
- `io.coil-kt:coil-compose:2.7.0`

## Configuration

**Environment:**
- `JAMENDO_CLIENT_ID` — optional Gradle property read in `app/build.gradle.kts:26-28` and exposed as `BuildConfig.JAMENDO_CLIENT_ID`; empty by default. Injected into `JamendoMusicProvider` via `@Named("jamendo_client_id")` in `app/src/main/java/com/clibeats/di/ProviderModule.kt`. Build with `./gradlew -PJAMENDO_CLIENT_ID=<id> assembleDebug`.
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` — read by the `Test` task configuration in `app/build.gradle.kts` (falls back to `C:\Android\Sdk`)
- No `.env` files present. No `local.properties` committed (ignored by `.gitignore`).
- Gradle properties — `gradle.properties`: `org.gradle.jvmargs=-Xmx2048m`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `android.suppressUnsupportedCompileSdk=35`

**Build:**
- Root plugin declarations: `build.gradle.kts`; module config: `app/build.gradle.kts`
- `buildFeatures { compose = true; buildConfig = true }`
- Room schema output: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`
- Release build: `isMinifyEnabled = false`, ProGuard rules in `app/proguard-rules.pro`, `signingConfig = signingConfigs.getByName("debug")` (no release keystore configured)
- Detekt config: `config/detekt/detekt.yml`

## Platform Requirements

**Development:**
- JDK 17
- Android SDK Platform 34
- Gradle 8.5+ (wrapper 8.9)
- Bash for `scripts/check-quality-gates.sh`; CI runs on `ubuntu-latest`
- Paparazzi screenshots render on the JVM (no emulator required); instrumented DAO tests in `app/src/androidTest/java/com/clibeats/data/local/dao/` require a device/emulator

**Production:**
- Android 8.0 (API 26) or newer device
- Distributed as an APK (`app/build/outputs/apk/`); no Play Store metadata, signing, or store-publishing integration in the repo
- No backend service required — all stream resolution happens on-device

---

*Stack analysis: 2026-11-17*
