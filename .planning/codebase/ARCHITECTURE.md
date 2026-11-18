---
last_mapped_commit: a77dc37a72fa6a498fb1ac17a68d814fbe3770cb
last_mapped_at: 2026-11-19
---
<!-- refreshed: 2026-11-19 -->

# Architecture

**Analysis Date:** 2026-11-19

## System Overview

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ Entry points                                                                 │
│  `app/.../CLIBeatsApp.kt`        `app/.../MainActivity.kt`                   │
│  `app/.../PlaybackService.kt`    `androidx.media3.session.MediaButtonReceiver`│
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ setContent / service binding
┌───────────────────────────────▼──────────────────────────────────────────────┐
│ Presentation — Jetpack Compose (Material 3 + custom TUI design system)       │
│  `presentation/layout/MainLayout.kt`  `presentation/layout/NavDestination.kt` │
│  `presentation/home|search|library|queue|playlist|player|settings/`           │
│  `presentation/onboarding|theme|component/`                                   │
│  Each feature = Screen.kt + ViewModel.kt (@HiltViewModel) + *UiState.kt       │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ StateFlow / method calls
┌───────────────────────────────▼──────────────────────────────────────────────┐
│ Domain — pure Kotlin contracts (models + interfaces, no Android imports)     │
│  `domain/model/`  `domain/repository/`  `domain/provider/`                   │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ implemented by
┌───────────────────────────────▼──────────────────────────────────────────────┐
│ Data — Room, DataStore, providers, caches, scanner, exchange                 │
│  `data/repository/`  `data/local/`  `data/provider/`  `data/preferences/`    │
│  `data/cache/`  `data/download/`  `data/scan/`  `data/playlist/`             │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ Track objects / MediaItems
┌───────────────────────────────▼──────────────────────────────────────────────┐
│ Playback subsystem                                                           │
│  `playback/PlayerAdapter.kt`       singleton wrapper around singleton ExoPlayer │
│  `playback/StreamResolver.kt`      provider stream lookup + expiry checks     │
│  `playback/TrackUriResolver.kt`    clibeats:// placeholder → real URI         │
│  `playback/service/PlaybackService.kt`  MediaLibrarySession + notification    │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────────────────────┐
│ DI — Hilt (KSP), all modules in SingletonComponent                            │
│  `di/AppModule.kt` `di/NetworkModule.kt` `di/DatabaseModule.kt`               │
│  `di/PlaybackModule.kt` `di/ProviderModule.kt` `di/RepositoryModule.kt`       │
│  `di/CacheModule.kt` `di/DownloadModule.kt` `di/ImageLoaderModule.kt`         │
│  `di/StorageModule.kt` `di/TelemetryModule.kt`                                │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `CLIBeatsApp` | `@HiltAndroidApp` application; supplies the Hilt-built Coil `ImageLoader` via `ImageLoaderFactory` | `app/src/main/java/com/clibeats/CLIBeatsApp.kt` |
| `MainActivity` | Sole Activity; theme + onboarding gate; builds the six-tab `HorizontalPager` shell | `app/src/main/java/com/clibeats/MainActivity.kt` |
| `MainLayout` | Adaptive nav suite, 48dp top bar, persistent `PlayerBar`, full-screen Now Playing overlay | `app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt` |
| `NavDestination` | Sealed tab model (`Home, Search, Library, Queue, Playlists, Settings`) with route/label/icon | `app/src/main/java/com/clibeats/presentation/layout/NavDestination.kt` |
| `PlaybackService` | `MediaLibraryService` owning the `MediaLibrarySession`; notification, lock screen, resume card, library browse | `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt` |
| `PlayerAdapter` | Only class that mutates `ExoPlayer`; maps player listener events into `StateFlow<PlaybackState>` / `StateFlow<List<Track>>` | `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt` |
| `StreamResolver` | Asks the active `MusicProvider` for a playable URL; caches/invalidates; detects `expire=` URLs | `app/src/main/java/com/clibeats/playback/StreamResolver.kt` |
| `TrackUriResolver` | Media3 `ResolvingDataSource.Resolver` turning `clibeats://track?p=…&id=…` into cache file or fresh URL | `app/src/main/java/com/clibeats/playback/TrackUriResolver.kt` |
| `LocalOrRemoteMediaSourceFactory` | Routes `content://`/`file://` to a non-caching source; everything else through `CacheDataSource` | `app/src/main/java/com/clibeats/playback/LocalOrRemoteMediaSourceFactory.kt` |
| `PlaybackRepositoryImpl` | Domain-facing facade over `PlayerAdapter`; persists/restores queue and position; records history; prefetches | `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt` |
| `ProviderRegistry` + `MusicProviderRegistry` | Ordered provider list and active-provider lookup | `app/src/main/java/com/clibeats/domain/provider/ProviderRegistry.kt`, `app/src/main/java/com/clibeats/data/provider/MusicProviderRegistry.kt` |
| `YouTubeMusicProvider` | Default provider; NewPipeExtractor → InnerTube PO-token → client fallback chain | `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt` |
| `CliBeatsDatabase` | Room DB v3 with 9 entities, 8 DAOs, migrations 1→2 and 2→3 | `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt` |
| `AppPreferences` | DataStore settings + encrypted `auth_token` via `EncryptedSharedPreferences` | `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt` |
| `MediaCaches` | Media3 `SimpleCache` for songs with adjustable LRU + Coil artwork disk cache | `app/src/main/java/com/clibeats/data/cache/MediaCaches.kt` |
| `LocalMusicScanner` | SAF `DocumentsContract` walk, `MediaMetadataRetriever` tags, M3U/PLS mirroring | `app/src/main/java/com/clibeats/data/scan/LocalMusicScanner.kt` |
| `PlaylistExchangeManager` | Import/export `clibeats.json` through SAF | `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt` |

