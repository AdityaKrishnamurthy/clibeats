---
last_mapped_commit: a77dc37a72fa6a498fb1ac17a68d814fbe3770cb
last_mapped_at: 2026-11-19
---
# Codebase Structure

**Analysis Date:** 2026-11-19

## Directory Layout

```text
clibeats/
├── app/                                  # Single Android application module (:app)
│   ├── build.gradle.kts                  # AGP, Compose, Hilt/KSP, Room/KSP, Media3, Paparazzi
│   ├── schemas/                          # Exported Room schemas (committed)
│   │   └── com.clibeats.data.local.CliBeatsDatabase/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Activity, PlaybackService, MediaButtonReceiver
│       │   ├── java/com/clibeats/
│       │   │   ├── CLIBeatsApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   ├── di/
│       │   │   ├── domain/
│       │   │   ├── playback/
│       │   │   ├── presentation/
│       │   │   ├── telemetry/
│       │   │   └── util/
│       │   └── res/                      # fonts (JetBrains Mono), drawables, xml backup rules
│       ├── test/                         # JVM unit + Paparazzi screenshot tests (45 files)
│       │   ├── java/com/clibeats/...     # Mirrors main package layout
│       │   └── snapshots/images/         # Paparazzi golden PNGs (committed)
│       └── androidTest/                  # Instrumented Room DAO tests (4 files)
│           └── java/com/clibeats/data/local/dao/
├── build.gradle.kts                      # Root build file (plugin aliases only)
├── settings.gradle.kts                   # Module list + repositories
├── gradle/libs.versions.toml             # Version catalog (all dependency versions)
├── gradle/wrapper/                       # Gradle 8.9 wrapper
├── config/detekt/detekt.yml              # Detekt ruleset
├── docs/                                 # ADRs, user guide, release notes, licenses, design docs
│   ├── adr/                              # ADR-001 … ADR-011
│   ├── evidence/                         # Verification evidence
│   └── screenshots/                      # UI screenshots
├── logo/                                 # SVG/PNG launcher artwork source
├── scripts/check-quality-gates.sh        # Local gate runner (tests, ktlint, detekt, lint)
├── .github/workflows/                    # ci.yml (quality gates), release.yml (tagged builds)
├── .planning/                            # GSD state (codebase docs, milestones, debug, evidence)
├── .agents/AGENTS.md                     # Agent/process standards
├── .gsd/                                 # GSD runtime files
├── .gitignore
├── LICENSE
└── README.md
```

## Directory Purposes

**`app/src/main/java/com/clibeats/data/`:**
- Purpose: All I/O and persistence implementations.
- Contains: repository implementations, Room schema/DAOs/entities/mappers, remote provider adapters + API interfaces + DTOs + mappers, DataStore settings, caches, download manager, local scanner, playlist exchange.
- Key files: `data/repository/PlaybackRepositoryImpl.kt`, `data/local/CliBeatsDatabase.kt`, `data/provider/MusicProviderRegistry.kt`, `data/preferences/AppPreferences.kt`, `data/cache/MediaCaches.kt`.

**`data/local/`:**
- Purpose: Room persistence.
- Contains: `CliBeatsDatabase.kt`, `CliBeatsTypeConverters.kt`, `dao/` (8 DAOs), `entity/` (9 entities), `mapper/` (`SongMapper.kt`, `PlaylistMapper.kt`).
- Key files: `data/local/dao/SongDao.kt`, `data/local/dao/QueueDao.kt`, `data/local/dao/PlaylistDao.kt`, `data/local/dao/CacheIndexDao.kt`.

**`data/provider/`:**
- Purpose: Catalog adapters behind `MusicProvider`.
- Contains: root-level `*MusicProvider.kt` implementations, `api/` (Retrofit interfaces + `InnerTubeHeaderInterceptor`), `dto/` (kotlinx.serialization models, InnerTube `SearchRequest`/`PlayerRequest`), `mapper/` (DTO → `Track`), `youtube/` (resolution internals).
- Key files: `data/provider/YouTubeMusicProvider.kt:78` (resolution chain), `data/provider/youtube/NewPipeExtractorResolver.kt`, `data/provider/youtube/PoTokenGenerator.kt`, `data/provider/youtube/StreamCacheManager.kt`, `data/provider/youtube/YouTubeClientStrategy.kt`, `data/provider/youtube/StreamUrlDeobfuscator.kt`.

**`app/src/main/java/com/clibeats/domain/`:**
- Purpose: Layer contracts with no Android framework usage (one known exception in `HistoryRepository`).
- Contains: `model/` (7 files), `repository/` (5 interfaces), `provider/` (4 files).
- Key files: `domain/model/Track.kt`, `domain/model/PlaybackState.kt`, `domain/provider/MusicProvider.kt`, `domain/provider/ProviderRegistry.kt`, `domain/provider/ProviderId.kt`, `domain/repository/PlaybackRepository.kt`.

