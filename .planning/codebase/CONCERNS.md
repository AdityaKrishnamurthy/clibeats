---
last_mapped_commit: a77dc37a72fa6a498fb1ac17a68d814fbe3770cb
last_mapped_at: 2026-11-19
---
# Codebase Concerns

**Analysis Date:** 2026-11-19

## Priority Summary

Highest-leverage problems in this repo, in order:

1. **`PoTokenGenerator` leaks a `WebView` + main-thread `Handler` runnables on every token generation** (`app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt`). Playback extraction depends on it and it is completely untested.
2. **Release build has R8/minification disabled** (`app/build.gradle.kts:48`) while `app/proguard-rules.pro` and all release docs (`.planning/PROJECT.md`, `docs/RELEASE_NOTES.md`) claim a shrunk release. Shipped APK is unminified, unshrunk, and fully reflectable.
3. **The whole download subsystem is unreachable dead code** (`app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt`, `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`), while two independent audio caches with two different size limits coexist.
4. **`targetSdk`/`compileSdk` are 34** (`app/build.gradle.kts:17,22`) — below current Play Store requirements — and `gradle.properties` still carries `android.suppressUnsupportedCompileSdk=35`.
5. **Silent-corruption paths:** cache filenames are built from unsanitized track ids (`CacheManager.kt:40`), and a revoked SAF folder is treated as "all files deleted" so the scanner deletes those songs from the library (`LocalMusicScanner.kt:78-110`).

## Tech Debt

**Release build never shrinks or obfuscates:**
- Issue: `isMinifyEnabled = false` in the `release` block; `app/proguard-rules.pro` exists (with entity/DAO/serialization keep rules) but is inert.
- Files: `app/build.gradle.kts:44-55`, `app/proguard-rules.pro`
- Impact: APK size and method count stay at debug levels; classes/fields remain trivially readable. Every claim of "R8" in `.planning/PROJECT.md`, `.planning/RETROSPECTIVE.md`, and `docs/RELEASE_NOTES.md` is currently false. Enabling it will surface missing keep rules (NewPipeExtractor, OkHttp/Retrofit, kotlinx.serialization).
- Fix approach: flip `isMinifyEnabled = true`, add `-keep` rules for `org.schabi.newpipe.**` and Coil, then run `assembleRelease` + `testDebugUnitTest` and a device smoke test before tagging.

**Stale SDK levels and a leftover build suppression:**
- Issue: `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26`; `gradle.properties` sets `android.suppressUnsupportedCompileSdk=35`, which only makes sense if AGP expected compileSdk 35.
- Files: `app/build.gradle.kts:17,22`, `gradle.properties:5`
- Impact: Play Store submission is blocked once target API minimum moves; the suppress flag hides a real AGP/compileSdk mismatch and will mask future upgrades.
- Fix approach: bump `compileSdk`/`targetSdk` to the current API level, add `FOREGROUND_SERVICE_MEDIA_PLAYBACK` behavior review for the new target, then delete the suppression property.

**Suppression-heavy static-analysis configuration (`D-02`, still open):**
- Issue: 63 `@Suppress` annotations in `app/src/main`. Root cause is the over-broad detekt `ForbiddenImport: com.clibeats.data.*` pattern plus detekt 1.23.6 vs ktlint_official `Indentation` mismatch, which forces `@file:Suppress("ForbiddenImport", "Indentation", "MaxLineLength", "Wrapping")` in every data file.
- Files: `config/detekt/detekt.yml:44-50`, `app/src/main/java/com/clibeats/data/repository/SongRepositoryImpl.kt:19`, `app/src/main/java/com/clibeats/data/repository/PlaylistRepositoryImpl.kt:23`, `app/src/main/java/com/clibeats/data/repository/HistoryRepositoryImpl.kt:17`, `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:21`, `app/src/main/java/com/clibeats/di/DatabaseModule.kt:1-2`, `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt:1`
- Impact: The `ForbiddenImport` rule no longer meaningfully enforces the presentation→data boundary (presentation files now import data types freely, e.g. `presentation/settings/SettingsViewModel.kt` imports `com.clibeats.data.cache.CacheManager`, `MediaCaches`, `com.clibeats.data.scan.LocalMusicScanner`). Also tracked as open broken window #3 in `.planning/WINDOWS.md`.
- Fix approach: replace the wildcard pattern with explicit forbidden packages (`com.clibeats.data.repository.*`, `com.clibeats.data.local.*`) and move presentation-facing cache/scan facades behind domain interfaces.