## Pattern Overview

**Overall:** MVVM + Clean Architecture (Presentation → Domain → Data) with a dedicated playback subsystem, wired by Hilt in a single `SingletonComponent`. Documented in `docs/adr/ADR-001-architecture-and-di-strategy.md`.

**Key Characteristics:**
- Three source layers (`presentation`, `domain`, `data`) plus `playback` as an engine-side subsystem that depends on both `domain` and `data`.
- One screen feature package = `XScreen.kt` (Composable) + `XViewModel.kt` (`@HiltViewModel`) + `XUiState.kt` (sealed interface or data class). Examples: `presentation/library/LibraryScreen.kt`, `presentation/library/LibraryViewModel.kt`, `presentation/library/LibraryUiState.kt`.
- No use-case/interactor layer. ViewModels call repository interfaces directly; providers are reached through `ProviderRegistry` or through `StreamResolver`.
- Reactive state only: `StateFlow` (and one `SharedFlow` for transient player messages) produced by the data/playback layer, collected in Compose with `collectAsState()`.
- Single `ExoPlayer` singleton is shared by `PlayerAdapter` (UI-facing) and `PlaybackService` (system-facing); the service never releases it.
- Provider-agnostic catalog access through `MusicProvider`; every `Track.id` is a composite `providerId:sourceId`.
- Local-first persistence: Room for songs/playlists/history/queue/likes/saved albums/artists/cache index; DataStore for settings.

## Layers

**Presentation:**
- Purpose: Compose UI and screen state.
- Location: `app/src/main/java/com/clibeats/presentation/`
- Contains: feature packages (`home`, `search`, `library`, `queue`, `playlist`, `player`, `settings`, `onboarding`), `layout/`, `theme/`, and reusable `component/`.
- Depends on: `domain` repository/provider interfaces, plus (in practice) a few data-layer types — see Anti-Patterns.
- Used by: `MainActivity` for screen composition.

**Domain:**
- Purpose: Framework-free models and contracts.
- Location: `app/src/main/java/com/clibeats/domain/`
- Contains: `model/` (`Track`, `Playlist`, `Album`, `Artist`, `PlaybackState`, `RepeatMode`, `PlaybackException`), `repository/` (5 interfaces), `provider/` (`MusicProvider`, `ProviderRegistry`, `ProviderResult`, `ProviderId`).
- Depends on: Kotlin stdlib/coroutines only — with one documented exception: `domain/repository/HistoryRepository.kt` imports `data.local.entity.HistoryEntity`.
- Used by: `data` implementations, `playback`, all ViewModels.

