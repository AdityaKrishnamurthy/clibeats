---
last_mapped_commit: 916a56379cbb0bf07eb302edd4489c7633418417
last_mapped_at: 2026-11-17
---
# Codebase Structure

**Analysis Date:** 2026-11-17

## Directory Layout

```text
clibeats/
├── app/                              # The single Android application module
│   ├── build.gradle.kts              # Module build: Compose, Hilt, KSP, Room, Media3, Detekt, Paparazzi
│   ├── proguard-rules.pro
│   ├── schemas/                      # Committed Room schema JSON (exportSchema = true)
│   │   └── com.clibeats.data.local.CliBeatsDatabase/
│   │       ├── 1.json 2.json 3.json
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # Application, MainActivity, PlaybackService, permissions
│       │   ├── java/com/clibeats/     # All production Kotlin (131 files)
│       │   └── res/                  # drawables, fonts, launcher mipmaps, xml rules
│       ├── test/java/com/clibeats/    # JVM unit + Paparazzi tests (43 files)
│       │   └── snapshots/images/     # Paparazzi golden PNGs
│       └── androidTest/java/com/clibeats/data/local/dao/  # Room instrumented tests (4 files)
├── config/detekt/detekt.yml          # Detekt rules incl. ForbiddenImport layer guard
├── docs/
│   ├── adr/                          # ADR-001 … ADR-011 (architecture decisions)
│   ├── evidence/                     # Validation screenshots
│   └── *.docx, USER_GUIDE.md, LICENSES.md, RELEASE_NOTES.md
├── gradle/
│   ├── libs.versions.toml            # Version catalog (all dependency/plugin versions)
│   └── wrapper/                       # Gradle wrapper 8.9
├── logo/                             # Brand asset
├── scripts/check-quality-gates.sh    # ktlint → detekt → lint → unit tests
├── .github/workflows/ci.yml          # CI quality gates + assembleDebug
├── .planning/                        # GSD planning state (PROJECT, ROADMAP, STATE, evidence, codebase)
├── .gsd/                             # GSD runtime sentinel
├── build.gradle.kts                  # Root: plugin aliases (apply false)
├── settings.gradle.kts               # Repos (google, mavenCentral, jitpack) + `include(":app")`
├── gradle.properties                 # JVM args, AndroidX, non-transitive R, SDK 35 suppression
└── README.md
```

## Directory Purposes

**`app/src/main/java/com/clibeats/`:**

- Purpose: all production code, organized by architectural layer first, feature second.
- Contains: `CLIBeatsApp.kt`, `MainActivity.kt`, then layer roots: `data/`, `domain/`, `playback/`, `presentation/`, `di/`, `telemetry/`, `util/`.
- Key files: `CLIBeatsApp.kt`, `MainActivity.kt`.

**`app/src/main/java/com/clibeats/data/`:**

- Purpose: implementations of domain contracts over network/Room/disk.
- Contains: `provider/` (impls + `api/` + `dto/` + `mapper/` + `youtube/`), `repository/` (5 `*RepositoryImpl.kt`), `local/` (`CliBeatsDatabase.kt`, `CliBeatsTypeConverters.kt`, `dao/`, `entity/`, `mapper/`), `preferences/AppPreferences.kt`, `cache/CacheManager.kt`, `download/` (`TrackDownloadManager.kt`, `DownloadStatus.kt`), `playlist/` (`CliBeatsFile.kt`, `PlaylistExchangeManager.kt`), `network/NetworkMonitor.kt`.
- Key files: `data/repository/PlaybackRepositoryImpl.kt`, `data/provider/YouTubeMusicProvider.kt`, `data/local/CliBeatsDatabase.kt`.

**`app/src/main/java/com/clibeats/domain/`:**