**Deprecated APIs kept deliberately (with a documented migration that has not started):**
- Issue: `EncryptedSharedPreferences`/`MasterKey` (deprecated in `security-crypto` 1.1.0-alpha06) and Coil 2 `DiskCache` interface overrides.
- Files: `app/src/main/java/com/clibeats/di/StorageModule.kt:22-23`, `app/src/main/java/com/clibeats/data/cache/ResizableDiskCache.kt:18`
- Impact: `security-crypto` is pinned to an alpha; the deprecated surface can disappear in any update. `ResizableDiskCache` overrides every member of an interface Coil marks experimental and could break on a Coil major upgrade.
- Fix approach: migrate the token store to Tink `KeystoreAesGcm` as ADR-003 already promises, and wrap the Coil cache behind `ResizableDiskCache`'s own small interface instead of implementing `DiskCache` directly.

**Download feature is dead code:**
- Issue: `TrackDownloadManager.downloadTrack()` / `isTrackDownloaded()` are never called from any screen or ViewModel; `DownloadModule` is the only reference.
- Files: `app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt:32`, `app/src/main/java/com/clibeats/data/download/DownloadStatus.kt`, `app/src/main/java/com/clibeats/di/DownloadModule.kt`, `app/src/main/java/com/clibeats/data/cache/CacheManager.kt:36-56`
- Impact: "Offline downloads" is advertised in project docs but impossible in the UI; the dead path still gets maintained and is covered by a test (`app/src/test/java/com/clibeats/data/download/TrackDownloadManagerTest.kt`) that gives false confidence.
- Fix approach: either wire a download action into `SongTableRow`/`PlayerViewModel` and surface state in Library, or delete the subsystem and rely on `MediaCaches` (streaming cache).

**Two audio caches, two limits, two accounting models:**
- Issue: `CacheManager` (`audio_cache`, default 500 MB, `DEFAULT_MAX_CACHE_BYTES`) and `MediaCaches.songCache` (Media3 `SimpleCache`, limit from `AppPreferences.cacheMaxMb`, default 512 MB) both store audio, and Settings "song cache" writes both.
- Files: `app/src/main/java/com/clibeats/data/cache/CacheManager.kt:28,75`, `app/src/main/java/com/clibeats/data/cache/MediaCaches.kt:53-58`, `app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt:47-69`
- Impact: Storage reporting is split-brain (`CacheUsage.songBytes` vs `uiState.currentCacheSizeBytes`); clearing "songs" deletes both; a user lowering the limit can still exceed it via the other cache.
- Fix approach: collapse to one cache abstraction (or make `CacheManager` a thin index over `MediaCaches`), and one settings value.

**Nullable secondary constructor / DI smells:**
- Issue: concrete Hilt types are declared optional (`LocalMusicScanner? = null`, `MediaCaches? = null`, `CliBeatsDatabase? = null`, `LibraryRepository? = null`, `ImageLoader?`) and `PlaybackRepositoryImpl` has a second non-`@Inject` constructor for tests.
- Files: `app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt:30-36`, `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt:29-36`, `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:44-70`, `app/src/main/java/com/clibeats/presentation/player/PlayerViewModel.kt:41-46`
- Impact: The graph silently tolerates a missing collaborator at runtime (e.g. `localMusicScanner?.scan()` becomes a no-op), which turns DI misconfiguration into "feature does nothing" bugs.
- Fix approach: make dependencies non-null in production constructors; keep fake collaborators in test-only constructors/factories.

**Planning/ADR documentation has drifted from the code:**
- Issue: `.planning/PROJECT.md` and ADR-012…ADR-020 describe a "CliBeats Gateway (Fastify + Redis)" and `GatewayMusicProvider`/`GatewayApi`; neither exists in `app/src/main` (providers are `YouTubeMusicProvider`, `AudiusMusicProvider`, `InternetArchiveMusicProvider`, `JamendoMusicProvider`, `LocalMusicProvider`). ADR-003 lists 5 entities; `CliBeatsDatabase` has 9.
- Files: `.planning/PROJECT.md`, `docs/adr/ADR-003-encrypted-storage-local-persistence.md`, `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt:29-40`, `app/src/main/java/com/clibeats/di/ProviderModule.kt`
- Impact: Plans written from these docs will target modules that don't exist; onboarding and audits waste time.
- Fix approach: add a superseding ADR for the direct-provider architecture, correct the entity list in ADR-003, and delete/archive gateway references.

**Open broken windows and deferred items:**
- Files: `.planning/WINDOWS.md` (2 open: instrumented DAO tests never run, detekt-suppression deviation), `.planning/STATE.md` (D-02), `.planning/milestones/v1.0-phases/03-database-local-persistence-layer/deferred-items.md`
- Impact: `/gsd-ship` is designed to block while `open_count > 0`; these have been open since 2026-08-05.
- Fix approach: run `connectedDebugAndroidTest` on an emulator (CI job) and resolve/waive the detekt entry after the config refinement above.