**Data:**
- Purpose: Persistence, remote catalog adapters, caches, device scanning, import/export.
- Location: `app/src/main/java/com/clibeats/data/`
- Contains: `repository/*Impl`, `local/{CliBeatsDatabase,dao,entity,mapper}`, `provider/{api,dto,mapper,youtube}` + five `*MusicProvider`, `preferences/AppPreferences`, `cache/{MediaCaches,CacheManager,ResizableDiskCache}`, `download/TrackDownloadManager`, `scan/{LocalMusicScanner,PlaylistFileParser}`, `playlist/{CliBeatsFile,PlaylistExchangeManager}`.
- Depends on: domain interfaces/models, Android framework, Room, DataStore, Retrofit/OkHttp, Coil, Media3 cache, NewPipeExtractor.
- Used by: `domain` consumers through `RepositoryModule` bindings, and by `playback` (`StreamResolver`, `TrackUriResolver`).

**Playback:**
- Purpose: Audio engine, stream resolution, queue state, background service.
- Location: `app/src/main/java/com/clibeats/playback/`
- Contains: `PlayerAdapter`, `StreamResolver`, `TrackUriResolver`, `LocalOrRemoteMediaSourceFactory`, `service/PlaybackService`.
- Depends on: `domain` (`Track`, `PlaybackState`, `ProviderRegistry`), `data` (`MediaCaches`, `CacheManager`, `YouTubeMusicProvider` for cache invalidation), Media3.
- Used by: `PlaybackRepositoryImpl` (UI path) and `PlaybackService` (system path).

**DI:**
- Purpose: Object graph construction.
- Location: `app/src/main/java/com/clibeats/di/`
- Contains: 10 Hilt modules, all `@InstallIn(SingletonComponent::class)`; `RepositoryModule`/`TelemetryModule` use `@Binds`, the rest `@Provides`.
- Depends on: everything.
- Used by: Hilt-generated components at app start.

## Data Flow

### Primary Request Path — play a track from a list

1. Screen calls `PlayerViewModel.playQueue(tracks, index)` (`app/src/main/java/com/clibeats/presentation/player/PlayerViewModel.kt:89`) or `playTrack` (`:84`).
2. `PlayerViewModel` delegates to `PlaybackRepository.setQueue/playTrack` (`app/src/main/java/com/clibeats/domain/repository/PlaybackRepository.kt`).
3. `PlaybackRepositoryImpl` generates a trace id and launches on `Dispatchers.Main` into `PlayerAdapter` (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:191-224`).
4. `PlayerAdapter.setQueue` starts `PlaybackService`, converts each `Track` to a `MediaItem`, sets items and calls `prepare()`/`play()` (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:183-195`).
5. `Track.toMediaItem` uses a direct URL only when it is non-`expire`; otherwise it builds a `clibeats://track` placeholder (`PlayerAdapter.kt:443-464`).
6. ExoPlayer asks `LocalOrRemoteMediaSourceFactory` for a source; remote items go through `CacheDataSource(SimpleCache)` → `ResolvingDataSource` (`app/src/main/java/com/clibeats/di/PlaybackModule.kt:64-83`).
7. `TrackUriResolver.resolveDataSpec` returns a cached file from `CacheManager` or calls `StreamResolver.resolve` (`app/src/main/java/com/clibeats/playback/TrackUriResolver.kt:26-41`).
8. `StreamResolver` picks the provider via `ProviderRegistry`, calls `MusicProvider.stream(trackId)` and returns `track.copy(streamUrl = …)` (`app/src/main/java/com/clibeats/playback/StreamResolver.kt:22-55`).
9. Provider execution: for YouTube, `StreamCacheManager` → `NewPipeExtractorResolver` → `PoTokenGenerator` + `InnerTubeApi.player` over `YouTubeClientStrategy.FALLBACK_CHAIN` (`app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt:78-188`).
10. Player listener events update `_playbackState` and `_queueFlow` (`PlayerAdapter.kt:101-170`, `:357-392`); `PlayerBarHost` and `NowPlayingScreen` recompose from `PlayerViewModel.playbackState` (`app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt:153-197`).

### Search / trending flow