- Purpose: framework-free contracts and models.
- Contains: `model/` (`Track.kt`, `Album.kt`, `Artist.kt`, `Playlist.kt`, `PlaybackState.kt`, `PlaybackException.kt`), `provider/` (`MusicProvider.kt`, `ProviderRegistry.kt`, `ProviderId.kt`, `ProviderResult.kt`), `repository/` (5 interfaces).
- Key files: `domain/provider/MusicProvider.kt`, `domain/model/Track.kt`.
- Note: no use-case package exists.

**`app/src/main/java/com/clibeats/presentation/`:**

- Purpose: Compose UI + ViewModels, one package per screen/feature.
- Contains: `home/`, `search/`, `library/`, `queue/`, `playlist/`, `settings/`, `more/`, `player/` (ViewModel only), plus `layout/`, `component/`, `theme/`.
- Key files: `presentation/layout/MainLayout.kt`, `presentation/layout/NavDestination.kt`, `presentation/component/TuiBlock.kt`, `presentation/theme/CliBeatsTheme.kt`.

**`app/src/main/java/com/clibeats/playback/`:**

- Purpose: Media3 integration.
- Contains: `PlayerAdapter.kt`, `StreamResolver.kt`, `service/PlaybackService.kt`.
- Key files: `playback/PlayerAdapter.kt`, `playback/service/PlaybackService.kt`.

**`app/src/main/java/com/clibeats/di/`:**

- Purpose: composition root; one module per concern.
- Contains: `NetworkModule`, `DatabaseModule`, `RepositoryModule`, `ProviderModule`, `PlaybackModule`, `CacheModule`, `DownloadModule`, `StorageModule`, `ImageLoaderModule`, `TelemetryModule`, `AppModule` (empty).

**`app/src/main/java/com/clibeats/telemetry/` and `util/`:**

- Purpose: local-only telemetry abstraction and structured diagnostics.
- Contains: `AnalyticsEvent.kt`, `TelemetryTracker.kt`, `TimberTelemetryTracker.kt`, `CrashReporter.kt`, `TimberCrashReporter.kt`; `util/DiagnosticLogger.kt`.

**`app/src/main/res/`:**

- Purpose: Android resources.
- Contains: `font/` (JetBrains Mono regular/medium/semibold/bold), `drawable/ic_stat_music_note.xml`, `mipmap-*/` launcher icons, `xml/data_extraction_rules.xml`.
- Note: all UI color/typography constants live in Kotlin (`presentation/theme/`), **not** in `values/*.xml`; there is no `res/values/` directory.

**`app/src/test/java/com/clibeats/`:**

- Purpose: JVM tests, mirroring production packages; plus Paparazzi screenshot tests under `theme/`.
- Contains: 43 test files (providers, repositories, ViewModels, playback, telemetry, integration, license compliance).
- Notables: `integration/PlaybackIntegrationTest.kt`, `license/LicenseComplianceTest.kt`, `theme/*ScreenshotTest.kt`.

**`app/src/androidTest/java/com/clibeats/data/local/dao/`:**

- Purpose: Room DAO instrumented tests (`SongDaoTest`, `PlaylistDaoTest`, `HistoryDaoTest`, `CacheIndexDaoTest`).

**`docs/`:**

- Purpose: product/architecture documentation.
- Contains: `adr/` (ADR-001, 003–011 + template), `evidence/` (screenshots), design `.docx` artifacts, `USER_GUIDE.md`, `LICENSES.md`, `RELEASE_NOTES.md`.

## Key File Locations

**Entry Points:**
- `app/src/main/AndroidManifest.xml`: declares `CLIBeatsApp`, `MainActivity` (launcher), `PlaybackService` (`mediaPlayback` + `MediaSessionService`).
- `app/src/main/java/com/clibeats/CLIBeatsApp.kt`: `@HiltAndroidApp` application class.
- `app/src/main/java/com/clibeats/MainActivity.kt`: `@AndroidEntryPoint` Compose host and destination switch.
- `app/src/main/java/com/clibeats/playback/service/PlaybackService.kt`: foreground service entry.