## Known Bugs

**`PoTokenGenerator` leaks WebViews and background polling runnables:**
- Symptoms: one unreleased `WebView` plus a 500 ms `Handler` poll loop per token generation; after the timeout the WebView still loads video content in the background.
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt:82-107` (`WebView(context)` never `destroy()`ed), `:126-150` (poll loop), `:158-164` (timeout `postDelayed` never removed)
- Trigger: any YouTube stream that falls through `NewPipeExtractorResolver` (i.e. every NewPipe failure ⇒ PO-token fallback).
- Workaround: none in code; `invalidate()` only clears the token, not the WebView.
- Fix: keep one long-lived WebView (created/destroyed on the main thread), remove callbacks in `finally`, and call `webView.destroy()`; consider moving PO-token generation out of the app entirely.

**PO-token caching assumes a 12-hour TTL that YouTube does not guarantee:**
- Symptoms: stale tokens are reused because `tokenTtlMs = 12h` is a local guess; hardcoded fallback video id `NJAv_7lHUIU` is embedded as the token-generation page.
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt:43,154`
- Trigger: YouTube rotating token validity, or that placeholder video becoming unavailable.
- Fix: invalidate on first `LOGIN_REQUIRED`/`403` (currently only `StreamCacheManager` is invalidated in `PlayerAdapter.retryAfterStreamError`), and use a configurable/real video id.

**Lowering the audio cache limit does not evict immediately (download cache):**
- Symptoms: `setSongCacheMb` writes `cacheManager.maxCacheSizeBytes` but never calls `evictLruIfNeeded()`; the user sees a smaller limit while the directory stays over it until the next download.
- Files: `app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt:47-54`, `app/src/main/java/com/clibeats/data/cache/CacheManager.kt:58-71`
- Trigger: Settings → Storage → reduce song cache.
- Fix: call `cacheManager.evictLruIfNeeded()` after the assignment (and expose it as suspend).

**Queue restore can resume the wrong track when queue rows outlive songs:**
- Symptoms: `queue_items` has no foreign key to `songs` and `getQueueSongs()` inner-joins, so missing songs silently shorten the restored list while `lastQueueIndex` still refers to the pre-shrink index (`startIndex.coerceIn` then lands on a neighbouring track).
- Files: `app/src/main/java/com/clibeats/data/local/entity/QueueEntity.kt`, `app/src/main/java/com/clibeats/data/local/dao/QueueDao.kt:28-42`, `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:146-170`
- Trigger: deleting a song that is in the persisted queue, then relaunching.
- Fix: add `@ForeignKey` (CASCADE) on `songId`, or persist the current track id rather than only an index.

**Home screen always claims the provider is online:**
- Symptoms: the "System Status" block hardcodes `[ ONLINE ]` and `NetworkMonitor` is never observed anywhere.
- Files: `app/src/main/java/com/clibeats/presentation/home/HomeScreen.kt:97`, `app/src/main/java/com/clibeats/data/network/NetworkMonitor.kt`, `app/src/main/java/com/clibeats/di/NetworkModule.kt:135-139`
- Trigger: airplane mode / offline launch.
- Fix: collect `NetworkMonitor.isOnline` into `HomeUiState` and render `[ OFFLINE ]`; reuse it to skip network prefetch.

**Audius playback has no mirror fallback:**
- Symptoms: `stream()` returns the single `discoveryprovider.audius.co/v1/tracks/{id}/stream` URL; if that discovery node or its 302 target is unhealthy, the track fails after one retry and is skipped (`MAX_CONSECUTIVE_FAILURES = 5`).
- Files: `app/src/main/java/com/clibeats/data/provider/AudiusMusicProvider.kt:100-104`, `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:420-445`
- Trigger: Audius content-node/gateway flakiness (documented in `.planning/debug/2026-08-10-no-audio-and-audius-catalog.md`).
- Fix: query `/v1/tracks/{id}/stream` against a small list of discovery providers or use the embedded `stream.mirrors` as fallbacks.

**`highQualityStreaming` preference has no effect:**
- Symptoms: the toggle persists and renders, but no provider or cache path reads it; `NewPipeExtractorResolver` always picks `audioStreams.maxByOrNull { averageBitrate }`.
- Files: `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:61-62`, `app/src/main/java/com/clibeats/presentation/settings/SettingsScreen.kt:309`, `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt:82`
- Trigger: turning "high quality streaming" off (expectation: lower bitrate ⇒ less data).
- Fix: pass the preference into stream selection (bitrate cap) or remove the setting from the UI.

