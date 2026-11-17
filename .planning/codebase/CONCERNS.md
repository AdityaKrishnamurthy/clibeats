---
last_mapped_commit: 916a56379cbb0bf07eb302edd4489c7633418417
last_mapped_at: 2026-11-17
---
# Codebase Concerns

**Analysis Date:** 2026-11-17

## Tech Debt

**Stale stream URLs are never re-resolved (no expiry awareness):**
- Issue: `StreamResolver.resolve()` returns the track untouched whenever `track.streamUrl` is non-blank (`app/src/main/java/com/clibeats/playback/StreamResolver.kt:20-22`). Persisted URLs in `SongEntity.streamUrl` and in-memory queue tracks therefore never refresh, even though YouTube `googlevideo` URLs carry an `expire` query parameter (typically ~6 h) that `StreamCacheManager`/`NewPipeExtractorResolver` already parse.
- Files: `app/src/main/java/com/clibeats/playback/StreamResolver.kt`, `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`, `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/StreamCacheManager.kt`
- Impact: Replaying a YouTube track from Library/History/Queue after expiry fails with HTTP 403, with only a log line in `PlayerAdapter`'s `onPlayerError` listener and no retry.
- Fix approach: Pass an explicit `forceRefresh` flag (or parse `expire=` and compare against `System.currentTimeMillis()`) in `StreamResolver`, and have `PlayerAdapter.onPlayerError` request one re-resolution of the current track before surfacing an error.