**Configuration:**
- `settings.gradle.kts`: module inclusion + repositories (jitpack required by NewPipeExtractor).
- `build.gradle.kts`: root plugin aliases only.
- `app/build.gradle.kts`: all app config — `namespace = "com.clibeats"`, SDK 34/min 26, `buildConfigField JAMENDO_CLIENT_ID`, Room schema location, Detekt config path, dependency list.
- `gradle/libs.versions.toml`: single source of dependency/plugin versions.
- `gradle.properties`: `org.gradle.jvmargs`, `android.useAndroidX`, `android.nonTransitiveRClass`.
- `config/detekt/detekt.yml`: `ForbiddenImport` (`com.clibeats.data.*`) layer guard.
- `.github/workflows/ci.yml`: ktlint → detekt → lint → assembleDebug → unit tests.
- `scripts/check-quality-gates.sh`: same gates locally.

**Core Logic:**
- `app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`: playback/queue engine façade (largest file, 326 lines).
- `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`: queue persistence/restore + stream resolution orchestration.
- `app/src/main/java/com/clibeats/data/provider/YouTubeMusicProvider.kt`: primary provider; streaming fallback chain.
- `app/src/main/java/com/clibeats/data/provider/youtube/`: `NewPipeExtractorResolver.kt`, `PoTokenGenerator.kt`, `StreamUrlDeobfuscator.kt`, `StreamCacheManager.kt`, `YouTubeClientStrategy.kt`.
- `app/src/main/java/com/clibeats/data/local/CliBeatsDatabase.kt`: schema, versions, migrations.
- `app/src/main/java/com/clibeats/di/ProviderModule.kt`: provider registration order.
- `app/src/main/java/com/clibeats/di/NetworkModule.kt`: base URLs, clients, Retrofit instances.

**Testing:**
- Unit tests: `app/src/test/java/com/clibeats/<mirrored package>/`.
- Instrumented DAO tests: `app/src/androidTest/java/com/clibeats/data/local/dao/`.
- Screenshot goldens: `app/src/test/snapshots/images/com.clibeats.theme_*.png`.
- Room schemas used by migration tests: `app/schemas/com.clibeats.data.local.CliBeatsDatabase/`.

## Naming Conventions

**Files:**
- One top-level public class per file; filename matches the class (`PlayerAdapter.kt`, `ProviderRegistry.kt`, `CliBeatsTheme.kt`).
- Screens: `<Feature>Screen.kt`; ViewModels: `<Feature>ViewModel.kt`; UI state: `<Feature>UiState.kt` in the same feature package.
- Repository implementations: `<Contract>Impl.kt` (`SongRepositoryImpl.kt`, `PlaybackRepositoryImpl.kt`).
- Room: `*Entity.kt`, `*Dao.kt`; API: `*Api.kt`; DTOs: plural `*Dtos.kt` for provider response groups plus request/response files (`SearchRequest.kt`, `PlayerResponse.kt`).
- Mappers: lowercase `*Mapper.kt` files containing top-level extension functions (`SongMapper.kt`, `TrackMapper.kt`).
- DI modules: `<Concern>Module.kt`.
- Multiple related small types may share a file (`CliBeatsFile.kt` holds model + codec; `DownloadStatus.kt` holds a sealed interface).

**Functions:**
- Lowercase camelCase for normal functions (`playTrack`, `saveTrackToCache`, `restorePersistentQueue`).
- Compose composables are PascalCase (`HomeScreen`, `TuiBlock`, `SongTableRow`) and suppress `FunctionNaming` / `ktlint:standard:function-naming` at file or function level.
- Mapping extensions: `fun XEntity.toDomain(): Y` and `fun Y.toEntity(...)`; provider mappers: `fun XDto.toDomainTrack(): Track?`.
- Test methods commonly use backticked descriptive names.

**Variables:**
- `private const val` UPPER_SNAKE_CASE at file scope for constants (`POLL_INTERVAL_MS`, `DEBOUNCE_MS`, `DEFAULT_MAX_CACHE_BYTES`).
- Backing state: `_playbackState`, `_queueFlow`, `_query`, `_downloads` with public `StateFlow` counterparts.
- Provider ids are snake_case string literals: `"youtube_music"`, `"internet_archive"`, `"audius"`, `"jamendo"`, `"local"`.