## Security Considerations

**Exported media service accepts every controller:**
- Risk: `PlaybackService` is `android:exported="true"` and `LibraryCallback.onConnect` calls `super.onConnect` without verifying `controller.packageName`/uid; any installed app can browse the queue metadata (titles/artists/artwork URIs) and drive playback.
- Files: `app/src/main/AndroidManifest.xml:30-41`, `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt:130-136`, `app/src/main/AndroidManifest.xml:44-50` (`MediaButtonReceiver` exported)
- Current mitigation: none beyond platform signature checks on `MediaBrowserService`.
- Recommendations: keep export for System UI/Android Auto but reject unknown controllers with `ConnectionResult.reject()` unless `controller.isTrusted`; log rejected packages.

**Release logs contain user queries and listening activity:**
- Risk: `DiagnosticLogger` writes search queries, track titles/ids, and stream hosts to logcat at INFO with no `BuildConfig.DEBUG` guard; `TimberTelemetryTracker` logs every analytics event.
- Files: `app/src/main/java/com/clibeats/util/DiagnosticLogger.kt:31,46,55`, `app/src/main/java/com/clibeats/telemetry/TimberTelemetryTracker.kt:12`
- Current mitigation: `TimberCrashReporter` redacts `Bearer …` tokens only (`app/src/main/java/com/clibeats/telemetry/TimberCrashReporter.kt:14`).
- Recommendations: gate `DiagnosticLogger` behind `BuildConfig.DEBUG`, or route through a logger with a release-level allowlist.

**Cloud backup includes the library database:**
- Risk: `android:allowBackup="true"`; backup rules exclude only the encrypted prefs and the DataStore file, so `clibeats.db` (9 tables: playlists, liked songs, full play history, local file paths) is uploaded to Google Drive.
- Files: `app/src/main/AndroidManifest.xml:12`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- Current mitigation: secure prefs are excluded (and correctly documented as undecryptable on a new device).
- Recommendations: exclude `database` domain entirely (or provide a `db` include list without history), since SAF grants and cache paths do not survive device transfer anyway.

**Cache filenames derived from attacker/API-controlled ids (path traversal / arbitrary write):**
- Risk: `File(cacheDir, "$songId.mp3")` uses the raw composite id. `ProviderId.composite()` allows arbitrary `sourceId` (SAF document ids look like `primary:Music/Album/track.flac` and contain `/`), and `PlaylistExchangeManager.import` accepts arbitrary `providerId`/`sourceId` from a JSON file.
- Files: `app/src/main/java/com/clibeats/data/cache/CacheManager.kt:40`, `app/src/main/java/com/clibeats/domain/provider/ProviderId.kt:13-16`, `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt:100-106`
- Current mitigation: the bug is currently unreachable from the UI because downloads are unwired — it becomes live the moment the download feature is connected.
- Recommendations: hash the id (`UUID.nameUUIDFromBytes` or SHA-256 hex) before using it as a filename; validate `providerId` against `ProviderRegistry` on import.

