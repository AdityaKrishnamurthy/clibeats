---
last_mapped_commit: 916a56379cbb0bf07eb302edd4489c7633418417
last_mapped_at: 2026-11-17
---
# External Integrations

**Analysis Date:** 2026-11-17

## APIs & External Services

**Music Catalog Providers (all consumed through the `MusicProvider` contract in `app/src/main/java/com/clibeats/domain/provider/MusicProvider.kt`):**

- **YouTube Music (InnerTube, unofficial)** — default provider; search + stream resolution
  - SDK/Client: Retrofit 2.11.0 interface `app/src/main/java/com/clibeats/data/provider/api/InnerTubeApi.kt`
  - Base URL: `https://music.youtube.com/youtubei/v1/` (`INNERTUBE_BASE_URL` in `app/src/main/java/com/clibeats/di/NetworkModule.kt`)
  - Endpoints: `POST search`, `POST player`
  - Auth: none — unauthenticated browser/client emulation. Headers injected by `app/src/main/java/com/clibeats/data/provider/api/InnerTubeHeaderInterceptor.kt` (`User-Agent`, `Origin: https://music.youtube.com`, `Referer`, `Accept-Language`)
  - Client identity: `app/src/main/java/com/clibeats/data/provider/youtube/YouTubeClientStrategy.kt` (`ANDROID_MUSIC` 6.42.52 primary, `WEB_REMIX` 1.20240618.01.00 used in `SearchRequest.forQuery`)
  - Search filter: Base64 protobuf "songs" param in `app/src/main/java/com/clibeats/data/provider/dto/SearchRequest.kt`
  - Stream extraction chain: `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt` (NewPipeExtractor v0.26.4) → `StreamUrlDeobfuscator.kt` (parses `n`/signature params, expiry) → `StreamCacheManager.kt` (in-memory `ConcurrentHashMap`, expires with 60s safety margin)
  - PoToken / visitor data: `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt` loads `music.youtube.com` in a headless `WebView` with JavaScript enabled, polls `ytcfg` `PO_TOKEN`/`VISITOR_DATA` via a `@JavascriptInterface` bridge (`PoTokenBridge`); 10s timeout, 12-hour TTL cache

- **Audius** — `https://discoveryprovider.audius.co/v1/`
  - SDK/Client: `app/src/main/java/com/clibeats/data/provider/api/AudiusApi.kt` (Retrofit)
  - Endpoints: `tracks/search`, `tracks/trending`, `tracks/{trackId}`, plus stream resolution `tracks/{id}/stream`
  - Auth: none — `app_name=clibeats` query parameter only (`AudiusApi.DEFAULT_APP_NAME`)
  - Implementation: `app/src/main/java/com/clibeats/data/provider/AudiusMusicProvider.kt`

- **Internet Archive** — `https://archive.org/`
  - SDK/Client: `app/src/main/java/com/clibeats/data/provider/api/InternetArchiveApi.kt` (Retrofit)
  - Endpoints: `advancedsearch.php` (Lucene query, `fl[]` fields, `rows`, `page`, `sort[]`, `output=json`), `metadata/{identifier}`
  - Auth: none
  - Streaming: direct `https://archive.org/download/...` URLs (302 to storage mirrors, HTTP Range/206)
  - Implementation: `app/src/main/java/com/clibeats/data/provider/InternetArchiveMusicProvider.kt`; ranking/selection in `app/src/main/java/com/clibeats/data/provider/mapper/InternetArchiveMapper.kt`

- **Jamendo** — `https://api.jamendo.com/v3.0/`
  - SDK/Client: `app/src/main/java/com/clibeats/data/provider/api/JamendoApi.kt` (Retrofit, `GET tracks/`)
  - Auth: free `client_id` supplied by Gradle property `JAMENDO_CLIENT_ID` → `BuildConfig.JAMENDO_CLIENT_ID` → `@Named("jamendo_client_id")` (`app/src/main/java/com/clibeats/di/ProviderModule.kt`). Empty by default; provider returns an error result when unset.
  - Implementation: `app/src/main/java/com/clibeats/data/provider/JamendoMusicProvider.kt`

- **Local device library** — no network
  - Implementation: `app/src/main/java/com/clibeats/data/provider/LocalMusicProvider.kt`, backed by Room via `SongRepository`
  - MediaStore audio scanning is not implemented; `READ_MEDIA_AUDIO` is not declared in the manifest

**Provider registry and selection:**
- `app/src/main/java/com/clibeats/data/provider/MusicProviderRegistry.kt` implements `app/src/main/java/com/clibeats/domain/provider/ProviderRegistry.kt`
- Registration order (display priority): YouTube Music → Internet Archive → Audius → Jamendo → Local (`app/src/main/java/com/clibeats/di/ProviderModule.kt`)
- Active provider persisted in DataStore key `active_provider_id` (`app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`)

## Data Storage

**Databases:**
- Room (SQLite), database name `clibeats.db`, version 3 — `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt`
  - Entities: `SongEntity`, `PlaylistEntity`, `PlaylistSongCrossRef`, `HistoryEntity`, `CacheIndexEntity`, `QueueEntity`, `LikedSongEntity`, `SavedAlbumEntity`, `SavedArtistEntity` (`app/src/main/java/com/clibeats/data/local/entity/`)
  - DAOs: `SongDao`, `PlaylistDao`, `HistoryDao`, `CacheIndexDao`, `QueueDao`, `LikedSongDao`, `SavedAlbumDao`, `SavedArtistDao`
  - Schema JSON exported to `app/schemas/com.clibeats.data.local.CliBeatsDatabase/` (1.json, 2.json, 3.json)
  - Migrations `MIGRATION_1_2`, `MIGRATION_2_3` registered in `app/src/main/java/com/clibeats/di/DatabaseModule.kt`
  - No encryption at rest (no SQLCipher)