**Types:**
- Interfaces: plain noun (`MusicProvider`, `ProviderRegistry`, `TelemetryTracker`).
- UI state: sealed interface named `<Feature>UiState` with `Loading`/`Success`/`Error`/`Empty`/`Idle` members.
- Results: sealed class `ProviderResult<out T>` with `Success`/`Error`/`Loading`.
- Events: sealed class `AnalyticsEvent` with data subclasses.
- DI qualifiers: `@Named(...)` constants in the providing module (e.g. `JAMENDO_CLIENT_ID_QUALIFIER`).

**Package / Directory:**
- Layer-first at the root (`data`, `domain`, `presentation`, `playback`), feature-second inside (`presentation/search`, `data/provider/youtube`).
- All lowercase, no underscores (`presentation`, `playlist`, `youtube`).

**Database:**
- Tables snake_case plural (`songs`, `playlists`, `queue_items`, `history`, `liked_songs`, `saved_albums`, `saved_artists`, `cache_index`, `playlist_song_cross_ref`).
- Columns snake_case via `@ColumnInfo(name = "...")` `duration_ms`, `artwork_url`, `provider_id`.
- Composite keys encode provider: `Track.id = "<providerId>:<sourceId>"`.

**Tests:**
- `<ClassUnderTest>Test.kt` (`CacheManagerTest.kt`, `SearchViewModelTest.kt`, `PlaybackIntegrationTest.kt`).
- Screenshot tests named `<Component>ScreenshotTest.kt` with snapshot files `<package>_<Class>_<case>.png`.

## Where to Add New Code

**New Music Provider:**
- Implementation: `app/src/main/java/com/clibeats/data/provider/<Name>MusicProvider.kt` implementing `MusicProvider` with a unique snake_case `providerId`, returning `ProviderResult` only.
- HTTP client: `app/src/main/java/com/clibeats/data/provider/api/<Name>Api.kt` + base URL/Retrofit provider in `app/src/main/java/com/clibeats/di/NetworkModule.kt`.
- Wire DTOs: `app/src/main/java/com/clibeats/data/provider/dto/<Name>Dtos.kt` (`@Serializable`).
- Mapping: `app/src/main/java/com/clibeats/data/provider/mapper/<Name>Mapper.kt` applying `ProviderId.composite`.
- Registration: add parameter + list entry in `app/src/main/java/com/clibeats/di/ProviderModule.kt` (order = display priority).
- Tests: `app/src/test/java/com/clibeats/data/provider/<Name>MusicProviderTest.kt` and `.../mapper/<Name>MapperTest.kt` (MockWebServer pattern).

**New Screen / Feature:**
- UI: `app/src/main/java/com/clibeats/presentation/<feature>/<Feature>Screen.kt`, `<Feature>ViewModel.kt` (`@HiltViewModel`), `<Feature>UiState.kt`.
- Navigation: add a `data object` to `app/src/main/java/com/clibeats/presentation/layout/NavDestination.kt`, add it to `all` (and `mainTabs` only if it belongs in the bottom bar), then add a branch in `MainActivity.kt`'s `when (selectedDestination)`.
- Tests: `app/src/test/java/com/clibeats/presentation/<feature>/<Feature>ViewModelTest.kt`.

**New Repository / Data Operation:**
- Contract: `app/src/main/java/com/clibeats/domain/repository/<Name>Repository.kt`.
- Implementation: `app/src/main/java/com/clibeats/data/repository/<Name>RepositoryImpl.kt`, annotated `@Singleton` with `@Inject constructor`.
- Binding: add `@Binds @Singleton` in `app/src/main/java/com/clibeats/di/RepositoryModule.kt`.
- Mapping: keep entity↔domain conversion in `app/src/main/java/com/clibeats/data/local/mapper/`; never expose `*Entity` through the contract (exception currently: `HistoryRepository`).