**WebView executes remote page script with a JS bridge:**
- Risk: `WebView` with `javaScriptEnabled` + `domStorageEnabled` loads `youtube.com/embed/...` and registers `@JavascriptInterface object` as `PoTokenBridge`; no `shouldOverrideUrlLoading` restriction, no `setAllowFileAccess(false)` call.
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt:82-107`
- Current mitigation: the bridge only hands back token/visitor strings.
- Recommendations: restrict navigation to the youtube.com host, disable file/content access explicitly, and destroy the WebView after use.

**Token storage lifecycle:**
- Risk: `EncryptedSharedPreferences` is excluded from backup (good) but uses a deprecated alpha library; `AppPreferences` keeps an in-memory mirror via `OnSharedPreferenceChangeListener` that is never unregistered (singleton, so bounded).
- Files: `app/src/main/java/com/clibeats/di/StorageModule.kt:22-52`, `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:118-137`
- Recommendations: migrate to Tink Keystore as ADR-003 planned; drop the listener when migrating.

**Export writes signed stream URLs into a user-visible file:**
- Risk: `CliBeatsTrack.sourceUrl` is populated from `song.streamUrl`, which for YouTube is a signed `googlevideo` URL with `expire=`; exported `clibeats.json` therefore contains short-lived signed URLs (and full artwork URLs) that are useless on import but leak request parameters.
- Files: `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt:70-80`, `:148` (`safeStreamUrl` keeps only local/`file` URLs on import)
- Recommendations: never export `sourceUrl`/`artworkUrl` for non-local providers.

## Performance Bottlenecks

**Blocking DataStore reads during Hilt graph construction (cold start):**
- Problem: `runBlocking { appPreferences.*.first() }` runs on whichever thread first requests the singleton — the main thread at startup for both the Coil cache and the Media3 cache.
- Files: `app/src/main/java/com/clibeats/di/ImageLoaderModule.kt:35`, `app/src/main/java/com/clibeats/data/cache/MediaCaches.kt:53`
- Cause: DataStore's first read includes file IO; the DoD budget is cold start < 2 s.
- Improvement path: read the limits asynchronously after startup (default value first, then `collect` and apply), e.g. expose `applyLimits()` on `MediaCaches`/`ResizableDiskCache` called from a coroutine.

**`SimpleCache` is constructed eagerly on the main thread:**
- Problem: `MediaCaches` builds `SimpleCache(...)` in its constructor (`MediaCaches.kt:55-56`), and `PlaybackModule.provideExoPlayer` injects `mediaCaches` non-lazily (`app/src/main/java/com/clibeats/di/PlaybackModule.kt:66`), so the cache index/database is opened during first player request.
- Cause: no `Lazy`/background init; SimpleCache initialization scans the cache directory.
- Improvement path: construct `SimpleCache` on `Dispatchers.IO` (`dagger.Lazy<MediaCaches>` everywhere) or defer until the first `prepare()`.

**Synchronous cache metadata reads on the main thread every 500 ms:**
- Problem: `PlayerAdapter.updateState()` calls `streamResolver.knownBitrateKbps(...)`, which falls through to `MediaCaches.cachedBitrateKbps()` → `SimpleCache.getContentMetadata()` (SQLite) on the main thread, twice a second while playing, whenever the format reports no bitrate (typical for YouTube).
- Files: `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:386`, `app/src/main/java/com/clibeats/data/cache/MediaCaches.kt:76-85`, ticker at `PlayerAdapter.kt:86-92` (`POLL_INTERVAL_MS = 500`)
- Cause: bitrate lookup is treated as cheap; it is a disk/DB call.
- Improvement path: memoize per track id in `PlayerAdapter` (like `localBitrateKbps`), do the lookup off the main thread, and only recompute on track change.

**Whole playback tree recomposes twice per second:**
- Problem: the ticker publishes a new `PlaybackState` (position, buffered) every 500 ms; `PlayerBarHost`, `NowPlayingScreen`, `QueueScreen` and `HomeScreen` all `collectAsState()` the same object.
- Files: `app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt:156-197`, `app/src/main/java/com/clibeats/presentation/player/NowPlayingScreen.kt:95`, plus 31 `collectAsState()` call sites
- Cause: single monolithic state object; no lifecycle-aware collection (`collectAsStateWithLifecycle` is used nowhere).
- Improvement path: split position/progress into its own state read only inside the progress composable, use `collectAsStateWithLifecycle`, and use `derivedStateOf` for formatted strings.

**Position persisted to DataStore every 5 s while playing:**
- Problem: `persistPlaybackPosition()` writes 4 keys via `dataStore.edit` every `POSITION_SAVE_STEP_MS` (5 s) for the entire playback session.
- Files: `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:125-141,272`
- Cause: no throttle beyond the 5 s bucket; DataStore rewrites the whole preferences file.
- Improvement path: throttle to 30 s / on pause / on `onStop`, and batch with `saveQueueMetadata` writes.

**Full queue rewrite on every queue mutation:**
- Problem: `persistQueueItems` upserts every queued song and `replaceQueue` deletes + reinserts all rows for each emission; adding 20 songs one by one triggers 20 full rewrites.
- Files: `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:100-106,174-190`
- Improvement path: `debounce(500)` the collection and diff against the previous persisted list.

**Coil prefetch without size hints:**
- Problem: `imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())` with no `.size(...)`, both in `prefetchArtwork` and `MainLayout`'s hi-res preload.
- Files: `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:240-248`, `app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt:163-168`
- Cause: decodes at original resolution into the memory cache (25 % of heap) for every cover.
- Improvement path: `.size(ARTWORK_FULL_PX)` (and a smaller size for the notification) so the memory cache holds a predictable bitmap.

**Internet Archive search fans out per query:**
- Problem: each search performs up to `METADATA_CAP = 8` `/metadata/{id}` calls (4 in parallel) before returning anything, and `trending` does the same.
- Files: `app/src/main/java/com/clibeats/data/provider/InternetArchiveMusicProvider.kt:112-150`
- Improvement path: return search hits immediately and backfill duration/file selection lazily, or cache metadata per identifier.

## Fragile Areas

**YouTube extraction stack (`NewPipeExtractorResolver` + `PoTokenGenerator` + `StreamCacheManager` + `YouTubeClientStrategy`):**
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`, `.../PoTokenGenerator.kt`, `.../StreamCacheManager.kt`, `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt:90-185`
- Why fragile: three undocumented fallbacks (NewPipe → InnerTube with PO token → per-client fallback chain), all dependent on an unofficial pinned extractor version and on YouTube anti-bot behaviour; `NewPipe.init` is global and initialized lazily with an `AtomicBoolean`.
- Safe modification: keep all extraction behind `YouTubeMusicProvider.stream()`, never call `ServiceList`/`api.player` from UI or repositories, and add a diagnostic log line per fallback decision (already partially present via `DiagnosticLogger`).
- Test coverage: none for any of these four classes.