**`app/src/main/java/com/clibeats/playback/`:**
- Purpose: Media3 engine, stream resolution, queue state.
- Contains: `PlayerAdapter.kt`, `StreamResolver.kt`, `TrackUriResolver.kt`, `LocalOrRemoteMediaSourceFactory.kt`, `service/PlaybackService.kt`.
- Key files: `playback/PlayerAdapter.kt` (494 lines, player mutations + state), `playback/TrackUriResolver.kt` (placeholder scheme), `playback/service/PlaybackService.kt` (MediaLibrarySession).

**`app/src/main/java/com/clibeats/presentation/`:**
- Purpose: Compose UI + screen state.
- Contains feature packages (`home`, `search`, `library`, `queue`, `playlist`, `player`, `settings`, `onboarding`), plus `layout/`, `theme/`, `component/`.
- Key files: `presentation/layout/MainLayout.kt`, `presentation/layout/NavDestination.kt`, `presentation/player/PlayerViewModel.kt`, `presentation/player/NowPlayingScreen.kt`, `presentation/component/PlayerBar.kt`, `presentation/component/SongTableRow.kt`, `presentation/component/TuiBlock.kt`, `presentation/component/TuiTextField.kt`, `presentation/theme/CliBeatsTheme.kt`.

**`presentation/component/`:**
- Purpose: Cross-feature UI building blocks (terminal-inspired design system usage).
- Contains: `PlayerBar.kt`, `SongTableRow.kt`, `TrackArtwork.kt`, `ArtworkUrl.kt` (`ARTWORK_FULL_PX`, `artworkUrlFor`), `AddToPlaylistDialog.kt` (`isLocalFilePlaylist()`), `ProviderSwitcher.kt`, `ButtonHint.kt`, `TuiBlock.kt`, `TuiTextField.kt`, `FavoritesViewModel.kt`.

**`app/src/main/java/com/clibeats/di/`:**
- Purpose: Hilt graph.
- Contains: `AppModule.kt` (empty placeholder), `NetworkModule.kt`, `DatabaseModule.kt`, `PlaybackModule.kt`, `ProviderModule.kt`, `RepositoryModule.kt`, `CacheModule.kt`, `DownloadModule.kt`, `ImageLoaderModule.kt`, `StorageModule.kt`, `TelemetryModule.kt`.
- Key files: `di/PlaybackModule.kt` (ExoPlayer + cache + resolving data source), `di/ProviderModule.kt` (provider ordering), `di/RepositoryModule.kt` (`@Binds`).

**`app/src/main/java/com/clibeats/telemetry/` and `util/`:**
- Purpose: Cross-cutting diagnostics.
- Contains: `TelemetryTracker.kt`, `CrashReporter.kt` + Timber implementations, `AnalyticsEvent.kt`; `util/DiagnosticLogger.kt`.

**`app/src/test/`:**
- Purpose: JVM tests and screenshot tests.
- Contains: provider, mapper, repository, ViewModel, player adapter, cache, preferences, theme, telemetry, license and integration tests; `snapshots/images/` holds Paparazzi goldens.
- Key files: `app/src/test/java/com/clibeats/presentation/player/PlayerViewModelTest.kt`, `app/src/test/java/com/clibeats/playback/PlayerAdapterQueueTest.kt`, `app/src/test/java/com/clibeats/data/repository/PlaybackRepositoryImplTest.kt`, `app/src/test/java/com/clibeats/theme/PlayerBarScreenshotTest.kt`.

**`app/src/androidTest/`:**
- Purpose: Device/emulator tests requiring a Room database.
- Contains: `data/local/dao/{SongDaoTest,PlaylistDaoTest,HistoryDaoTest,CacheIndexDaoTest}.kt`.

**`docs/`:**
- Purpose: Design and process documentation.
- Contains: `adr/` (11 ADRs), `evidence/`, `screenshots/`, `USER_GUIDE.md`, `RELEASE_NOTES.md`, `LICENSES.md`, and Word design/SRS/SDD/FSD documents.