**New Room Entity / Column:**
- Entity: `app/src/main/java/com/clibeats/data/local/entity/<Name>Entity.kt`.
- DAO: `app/src/main/java/com/clibeats/data/local/dao/<Name>Dao.kt` (Flow for reads, `suspend` for writes).
- Database: add to `@Database(entities = [...])`, bump `version`, add a `MIGRATION_x_y`, register it in `app/src/main/java/com/clibeats/di/DatabaseModule.kt`, expose a `provide<Name>Dao` function.
- Schema: regenerate `app/schemas/com.clibeats.data.local.CliBeatsDatabase/<version>.json` (KSP `room.schemaLocation`).
- Tests: `app/src/androidTest/java/com/clibeats/data/local/dao/<Name>DaoTest.kt` using in-memory Room.

**New UI Component / Design Token:**
- Reusable composable: `app/src/main/java/com/clibeats/presentation/component/<Name>.kt`; use `LocalAccentColor`, flat borders, and monospaced `MaterialTheme.typography` instead of raw colors.
- Theme tokens: `app/src/main/java/com/clibeats/presentation/theme/CliBeatsColors.kt` / `CliBeatsTypography.kt` / `CliBeatsShapes.kt`; accent presets in `AccentColor.kt`; mode enum in `CliBeatsThemeMode.kt`.

**New Preference:**
- Add a key to `Keys` in `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`, a `Flow` accessor with a default, and a `suspend fun set...` using `dataStore.edit { }`. Sensitive values go to `securePrefs` instead.

**New Cache / Download Behaviour:**
- Cache/index: `app/src/main/java/com/clibeats/data/cache/CacheManager.kt` (+ `CacheIndexDao`).
- Downloads: `app/src/main/java/com/clibeats/data/download/TrackDownloadManager.kt`; register new managers in `CacheModule.kt` / `DownloadModule.kt`.

**New Telemetry Event:**
- Add a subclass to `app/src/main/java/com/clibeats/telemetry/AnalyticsEvent.kt`; keep sinks local (no network SDK) unless an ADR approves it.

**Utilities:**
- Android-aware logging: `app/src/main/java/com/clibeats/util/DiagnosticLogger.kt` (add a `log<Event>(traceId, ...)` method).
- Pure formatting helpers: currently misplaced in `presentation/search/SearchScreen.kt` (`formatDuration`); if adding more, prefer a shared `presentation/component/` or new `presentation/format/` location rather than importing across screens.

## Special Directories

**`app/schemas/com.clibeats.data.local.CliBeatsDatabase/`:**
- Purpose: Room exported schema JSON for migration verification (currently `1.json`, `2.json`, `3.json`).
- Generated: Yes (by KSP via `room.schemaLocation`).
- Committed: Yes — must be updated whenever entities/version change.

**`app/src/test/snapshots/images/`:**
- Purpose: Paparazzi golden images for theme/component regression.
- Generated: Yes (record with `./gradlew recordPaparazziDebug`).
- Committed: Yes.

**`app/build/`:**
- Purpose: Gradle outputs (APKs at `app/build/outputs/apk/debug/`).
- Generated: Yes. Committed: No (`.gitignore`: `app/build/`, `/build/`, `.gradle/`).

**`.planning/`:**
- Purpose: GSD project state — `PROJECT.md`, `ROADMAP.md`, `STATE.md`, `MILESTONES.md`, `codebase/` (these docs), `research/`, `evidence/`, `debug/`.
- Generated: Partly by GSD commands. Committed: Yes.

**`docs/adr/`:**
- Purpose: architectural decisions that constrain new code (e.g. ADR-001 layering, ADR-003 storage, ADR-004 Media3).
- Generated: No. Committed: Yes. Add a new ADR before deviating from these patterns.

**`config/detekt/`:**
- Purpose: static-analysis rules, including the `ForbiddenImport` layer guard.
- Generated: No. Committed: Yes.

---

*Structure analysis: 2026-11-17*
