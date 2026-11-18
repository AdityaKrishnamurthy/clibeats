---
last_mapped_commit: a77dc37a72fa6a498fb1ac17a68d814fbe3770cb
last_mapped_at: 2026-11-19
---
# Technology Stack

**Analysis Date:** 2026-11-19

## Languages

**Primary:**
- Kotlin 2.0.21 — all application code under `app/src/main/java/com/clibeats/`; Android Gradle Plugin 8.5.2; Kotlin Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) and `kotlinx-serialization` plugin enabled in `app/build.gradle.kts`.
- Java 17 language level — `compileOptions` / `jvmTarget` set to JVM 17 in `app/build.gradle.kts:57-78`.

**Secondary:**
- XML — `app/src/main/AndroidManifest.xml`, `app/src/main/res/**` (drawables, fonts, backup/data-extraction rules).
- Gradle Kotlin DSL — `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`.
- TOML — version catalog `gradle/libs.versions.toml`.
- JSON — Room schema export at `app/schemas/com.clibeats.data.local.CliBeatsDatabase/`, kotlinx.serialization DTOs in `app/src/main/java/com/clibeats/data/provider/dto/`.

## Runtime

**Environment:**
- Android 8.0+ (`minSdk = 26`), `targetSdk = 34`, `compileSdk = 34` (`app/build.gradle.kts:17-22`).
- Application ID / namespace: `com.clibeats` (`app/build.gradle.kts:16,20`).
- JVM 17 is required to build; CI uses Temurin JDK 17 (`.github/workflows/ci.yml:23-27`, `.github/workflows/release.yml:32-36`).
- `gradle.properties` sets `android.suppressUnsupportedCompileSdk=35` for libraries compiled against API 35.

**Package Manager:**
- Gradle 8.9 via wrapper (`gradle/wrapper/gradle-wrapper.properties`).
- Android Gradle Plugin 8.5.2 (`gradle/libs.versions.toml:2`).
- Dependency repositories: `google()`, `mavenCentral()`, `https://jitpack.io` (`settings.gradle.kts:8-15`); JitPack is required for NewPipeExtractor.
- Lockfile: not present. Versions are centralized in `gradle/libs.versions.toml`.
- `local.properties` (SDK path) is gitignored (`.gitignore`).

## Frameworks

**Core:**
- Jetpack Compose (BOM `2024.09.03`) — Material 3 UI, `androidx.compose.ui`, `material3`, `ui-tooling-preview`, `material-icons-extended` (`gradle/libs.versions.toml:38-53`).
- `androidx.compose.material3:material3-adaptive-navigation-suite` 1.3.1 — navigation/layout (`app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt`).
- `androidx.activity:activity-compose` 1.9.2, `androidx.core:core-ktx` 1.13.1, `androidx.lifecycle:lifecycle-runtime-ktx` 2.8.6.
- Hilt 2.51.1 + `hilt-navigation-compose` 1.2.0, processed with KSP 2.0.21-1.0.27 — DI modules in `app/src/main/java/com/clibeats/di/`, entry point `app/src/main/java/com/clibeats/CLIBeatsApp.kt`.
- Room 2.6.1 (runtime, ktx, compiler via KSP) — database `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt`, DAOs in `app/src/main/java/com/clibeats/data/local/dao/`, schema export configured in `app/build.gradle.kts:80-82`.
- DataStore Preferences 1.1.1 — settings and playback state (`app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`, wiring in `app/src/main/java/com/clibeats/di/StorageModule.kt`).
- `androidx.security:security-crypto` 1.1.0-alpha06 — `EncryptedSharedPreferences` with AES256-GCM MasterKey (`app/src/main/java/com/clibeats/di/StorageModule.kt:34-50`).
- AndroidX Media3 1.4.1 (`media3-exoplayer`, `media3-session`, `media3-common`) — playback engine, `MediaLibraryService`, notifications, `SimpleCache` (`app/src/main/java/com/clibeats/di/PlaybackModule.kt`, `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`, `app/src/main/java/com/clibeats/data/cache/MediaCaches.kt`).
- `androidx.media:media` 1.7.0 — declared in `app/build.gradle.kts:122` but no `androidx.media.` package imports exist in main sources (only `androidx.media3`); effectively unused.
- Retrofit 2.11.0 + `retrofit2-kotlinx-serialization-converter` 1.0.0 + kotlinx.serialization JSON 1.7.1 — typed provider APIs (`app/src/main/java/com/clibeats/di/NetworkModule.kt`).
- OkHttp 4.12.0 + `logging-interceptor` (debug-only BASIC logging) — shared HTTP client, 20 s read / 15 s connect timeouts (`app/src/main/java/com/clibeats/di/NetworkModule.kt:45-61`).
- Coil 2.7.0 (`coil-compose`) — artwork loading with custom memory/disk caches (`app/src/main/java/com/clibeats/di/ImageLoaderModule.kt`).
- NewPipeExtractor `v0.27.1` (`com.github.TeamNewPipe:NewPipeExtractor`, JitPack) — primary YouTube stream extraction (`app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`).
- Kotlin coroutines — used throughout; version is not pinned directly in the catalog, only `kotlinx-coroutines-test` 1.8.1 for tests.

