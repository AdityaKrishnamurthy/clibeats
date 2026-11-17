---
last_mapped_commit: 916a56379cbb0bf07eb302edd4489c7633418417
last_mapped_at: 2026-11-17
---
<!-- refreshed: 2026-11-17 -->
# Architecture

**Analysis Date:** 2026-11-17

## System Overview

CLIBeats is a single-module Android application (`:app`) built as **MVVM + Clean Architecture** with Hilt DI. There is no backend service: all state is on-device (Room + DataStore), and music is sourced directly from third-party providers over HTTP.

```text
┌──────────────────────────────────────────────────────────────────────┐
│                        Presentation (`presentation/`)                │
│  Compose screens · ViewModels · UiState sealed interfaces · Theme    │
│  `MainActivity.kt` · `presentation/home/HomeScreen.kt` ·             │
│  `presentation/layout/MainLayout.kt` · `presentation/theme/`         │
└───────────────┬───────────────────────────────▲──────────────────────┘
                │ domain interfaces / StateFlow │
┌───────────────▼───────────────────────────────┴──────────────────────┐
│                          Domain (`domain/`)                          │
│  `Track` · `Album` · `Artist` · `Playlist` · `PlaybackState`         │
│  `MusicProvider` · `ProviderRegistry` · `ProviderResult`             │
│  Repository interfaces (`SongRepository`, `PlaybackRepository`, …)   │
└───────────────▲───────────────────────────────┬──────────────────────┘
                │ implements                     │ uses
┌───────────────┴───────────────────┐   ┌────────▼─────────────────────┐
│      Data (`data/`)               │   │  Playback (`playback/`)      │
│  Provider impls + Retrofit APIs   │◄──┤  `PlayerAdapter` (ExoPlayer) │
│  Repository impls                 │   │  `StreamResolver`            │
│  Room (`data/local/`)             │   │  `service/PlaybackService`   │
│  DataStore/EncryptedSharedPrefs   │   └────────┬─────────────────────┘
│  Cache · Downloads · Exchange     │            │
└───────────────┬───────────────────┘            │
                │                                │
      ┌─────────▼─────────┐          ┌───────────▼────────────┐
      │ Room `clibeats.db`│          │ Media3 ExoPlayer       │
      │ + `audio_cache/`  │          │ (singleton) + MediaSession│
      └───────────────────┘          └────────────────────────┘

  Cross-cutting: `di/` (Hilt), `telemetry/`, `util/DiagnosticLogger.kt`
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Application shell | `@HiltAndroidApp` entry; no custom logic | `app/src/main/java/com/clibeats/CLIBeatsApp.kt` |
| Activity / navigation host | Sets Compose content, resolves theme, manual `when`-based destination switching | `app/src/main/java/com/clibeats/MainActivity.kt` |
| Root layout | Adaptive nav suite (bar/rail/drawer), top bar, content slot, persistent player bar | `app/src/main/java/com/clibeats/presentation/layout/MainLayout.kt` |
| Destination model | Sealed `NavDestination` with `mainTabs` and `all` lists | `app/src/main/java/com/clibeats/presentation/layout/NavDestination.kt` |
| Domain track model | Provider-agnostic track (`id`, `streamUrl`, `providerId`) | `app/src/main/java/com/clibeats/domain/model/Track.kt` |
| Provider contract | `search`/`trending`/`getTrack`/`stream`/`playlists`/`queue` | `app/src/main/java/com/clibeats/domain/provider/MusicProvider.kt` |
| Provider selection | Ordered registry, id lookup, default (`youtube_music`) | `app/src/main/java/com/clibeats/domain/provider/ProviderRegistry.kt`, `app/src/main/java/com/clibeats/data/provider/MusicProviderRegistry.kt` |
| Provider result type | `Success`/`Error`/`Loading` sealed class (no exceptions across layer boundary) | `app/src/main/java/com/clibeats/domain/provider/ProviderResult.kt` |
| Provider id scheme | Composite ids `providerId:sourceId` | `app/src/main/java/com/clibeats/domain/provider/ProviderId.kt` |
| Provider implementations | YouTube Music (InnerTube + NewPipe + PO token), Internet Archive, Audius, Jamendo, Local | `app/src/main/java/com/clibeats/data/provider/*MusicProvider.kt` |
| HTTP clients | One Retrofit `OkHttpClient` + separate InnerTube client with header interceptor | `app/src/main/java/com/clibeats/di/NetworkModule.kt` |
| Repository impls | Room ↔ domain mapping, playback orchestration, history, library | `app/src/main/java/com/clibeats/data/repository/*.kt` |
| Playback orchestration | `PlayerAdapter` wraps the singleton `ExoPlayer`; `StreamResolver` lazily resolves URLs | `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`, `app/src/main/java/com/clibeats/playback/StreamResolver.kt` |
| Background audio | `MediaSessionService` with manual notification + action intents | `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt` |
| Persistence | Room database v3, 9 entities, 8 DAOs, schema export | `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt` |
| Preferences | DataStore (non-sensitive) + EncryptedSharedPreferences (auth token) | `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt` |
| Audio cache / downloads | LRU file cache with Room index; OkHttp download manager | `app/src/main/java/com/clibeats/data/cache/CacheManager.kt`, `app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt` |
| Playlist exchange | `clibeats.json` export/import codec + file manager | `app/src/main/java/com/clibeats/data/playlist/CliBeatsFile.kt`, `app/src/main/java/com/clibeats/data/playlist/PlaylistExchangeManager.kt` |
| DI graph | Hilt `@SingletonComponent` modules | `app/src/main/java/com/clibeats/di/*.kt` |
| Telemetry | Local-only event/crash logging interfaces (no network SDK) | `app/src/main/java/com/clibeats/telemetry/*.kt` |
| Diagnostics | Structured trace-id logging funnel | `app/src/main/java/com/clibeats/util/DiagnosticLogger.kt` |

## Pattern Overview

**Overall:** Layered Clean Architecture (Presentation → Domain ← Data) with MVVM and Hilt DI. ADR-001 (`docs/adr/ADR-001-architecture-and-di-strategy.md`) is the governing decision.

**Key Characteristics:**
- **No use-case layer.** ViewModels call `domain.repository` interfaces or `MusicProvider` directly; business rules live in repository impls and providers.
- **Interface-based boundaries.** All cross-layer contracts are interfaces in `domain/` (`MusicProvider`, `ProviderRegistry`, five repository interfaces), bound to impls in `di/RepositoryModule.kt` and `di/ProviderModule.kt`.
- **Reactive state everywhere.** Repositories and playback expose `Flow`/`StateFlow`; screens collect with `collectAsState()`; UiState is expressed as sealed interfaces.
- **Provider-agnostic core.** `Track` carries `providerId`; ids are composite (`ProviderId.composite`), so library/queue/cache keys stay unique across providers.
- **Single long-lived player.** One `ExoPlayer` `@Singleton` is shared by `PlayerAdapter` and `PlaybackService`; the service never releases it.
- **Local-first.** No backend, no accounts; Room + file cache + DataStore are the sources of truth.
- **Manual navigation.** No Navigation-Compose; a sealed class + `when` in `MainActivity` drives screens.
- **TUI design system.** Monochrome palette, monospaced typography, `TuiBlock`/`TuiTextField`/`SongTableRow` primitives, accent color via `LocalAccentColor`.

## Layers

**Presentation:**
- Purpose: render state, capture input, expose actions.
- Location: `app/src/main/java/com/clibeats/presentation/`
- Contains: one package per feature (`home`, `search`, `library`, `queue`, `playlist`, `settings`, `more`, `player`), plus `layout/`, `component/`, `theme/`.
- Depends on: `domain` models/repositories/providers. In practice (and in violation of the stated layer rule) also `data.preferences.AppPreferences`, `data.cache.CacheManager`, `data.playlist.PlaylistExchangeManager`.
- Used by: `MainActivity.kt`.

**Domain:**
- Purpose: pure Kotlin contracts and models.
- Location: `app/src/main/java/com/clibeats/domain/`
- Contains: `model/` (`Track`, `Album`, `Artist`, `Playlist`, `PlaybackState`, `PlaybackException`), `provider/` (`MusicProvider`, `ProviderRegistry`, `ProviderResult`, `ProviderId`), `repository/` (5 interfaces).
- Depends on: nothing Android except one leak: `domain/repository/HistoryRepository.kt` imports `com.clibeats.data.local.entity.HistoryEntity` (suppressed `ForbiddenImport`).
- Used by: presentation, data, playback.

**Data:**
- Purpose: implement domain contracts over network, Room, disk, and preferences.
- Location: `app/src/main/java/com/clibeats/data/`
- Contains: `provider/` (5 provider impls, Retrofit APIs in `provider/api/`, DTOs in `provider/dto/`, mappers in `provider/mapper/`, YouTube internals in `provider/youtube/`), `repository/` (5 impls), `local/` (Database, DAOs, entities, mappers, type converters), `preferences/`, `cache/`, `download/`, `playlist/`, `network/`.
- Depends on: `domain`, `playback` (`PlaybackRepositoryImpl` → `PlayerAdapter`, `StreamResolver`), `util`.
- Used by: domain consumers through interfaces; presentation (violations listed below).

**Playback:**
- Purpose: own the ExoPlayer instance, map player callbacks to `StateFlow`, provide background audio.
- Location: `app/src/main/java/com/clibeats/playback/`
- Contains: `PlayerAdapter.kt` (queue + transport + state mapping), `StreamResolver.kt` (unresolved `streamUrl` → provider call), `service/PlaybackService.kt`.
- Depends on: `domain`, `data.cache.CacheManager`, `util.DiagnosticLogger`.
- Used by: `data/repository/PlaybackRepositoryImpl.kt`, `di/PlaybackModule.kt`, `di/AppModule` consumers.

**DI:**
- Purpose: assemble the graph.
- Location: `app/src/main/java/com/clibeats/di/` (11 modules).
- Contains: `NetworkModule` (OkHttp/Retrofit/APIs/NetworkMonitor), `DatabaseModule` (DB + DAOs), `RepositoryModule` (`@Binds`), `ProviderModule` (ordered provider list), `PlaybackModule` (ExoPlayer), `CacheModule`, `DownloadModule`, `StorageModule` (DataStore + EncryptedSharedPreferences), `ImageLoaderModule` (Coil), `TelemetryModule` (`@Binds`), `AppModule` (empty marker).
- Depends on: all layers (composition root).
- Used by: Hilt-generated components.

**Telemetry / Util:**
- Purpose: local logging and event abstraction.
- Location: `app/src/main/java/com/clibeats/telemetry/`, `app/src/main/java/com/clibeats/util/`
- Contains: `TelemetryTracker`/`TimberTelemetryTracker`, `CrashReporter`/`TimberCrashReporter`, `AnalyticsEvent`; `DiagnosticLogger`.
- Note: classes are named "Timber*" but there is no Timber dependency — they use `android.util.Log`.

## Data Flow

### Primary Request Path: Search

1. User types in `TuiTextField`; `SearchScreen` collects `viewModel.query` (`app/src/main/java/com/clibeats/presentation/search/SearchScreen.kt`).
2. `SearchViewModel.onQueryChange` updates `_query`; pipeline debounces 300 ms, requires 2+ chars, switches on `appPreferences.activeProviderId` (`app/src/main/java/com/clibeats/presentation/search/SearchViewModel.kt`).
3. Provider resolved from `ProviderRegistry` (default `youtube_music`); `provider.search(q)` runs.
4. `YouTubeMusicProvider.search` posts an InnerTube request and maps JSON with `toTrackList()` (`app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`, `app/src/main/java/com/clibeats/data/provider/mapper/TrackMapper.kt`).
5. Result mapped to `SearchUiState.Success/Error/Loading/Idle`; rendered as rows with play / add-to-queue / play-next actions.

### Primary Request Path: Play a Track

1. Row click → `PlayerViewModel.playTrack(track)` (`app/src/main/java/com/clibeats/presentation/player/PlayerViewModel.kt`).
2. `PlaybackRepositoryImpl.playTrack` generates a trace id, logs, and launches on its own IO `CoroutineScope` (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`).
3. `StreamResolver.resolve` returns the track unchanged if `streamUrl` is set; otherwise calls the matching provider's `stream(trackId)` and copies the URL into the track, throwing `PlaybackException.StreamResolutionFailed` on error (`app/src/main/java/com/clibeats/playback/StreamResolver.kt`).
4. `PlayerAdapter.playTrack` starts `PlaybackService`, sets a `MediaItem` (prefers a cached file from `CacheManager.getCachedFile`), prepares and plays (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`).
5. Player listener callbacks rebuild `_playbackState`/`_queueFlow`; a 500 ms ticker updates position while playing.

### Background Playback / Notification

1. `PlayerAdapter.startPlaybackService()` calls `startForegroundService(PlaybackService)`.
2. `PlaybackService` (a `MediaSessionService`) injects the **same** `ExoPlayer` singleton and builds a `MediaSession` (`app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`).
3. Because nothing binds a `MediaController`, the service manually builds and posts a `MediaStyle` notification and handles `ACTION_PLAY_PAUSE` / `ACTION_NEXT` / `ACTION_PREVIOUS` in `onStartCommand`.
4. `onDestroy` releases only the `MediaSession`, never the player (releasing it would permanently kill playback).

### Queue Persistence and Restore

1. `PlaybackRepositoryImpl.init` launches three collectors on an IO scope: restore queue, persist `queueFlow` → `QueueDao.replaceQueue` + `songDao.upsertAll`, and persist playback metadata (index/position/repeat/shuffle) → `AppPreferences.saveQueueMetadata`.
2. Empty queue → `QueueDao.clearQueue()`.
3. On next launch the same `init` calls `restorePersistentQueue()` which reads songs from `QueueDao.getQueueSongs()` plus preferences and calls `playerAdapter.restoreQueue(...)` on `Dispatchers.Main`.
4. Failures are swallowed through `runCatching` + `DiagnosticLogger.logError`.

### Library Browsing

1. Room emits `SongEntity` flows from `SongDao.getAllAsFlow()`.
2. `SongRepositoryImpl` maps entities to `Track`.
3. `LibraryViewModel` groups by artist and (album, artist), producing a `LibraryUiState` consumed by the Tracks / Artists / Albums tabs (`app/src/main/java/com/clibeats/presentation/library/LibraryViewModel.kt`).

### Settings & Theme

1. `AppPreferences` reads DataStore keys (`theme_mode`, `accent_color_name`, `active_provider_id`, `cache_max_mb`, `queue` metadata).
2. `ThemeViewModel` combines mode+accent into a `StateFlow<ThemeState>`; `MainActivity` feeds it into `CliBeatsTheme`.
3. `SettingsViewModel` combines provider list, cache size, token presence, and theme settings into `SettingsUiState`; write actions call `AppPreferences` / `CacheManager` / `PlaylistExchangeManager`.

### Offline Cache / Download

1. `TrackDownloadManager.downloadTrack` issues an OkHttp GET for `track.streamUrl` on an IO scope.
2. `CacheManager.saveTrackToCache` writes to `context.cacheDir/audio_cache/<id>.mp3`, upserts `CacheIndexEntity`, then evicts LRU while over `maxCacheSizeBytes` (default 500 MB).
3. Playback prefers the cached file in `PlayerAdapter.toMediaItem`.

### Provider Selection

1. `AppPreferences.activeProviderId` (DataStore) is the only selector.
2. `MusicProviderRegistry.getProvider(id)` → `defaultProvider()` falls back to `youtube_music`, then to the first registered provider.
3. `ProviderModule` fixes registration order: YouTube Music, Internet Archive, Audius, Jamendo, Local.

**State Management:**
- ViewModels expose `StateFlow` built with `stateIn(WhileSubscribed(5_000))`; UI state is sealed interfaces (`HomeUiState`, `SearchUiState`, `LibraryUiState`, `QueueUiState`, `PlaylistUiState`).
- Playback state is a singleton `StateFlow` in `PlayerAdapter`, proxied by `PlaybackRepository`.
- Screen selection lives in `MainActivity` as `remember { mutableStateOf<NavDestination>(...) }` — not saveable, no back stack.
- Persisted state: Room (library/playlists/queue/cache index/history/likes/saved albums+artists) and DataStore (settings, queue metadata).

## Key Abstractions

**`MusicProvider` (domain contract):**
- Purpose: isolate all provider-specific networking/extraction.
- Examples: `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`, `AudiusMusicProvider.kt`, `InternetArchiveMusicProvider.kt`, `JamendoMusicProvider.kt`, `LocalMusicProvider.kt`
- Pattern: every implementation returns `ProviderResult<T>` and never throws across the boundary; each declares a snake_case `providerId` and a human `displayName`.

**`ProviderId` composite keys:**
- Purpose: keep `Track.id` unique across providers.
- Pattern: `ProviderId.composite(providerId, sourceId)` on write; `ProviderId.rawSourceId(providerId, id)` before calling provider APIs (`app/src/main/java/com/clibeats/domain/provider/ProviderId.kt`).

**`PlayerAdapter` (playback façade):**
- Purpose: single owner of ExoPlayer queue/transport plus state mapping.
- Pattern: imperative methods (`playTrack`, `setQueue`, `moveTrack`, `removeFromQueue`, …) that mutate the player and call `updateState()`; state exposed as `StateFlow` (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`).

**`TuiBlock` / `TuiTextField` / `SongTableRow` (design primitives):**
- Purpose: enforce the terminal look consistently.
- Examples: `app/src/main/java/com/clibeats/presentation/component/TuiBlock.kt`, `TuiTextField.kt`, `SongTableRow.kt`, `PlayerBar.kt`, `TrackArtwork.kt`
- Pattern: border-only flat surfaces, `LocalAccentColor` for active state, monospaced typography.

**Theme composition locals:**
- Purpose: let deep components read accent/mode without prop drilling.
- Pattern: `LocalAccentColor` / `LocalThemeMode` provided by `CliBeatsTheme` (`app/src/main/java/com/clibeats/presentation/theme/CliBeatsColors.kt`, `CliBeatsTheme.kt`).

**Provider DTO → domain mappers:**
- Purpose: keep DTO shapes out of the domain.
- Examples: `app/src/main/java/com/clibeats/data/provider/mapper/TrackMapper.kt`, `AudiusMapper.kt`, `JamendoMapper.kt`, `InternetArchiveMapper.kt`
- Pattern: top-level extension functions returning `Track?` (null = skip unparseable item), composite ids applied here.

## Entry Points

**Application:**
- Location: `app/src/main/java/com/clibeats/CLIBeatsApp.kt`
- Triggers: process start (`android:name=".CLIBeatsApp"`).
- Responsibilities: `@HiltAndroidApp` graph initialization only.

**Activity:**
- Location: `app/src/main/java/com/clibeats/MainActivity.kt`
- Triggers: `MAIN`/`LAUNCHER` intent filter (`app/src/main/AndroidManifest.xml`).
- Responsibilities: edge-to-edge, theme resolution, destination switching, hosting `MainLayout` + `PlayerBar`.

**Media service:**
- Location: `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`
- Triggers: `startForegroundService` from `PlayerAdapter`, plus `androidx.media3.session.MediaSessionService` intent filter.
- Responsibilities: foreground notification, media session, notification action handling.

**Build entry:**
- `settings.gradle.kts` (single `:app` module), `app/build.gradle.kts`, `gradle/libs.versions.toml`.

## Architectural Constraints

- **Threading:** UI/player mutations must run on `Dispatchers.Main`; `PlaybackRepositoryImpl` explicitly wraps `playerAdapter` calls in `withContext(Dispatchers.Main)`. Provider/repository IO uses `Dispatchers.IO` scopes. `PlayerAdapter` uses a `SupervisorJob` + Main scope for its position ticker.
- **Global/singleton state:** one `ExoPlayer` (`di/PlaybackModule.kt`), one `PlayerAdapter`, one `PlaybackRepositoryImpl` (each `@Singleton`), one Room DB, one in-memory `StreamCacheManager` (`ConcurrentHashMap`), plus object singletons `DiagnosticLogger` and `ProviderRegistry.DEFAULT_PROVIDER_ID`.
- **No use-case layer:** adding business rules means either extending a repository impl or putting logic in a ViewModel; do not introduce `domain/usecase/` without an ADR.
- **Provider isolation:** `domain` must not import provider implementation packages; all provider calls go through `MusicProvider` / `ProviderRegistry`.
- **Layer enforcement:** Detekt `ForbiddenImport` bans `com.clibeats.data.*` outside the data layer (`config/detekt/detekt.yml`). Files that legitimately cross the boundary must opt out with `@file:Suppress("ForbiddenImport")`.
- **Room schema:** `exportSchema = true`; schemas committed under `app/schemas/com.clibeats.data.local.CliBeatsDatabase/` (versions 1–3). Any entity change requires a migration registered in `DatabaseModule` + a new schema JSON.
- **Background service contract:** never release or recreate the singleton player in `PlaybackService`; never remove `startForeground` from `onStartCommand`.
- **Permissions:** only `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` are declared; local MediaStore scanning is not implemented.
- **Build configuration:** `compileSdk`/`targetSdk` 34, `minSdk` 26, JDK 17, Kotlin 2.0.21, Compose BOM 2024.09.03, Gradle wrapper 8.9.
- **Release signing:** `release` build type uses the debug signing config — not production-ready.

## Anti-Patterns

### Presentation importing the data layer

**What happens:** `SettingsViewModel` imports `CacheManager`, `PlaylistExchangeManager`, `AppPreferences`; `SearchViewModel`, `HomeViewModel`, `ThemeViewModel` import `AppPreferences` (`app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt`, `search/SearchViewModel.kt`, `home/HomeViewModel.kt`, `theme/ThemeViewModel.kt`).
**Why it's wrong:** ViewModels now depend on concrete data classes instead of domain contracts; each file needs `@file:Suppress("ForbiddenImport")` to pass Detekt, and changes to storage ripple into UI.
**Do this instead:** define domain-level interfaces (e.g. `UserPreferences`, `CacheController`, `PlaylistExchange`) in `domain/repository/` and `@Binds` the data impls in `di/RepositoryModule.kt`, mirroring how `SongRepository` is wired.

### Domain depending on a data entity

**What happens:** `HistoryRepository` returns `Flow<List<HistoryEntity>>` and imports `com.clibeats.data.local.entity.HistoryEntity` (`app/src/main/java/com/clibeats/domain/repository/HistoryRepository.kt`).
**Why it's wrong:** the domain layer is no longer pure Kotlin and cannot be tested without Room types; the documented ADR-001 promise is broken.
**Do this instead:** return a domain model (e.g. a `PlayHistoryEntry`) and map inside `HistoryRepositoryImpl`, as `SongRepository` does with `Track`.

### Repository reaching into playback internals

**What happens:** `PlaybackRepositoryImpl` imports and owns `PlayerAdapter` and `StreamResolver` directly (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`).
**Why it's wrong:** the data layer depends on a sibling layer; `PlaybackRepository` cannot be tested without a Media3 player.
**Do this instead:** declare a playback port in the domain layer (e.g. `PlaybackEngine`) implemented by `PlayerAdapter`, and inject that interface into the repository impl.

### Business logic in ViewModels

**What happens:** search debounce/min-length, provider fallback, trending fetch, and library grouping all live in ViewModels (`SearchViewModel`, `HomeViewModel`, `LibraryViewModel`).
**Why it's wrong:** logic is untestable without a `ViewModel`/`viewModelScope` and is duplicated per screen (provider resolution appears in both `SearchViewModel` and `HomeViewModel`).
**Do this instead:** extract a thin `domain/usecase` (e.g. `SearchTracks`, `ObserveTrending`) once an ADR authorizes the layer; at minimum centralize provider resolution in a single helper.

### Manual navigation with non-saveable state

**What happens:** `MainActivity` keeps `var selectedDestination by remember { mutableStateOf(...) }` and renders screens with a `when` (`app/src/main/java/com/clibeats/MainActivity.kt`).
**Why it's wrong:** destination is lost on configuration change/process death, there is no back stack, no deep links, and no per-destination ViewModel scoping.
**Do this instead:** either use `rememberSaveable` plus an explicit back handler for incremental fixes, or migrate to Navigation-Compose with `NavDestination.route` values.

### Blocking cache lookup on the main thread

**What happens:** `PlayerAdapter.toMediaItem()` calls `runBlocking { cacheManager.getCachedFile(id) }` while building media items, and `setQueue` maps over every track (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`).
**Why it's wrong:** a Room query per track on the caller thread risks jank/ANR for large queues.
**Do this instead:** resolve cached paths asynchronously before constructing the queue (e.g. in `PlaybackRepositoryImpl` on IO) and pass `Track`s that already carry a `file://` URL, as `LocalMusicProvider` does.

### Cross-feature utility import

**What happens:** `HomeScreen` and `PlaylistScreen` import `formatDuration` from `com.clibeats.presentation.search` (`app/src/main/java/com/clibeats/presentation/home/HomeScreen.kt`, `presentation/playlist/PlaylistScreen.kt`).
**Why it's wrong:** creates a hidden dependency between sibling screens and makes `search` a de-facto util module.
**Do this instead:** move formatting helpers into a shared location such as `presentation/component/` or a new `presentation/format/` package.

### Constructor side effects in a singleton repository

**What happens:** `PlaybackRepositoryImpl.init` starts three long-lived coroutines and performs queue restoration immediately on construction (`app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`).
**Why it's wrong:** work begins at injection time (possibly before the UI/service is ready), and tests get unpredictable background activity.
**Do this instead:** expose an idempotent `start()`/`initialize()` invoked from `CLIBeatsApp` or a lifecycle-aware component, or keep collectors but gate them behind an explicit call.

## Error Handling

**Strategy:** returning result types at provider boundaries, swallowing-and-logging at repository/playback boundaries, and letting UI represent failures as UiState.

**Patterns:**
- Providers wrap calls in `runCatching { ... }.getOrElse { ProviderResult.Error(message, cause) }` — never throw to callers (e.g. `YouTubeMusicProvider`, `AudiusMusicProvider`, `JamendoMusicProvider`).
- Repositories use `runCatching { }.onFailure { DiagnosticLogger.logError(...) }` without rethrowing (e.g. queue restore/persist in `PlaybackRepositoryImpl`).
- `PlaybackException` (`StreamResolutionFailed`, etc.) is the only domain-level exception (`app/src/main/java/com/clibeats/domain/model/PlaybackException.kt`), thrown by `StreamResolver` and caught in `PlaybackRepositoryImpl`.
- `ProviderResult.Loading` exists but is largely unused — UI loading states are emitted by explicit `Loading` UiState values.
- `DiagnosticLogger` is failure-tolerant: `safeLog` falls back to `println` when `android.util.Log` is unavailable (unit tests).
- Notification permission/opt-in failures are suppressed with `@SuppressLint` rather than handled (`PlaybackService`).

## Cross-Cutting Concerns

**Logging:** `com.clibeats.util.DiagnosticLogger` (trace-id based: `SEARCH_REQUEST`, `STREAM_URL_RESOLVED`, `MEDIA_ERROR`, …). Telemetry interfaces (`TimberTelemetryTracker`, `TimberCrashReporter`) log via `android.util.Log` only — no remote sink.

**Validation:** SQL LIKE wildcards escaped via `String.escapeForLike()` (`app/src/main/java/com/clibeats/data/local/dao/SongDao.kt`); provider responses filtered for required ids/stream URLs; `ProviderId.rawSourceId` guards provider prefixes; settings writes validated in ViewModels (blank playlist names rejected).

**Authentication:** no user accounts. Only an optional `auth_token` stored in `EncryptedSharedPreferences` with an Android Keystore `MasterKey` (`app/src/main/java/com/clibeats/di/StorageModule.kt`, `data/preferences/AppPreferences.kt`).

**Network posture:** only unauthenticated public provider APIs (`music.youtube.com` InnerTube, `archive.org`, `discoveryprovider.audius.co`, `api.jamendo.com`). Jamendo requires a `JAMENDO_CLIENT_ID` Gradle property injected via `BuildConfig`.

**Security:** release build is **not minified** (`isMinifyEnabled = false`) and is signed with the debug key; crash/telemetry output sanitizes `Bearer` tokens. See `docs/adr/ADR-007-caching-downloads-security.md`.

**Accessibility:** content descriptions on nav icons and artwork; Compose test semantics (`semantics { contentDescription = "Search music" }`). See `docs/adr/ADR-008-performance-budgets-accessibility.md`.

---

*Architecture analysis: 2026-11-17*