1. Query enters `SearchViewModel._query` and is debounced 300 ms, filtered to ≥2 chars (`app/src/main/java/com/clibeats/presentation/search/SearchViewModel.kt:79-103`).
2. `activeProviderId` comes from `AppPreferences`, falling back to `ProviderRegistry.DEFAULT_PROVIDER_ID` (`SearchViewModel.kt:45-48`).
3. `flatMapLatest` calls `provider.search(q)`; `ProviderResult` maps to `SearchUiState.Loading/Success/Error`.
4. `onPlay` either queues all results or only the tapped track depending on the `auto_queue_related` preference (`SearchViewModel.kt:55-67`).
5. Home mirrors this with `provider.trending(15)` and `historyRepository.getRecentlyPlayedTracks(50)` (`app/src/main/java/com/clibeats/presentation/home/HomeViewModel.kt:46-110`).

### Queue persistence and restoration flow

1. `PlaybackRepositoryImpl.init` launches `restorePersistentQueue()` on an IO scope (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:94-120`).
2. Restore reads `QueueDao.getQueueSongs()` + `AppPreferences.lastQueueIndex/lastPlaybackPosition/savedRepeatMode/savedShuffleEnabled`, then calls `PlayerAdapter.restoreQueue` on `Dispatchers.Main` (`:147-172`).
3. After restore, `queueFlow.drop(1)` is persisted via `SongDao.upsertAll` + `QueueDao.replaceQueue` (`:174-189`).
4. Position/repeat/shuffle are saved on meaningful changes and every 5 s while playing (`:126-145`).
5. `CompletableDeferred restored` gates `awaitRestored()`; `PlaybackService.afterRestore` uses it so the resume card and media-browser queries see a live queue (`app/src/main/java/com/clibeats/playback/service/PlaybackService.kt:106-116`).

### History + prefetch flow

1. `playbackState.map { currentTrack }.distinctUntilChanged { id }` triggers on each new track (`PlaybackRepositoryImpl.kt:108-119`).
2. `HistoryRepository.recordPlay(songId, providerId)` writes a `HistoryEntity`.
3. `prefetchAfter` warms artwork (Coil) and resolves stream URLs for the next 2 queue items (`:226-243`).

### Background / system-control flow

1. `PlayerAdapter` starts `PlaybackService` with `context.startService` before any playback call (`PlayerAdapter.kt:48-52`).
2. `PlaybackService.onCreate` builds a `MediaLibrarySession` over the injected singleton `ExoPlayer` and registers it with `addSession` (`PlaybackService.kt:53-68`).
3. `LibraryCallback` answers `onGetLibraryRoot` (`clibeats_root` / `clibeats_recent`), `onGetChildren`, `onGetItem`, `onPlaybackResumption`, `onSetMediaItems`, `onAddMediaItems` (`PlaybackService.kt:129-227`).
4. `onTaskRemoved` keeps the service alive while the player has items (`PlaybackService.kt:78-81`); the service never releases the player (`PlaybackService.kt:83-92`).

**State Management:**
- Data/playback state is exposed as `StateFlow` and collected with `collectAsState()`; screens use `SharingStarted.WhileSubscribed(5_000L)` so work stops 5 s after the UI leaves.
- Ephemeral UI signals use `SharedFlow` (`PlayerAdapter.messages` → `PlayerViewModel.playerMessage` with a 3 s auto-clear) and `MutableStateFlow` hints (`PlayerViewModel.hint`).
- Local UI-only state (pager page, Now Playing visibility, search focus) uses `rememberSaveable` / `remember` in `MainActivity` and `MainLayout`.
- No global Compose state holder, no Navigation-Compose back stack: navigation is a `HorizontalPager` indexed by `NavDestination.mainTabs`, with Now Playing as an overlay.

## Key Abstractions

**`MusicProvider` (domain contract):**
- Purpose: One interchangeable catalog/source adapter (search, trending, getTrack, stream, playlists, queue).
- Examples: `data/provider/YouTubeMusicProvider.kt`, `InternetArchiveMusicProvider.kt`, `AudiusMusicProvider.kt`, `JamendoMusicProvider.kt`, `LocalMusicProvider.kt`.
- Pattern: `ProviderResult<T>` sealed class (`Success`/`Error`/`Loading`) instead of exceptions across the boundary; implementations wrap in `runCatching`.

**`ProviderRegistry` (provider selection):**
- Purpose: Ordered provider list + active-provider lookup; the only selection mechanism.
- Examples: `domain/provider/ProviderRegistry.kt`, `data/provider/MusicProviderRegistry.kt`, construction in `di/ProviderModule.kt`.
- Pattern: Registry over an explicitly ordered list (avoids Dagger `List<MusicProvider>` resolution); `DEFAULT_PROVIDER_ID = "youtube_music"`.

**Composite track identity (`ProviderId`):**
- Purpose: Collision-safe ids and cache keys across providers.
- Examples: `domain/provider/ProviderId.kt` (`composite`, `rawSourceId`).
- Pattern: `"$providerId:$sourceId"`, e.g. `youtube_music:<videoId>`, `local:<documentId>`.

**`Track` (domain model):**
- Purpose: The single currency between providers, repositories, player, and UI.
- File: `app/src/main/java/com/clibeats/domain/model/Track.kt` (`id, title, artist, album, durationMs, artworkUrl, streamUrl, providerId`).
- Pattern: Immutable data class; providers fill `streamUrl` lazily or eagerly; persistence strips signed `expire=` URLs.

**`TrackUriResolver` placeholder scheme:**
- Purpose: Decouple queue construction from stream extraction.
- File: `app/src/main/java/com/clibeats/playback/TrackUriResolver.kt:43-52`.
- Pattern: `clibeats://track?p=<providerId>&id=<trackId>` resolved on ExoPlayer's loader thread; also acts as the `SimpleCache` key.

**Room + mapper pattern:**
- Purpose: Keep entities out of the domain and vice versa.
- Examples: `data/local/mapper/SongMapper.kt` (`SongEntity.toDomain()`, `Track.toEntity()`), `data/local/mapper/PlaylistMapper.kt`.
- Pattern: Extension functions in `mapper/` directories; providers use their own `provider/mapper/*Mapper.kt`.

**Repository interface + Impl binding:**
- Purpose: Domain-facing persistence API; swap/testable.
- Examples: `domain/repository/*.kt` bound in `di/RepositoryModule.kt` to `data/repository/*Impl.kt`.
- Pattern: Interfaces return `Flow`/`StateFlow`; blocking work is `suspend`.

**Singleton `ExoPlayer` + `PlayerAdapter`:**
- Purpose: One engine shared by UI and service, with a single mutation point.
- Examples: `di/PlaybackModule.kt:44-88`, `playback/PlayerAdapter.kt`.
- Pattern: `PlayerAdapter` owns the mutable track list and emits `PlaybackState`; `PlaybackRepositoryImpl` is the domain facade.

## Entry Points

**`CLIBeatsApp`:**
- Location: `app/src/main/java/com/clibeats/CLIBeatsApp.kt`
- Triggers: Process start (declared `android:name=".CLIBeatsApp"` in `app/src/main/AndroidManifest.xml`).
- Responsibilities: `@HiltAndroidApp` graph creation; `ImageLoaderFactory` returns the injected Coil loader.

**`MainActivity`:**
- Location: `app/src/main/java/com/clibeats/MainActivity.kt`
- Triggers: LAUNCHER intent.
- Responsibilities: edge-to-edge + system bar styling, theme/onboarding gate (`ThemeViewModel`), six-tab pager shell, double-tap tab gestures, back handling.

**`PlaybackService`:**
- Location: `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`
- Triggers: `startService` from `PlayerAdapter`; media buttons; resume card; media-browser connections (manifest intent filters for `MediaLibraryService`, `MediaSessionService`, `MediaBrowserService`).
- Responsibilities: media notification, foreground playback, session/library callbacks, queue restoration for external controllers.

**`MediaButtonReceiver`:**
- Location: Declared in `app/src/main/AndroidManifest.xml`
- Triggers: `android.intent.action.MEDIA_BUTTON`.
- Responsibilities: Routes hardware/Bluetooth transport controls to `PlaybackService`.

## Architectural Constraints

- **Threading:** Player mutation is posted to `Dispatchers.Main` by `PlayerAdapter` and `PlaybackRepositoryImpl`. Room/DataStore/cache work runs on IO. `TrackUriResolver` intentionally blocks with `runBlocking` on ExoPlayer's loader thread inside `resolveDataSpec` (`app/src/main/java/com/clibeats/playback/TrackUriResolver.kt:32-40`).
- **Global state:** `PlayerAdapter` (`@Singleton`) holds `trackList`, `consecutiveFailures`, `retriedMediaId`, and `localBitrateKbps`. `PlaybackService` holds `librarySession`. `MediaCaches` owns a process-wide `SimpleCache`. `NewPipeExtractorResolver` guards `NewPipe.init` with an `AtomicBoolean`.
- **Single player instance:** `PlaybackModule.provideExoPlayer` is `@Singleton`; `PlaybackService` must not release it (comment at `PlaybackService.kt:85-86`).
- **Startup blocking reads:** `MediaCaches` and `ImageLoaderModule` use `runBlocking { appPreferences.…first() }` during DI construction to size caches (`MediaCaches.kt:47-48`, `di/ImageLoaderModule.kt:24-28`).
- **One module:** The whole app is the `:app` Gradle module (`settings.gradle.kts`); no `:core`, `:feature`, or `:domain` modules. Layering is enforced by package convention and detekt `ForbiddenImport`, not Gradle boundaries.
- **No Navigation-Compose:** Destinations are pager pages; deep links and per-destination back stacks do not exist.
- **Ordering contract:** `ProviderModule` fixes provider display priority: YouTube Music, Internet Archive, Audius, Jamendo, Local.
- **State restoration contract:** `PlaybackRepositoryImpl` intentionally restores before it starts persisting; `drop(1)` prevents the empty initial player state from overwriting the saved queue (`PlaybackRepositoryImpl.kt:94-107`).

## Anti-Patterns

### Domain layer imports a data entity

**What happens:** `domain/repository/HistoryRepository.kt` returns `Flow<List<HistoryEntity>>` from `data/local/entity/HistoryEntity.kt`, with a `@file:Suppress("ForbiddenImport")` explaining the Plan 03-03 spec.
**Why it's wrong:** The domain layer now compiles against Room-annotated data types, breaking ADR-001's "pure Kotlin domain" rule and making domain tests depend on the data layer.
**Do this instead:** Return a domain model (e.g. a `PlayHistory` data class) and map in `HistoryRepositoryImpl`, following `SongMapper.kt`.

### Playback layer depends on presentation helpers

**What happens:** `PlayerAdapter` imports `presentation.component.ARTWORK_FULL_PX` and `presentation.component.artworkUrlFor` (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt:16-17`); `PlaybackRepositoryImpl` does the same (`:21-22`); `PlaybackService` imports `com.clibeats.R`.
**Why it's wrong:** The engine layer gains a compile-time dependency on the UI layer; artwork sizing policy leaks into playback and cannot be reused headlessly.
**Do this instead:** Move artwork URL/size helpers to a neutral package (e.g. `util/` or `domain/model/`) and have presentation and playback both depend on it.

### Optional constructor dependencies for testability

**What happens:** Production classes declare nullable/defaulted collaborators: `PlaybackRepositoryImpl(historyRepository = null, imageLoader = null, appContext = null)` (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt:52-55`), `PlayerViewModel(playlistRepository? = null, libraryRepository? = null)` (`presentation/player/PlayerViewModel.kt:44-45`), `HomeViewModel(historyRepository? = null, playbackRepository? = null)` (`presentation/home/HomeViewModel.kt:36-37`), `PlaylistExchangeManager(database? = null, libraryRepository? = null)`, `SettingsViewModel(localMusicScanner? = null, mediaCaches? = null)`.
**Why it's wrong:** Every call site must branch on null, real misconfiguration is hidden at runtime, and the class silently degrades instead of failing at graph construction.
**Do this instead:** Keep constructor dependencies non-null and required; use constructor injection with test doubles in `app/src/test/...` (as `PlaybackRepositoryImplTest.kt` already does) or a test-specific factory.

### ViewModels reach directly into the data layer

**What happens:** `HomeViewModel`/`SearchViewModel`/`OnboardingViewModel` import `data.preferences.AppPreferences`; `SettingsViewModel` imports `data.cache.CacheManager`, `data.cache.MediaCaches`, `data.scan.LocalMusicScanner`, `data.playlist.PlaylistExchangeManager`.
**Why it's wrong:** There is no use-case boundary; preference keys, cache internals and scanner status become part of the UI contract, and swapping persistence requires editing ViewModels.
**Do this instead:** Expose the needed operations through repository interfaces (e.g. a `SettingsRepository`/`LibraryScanner` contract in `domain/repository/`) as ADR-001 intends.

### Suppression-heavy files

**What happens:** Large files start with blanket suppressions, e.g. `PlayerAdapter.kt` (`ForbiddenImport, TooManyFunctions, ReturnCount, MagicNumber, LargeClass`), `YouTubeMusicProvider.kt` (`CyclomaticComplexMethod`, `LongMethod`, `LoopWithTooManyJumpStatements`), and nearly every data file suppresses `ForbiddenImport`.
**Why it's wrong:** Suppressing a whole file hides new violations and defeats the `config/detekt/detekt.yml` gate; `ForbiddenImport` suppression at file scope also masks accidental UI/data imports.
**Do this instead:** Use narrow `@Suppress` on the specific declaration and keep `ForbiddenImport` out of new files unless the import is genuinely layer-internal.

## Error Handling

**Strategy:** Recoverable catalog/stream failures are values (`ProviderResult.Error`); player failures are handled in `PlayerAdapter` with retry-then-skip; persistence failures are swallowed with diagnostics so playback is never blocked; UI receives short human-readable messages.

**Patterns:**
- `ProviderResult<T>` sealed result for all `MusicProvider` methods (`app/src/main/java/com/clibeats/domain/provider/ProviderResult.kt`).
- `runCatching { … }.getOrElse { ProviderResult.Error(...) }` in every provider implementation.
- `PlaybackException` domain type for stream-resolution failures (`app/src/main/java/com/clibeats/domain/model/PlaybackException.kt`).
- `PlayerAdapter.onPlayerError` → `retryAfterStreamError` (once per track, on HTTP/IO errors) → `skipAfterError` → stop after 5 consecutive failures (`PlayerAdapter.kt:134-139`, `:402-441`).
- `_messages` (`SharedFlow<String>`) surfaces "Couldn't play …" above the player bar; `PlayerViewModel` clears it after 3 s.
- Diagnostics: `DiagnosticLogger` (`util/DiagnosticLogger.kt`) with trace ids for search → resolve → play; `CrashReporter`/`TelemetryTracker` bound to Timber implementations in `di/TelemetryModule.kt`.
- Startup restore failures are caught and logged (`QUEUE_RESTORE_FAILED`) rather than crashing (`PlaybackRepositoryImpl.kt:169-171`).

## Cross-Cutting Concerns

**Logging:** `DiagnosticLogger` (`app/src/main/java/com/clibeats/util/DiagnosticLogger.kt`) for structured playback/search traces; Media3 session logging with tag `CLIBeatsSession`; Timber-backed `TelemetryTracker`/`CrashReporter` (`telemetry/TimberTelemetryTracker.kt`, `telemetry/TimberCrashReporter.kt`).

**Validation:** Provider DTO → domain mappers drop incomplete records (`mapNotNull`, null-safe `JsonElement` navigation in `data/provider/mapper/TrackMapper.kt`); `ProviderId.rawSourceId` normalizes ids; DAO search uses `escapeForLike` (`data/local/dao/SongDao.kt`); signing URLs (`expire=`) are never persisted (`LibraryRepositoryImpl.toggleLike`).

**Authentication:** No account system. `AppPreferences.authToken` is the only credential, stored in `EncryptedSharedPreferences` with an Android Keystore `MasterKey` (`di/StorageModule.kt`), exposed as `Flow<String?>` and surfaced to Settings as `hasAuthToken`.

**Preferences:** `AppPreferences` (`data/preferences/AppPreferences.kt`) is the single settings facade over DataStore (`clibeats_prefs`) plus secure prefs; keys are private constants; defaults live in the getters (e.g. cache 512 MB, artwork 128 MB, provider `youtube_music`, theme `DARK`, accent `GREEN`).

**Theming:** `presentation/theme/` defines `CliBeatsTheme`, `CliBeatsColors`, `CliBeatsTypography` (JetBrains Mono from `app/src/main/res/font/`), `CliBeatsShapes`, `AccentColor`, `CliBeatsThemeMode`; `ThemeViewModel` combines `theme_mode` + `accent_color_name` and defaults to null `onboardingDone` to avoid flashing the wrong screen.

---

*Architecture analysis: 2026-11-19*