**`StreamResolver` / `TrackUriResolver` resolution inside ExoPlayer's loader thread:**
- Files: `app/src/main/java/com/clibeats/playback/TrackUriResolver.kt:25-45`, `app/src/main/java/com/clibeats/playback/StreamResolver.kt:26-68`
- Why fragile: `runBlocking` around network extraction inside `ResolvingDataSource.Resolver.resolveDataSpec`; a slow provider stalls the loader up to OkHttp's 20 s read timeout, and the placeholder `Track` it builds has blank title/artist, so providers that need metadata (Jamendo/IA re-fetch by id anyway) can behave differently from the UI path. Expiry detection is a regex on `expire=` (`StreamResolver.kt:85`), so any provider that signs differently is treated as non-expiring.
- Safe modification: keep the placeholder contract (`p` + `id` params) stable; any change to `ProviderId` format breaks cached queue entries and persisted `streamUrl`s.
- Test coverage: none.

**Error recovery is single-shot and stateful:**
- Files: `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:406-445` (`skipAfterError` 406, `retryAfterStreamError` 423)
- Why fragile: one retry per media id (`retriedMediaId`), `consecutiveFailures` only resets on `STATE_READY` (not on a manual play/skip), and `retryAfterStreamError` only strips `http` URLs before re-preparing. A stream that 403s twice is permanently skipped until the queue changes.
- Safe modification: add instrumentation around `onPlayerError` before changing the retry policy; keep `MAX_CONSECUTIVE_FAILURES` behaviour (queue-wide outage) intact.
- Test coverage: `PlayerAdapterQueueTest`/`PlayerAdapterTest` cover queue ops; retry/skip paths are untested.

**Background service start is best-effort and excepted away:**
- Files: `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:48-53`, `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt:78-81`
- Why fragile: `runCatching { context.startService(...) }` silently swallows `IllegalStateException` when Android forbids a background start; playback then runs with no foreground notification and can be killed. `onTaskRemoved` deliberately keeps the service alive, which on Android 14+ depends on the foreground-service type being posted in time.
- Safe modification: test on API 34 with the app swiped away; if you switch to `startForegroundService`, guarantee `startForeground` within 5 s.

**Local library scanning conflates "permission revoked" with "folder empty":**
- Files: `app/src/main/java/com/clibeats/data/scan/LocalMusicScanner.kt:69-110`
- Why fragile: a failed `listDocuments` becomes `emptyListing`, the "delete songs not found" pass then removes those songs (cascading to playlist/liked rows), and the M3U mirror playlists are deleted too. A transient SAF failure, ejected SD card, or revoked grant wipes the user's local library entries and playlists.
- Safe modification: only run deletions when every tree listed successfully; otherwise mark the scan as partial and leave rows untouched.
- Test coverage: only `PlaylistFileParserTest` exists; the scanner itself is untested.

**Local artwork files are written forever and never cleaned:**
- Files: `app/src/main/java/com/clibeats/data/scan/LocalMusicScanner.kt:225,244-253`
- Why fragile: every changed file writes `filesDir/local_artwork/<uuid>.img` (no size cap); deleting songs (`songDao.deleteByIds`) never removes the image, so `filesDir` grows unbounded and is included in device backups.
- Safe modification: store artwork in `cacheDir` (or delete alongside `deleteByIds`).

**`AdjustableLruEvictor` size accounting depends on callbacks alone:**
- Files: `app/src/main/java/com/clibeats/data/cache/MediaCaches.kt:118-182`
- Why fragile: `onCacheInitialized()` is a no-op and `currentSize` starts at 0, so the evictor's view of existing spans relies on Media3 replaying `onSpanAdded` at init; `trim()` synchronizes on the cache instance to avoid deadlock, which is subtle and untested. A wrong `currentSize` either never evicts or over-evicts.
- Safe modification: add a unit test with a fake `Cache` that verifies `trim()` after a simulated startup population; do not change the `TreeSet` comparator without re-checking `CacheSpan` equality.