**Testing:**
- JUnit 4.13.2, Mockito Kotlin 5.4.0, Mockito Core 5.12.0, kotlinx-coroutines-test 1.8.1.
- Room testing 2.6.1 (instrumented DAO tests in `app/src/androidTest/java/com/clibeats/data/local/dao/`).
- Media3 `media3-test-utils` 1.4.1, OkHttp `mockwebserver` 4.12.0.
- Compose UI test (`ui-test-junit4`, manifest via debug), Espresso 3.6.1, `androidx.test.ext:junit` 1.2.1.
- Paparazzi 1.3.4 — screenshot tests in `app/src/test/java/com/clibeats/theme/`.
- Google Truth is used extensively (`com.google.common.truth.Truth`, 34 imports under `app/src/test/`) but is not declared in `gradle/libs.versions.toml`; it resolves transitively (e.g. via `room-testing`/AndroidX test). Consider declaring it explicitly.

**Build/Dev:**
- ktlint Gradle plugin 12.1.1 (`org.jlleitschuh.gradle.ktlint`) — `ktlintCheck`.
- detekt 1.23.6 + `detekt-formatting`; config `config/detekt/detekt.yml`.
- Android Lint with `abortOnError = true`, `checkDependencies = true` (`app/build.gradle.kts:67-71`).
- Paparazzi 1.3.4 (applied only in `app/build.gradle.kts`).
- Local quality gate script: `scripts/check-quality-gates.sh` (ktlint → detekt → lintDebug → testDebugUnitTest).

## Key Dependencies

**Critical:**
- `com.github.TeamNewPipe:NewPipeExtractor:v0.27.1` — primary YouTube Music stream URL resolver. GPLv3; the distributed APK inherits GPLv3 (`docs/LICENSES.md`).
- AndroidX Media3 1.4.1 — streaming + cached playback, background `MediaLibraryService`, media notification and resume card.
- Hilt 2.51.1 — dependency graph for all repositories, providers, player, caches (`app/src/main/java/com/clibeats/di/`).
- Room 2.6.1 — library, playlists, queue, history, cache index, saved albums/artists, liked songs.
- Retrofit + OkHttp + kotlinx.serialization — all four online provider integrations.
- Coil — artwork fetch through the shared OkHttp client with a resizable disk cache.
- `androidx.security:security-crypto` — encrypted storage of the reserved auth token.

**Infrastructure:**
- JitPack repository (`settings.gradle.kts:13`) — exclusively needed for NewPipeExtractor.
- Guava is available transitively through Media3 and used directly in `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt` (`ImmutableList`, `ListenableFuture`) without an explicit dependency declaration.
- JetBrains Mono fonts bundled as resources (`app/src/main/res/font/`).

## Configuration

**Environment:**
- Gradle build properties (`app/build.gradle.kts:23-29`):
  - `-PversionName` (default `2.0.0`) and `-PversionCode` (default `20000`).
  - `-PJAMENDO_CLIENT_ID` → `BuildConfig.JAMENDO_CLIENT_ID`; empty by default, Jamendo provider returns a configuration error until set.
- Release signing environment variables (`app/build.gradle.kts:32-44`): `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; missing keystore falls back to debug signing.
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` are required by unit-test tasks and Paparazzi; fallback is `C:\Android\Sdk` (`app/build.gradle.kts:90-99`).
- Runtime secrets: none hardcoded. No backend URL, API key, or token is compiled in except the public Audius `app_name=clibeats` and YouTube client identifiers.
- No `.env`-style configuration is used for the app; configuration is Gradle properties + environment variables + GitHub secrets.

**Build:**
- `app/build.gradle.kts` — `compose = true`, `buildConfig = true`, KSP `room.schemaLocation`, detekt, ktlint, Paparazzi.
- `config/detekt/detekt.yml` — detekt ruleset.
- `gradle/libs.versions.toml` — single source of dependency/plugin versions.
- `.github/workflows/ci.yml` — build/lint/test pipeline.
- `.github/workflows/release.yml` — tag-driven signed release.

## Platform Requirements

**Development:**
- JDK 17 (Temurin in CI), Android SDK Platform 34 + build-tools, `ANDROID_HOME` exported.
- Gradle 8.9 (wrapper handles download, `networkTimeout=10000`).
- Linux, macOS, or Windows (gradlew.bat present; `CLIBeats` documented for both shells in `README.md`).
- Screenshot tests require `./gradlew verifyPaparazziDebug` and a valid SDK location.

**Production:**
- Android 8.0 (API 26) or newer device/tablet.
- Distribution is APK-only through GitHub Releases; no Play Store or other store listing pipeline exists.
- Runtime permissions requested: `INTERNET`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (`app/src/main/AndroidManifest.xml:4-8`). No storage/media permissions — local library access uses SAF.
- Foreground `mediaPlayback` service plus `MediaButtonReceiver` declared (`app/src/main/AndroidManifest.xml:30-49`).

---

*Stack analysis: 2026-11-19*
