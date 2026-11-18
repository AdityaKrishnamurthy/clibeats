---
last_mapped_commit: a77dc37a72fa6a498fb1ac17a68d814fbe3770cb
last_mapped_at: 2026-11-19
---
# External Integrations

**Analysis Date:** 2026-11-19

## APIs & External Services

**Music Catalog / Streaming:**
- **YouTube Music (unofficial InnerTube API)** — search and stream resolution for provider id `youtube_music` (`app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`).
  - SDK/Client: Retrofit interfaces `app/src/main/java/com/clibeats/data/provider/api/InnerTubeApi.kt` (POST `search`, POST `player`), DTOs `app/src/main/java/com/clibeats/data/provider/dto/SearchRequest.kt` and `app/src/main/java/com/clibeats/data/provider/dto/PlayerRequest.kt`.
  - Base URL: `https://music.youtube.com/youtubei/v1/` (`app/src/main/java/com/clibeats/di/NetworkModule.kt:30`).
  - Auth: none (no account/OAuth). Spoofed client identity instead: `app/src/main/java/com/clibeats/data/provider/api/InnerTubeHeaderInterceptor.kt` sets `User-Agent`, `Origin`, `Referer`, `Accept-Language`; `app/src/main/java/com/clibeats/data/provider/youtube/YouTubeClientStrategy.kt` defines the fallback chain `ANDROID_MUSIC → IOS → WEB_REMIX → TVHTML5`.
  - Bot-check mitigation: `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt` loads `https://www.youtube.com/embed/<videoId>` in a JS-enabled `WebView` and polls `ytcfg` for `PO_TOKEN`/`VISITOR_DATA` (10 s timeout, 12 h cache).
  - Primary stream extraction: NewPipeExtractor v0.27.1 via `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`, fetching `https://www.youtube.com/watch?v=<id>` and selecting the highest-bitrate audio stream. Fallback parsing: `app/src/main/java/com/clibeats/data/provider/youtube/StreamUrlDeobfuscator.kt` (handles `cipher`/`signatureCipher`).
  - Stream hosts: `*.googlevideo.com`; URLs expire (`expire=` query param) and are cached in memory only by `app/src/main/java/com/clibeats/data/provider/youtube/StreamCacheManager.kt` with a 60 s safety margin.
- **Audius** — open music catalog for provider id `audius` (`app/src/main/java/com/clibeats/data/provider/AudiusMusicProvider.kt`).
  - SDK/Client: Retrofit `app/src/main/java/com/clibeats/data/provider/api/AudiusApi.kt`; DTOs `app/src/main/java/com/clibeats/data/provider/dto/AudiusDtos.kt`; mapper `app/src/main/java/com/clibeats/data/provider/mapper/AudiusMapper.kt`.
  - Base URL: `https://discoveryprovider.audius.co/v1/` (`AudiusApi.BASE_URL`).
  - Endpoints used: `tracks/search`, `tracks/trending`, `tracks/{trackId}`, and `tracks/{trackId}/stream`.
  - Auth: none — public read API with identifying query param `app_name=clibeats` (`AudiusApi.DEFAULT_APP_NAME`). Streams use the `/stream` endpoint (HTTP 302 → audio) rather than the signed embedded URL.
- **Internet Archive** — public audio catalog for provider id `internet_archive` (`app/src/main/java/com/clibeats/data/provider/InternetArchiveMusicProvider.kt`).
  - SDK/Client: Retrofit `app/src/main/java/com/clibeats/data/provider/api/InternetArchiveApi.kt`; DTOs `app/src/main/java/com/clibeats/data/provider/dto/InternetArchiveDtos.kt`; mapper `app/src/main/java/com/clibeats/data/provider/mapper/InternetArchiveMapper.kt`.
  - Base URL: `https://archive.org/` (`app/src/main/java/com/clibeats/di/NetworkModule.kt:28`).
  - Endpoints: `advancedsearch.php` (Lucene query, JSON output) and `metadata/{identifier}`; playable audio is fetched from `https://archive.org/download/{identifier}/{file}` (302 → mirror, HTTP Range/206). Artwork comes from `https://archive.org/services/img/{identifier}`.
  - Auth: none required.