**`config/detekt/`:**
- Purpose: Single static-analysis ruleset referenced from `app/build.gradle.kts`.

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/clibeats/CLIBeatsApp.kt`: `@HiltAndroidApp` application + Coil loader.
- `app/src/main/java/com/clibeats/MainActivity.kt`: LAUNCHER activity, theme/onboarding gate, six-tab shell.
- `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`: background media service.
- `app/src/main/AndroidManifest.xml`: component declarations and permissions.

**Configuration:**
- `app/build.gradle.kts`: SDK 34/min 26, JVM 17, Compose, KSP Room schema output, detekt, Paparazzi, signing.
- `gradle/libs.versions.toml`: every dependency/plugin version.
- `settings.gradle.kts`: repositories + `include(":app")`.
- `config/detekt/detekt.yml`: detekt config.
- `app/src/main/java/com/clibeats/di/`: all dependency wiring.
- `scripts/check-quality-gates.sh` and `.github/workflows/ci.yml`: the gate commands.

**Core Logic:**
- `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`: queue + playback state.
- `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`: restore/persist/prefetch/history.
- `app/src/main/java/com/clibeats/playback/StreamResolver.kt` + `app/src/main/java/com/clibeats/playback/TrackUriResolver.kt`: lazy stream resolution.
- `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`: multi-stage stream extraction.
- `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt`: schema + migrations.
- `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`: all persisted settings.

**Testing:**
- `app/src/test/java/com/clibeats/`: unit/screenshot tests, packages mirror `main`.
- `app/src/androidTest/java/com/clibeats/data/local/dao/`: Room DAO instrumented tests.
- `app/src/test/snapshots/images/`: committed Paparazzi reference images.

## Naming Conventions

**Files:**
- One top-level type per file, file named after the type in PascalCase: `PlayerAdapter.kt`, `CacheManager.kt`, `NavDestination.kt`.
- Screen triples per feature: `XScreen.kt`, `XViewModel.kt`, `XUiState.kt` (e.g. `LibraryScreen.kt`, `LibraryViewModel.kt`, `LibraryUiState.kt`). `player/` is the exception: `PlayerViewModel.kt` + `NowPlayingScreen.kt` and no `PlayerUiState.kt` (state is the domain `PlaybackState`).
- Implementations end in `Impl`: `data/repository/*RepositoryImpl.kt`.
- Providers end in `MusicProvider`: `YouTubeMusicProvider.kt`, `LocalMusicProvider.kt`.
- Room types: `XEntity` (`entity/`), `XDao` (`dao/`), `CliBeatsDatabase`.
- Hilt modules: `XModule.kt` in `di/`.
- Mappers live in `mapper/` and are named after the source: `SongMapper.kt`, `PlaylistMapper.kt`, `TrackMapper.kt`, `AudiusMapper.kt`, `InternetArchiveMapper.kt`, `JamendoMapper.kt`.

**Packages:**
- Root `com.clibeats`, one package per layer (`data`, `domain`, `playback`, `presentation`, `di`, `telemetry`, `util`).
- Feature packages are single nouns: `presentation/home`, `presentation/search`, `presentation/library`, `presentation/queue`, `presentation/playlist`, `presentation/settings`, `presentation/onboarding`.
- Cross-cutting UI in `presentation/component`, shell in `presentation/layout`, design system in `presentation/theme`.
- Tests mirror production packages exactly under `app/src/test/java/com/clibeats/...` and `app/src/androidTest/java/com/clibeats/...`.

**Kotlin symbols:**
- Types: PascalCase (`PlaybackState`, `ProviderResult`).
- Functions/properties: camelCase (`playTrack`, `setQueue`, `isCurrentLiked`, `playbackState`).
- Constants: `UPPER_SNAKE_CASE` inside `companion object` or private file-level `const val` (`DEFAULT_PROVIDER_ID`, `POLL_INTERVAL_MS`, `SCHEME`).
- Compose Composables: PascalCase, frequently `@Suppress("FunctionNaming")` for screen-level functions (`MainLayout`, `AppContent`, `CliBeatsTopAppBar`).
- Repository methods: `getXAsFlow()` for streams, `upsertX`, `toggleX`, `clearX`, `setX`.
- State fields: private `_x` backing + public `x` (`_playbackState` / `playbackState`, `_query` / `query`).
- Test names use backtick sentences (`PlayerAdapterQueueTest.kt`, ViewModel tests).

**IDs and keys:**
- Composite track ids: `providerId:sourceId` (`domain/provider/ProviderId.kt`).
- Local-playlist mirror ids: `localpl:` prefix (`presentation/component/AddToPlaylistDialog.kt:78`, `data/repository/PlaylistRepositoryImpl.kt:83`).
- Placeholder playback URIs: `clibeats://track?p=<providerId>&id=<trackId>` (`playback/TrackUriResolver.kt`).
- Media3 session roots: `clibeats_root`, `clibeats_recent` (`playback/service/PlaybackService.kt:236-237`).

## Where to Add New Code

**New music provider:**
- Implementation: add `app/src/main/java/com/clibeats/data/provider/<Name>MusicProvider.kt` implementing `domain/provider/MusicProvider.kt`; use `ProviderId.composite` for ids and `ProviderResult` for outcomes.
- API/DTO/mapping: add `data/provider/api/<Name>Api.kt`, `data/provider/dto/<Name>Dtos.kt`, `data/provider/mapper/<Name>Mapper.kt`.
- Networking: add a Retrofit provider function in `app/src/main/java/com/clibeats/di/NetworkModule.kt` if the base URL differs.
- Registration: add the provider to the ordered list in `app/src/main/java/com/clibeats/di/ProviderModule.kt` (position = display priority).
- Tests: `app/src/test/java/com/clibeats/data/provider/<Name>MusicProviderTest.kt` + mapper test.

**New screen/feature:**
- Create `app/src/main/java/com/clibeats/presentation/<feature>/` with `XScreen.kt`, `XViewModel.kt` (`@HiltViewModel`, constructor injection of domain repositories), and `XUiState.kt`.
- If it is a top-level tab, add a `data object` to `presentation/layout/NavDestination.kt` (`route`, `label`, `icon`, `contentDescription`) and include it in `mainTabs`; then add the `when (tabs[page])` branch in `MainActivity.kt:133-145`.
- Use `hiltViewModel()` in the screen; collect state with `collectAsState()`; model loading/success/error as a sealed interface.
- Reuse `presentation/component/` widgets (`SongTableRow`, `TuiBlock`, `TuiTextField`, `PlayerBar`) and `presentation/theme/` tokens.

**New domain model:**
- Add a data class in `app/src/main/java/com/clibeats/domain/model/`; do not import Android or data-layer types.
- Map to/from Room in `data/local/mapper/`; map from provider DTOs in `data/provider/mapper/`.

**New persisted table:**
- Add `data/local/entity/XEntity.kt`, `data/local/dao/XDao.kt`, register both in `data/local/CliBeatsDatabase.kt` (bump `version`, add a `MIGRATION_n_n+1` and `.addMigrations(...)` in `di/DatabaseModule.kt`), and provide the DAO in `di/DatabaseModule.kt`.
- Update `app/schemas/com.clibeats.data.local.CliBeatsDatabase/` by building (schema export is on).
- Add a repository method to the relevant `domain/repository/` interface + `data/repository/*Impl.kt`.

**New setting:**
- Add a private key + `Flow` getter + setter to `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`.
- Surface it through `presentation/settings/SettingsViewModel.kt` + `SettingsUiState.kt`; never read DataStore directly from a Composable.

**New DI binding:**
- Abstract binding → `@Binds` in `di/RepositoryModule.kt` or `di/TelemetryModule.kt`.
- Concrete construction → `@Provides @Singleton` in a focused `di/XModule.kt`, installed in `SingletonComponent`.

**New playback behavior:**
- Queue/player mutation → extend `playback/PlayerAdapter.kt` (the only place that touches `ExoPlayer`).
- Domain API → add to `domain/repository/PlaybackRepository.kt` and delegate from `data/repository/PlaybackRepositoryImpl.kt`.
- Stream URL handling → `playback/StreamResolver.kt`; URI translation → `playback/TrackUriResolver.kt`.

**Utilities:**
- Shared helpers: `app/src/main/java/com/clibeats/util/` (currently `DiagnosticLogger.kt`); keep UI-only helpers in `presentation/component/`.

**Tests:**
- Unit tests: `app/src/test/java/com/clibeats/<mirrored package>/` (`XTest.kt`).
- Instrumented Room tests: `app/src/androidTest/java/com/clibeats/data/local/dao/`.
- Screenshot tests: `app/src/test/java/com/clibeats/theme/` using Paparazzi; update goldens in `app/src/test/snapshots/images/`.

## Special Directories

**`app/schemas/com.clibeats.data.local.CliBeatsDatabase/`:**
- Purpose: Room export of every schema version (configured by `ksp { arg("room.schemaLocation", …) }`).
- Generated: Yes (by KSP on build).
- Committed: Yes — required for migration review.

**`app/src/test/snapshots/images/`:**
- Purpose: Paparazzi golden screenshots (`verifyPaparazziDebug`).
- Generated: Yes (recorded with `recordPaparazziDebug`).
- Committed: Yes.

**`app/src/main/res/font/`:**
- Purpose: JetBrains Mono family (regular/medium/semibold/bold) used by `CliBeatsTypography`.
- Generated: No.
- Committed: Yes.

**`docs/adr/`:**
- Purpose: Architecture decision record set (001 architecture/DI, 003 encrypted storage, 004 Media3 background audio, 005 InnerTube provider, 006 playlists/queue/library, 007 caching/downloads security, 008 performance/accessibility, 009 testing, 010 telemetry privacy, 011 release distribution).
- Generated: No.
- Committed: Yes.

**`.planning/`:**
- Purpose: GSD workflow state, including this `codebase/` directory, `milestones/`, `debug/`, `evidence/`, `research/`.
- Generated: Yes (tooling).
- Committed: Depends on repository policy; treat as project metadata.

**`logo/`:**
- Purpose: Source artwork for the launcher icon (SVG + PNG).
- Generated: No.
- Committed: Yes.

---

*Structure analysis: 2026-11-19*