**Playlist import/export assumes a well-formed file:**
- Files: `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt:84-138`, `app/src/main/java/com/clibeats/data/playlist/CliBeatsFileCodec.kt`
- Why fragile: imports run inside `withTransaction` only when `database != null`; with the nullable-DI constructor they can partially apply. Provider ids from the file are accepted verbatim, so a typo yields tracks that can never resolve a stream.
- Safe modification: validate `providerId` against `ProviderRegistry` and reject unknown ids; remove the nullable database path.

## Scaling Limits

**Cache directories live under `cacheDir`:**
- Current capacity: song cache bounded by `cacheMaxMb` (default 512 MB), Coil artwork by `artworkCacheMb` (default 128 MB), both in `context.cacheDir`.
- Limit: Android may clear `cacheDir` under storage pressure, invalidating `CacheIndexEntity` rows and silently removing "downloaded" tracks (the index tolerates it via `file.exists()`, but the index rows leak).
- Scaling path: keep explicit downloads in `filesDir`, keep streaming caches in `cacheDir`, and reconcile the index on startup.

**Unbounded history and `LIKE '%query%'` search:**
- Current capacity: `history` grows one row per play; `SongDao.searchAsFlow`/`searchLocalAsFlow` scan with leading-wildcard LIKE on title/artist/album.
- Limit: history size is unbounded (`getPlayedOnlineAsFlow` groups all rows per query), and full-table LIKE scans degrade past a few thousand local tracks.
- Scaling path: prune history to N days, index `history(song_id)` (currently absent, see `app/src/main/java/com/clibeats/data/local/entity/HistoryEntity.kt`), and move local search to FTS4/FTS5.

**Artwork memory cache at 25 % of heap:**
- Current capacity: `MEMORY_CACHE_PERCENT = 0.25` in `app/src/main/java/com/clibeats/di/ImageLoaderModule.kt:20`.
- Limit: prefetching full-resolution covers (see above) can fill it quickly on low-RAM devices and push `AsyncImage` back to disk.
- Scaling path: bound sizes at request time and lower the percentage on `ActivityManager.isLowRamDevice`.

## Dependencies at Risk

**`org.schabi.newpipe:extractor` (0.27.1, pinned):**
- Risk: unofficial, fast-moving extractor that breaks whenever YouTube changes its player/anti-bot flow; the repo already contains an in-progress investigation (`.planning/debug/yt-po-token-investigation.md`) describing `LOGIN_REQUIRED` across all InnerTube clients.
- Impact: primary YouTube stream path fails, forcing the WebView PO-token fallback (which leaks memory, see above).
- Migration plan: treat extraction as a remote/configurable concern; keep the multi-provider registry (Audius/IA/Jamendo/local already work) and degrade YouTube gracefully with a user-facing message instead of silent skips.

**InnerTube internal endpoints + hardcoded client strategies:**
- Risk: `app/src/main/java/com/clibeats/data/provider/api/InnerTubeApi.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/YouTubeClientStrategy.kt`, and `InnerTubeHeaderInterceptor` encode unofficial `youtubei/v1` request shapes that can be invalidated server-side without notice.
- Impact: search and streaming for the default provider (`DEFAULT_PROVIDER_ID = "youtube_music"`).
- Migration plan: keep it behind `YouTubeMusicProvider`, add a fallback to another provider on `ProviderResult.Error` (currently the UI just shows the error).

**`androidx.security:security-crypto` 1.1.0-alpha06:**
- Risk: alpha SDK with deprecated APIs (see Tech Debt).
- Impact: token storage; a major upgrade changes both the API and the on-disk format (needs a re-encryption migration).
- Migration plan: Tink `KeystoreAesGcm` behind the existing `SharedPreferences` façade in `StorageModule`/`AppPreferences`.

**Media3 `UnstableApi` surface used broadly:**
- Risk: `@OptIn(UnstableApi::class)` / `@file:Suppress("ForbiddenImport")` appear in caching, playback and DI files (`MediaCaches.kt`, `ResizableDiskCache.kt` (experimental Coil), `PlaybackModule.kt`, `PlayerAdapter.kt`, `TrackUriResolver.kt`, `LocalOrRemoteMediaSourceFactory.kt`).
- Impact: minor-version upgrades can change `CacheDataSource`, `ResolvingDataSource`, `CacheEvictor` or `SimpleCache` semantics without a deprecation cycle.
- Migration plan: pin the media3 version in the catalog and add instrumentation tests for cache + resolver before each bump.