- **Jamendo** — Creative Commons catalog for provider id `jamendo` (`app/src/main/java/com/clibeats/data/provider/JamendoMusicProvider.kt`).
  - SDK/Client: Retrofit `app/src/main/java/com/clibeats/data/provider/api/JamendoApi.kt` (`tracks/`); DTOs `app/src/main/java/com/clibeats/data/provider/dto/JamendoDtos.kt`; mapper `app/src/main/java/com/clibeats/data/provider/mapper/JamendoMapper.kt`.
  - Base URL: `https://api.jamendo.com/v3.0/` (`app/src/main/java/com/clibeats/di/NetworkModule.kt:29`).
  - Auth: free `client_id` passed as a query parameter. Supplied at build time via Gradle property `JAMENDO_CLIENT_ID` → `BuildConfig.JAMENDO_CLIENT_ID` → Hilt qualifier `jamendo_client_id` (`app/build.gradle.kts:27-29`, `app/src/main/java/com/clibeats/di/ProviderModule.kt:41-47`). Never hardcoded; provider returns a configuration error when blank.
- **Local device media (no remote service)** — provider id `local` (`app/src/main/java/com/clibeats/data/provider/LocalMusicProvider.kt`).
  - Access path: Storage Access Framework only. Folder pickers use `ActivityResultContracts.OpenDocumentTree()` and `takePersistableUriPermission` in `app/src/main/java/com/clibeats/presentation/settings/SettingsScreen.kt:83-87` and `app/src/main/java/com/clibeats/presentation/onboarding/OnboardingScreen.kt:61-64`.
  - Enumeration: `DocumentsContract.buildChildDocumentsUriUsingTree` recursion in `app/src/main/java/com/clibeats/data/scan/LocalMusicScanner.kt`.
  - Metadata: `android.media.MediaMetadataRetriever` tags + embedded artwork.
  - No `MediaStore` usage and no `READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE` permission — SAF grants are the only access.
  - Playlist files: M3U/M3U8/PLS parsed by `app/src/main/java/com/clibeats/data/scan/PlaylistFileParser.kt` and mirrored as read-only `localpl:` playlists.

**Artwork / Images:**
- Coil 2.7.0 loads provider artwork URLs through the shared OkHttp client (`app/src/main/java/com/clibeats/di/ImageLoaderModule.kt`), with a resizable disk cache at `cacheDir/image_cache` and a 25% memory cache. No third-party image CDN is configured.

**Playlist Exchange (user-chosen file destinations):**
- Export/import of `clibeats.json` files through the system document picker (`app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt`, codec `app/src/main/java/com/clibeats/data/playlist/CliBeatsFile.kt`). No cloud API integration; the destination URI is whatever the user selects (Downloads, Drive provider, etc.).

## Data Storage

**Databases:**
- SQLite via Room, database file `clibeats.db`, version 3 (`app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt`).
  - Connection/builder: `app/src/main/java/com/clibeats/di/DatabaseModule.kt`.
  - Entities: songs, playlists, playlist-song cross refs, history, cache index, queue, liked songs, saved albums, saved artists (`app/src/main/java/com/clibeats/data/local/entity/`).
  - Migrations `MIGRATION_1_2` (liked songs) and `MIGRATION_2_3` (saved albums/artists) defined inline; schemas exported to `app/schemas/com.clibeats.data.local.CliBeatsDatabase/`.

**File Storage:**
- `cacheDir/song_cache` — Media3 `SimpleCache` for streamed audio with an adjustable LRU evictor (`app/src/main/java/com/clibeats/data/cache/MediaCaches.kt:52-56`).
- `cacheDir/audio_cache` — explicit download cache keyed in Room `cache_index` (`app/src/main/java/com/clibeats/data/cache/CacheManager.kt:23-26`, `app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt`).
- `cacheDir/image_cache` — Coil artwork disk cache (`app/src/main/java/com/clibeats/di/ImageLoaderModule.kt:29-36`).
- `filesDir/local_artwork` — embedded album art extracted from local files (`app/src/main/java/com/clibeats/data/scan/LocalMusicScanner.kt:244-253`).
- User music folder — read-only through persisted SAF tree URIs; no writes.

**Caching:**
- In-memory YouTube stream URL cache with expiry (`app/src/main/java/com/clibeats/data/provider/youtube/StreamCacheManager.kt`).
- Default limits: songs 512 MB (`AppPreferences.cacheMaxMb`), artwork 128 MB (`AppPreferences.DEFAULT_ARTWORK_CACHE_MB`); user-adjustable in Settings.
- No Redis/memcached or server-side cache — all caching is on-device.

**Preferences:**
- DataStore file `clibeats_prefs` (`app/src/main/java/com/clibeats/di/StorageModule.kt:17`) holding provider selection, theme, cache limits, playback/queue restore state, onboarding flag and SAF tree URIs (`app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`).
- `EncryptedSharedPreferences` file `clibeats_secure_prefs` using Android Keystore AES256-GCM for the reserved `auth_token` key (`app/src/main/java/com/clibeats/di/StorageModule.kt:34-50`, `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:47-49,123-196`).
- Backup exclusions for both preference stores in `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`.