**Key-value storage:**
- DataStore Preferences, file name `clibeats_prefs` — non-sensitive settings (provider, cache limit, theme, accent, queue index, resume position, shuffle/repeat)
- EncryptedSharedPreferences, file name `clibeats_secure_prefs` — Android Keystore `MasterKey` (AES256_GCM), `AES256_SIV` key encryption / `AES256_GCM` value encryption — `app/src/main/java/com/clibeats/di/StorageModule.kt`; holds `auth_token` key (unused by current providers)
- Both stores excluded from cloud backup and device transfer in `app/src/main/res/xml/data_extraction_rules.xml`

**File Storage:**
- Local filesystem only
- Audio cache: `context.cacheDir/audio_cache/<songId>.mp3` with LRU eviction and Room index — `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`
- Image cache: Coil disk cache at `context.cacheDir/image_cache` — `app/src/main/java/com/clibeats/di/ImageLoaderModule.kt`
- Playlist export: `clibeats.json` in the app-specific external files directory — `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt`

**Caching:**
- Audio cache (500 MB default cap, configurable via `cache_max_mb` preference)
- Coil memory (25% of heap) and disk (2%) image caches
- In-memory YouTube stream URL cache (`app/src/main/java/com/clibeats/data/provider/youtube/StreamCacheManager.kt`)
- In-memory PoToken cache (12h TTL)

## Authentication & Identity

**Auth Provider:**
- None — CLIBeats has no accounts, no sign-in, and no CLIBeats-owned backend (stated in `README.md` and `docs/adr/ADR-005-provider-integration-innertube.md`)
- No OAuth, no Firebase Auth, no token refresh flow in code
- `AppPreferences` reserves an `auth_token` secure-preference key for future authenticated providers

**Manifest permissions** (`app/src/main/AndroidManifest.xml`):
- `android.permission.INTERNET`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- Not declared: `POST_NOTIFICATIONS` (API 33+ notification display), `READ_MEDIA_AUDIO`

## Monitoring & Observability

**Error Tracking:**
- None third-party (no Crashlytics, Sentry, or Bugsnag)
- `app/src/main/java/com/clibeats/telemetry/CrashReporter.kt` interface bound to `TimberCrashReporter` (`app/src/main/java/com/clibeats/telemetry/TimberCrashReporter.kt`) — writes to `android.util.Log` tag `CLIBeatsCrash` and redacts `Bearer ...` tokens via regex. No Timber library dependency exists.

**Logs:**
- Structured `AnalyticsEvent` logging through `TelemetryTracker` → `TimberTelemetryTracker` (`Log.d`, tag `CLIBeatsTelemetry`) — events defined in `app/src/main/java/com/clibeats/telemetry/AnalyticsEvent.kt`
- Diagnostic search/stream tracing via `app/src/main/java/com/clibeats/util/DiagnosticLogger.kt` (8-char UUID trace IDs, tag `CliBeatsDiagnostic`)
- OkHttp `HttpLoggingInterceptor` at `Level.BODY` in debug builds only (`app/src/main/java/com/clibeats/di/NetworkModule.kt`)
- Bindings declared in `app/src/main/java/com/clibeats/di/TelemetryModule.kt`

## CI/CD & Deployment

**Hosting:**
- Not applicable — on-device Android app with no server component
- Repository remote: `https://github.com/Omprakash-p06/clibeats.git`

**CI Pipeline:**
- GitHub Actions — `.github/workflows/ci.yml`
- Trigger: push/PR to `main`, `master`, `develop`
- Steps: JDK 17 Temurin → Gradle setup → `ktlintCheck` → `detekt` → `lintDebug` → `assembleDebug` → `testDebugUnitTest` → upload `app/build/reports/` artifacts
- No release/publication workflow; `assembleRelease` runs locally via `scripts/check-quality-gates.sh` workflow docs in `README.md`

## Environment Configuration

**Required env vars:**
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` — required by Gradle `Test` tasks and CI for the Android SDK
- `JAMENDO_CLIENT_ID` — optional Gradle property (`-PJAMENDO_CLIENT_ID=...`), not an environment variable; Jamendo provider is disabled while empty

**Secrets location:**
- No `.env` or checked-in secret files
- `.gitignore` excludes `local.properties`, `*.keystore` (with `!debug.keystore`), and `/build/`
- Release builds currently reuse the debug signing config (`app/build.gradle.kts`), so no signing secrets are configured

## Webhooks & Callbacks

**Incoming:**
- None — no server, no webhook endpoints, no deep links (`MainActivity` only handles `MAIN`/`LAUNCHER`)

**Outgoing:**
- None — no push/event callbacks to external services
- Internal Android callbacks: `MediaSessionService` intent actions consumed by `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`; `ConnectivityManager.NetworkCallback` in `app/src/main/java/com/clibeats/data/network/NetworkMonitor.kt`; `@JavascriptInterface` `PoTokenBridge` in `PoTokenGenerator.kt`; playlist import/export via `PlaylistExchangeManager`

---

*Integration audit: 2026-11-17*