**Queue items after the start index are shipped to ExoPlayer without stream URLs:**
- Issue: `PlaybackRepositoryImpl.setQueue` resolves only `tracks[startIndex]` (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:194-214`), while `PlayerAdapter.setQueue` maps the entire list through `Track.toMediaItem()` (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:148-160`, `:300-306`). `TrackMapper` now sets `streamUrl = null` for all InnerTube/YouTube results (`app/src/main/java/com/clibeats/data/provider/mapper/TrackMapper.kt:101-102`), so every non-start YouTube item becomes `android.net.Uri.EMPTY` (`PlayerAdapter.kt:306`).
- Files: `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`, `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`
- Impact: Auto-advance, skip-next, and queue-from-search stop working after the first track for the default provider. There is no per-item resolution hook.
- Fix approach: Resolve queue items lazily on `onMediaItemTransition`/`onPlayerError`, or resolve the whole queue with bounded parallelism before `setMediaItems` (mirroring `InternetArchiveMusicProvider`'s semaphore pattern).

**Playback failures are swallowed — no user-visible error state:**
- Issue: Every operation in `PlaybackRepositoryImpl` runs inside `repositoryScope.launch { runCatching { ... }.onFailure { log } }` (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:128-226`). `PlaybackException` subclasses (`app/src/main/java/com/clibeats/domain/model/PlaybackException.kt`) are constructed but never reach any UI. `PlaybackState` (`app/src/main/java/com/clibeats/domain/model/PlaybackState.kt`) has no error field.
- Files: `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`, `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`, `app/src/main/java/com/clibeats/presentation/player/PlayerViewModel.kt`
- Impact: Tapping a dead track appears to do nothing; users cannot distinguish loading, resolving, and failure.
- Fix approach: Add an `error: PlaybackError?` (or a separate `SharedFlow<PlaybackEvent>`) to the playback contract and render it in `PlayerBar`/`MainLayout`.

**Signature-cipher "deobfuscation" is a placeholder:**
- Issue: `StreamUrlDeobfuscator.deobfuscateSignature` returns `signature.reversed()` (`app/src/main/java/com/clibeats/data/provider/youtube/StreamUrlDeobfuscator.kt:103-105`). Real YouTube ciphers require the player-JS decipher routine.
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/StreamUrlDeobfuscator.kt`
- Impact: Any format delivered via `cipher`/`signatureCipher` yields an invalid signed URL that fails with 403, silently burning one iteration of the `YouTubeClientStrategy.FALLBACK_CHAIN`.
- Fix approach: Delete the stub and explicitly skip cipher-only formats (documenting the limitation), or delegate cipher handling to NewPipeExtractor's `YoutubeStreamExtractor`.

**History recording is dead code (and leaks data types into domain):**
- Issue: `HistoryRepository.recordPlay` has zero production callers — only `app/src/test/java/com/clibeats/data/repository/HistoryRepositoryImplTest.kt:52-54` invokes it. No playback path writes to the `history` table. The domain interface also imports a data-layer entity (`app/src/main/java/com/clibeats/domain/repository/HistoryRepository.kt:6` returns `HistoryEntity`) behind a `@file:Suppress("ForbiddenImport")` comment.
- Files: `app/src/main/java/com/clibeats/data/repository/HistoryRepositoryImpl.kt`, `app/src/main/java/com/clibeats/domain/repository/HistoryRepository.kt`, `app/src/main/java/com/clibeats/data/local/dao/HistoryDao.kt`
- Impact: "Recently played" surfaces can never populate; the domain layer violates Clean Architecture; the repo is bound in `app/src/main/java/com/clibeats/di/RepositoryModule.kt:35` but never consumed.
- Fix approach: Call `historyRepository.recordPlay(track.id, track.providerId)` from `PlayerAdapter.onMediaItemTransition`/`PlaybackRepositoryImpl.playTrack`, and return a domain `HistoryEntry` model instead of `HistoryEntity`.

**"Download" is cache-only and cannot be trusted as offline storage:**
- Issue: `TrackDownloadManager.downloadTrack` writes through `CacheManager.saveTrackToCache` into `context.cacheDir/audio_cache` (`app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt:39-53`, `app/src/main/java/com/clibeats/data/cache/CacheManager.kt:23-45`). There is no progress (status stuck at `Downloading(0)`, `TrackDownloadManager.kt:41`), no cancellation, no per-track dedupe, no `SupervisorJob`, no partial-file cleanup, and no distinct "downloaded" index.
- Files: `app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt`, `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`, `app/src/main/java/com/clibeats/data/download/DownloadStatus.kt`
- Impact: Files disappear when the OS clears cache or LRU eviction runs (500 MB cap); concurrent taps start duplicate downloads; failures can embed the full resolved stream URL in the error message.
- Fix approach: Add a durable `filesDir/downloads` directory with its own persistence and progress-emitting download queue; keep the cache subsystem separate.

**Static-analysis gates are defeated by blanket suppressions:**
- Issue: 117 `@Suppress`/`@file:Suppress` annotations exist under `app/src/main`. The largest ones sit on the highest-risk code: `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt` (`LongMethod`, `CyclomaticComplexMethod`, `MaxLineLength`), `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt` (`SwallowedException`, `TooGenericExceptionCaught`), `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt` (`TooManyFunctions`), `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt` (`LongMethod`, `TooManyFunctions`). The `ForbiddenImport` rule in `config/detekt/detekt.yml` is suppressed in presentation ViewModels that import `com.clibeats.data.*` (`app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt:1`, `app/src/main/java/com/clibeats/presentation/home/HomeViewModel.kt:1`, `app/src/main/java/com/clibeats/presentation/search/SearchViewModel.kt:1`).
- Files: `config/detekt/detekt.yml`, `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`, `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`
- Impact: `maxIssues: 0` is nominal; the architecture boundary and complexity limits only apply to files nobody had to suppress. Regressions in these files must be caught by tests, which do not exist for most of them.
- Fix approach: Narrow the `ForbiddenImport` rule to exact offending symbols, move `AppPreferences`/`CacheManager` usage behind domain ports, and split the two provider/playback god-classes instead of suppressing.

**Release build is not shippable:**
- Issue: `app/build.gradle.kts:29-40` sets `isMinifyEnabled = false` and `signingConfig = signingConfigs.getByName("debug")` for the `release` build type; `proguard-rules.pro` is therefore dormant. `versionName = "0.1.0"` (`app/build.gradle.kts:24`) disagrees with `docs/RELEASE_NOTES.md` which describes a "CLIBeats v1.0" release. `versionCode` is fixed at `1`.
- Files: `app/build.gradle.kts`, `app/proguard-rules.pro`, `.github/workflows/ci.yml`, `docs/RELEASE_NOTES.md`
- Impact: No obfuscation, debug-signed APKs cannot be distributed through Play, and the release variant is never compiled in CI (`ci.yml` only runs `assembleDebug`).
- Fix approach: Enable R8 with keep rules for NewPipeExtractor/kotlinx.serialization/Hilt, add a real signing config sourced from CI secrets, and add `assembleRelease` to `.github/workflows/ci.yml`.

**compileSdk/targetSdk drift hidden by a Gradle property:**
- Issue: `app/build.gradle.kts:17,22` pins `compileSdk = 34` / `targetSdk = 34`, while `gradle.properties:4` sets `android.suppressUnsupportedCompileSdk=35`, indicating at least one dependency expects SDK 35 APIs. Google Play now requires targetSdk 35 for new submissions.
- Files: `app/build.gradle.kts`, `gradle.properties`
- Impact: Suppressing the warning hides incompatibility risk; the app cannot be published to Play under current targetSdk policy; future media3/Compose bumps will fail to compile.
- Fix approach: Bump `compileSdk`/`targetSdk` to 35 (or the current required level), remove the suppression property, and validate notification/FGS behavior on Android 15.

**EncryptedSharedPreferences sits on a deprecated alpha:**
- Issue: `StorageModule` uses `androidx.security:security-crypto:1.1.0-alpha06` (`gradle/libs.versions.toml:25`, `app/src/main/java/com/clibeats/di/StorageModule.kt:30-49`) with a `@Suppress("DEPRECATION")` and a comment deferring to ADR-003. `AppPreferences` touches `securePrefs` synchronously during construction (`app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:72-86`).
- Files: `app/src/main/java/com/clibeats/di/StorageModule.kt`, `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`
- Impact: Known OEM Keystore keyset-corruption crashes at initialization and synchronous disk I/O on the main thread at first `AppPreferences` resolution.
- Fix approach: Execute the Tink `KeystoreAesGcm` migration from `docs/adr/ADR-003-encrypted-storage-local-persistence.md`, and wrap creation in a fallback path.

**Cache size defaults disagree and the setting is never applied on startup:**
- Issue: `AppPreferences.cacheMaxMb` defaults to `512` (`app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:58`), `CacheManager.DEFAULT_MAX_CACHE_BYTES` is 500 MB (`app/src/main/java/com/clibeats/data/cache/CacheManager.kt:28,83`), and `maxCacheSizeBytes` is only assigned from user interaction in `SettingsViewModel.setCacheMaxMb` (`app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt:88`). A persisted setting is not re-applied at process start.
- Files: `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`, `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`, `app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt`
- Impact: Settings UI reports a limit that is not enforced; users who set 2 GB find the cap back at 500 MB after restart.
- Fix approach: Apply `cacheMaxMb` in a startup initializer (`CacheManager` constructor or `CLIBeatsApp`), and derive both defaults from one constant.

**Empty and placeholder modules:**
- Issue: `app/src/main/java/com/clibeats/di/AppModule.kt` is an empty `@Module` object; `TimberTelemetryTracker`/`TimberCrashReporter` are logcat-only stubs despite ADR-009/ADR-010 promising telemetry and crash reporting (`app/src/main/java/com/clibeats/telemetry/TimberTelemetryTracker.kt`, `app/src/main/java/com/clibeats/telemetry/TimberCrashReporter.kt`).
- Files: `app/src/main/java/com/clibeats/di/AppModule.kt`, `app/src/main/java/com/clibeats/telemetry/*.kt`, `app/src/main/java/com/clibeats/di/TelemetryModule.kt`
- Impact: Misleading naming ("Timber" is not used), dead DI surface, and an assumption that crashes/events are collected when they only reach logcat.
- Fix approach: Delete `AppModule` or populate it; either implement transports or rename to `LogcatTelemetryTracker` and record the gap in the ADR.

**PO-token WebView is never destroyed and rescrapes unstable `ytcfg` keys:**
- Issue: `PoTokenGenerator.generatePoTokenInWebView` constructs `WebView(context)` per generation and never calls `destroy()`/`removeJavascriptInterface` (`app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt:81-160`). The extractor JS reads `window.ytcfg.get('PO_TOKEN')`/`data_.PO_TOKEN`, keys that are not part of the public page config, and polls 20× at 500 ms. `visitorData` from the token result is passed to `PlayerRequest`, but the `videoOrVisitorHint` is only ever used as the embed video id.
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/YouTubeClientStrategy.kt`
- Impact: Leaks a WebView (and its renderer) per token generation on a singleton that lives for the process; the fallback likely returns blank tokens and silently disables the InnerTube path; every YouTube config change can break it.
- Fix approach: Own a single reused `WebView` behind a mutex and destroy it on app teardown; or remove the WebView path entirely and rely on NewPipeExtractor with a documented degradation.

**Unreadable nested-tuple Flow composition in settings:**
- Issue: `SettingsViewModel.uiState` combines six flows using nested `Triple`/`Pair` destructuring (`app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt:35-84`), requiring a comment-free mental model of `(inner, extra, modeName)`.
- Files: `app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt`
- Impact: Any new preference multiplies the tuple arity; easy to swap fields silently (no named types).
- Fix approach: Introduce a private `RawSettings` data class and use `combine(vararg flows)` with named fields.

## Known Bugs

**Queue auto-advance / skip-next plays nothing for YouTube queues:**
- Symptoms: A queue started from Search or Library plays the first track, then stops or errors on advance; `PlayerBar` still shows the next title.
- Files: `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:194-214`, `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:148-160,300-306`
- Trigger: `LibraryViewModel.onTrackClick` → `setQueue(tracks, index)` with more than one InnerTube track (`app/src/main/java/com/clibeats/presentation/library/LibraryViewModel.kt:44-51`).
- Workaround: None in-app; play tracks one at a time via `playTrack`.

**Expired stream URLs break Library/Queue replay:**
- Symptoms: A YouTube track that played yesterday fails with 403 on every later attempt; the app shows no error.
- Files: `app/src/main/java/com/clibeats/playback/StreamResolver.kt`, `app/src/main/java/com/clibeats/data/local/entity/SongEntity.kt`
- Trigger: Persisted `SongEntity.streamUrl` older than the URL `expire` value.
- Workaround: Clear app data or re-import playlists.

**Playlist import IDs collide and silently overwrite:**
- Symptoms: Two imported playlists whose names share `String.hashCode()` map to the same `imported_<hash>` id; the later upsert replaces the earlier playlist's mapping.
- Files: `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt:76`
- Trigger: Importing `clibeats.json` containing two such names (hash collisions are cheap to construct).
- Workaround: Rename before import; the overwrite is not reversible in-app.

**Imported `sourceUrl` values are trusted verbatim:**
- Symptoms: A crafted `clibeats.json` puts arbitrary `http(s)`/`file` URLs into `Track.streamUrl`, which `PlayerAdapter.toMediaItem` hands straight to ExoPlayer.
- Files: `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt:86`, `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:306`
- Trigger: Write access to `Android/data/com.clibeats/files/clibeats_exchange/clibeats.json` (adb, shared-storage tools, rooted device) followed by Settings → Import.
- Workaround: None. Fix by validating scheme/host and forcing re-resolution for remote providers on import.

**Media notification never appears on Android 13+:**
- Symptoms: Playback continues but the notification/controls are missing; `PlaybackService.refreshNotification` is annotated `@SuppressLint("MissingPermission", "NotificationPermission")` (`app/src/main/java/com/clibeats/playback/service/PlaybackService.kt:107`).
- Files: `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`
- Trigger: Any device on API 33+ because `POST_NOTIFICATIONS` is neither declared nor requested anywhere (`grep` finds no permission usage under `app/src/main/java`).
- Workaround: Manually grant notifications in system settings.

**Cache filenames always use `.mp3` regardless of codec:**
- Symptoms: WebM/Opus and M4A/AAC streams are stored as `<songId>.mp3` (`app/src/main/java/com/clibeats/data/cache/CacheManager.kt:40`). Playback works because ExoPlayer sniffs content, but external players, MediaStore scans, and file inspectors see the wrong type.
- Files: `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`, `app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt`
- Trigger: Caching any non-MP3 stream (the normal YouTube case is M4A/WebM).
- Workaround: None.

**Cache clearing uses a time-travel cutoff:**
- Symptoms: `clearAllCache` deletes index rows with `deleteBefore(System.currentTimeMillis() + 1000)` (`app/src/main/java/com/clibeats/data/cache/CacheManager.kt:75-78`) instead of clearing the table, leaving a 1-second window of orphaned rows pointing at deleted files.
- Files: `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`, `app/src/main/java/com/clibeats/data/local/dao/CacheIndexDao.kt`
- Trigger: Clear cache while a download completes; `getAllCachedAsFlow` reports phantom bytes until the next eviction.
- Workaround: Restart the app.

## Security Considerations

**Backup exclusions only apply on Android 12+:**
- Risk: `app/src/main/AndroidManifest.xml:9-10` sets `android:allowBackup="true"` with `android:dataExtractionRules` only. That attribute is ignored below API 31, and no `android:fullBackupContent` is declared, so on API 26–30 Auto Backup ships the Room DB, DataStore, and `clibeats_secure_prefs.xml` to Google Drive.
- Files: `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- Current mitigation: Exclusions exist for API 31+ only; restoring encrypted prefs to a new device yields undecryptable ciphertext anyway because the Keystore key is not backed up.
- Recommendations: Add `res/xml/backup_rules.xml` and reference it with `android:fullBackupContent`, excluding `clibeats_secure_prefs.xml`, DataStore, and `clibeats.db`; consider `allowBackup="false"` given the project's privacy posture.

**Diagnostics log user search queries and track titles in release builds:**
- Risk: `DiagnosticLogger` is not gated on `BuildConfig.DEBUG`; `logSearchRequest` writes the raw query (`app/src/main/java/com/clibeats/util/DiagnosticLogger.kt:27-32`) and `logTrackSelected`/`logStreamUrlResolved` write titles, ids, and CDN hosts (`DiagnosticLogger.kt:47-53,86-98`). Any app with `READ_LOGS` or adb access on a debuggable/flashed build can read listening history.
- Files: `app/src/main/java/com/clibeats/util/DiagnosticLogger.kt`, `app/src/main/java/com/clibeats/di/NetworkModule.kt:53-60`
- Current mitigation: Only the OkHttp `BODY` logger is debug-gated; the diagnostic logger is not.
- Recommendations: Wrap `safeLog` in `if (BuildConfig.DEBUG)`, redact query text, and keep `Level.BODY` limited to debug.

**Hidden WebView with JavaScript bridge and no network hardening:**
- Risk: `PoTokenGenerator` enables `javaScriptEnabled`/`domStorageEnabled`, registers `PoTokenBridge`, and loads remote YouTube HTML (`app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt:88-160`). No `network_security_config`, no `setAllowFileAccess(false)`, no `setSafeBrowsingEnabled`, no domain allowlist.
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt`, `app/src/main/res/xml/` (no network-security config present)
- Current mitigation: Only a 10 s timeout and a JSON-string JS bridge that accepts blank values.
- Recommendations: Disable file/content access on the WebView, restrict navigation via `shouldOverrideUrlLoading`, and only attach the JS interface after the expected origin loads.

**Exported MediaSessionService accepts control intents from any app:**
- Risk: `PlaybackService` is `android:exported="true"` (`app/src/main/AndroidManifest.xml:29-35`) and `onGetSession` returns the session to all controllers (`app/src/main/java/com/clibeats/playback/service/PlaybackService.kt:61`). Any installed app can bind or send `com.clibeats.action.PLAY_PAUSE`/`NEXT`/`PREVIOUS` intents.
- Files: `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`
- Current mitigation: Media3 default controller policy; the actions are limited to transport controls.
- Recommendations: Keep exported (required for media integration) but add a `MediaSession.Callback.onConnect` policy that only accepts system/own UID controllers.

**Cache file path is derived from unvalidated `songId`:**
- Risk: `File(cacheDir, "$songId.mp3")` in `CacheManager.saveTrackToCache` (`app/src/main/java/com/clibeats/data/cache/CacheManager.kt:40`) treats provider ids as filenames. Composite ids come from `ProviderId.composite` and imported playlist data, so a crafted id containing `/` or `..` can escape the cache directory.
- Files: `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`, `app/src/main/java/com/clibeats/domain/provider/ProviderId.kt`, `app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt`
- Current mitigation: None structural — providers currently emit safe ids.
- Recommendations: Sanitize with a regex allowlist before building any file name, and reject ids that normalize outside `cacheDir`.

**Spoofed browser headers against YouTube endpoints:**
- Risk: `InnerTubeHeaderInterceptor` stamps a desktop Chrome UA plus `Origin`/`Referer` (`app/src/main/java/com/clibeats/data/provider/api/InnerTubeHeaderInterceptor.kt:18-40`), and `NewPipeExtractorResolver` does the same for its extractor downloads (`app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt:47-64`). This is deliberate anti-bot evasion and a Terms-of-Service exposure.
- Files: `app/src/main/java/com/clibeats/data/provider/api/InnerTubeHeaderInterceptor.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`, `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`
- Current mitigation: Multi-client fallback and stream caching; the legal notice in `README.md` shifts responsibility to users.
- Recommendations: Keep the behavior documented in `docs/adr/ADR-005-provider-integration-innertube.md` and ensure provider failures degrade gracefully (currently they do not — see silent failures).

## Performance Bottlenecks

**DataStore write every 500 ms during playback:**
- Problem: `PlayerAdapter.startPositionTicker` calls `updateState()` every 500 ms (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:70-79,324`), which emits a new `PlaybackState` with a changed `positionMs`. `PlaybackRepositoryImpl` collects that flow and calls `appPreferences.saveQueueMetadata(...)` on every emission (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:70-84`), which performs a `DataStore.edit` (`app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:118-130`).
- Files: `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`, `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`, `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`
- Cause: Position (a high-frequency value) is persisted alongside low-frequency queue metadata; the collector has no `distinctUntilChanged`/sampling.
- Improvement path: Persist queue index/position only on `onMediaItemTransition`, pause, stop, or a lifecycle callback (e.g. every 15–30 s or on `ON_STOP`), never per tick.

**Blocking Room query on the main thread for every media item:**
- Problem: `PlayerAdapter.Track.toMediaItem()` calls `runBlocking { cacheManager.getCachedFile(id) }` (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:300-301`). This runs once per queue item while building `MediaItem`s on the caller's thread (main thread for UI-triggered queue changes and player callbacks).
- Files: `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`, `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`
- Cause: Cached-file lookup is asynchronous but media-item construction is synchronous.
- Improvement path: Pre-resolve cached files off-main and pass a `Map<trackId, File>` into `setQueue`/`restoreQueue`, or build media items in a coroutine and hand the prepared list to ExoPlayer on the main thread.

**Whole-layout recomposition at 2 Hz:**
- Problem: `MainLayout` collects `playbackState` (`app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt:60-68`) and derives the progress fraction, so the nav suite, top bar, and content slot are recomposed every tick.
- Files: `app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt`, `app/src/main/java/com/clibeats/presentation/component/PlayerBar.kt`
- Cause: One broad `State` read in the root composable; progress is computed in the shell.
- Improvement path: Read `positionMs` only inside `PlayerBar` (or a `derivedStateOf`/`graphicsLayer`-driven progress), keeping the shell on a coarse state.

**YouTube stream resolution is a long sequential chain:**
- Problem: `YouTubeMusicProvider.stream` runs cache → NewPipeExtractor → PO-token WebView (up to 10 s) → `FALLBACK_CHAIN` clients sequentially (`app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt:96-200`). The UI has no "resolving" state.
- Files: `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/YouTubeClientStrategy.kt`
- Cause: Every stage must fail before the next starts, and failures are only logged.
- Improvement path: Race safe stages where possible, cap total resolution time, and emit a `Resolving` UI state so the delay is visible.

**Insertion-order eviction instead of LRU:**
- Problem: `CacheManager.evictLruIfNeeded` sorts solely by `cachedAt` (`app/src/main/java/com/clibeats/data/cache/CacheManager.kt:59-73`); `cachedAt` is never refreshed on a cache hit, and there is no `lastAccessedAt` column.
- Files: `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`, `app/src/main/java/com/clibeats/data/local/entity/CacheIndexEntity.kt`
- Cause: The eviction key records creation, not use.
- Improvement path: Add `lastAccessedAt` and update it on `getCachedFile` hits; serialize save/evict with a mutex to avoid size races.

**Metadata fan-out on Internet Archive search:**
- Problem: `rankAndMap` issues up to 8 additional `api.metadata` calls per search with semaphore 4 (`app/src/main/java/com/clibeats/data/provider/InternetArchiveMusicProvider.kt:110-144`).
- Files: `app/src/main/java/com/clibeats/data/provider/InternetArchiveMusicProvider.kt`
- Cause: Search docs do not include file metadata; each item needs a detail fetch.
- Improvement path: Cache metadata per identifier and reduce `METADATA_CAP` if search latency is user-visible.

**Library groups the entire song table on every emission:**
- Problem: `LibraryViewModel` groups all tracks into artists/albums synchronously in `map` (`app/src/main/java/com/clibeats/presentation/library/LibraryViewModel.kt:21-42`); `getAllTracksAsFlow` has no pagination.
- Files: `app/src/main/java/com/clibeats/presentation/library/LibraryViewModel.kt`, `app/src/main/java/com/clibeats/data/local/dao/SongDao.kt`
- Cause: Full-table Flow plus in-memory grouping.
- Improvement path: Use `PagingSource` and aggregate counts in SQL once the library grows past a few thousand rows.

## Fragile Areas

**YouTube provider stack (highest fragility):**
- Files: `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/StreamUrlDeobfuscator.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/StreamCacheManager.kt`, `app/src/main/java/com/clibeats/data/provider/api/InnerTubeHeaderInterceptor.kt`, `app/src/main/java/com/clibeats/data/provider/api/InnerTubeApi.kt`
- Why fragile: Entirely reverse-engineered/undocumented behavior (InnerTube payloads, `ytcfg` keys, `googlevideo` signature params, bot checks). Each stage has its own silent failure mode, and several are suppression-heavy with no behavioral tests.
- Safe modification: Change one stage at a time, keep the NewPipe-first fallback ordering, and manually smoke-test real playback (`Attention`, `Blinding Lights`) before merging. Do not remove `StreamCacheManager` expiry margins.
- Test coverage: `app/src/test/java/com/clibeats/data/provider/YouTubeMusicProviderTest.kt` covers request construction, not fallback ordering, cache expiry, WebView token generation, or cipher handling.

**Playback pipeline (singleton ExoPlayer shared by app and service):**
- Files: `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`, `app/src/main/java/com/clibeats/playback/StreamResolver.kt`, `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`, `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`
- Why fragile: One `ExoPlayer` instance provided by `app/src/main/java/com/clibeats/di/PlaybackModule.kt:21-32` is driven both by UI calls and by the foreground service's hand-rolled notification. `PlayerAdapter` mirrors ExoPlayer state into its own `trackList`, and every mirroring bug (queue reordering, restore, skip) desynchronizes the two. `PlaybackService.onDestroy` deliberately does not release the player (`PlaybackService.kt:66-77`) — a subtle invariant.
- Safe modification: Keep `PlayerAdapter` as the only mutator of ExoPlayer; never release the player from the service; add the queue-resolution fix only after tests cover `restoreQueue`/`moveTrack` index mapping.
- Test coverage: `PlayerAdapterTest.kt`, `PlayerAdapterQueueTest.kt`, `PlaybackRepositoryImplTest.kt`, `PlaybackIntegrationTest.kt`; `PlaybackService` has no tests at all.

**Playlist import/export:**
- Files: `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt`, `app/src/main/java/com/clibeats/data/playlist/CliBeatsFile.kt`, `app/src/main/java/com/clibeats/data/playlist/CliBeatsFileCodec.kt`
- Why fragile: Depends on `getExternalFilesDir(null)` (user- and OEM-clearable), derives ids from `name.hashCode()`, trusts file URLs, and performs many separate DAO writes without an enclosing transaction — a mid-import failure leaves a partially imported playlist.
- Safe modification: Wrap `import()` in `withTransaction`, introduce a stable content-hash id, and validate URLs before persisting.
- Test coverage: `app/src/test/java/com/clibeats/data/playlist/CliBeatsFileCodecTest.kt` only; no import/export flow tests.

**Foreground service notification lifecycle:**
- Files: `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`
- Why fragile: The notification is built manually around `MediaSession`, `startForeground` is invoked in `onStartCommand` for every start, `refreshNotification` suppresses missing-permission lint, and there is no `POST_NOTIFICATIONS` handling. There is no `WAKE_LOCK` permission or `setWakeMode` anywhere in the manifest or `PlaybackModule`, so CPU sleep can stall network streaming after screen-off.
- Safe modification: Add the notification permission flow first, then test FGS start/stop, notification taps, and screen-off playback on API 26 and 34 emulators.
- Test coverage: None (no unit or instrumentation tests).

**Telemetry/crash reporting contracts:**
- Files: `app/src/main/java/com/clibeats/telemetry/TelemetryTracker.kt`, `app/src/main/java/com/clibeats/telemetry/CrashReporter.kt`, `app/src/main/java/com/clibeats/telemetry/TimberTelemetryTracker.kt`, `app/src/main/java/com/clibeats/telemetry/TimberCrashReporter.kt`
- Why fragile: The interfaces look production-ready but only write to logcat; ADR-010 describes privacy guarantees that have no implementation to verify.
- Safe modification: Keep the interfaces; swap implementations when real transports land.

## Scaling Limits

**In-memory stream cache:**
- Current capacity: Unbounded `ConcurrentHashMap<String, ExtractedStreamInfo>` (`app/src/main/java/com/clibeats/data/provider/youtube/StreamCacheManager.kt:11-49`), entries dropped only on expiry (URL TTL + 60 s) or `clear()`.
- Limit: Long sessions with many tracks retain every resolved URL plus metadata until process death.
- Scaling path: Cap entries (LRU map) or persist the cache with TTL; small strings, so the risk is modest but unbounded.

**Audio cache:**
- Current capacity: 500 MB hard cap shared by streaming cache and "downloads" (`app/src/main/java/com/clibeats/data/cache/CacheManager.kt:28`); eviction by `cachedAt` ascending.
- Limit: Frequently replayed tracks are evicted before one-off tracks; there is no per-file progress or partial-file accounting; concurrent save/evict can overshoot the cap transiently.
- Scaling path: Access-time-based LRU, a mutex around save+evict, and a separate durable download store.

**Room library tables:**
- Current capacity: Unbounded `songs`, `playlists`, `playlist_song_cross_ref`, `queue_items`, `history`; no pagination in `SongDao`/`PlaylistDao` flows.
- Limit: `LibraryViewModel` sorts/groups the full table on the main thread at every emission.
- Scaling path: `PagingSource` + SQL aggregates (counts per artist/album) before implementing a MediaStore scan, which can add thousands of rows at once.

## Dependencies at Risk

**NewPipeExtractor v0.26.4 (JitPack, GPLv3) — license and stability risk:**
- Risk: `com.github.TeamNewPipe:NewPipeExtractor:v0.26.4` is pulled from JitPack (`gradle/libs.versions.toml:28`, `settings.gradle.kts:9`) and is licensed **GPLv3**, while this project ships under MIT (`README.md`). It is not mentioned in `docs/LICENSES.md` at all, which contradicts `docs/adr/ADR-011-production-release-distribution.md`'s claim of "100% open source license compliance verified". It is also a pinned snapshot of a reverse-engineered extractor.
- Impact: (1) Legal: distributing an MIT-licensed app that links GPLv3 code is a copyleft violation unless the whole app is GPL-compatible — this is the single largest release blocker. (2) Technical: any YouTube change can break the primary stream path; the pinned commit only advances when the team bumps it.
- Migration plan: Decide licensing first — either relicense the app under GPLv3 and document it, or isolate/replace NewPipe with an in-house extractor (or drop YouTube as a bundled provider). Track upstream releases and add a dependency-license check to CI.

**androidx.security:security-crypto 1.1.0-alpha06:**
- Risk: Deprecated alpha with known keyset-corruption and OEM Keystore issues (`gradle/libs.versions.toml:25`).
- Impact: Startup crash path in `StorageModule` and auth-token storage.
- Migration plan: Tink `KeystoreAesGcm` per `docs/adr/ADR-003-encrypted-storage-local-persistence.md`.

**Implicit Truth dependency in 32 test files:**
- Risk: Test sources import `com.google.common.truth.Truth` (e.g. `app/src/test/java/com/clibeats/license/LicenseComplianceTest.kt:3`) but `com.google.truth:truth` is not declared in `gradle/libs.versions.toml` or `app/build.gradle.kts`; it resolves only transitively (via `room-testing`/Compose test artifacts).
- Impact: A routine dependency bump can break compilation of the majority of the test suite with `Unresolved reference: Truth`.
- Migration plan: Declare Truth explicitly as `testImplementation`.

**androidx.media3 1.4.1 / Room 2.6.1 / AGP 8.5.2 / Kotlin 2.0.21 / Compose BOM 2024.09.03 / Coil 2.7.0:**
- Risk: All roughly a year+ behind current stable lines; media3 1.4.x predates several session/notification fixes; Coil 2.x is in maintenance while 3.x is current.
- Impact: Cumulative bug fixes and new test APIs unavailable; the `compileSdk 34` plus `suppressUnsupportedCompileSdk=35` combination is already forced.
- Migration plan: Bump deliberately in one PR per major component, re-running `verifyPaparazziDebug` and notification smoke tests (media3 changes notification defaults).

**No dependency-update or vulnerability automation:**
- Risk: `.github/` contains only `workflows/ci.yml` — no Dependabot/Renovate config, no OWASP dependency-check, no secret scanning, no SAST.
- Impact: Stale/fragile dependencies (above) are discovered manually.
- Migration plan: Add `.github/dependabot.yml` covering Gradle, plus a license-check step in CI.

## Missing Critical Features

**No user-facing playback error/resolving state:**
- Problem: `PlaybackState` has no error or resolving field, so failures and multi-second stream resolution are invisible (`app/src/main/java/com/clibeats/domain/model/PlaybackState.kt`, `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`).
- Blocks: Trustworthy playback UX, retry-on-403 behavior, and any useful debugging by non-developers.

**No notification permission flow (Android 13+):**
- Problem: `POST_NOTIFICATIONS` is absent from `app/src/main/AndroidManifest.xml` and never requested; `PlaybackService` suppresses the lint instead.
- Blocks: Media controls on modern devices, and passing Play policy review.

**No on-device (MediaStore) library and no add-to-library path:**
- Problem: `LocalMusicProvider` explicitly documents MediaStore scanning as future work (`app/src/main/java/com/clibeats/data/provider/LocalMusicProvider.kt:17-19`); the only writers to `songs` are the import path and queue persistence. `READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE` is not declared.
- Blocks: The "Local" provider is non-functional for un-imported music; "Library" cannot be populated from search/player.

**No real telemetry/crash reporting despite ADR-009/ADR-010:**
- Problem: Both interfaces resolve to logcat-only stubs.
- Blocks: Any beta feedback loop; crash diagnosis in the field.

**No wake-lock support for background streaming:**
- Problem: Neither `android.permission.WAKE_LOCK` in `app/src/main/AndroidManifest.xml` nor `setWakeMode(C.WAKE_MODE_NETWORK)` in `app/src/main/java/com/clibeats/di/PlaybackModule.kt`; screen-off playback of network streams can stall.
- Blocks: Reliable background playback, a headline README feature.

**No license attribution for the GPL dependency:**
- Problem: `docs/LICENSES.md` omits NewPipeExtractor entirely while claiming a complete inventory, and `app/src/test/java/com/clibeats/license/LicenseComplianceTest.kt` only greps for AndroidX/Media3/OkHttp strings.
- Blocks: A compliant release; the existing "compliance test" gives false assurance.

## Test Coverage Gaps

**Queue resolution and playback failure paths (Highest priority):**
- What's not tested: Resolution of queue items 2..n, `Uri.EMPTY` media items, `onPlayerError` recovery, stale-URL retry, and error surfacing to UI.
- Files: `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`, `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`, `app/src/main/java/com/clibeats/playback/StreamResolver.kt`
- Risk: The most visible user-facing failures (silent queue breakage) are completely uncovered.
- Priority: High

**YouTube resolution chain (High):**
- What's not tested: Fallback ordering, `StreamCacheManager` expiry boundaries, `StreamUrlDeobfuscator.parseCipher`/`deobfuscateSignature`, `PoTokenGenerator` (WebView path), and `NewPipeExtractorResolver` error mapping.
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/*`, `app/src/test/java/com/clibeats/data/provider/YouTubeMusicProviderTest.kt`
- Risk: Default provider regressions ship silently.
- Priority: High

**UI screens (High):**
- What's not tested: `HomeScreen.kt`, `SearchScreen.kt`, `LibraryScreen.kt`, `PlaylistScreen.kt`, `QueueScreen.kt`, `MoreScreen.kt`, `SettingsScreen.kt`, `MainLayout.kt`, and the player screen have no Compose UI tests; only `PlayerBar`, `SongTableRow`, and `SearchScreenKt` have coverage (`app/src/test/java/com/clibeats/presentation/component/`, `app/src/test/java/com/clibeats/presentation/search/SearchScreenKtTest.kt`), plus four Paparazzi snapshots in `app/src/test/java/com/clibeats/theme/`.
- Files: `app/src/main/java/com/clibeats/presentation/*`
- Risk: Layout/interaction regressions (keyboard navigation, dense table rendering, empty states) are invisible.
- Priority: High

**Foreground service and notification (Medium):**
- What's not tested: `PlaybackService` lifecycle, action intents, notification content/actions, `startForeground` on API 26 vs 34.
- Files: `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`
- Risk: Notification/FGS crashes and permission regressions.
- Priority: Medium

**Storage, caching, and exchange flows (Medium):**
- What's not tested: cache eviction ordering and save/evict races, `TrackDownloadManager` failure/cancel, `PlaylistExchangeManager` import/export end-to-end, `AppPreferences` Keystore fallback.
- Files: `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`, `app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt`, `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt`, `app/src/test/java/com/clibeats/data/cache/CacheManagerTest.kt`
- Risk: Data-loss and crash bugs found only by users.
- Priority: Medium

**CI does not exercise device, screenshot, or release layers:**
- What's not tested: `.github/workflows/ci.yml` runs `ktlintCheck`, `detekt`, `lintDebug`, `assembleDebug`, `testDebugUnitTest` only. Room DAO instrumentation tests in `app/src/androidTest/java/com/clibeats/data/local/dao/` (4 files) never run; Paparazzi screenshot tests are never verified despite `docs/adr/ADR-009` and `README.md` claiming them as a gate; `assembleRelease` is never built; no coverage report is produced or enforced.
- Files: `.github/workflows/ci.yml`, `app/src/androidTest/java/com/clibeats/data/local/dao/`, `app/src/test/java/com/clibeats/theme/*ScreenshotTest.kt`
- Risk: Migration breaks, visual regressions, and release-only failures (R8/signing) reach main undetected.
- Priority: High

**Migration tests for Room v1→v2→v3 (Medium):**
- What's not tested: `MIGRATION_1_2` and `MIGRATION_2_3` in `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt:56-113` have no `MigrationTestHelper` coverage, although schemas `app/schemas/com.clibeats.data.local.CliBeatsDatabase/1.json`–`3.json` are exported and `room-testing` is available.
- Files: `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt`, `app/schemas/`
- Risk: An upgrade-path SQL error wipes or crashes existing users' libraries on release.
- Priority: Medium

---

*Concerns audit: 2026-11-17*