## Authentication & Identity

**Auth Provider:**
- None. There is no CLIBeats account, sign-in flow, backend, or OAuth client (`README.md` "Privacy & Legal"; ADR-010 in `docs/adr/ADR-010-beta-telemetry-privacy.md`).
  - Implementation: `AppPreferences` exposes `authToken`, `setAuthToken`, `clearAuthToken` backed by EncryptedSharedPreferences, and Settings can clear it (`app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt:185-188`), but no code path obtains a token. The slot is reserved for a future authenticated provider.
  - All online providers are accessed anonymously; YouTube requests rely on public client identity + PO token rather than user credentials.

## Monitoring & Observability

**Error Tracking:**
- None. No Crashlytics, Sentry, Bugsnag, Firebase, or analytics SDK is present in `gradle/libs.versions.toml`.
- `app/src/main/java/com/clibeats/telemetry/TimberCrashReporter.kt` and `app/src/main/java/com/clibeats/telemetry/TimberTelemetryTracker.kt` are named after Timber but write only to `android.util.Log` (`TimberTelemetryTracker.kt:12`); nothing leaves the device.

**Logs:**
- `android.util.Log` with a dedicated diagnostic channel in `app/src/main/java/com/clibeats/util/DiagnosticLogger.kt` (trace IDs and stream-resolution stages, tag `CliBeatsDiagnostic`).
- OkHttp `HttpLoggingInterceptor` at BASIC level in debug builds only (`app/src/main/java/com/clibeats/di/NetworkModule.kt:53-59`).
- Playback session diagnostics logged under tag `CLIBeatsSession` (`app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`).

## CI/CD & Deployment

**Hosting:**
- GitHub Releases only — signed APK `CLIBeats-vX.Y.Z.apk` plus `SHA256SUMS.txt` (`.github/workflows/release.yml:69-91`).
- No Play Store, F-Droid, Firebase App Distribution, or Fastlane configuration.

**CI Pipeline:**
- `.github/workflows/ci.yml` — on push/PR to `main`, `master`, `develop`: JDK 17 + `gradle/actions/setup-gradle`, then `ktlintCheck`, `detekt`, `lintDebug`, `assembleDebug`, `testDebugUnitTest`; uploads test/detekt/lint reports as artifacts.
- `.github/workflows/release.yml` — on `v*.*.*` tags: skips if the release already exists, decodes `KEYSTORE_BASE64` to a temporary JKS, runs `testDebugUnitTest`, builds `assembleRelease` with `-PversionName`/`-PversionCode` derived from the tag, produces SHA-256 sums, and publishes via `softprops/action-gh-release@v2`.
- Release versioning: tag `2.0.0` → versionCode `20000`, versionName `2.0.0` (`app/build.gradle.kts:23-25`, `.github/workflows/release.yml:64-68`).

## Environment Configuration

**Required env vars:**
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` — required for unit-test tasks and Paparazzi (`app/build.gradle.kts:90-99`).
- `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — release signing; optional locally (falls back to debug key) and injected by CI (`app/build.gradle.kts:32-44`, `.github/workflows/release.yml:57-68`).

**Optional configuration:**
- Gradle project property `JAMENDO_CLIENT_ID` — enables the Jamendo provider (`app/build.gradle.kts:27-29`).
- Gradle project properties `versionName`, `versionCode` — release override.

**Secrets location:**
- GitHub repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (`.github/workflows/release.yml:3-5`).
- Locally, signing material is supplied through environment variables; `*.jks`/`*.keystore` files are gitignored (`.gitignore`).
- No application secret keystore, `.env` file, or token is committed; the only encrypted runtime store is `clibeats_secure_prefs`.

**Network security:**
- All configured endpoints are HTTPS. No `network_security_config` resource and no cleartext exceptions are declared in `app/src/main/AndroidManifest.xml`.
- Manifest permissions: `INTERNET`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

## Webhooks & Callbacks

**Incoming:**
- None (no HTTP server, no push notifications, no webhook receiver).

**Outgoing:**
- None. There is no telemetry/crash upload or callback URL registration.

**Local system callbacks:**
- Media3 `MediaLibraryService` / `MediaSession` binds for system media controls, Bluetooth/media buttons and the "resume playback" card (`app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`); `MediaButtonReceiver` handles `android.intent.action.MEDIA_BUTTON` (`app/src/main/AndroidManifest.xml:42-49`).
- SAF result callbacks in `app/src/main/java/com/clibeats/presentation/settings/SettingsScreen.kt` and `app/src/main/java/com/clibeats/presentation/onboarding/OnboardingScreen.kt`.

---

*Integration audit: 2026-11-19*