## Missing Critical Features

**Downloads / true offline mode:**
- Problem: no UI entry point for `TrackDownloadManager`; "offline" in Library means "local scanned files" only (`app/src/main/java/com/clibeats/presentation/library/LibraryScreen.kt:83`).
- Blocks: offline listening of streamed catalogs; the settings screen already promises cache management for "songs", which users will read as downloads.

**Network awareness:**
- Problem: `NetworkMonitor` exists but is not injected anywhere; nothing disables Home trending, search, or prefetch when offline.
- Blocks: graceful offline UX; currently the user gets provider errors and repeated failed stream resolutions.

**Real crash reporting / telemetry sink:**
- Problem: `TelemetryTracker` and `CrashReporter` are bound to `TimberTelemetryTracker`/`TimberCrashReporter`, which only call `Log.*`, and neither interface is injected into any consumer.
- Blocks: field validation promised in `.planning/PROJECT.md` ("beta feedback, crash telemetry review").

**Sleep timer survives process death / backgrounding:**
- Problem: the countdown is a `viewModelScope` coroutine (`app/src/main/java/com/clibeats/presentation/player/PlayerViewModel.kt:163-197`); it dies when the ViewModel clears, leaving audio playing past the requested time.
- Blocks: reliable sleep timer for background listening.

**Settings persistence for the "high quality" toggle:**
- Problem: see Known Bugs — the value is stored but not honoured.

## Test Coverage Gaps

**Untrusted/extraction code is entirely untested:**
- What's not tested: PO-token WebView lifecycle, NewPipe extraction/format selection, stream URL cache expiry, `StreamResolver` expiry regex, `TrackUriResolver` placeholder resolution.
- Files: `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt`, `.../NewPipeExtractorResolver.kt`, `.../StreamCacheManager.kt`, `app/src/main/java/com/clibeats/playback/StreamResolver.kt`, `app/src/main/java/com/clibeats/playback/TrackUriResolver.kt`
- Risk: the most fragile, most provider-dependent code has zero regression protection; a library bump silently breaks playback.
- Priority: High

**Caching layer untested for eviction/resize semantics:**
- What's not tested: `AdjustableLruEvictor` accounting, `MediaCaches.setSongLimitMb`/`setArtworkLimitMb`, `ResizableDiskCache.resize`.
- Files: `app/src/main/java/com/clibeats/data/cache/MediaCaches.kt`, `app/src/main/java/com/clibeats/data/cache/ResizableDiskCache.kt` (only `CacheManagerTest` exists)
- Risk: over-limit growth or accidental mass eviction goes unnoticed.
- Priority: High

**Local scanner and playlist exchange untested end to end:**
- What's not tested: `LocalMusicScanner.scan()` (permission revocation, unchanged-file skipping, stale-row deletion), `PlaylistExchangeManager.import/export`, M3U mirror sync.
- Files: `app/src/main/java/com/clibeats/data/scan/LocalMusicScanner.kt`, `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt`
- Risk: the data-loss scenario above (revoked folder wipes library) has no test that would catch a fix or a regression.
- Priority: High

**Playback service / media session callbacks untested:**
- What's not tested: `PlaybackService.LibraryCallback` (root, children, resumption, `onSetMediaItems`), `afterRestore` ordering.
- Files: `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`
- Risk: the "resume card" contract (why `MediaLibraryService` is used at all) can regress without any failing test.
- Priority: Medium

**Instrumented tests never run; CI does not exercise them:**
- What's not tested in CI: `app/src/androidTest/java/com/clibeats/data/local/dao/*` (Room DAO integration), Compose UI tests (`androidTestImplementation` deps present, `manifest` debug artifact declared).
- Files: `.github/workflows/ci.yml` (only `testDebugUnitTest`), `app/src/androidTest/**`, `.planning/WINDOWS.md` broken window #2
- Risk: Room migrations and UI flows are unverified on device; DoD items "UI & Compose Tests" and "≥85 % coverage" are not enforced anywhere.
- Priority: Medium

**ViewModel tests bypass the real dependencies:**
- What's not tested: behaviour with `LocalMusicScanner`/`MediaCaches` absent (the nullable-DI path), and `SettingsViewModel` cache-limit side effects.
- Files: `app/src/test/java/com/clibeats/presentation/settings/SettingsViewModelTest.kt`, `app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt`
- Risk: the null-tolerant production path (no scanning, no cache usage) is only exercised in tests, never asserted.
- Priority: Low

---

*Concerns audit: 2026-11-19*
