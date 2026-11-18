---
last_mapped_commit: a77dc37a72fa6a498fb1ac17a68d814fbe3770cb
last_mapped_at: 2026-11-19
---
# Coding Conventions

**Analysis Date:** 2026-11-19

## Naming Patterns

**Files:**
- One top-level declaration group per file; the filename is the PascalCase name of the primary class or of the concept the top-level functions operate on: `HomeViewModel.kt`, `HomeUiState.kt`, `HomeScreen.kt`, `PlaybackRepositoryImpl.kt`, `TrackMapper.kt`, `ArtworkUrl.kt`, `SongEntity.kt`.
- UI state files are separate from the ViewModel file: `app/src/main/java/com/clibeats/presentation/*/XxxUiState.kt` (e.g. `app/src/main/java/com/clibeats/presentation/home/HomeUiState.kt`).
- DI modules: `app/src/main/java/com/clibeats/di/XxxModule.kt` (`NetworkModule.kt`, `RepositoryModule.kt`, `DatabaseModule.kt`, `ProviderModule.kt`, `StorageModule.kt`, `TelemetryModule.kt`, `PlaybackModule.kt`, `DownloadModule.kt`, `CacheModule.kt`, `ImageLoaderModule.kt`, `AppModule.kt`).
- Tests: `<ClassUnderTest>Test.kt` mirroring the main source path (see TESTING.md).

**Packages:**
- All lowercase, no underscores: `com.clibeats.data.provider.youtube`, `com.clibeats.presentation.layout`, `com.clibeats.domain.repository`.
- Layer-first then feature: `com.clibeats.<layer>.<feature>` where layer is `data`, `domain`, `presentation`, `playback`, `di`, `telemetry`, `util`.
- Data layer is split by mechanism: `data.local.{dao,entity,mapper}`, `data.provider.{api,dto,mapper,youtube}`, `data.network`, `data.cache`, `data.preferences`, `data.repository`, `data.scan`, `data.download`, `data.playlist`.

**Classes/Interfaces:**
- PascalCase, no `I` prefix on interfaces: `MusicProvider`, `PlaybackRepository`, `TelemetryTracker`.
- Repository interface in `domain/repository`, implementation suffixed `Impl` in `data/repository`: `PlaylistRepository` → `PlaylistRepositoryImpl`.
- Providers are suffixed `MusicProvider` and named after the source: `AudiusMusicProvider`, `YouTubeMusicProvider`, `InternetArchiveMusicProvider`, `JamendoMusicProvider`, `LocalMusicProvider` (all in `app/src/main/java/com/clibeats/data/provider/`).
- ViewModels: `XxxViewModel` annotated `@HiltViewModel`, in the same feature package as their screen.
- Screens/composables: PascalCase, suffix `Screen` for destinations (`HomeScreen`, `SearchScreen`, `NowPlayingScreen`, `MainLayout`).
- Terminal-design system primitives use the `Tui` prefix: `TuiBlock`, `TuiTextField` in `app/src/main/java/com/clibeats/presentation/component/`.
- State/results are sealed: `sealed class ProviderResult<out T>` (`app/src/main/java/com/clibeats/domain/provider/ProviderResult.kt`), `sealed interface HomeUiState`, `sealed interface SleepTimer` in `app/src/main/java/com/clibeats/presentation/player/PlayerViewModel.kt`.
- Entity suffix `Entity`, DTO suffix `Dto`, Room table names snake_case plural: `SongEntity` → `@Entity(tableName = "songs")` in `app/src/main/java/com/clibeats/data/local/entity/SongEntity.kt`.

**Functions:**
- camelCase; boolean-returning functions read as predicates (`isLiked`, `isReadOnly`, `isLocalFilePlaylist`).
- Mapping is done with top-level extension functions, **not** mapper classes: `toDomain()`, `toEntity()`, `toDomainTrack()`, `toTrackList()` in `app/src/main/java/com/clibeats/data/local/mapper/` and `app/src/main/java/com/clibeats/data/provider/mapper/`.
- Compose UI event callbacks are prefixed `on` (`onPlay`, `onQueryChange`, `onSkipNextClick`, `onAddToQueue`, `playFromList`). Slot callbacks passed to composables are also `on*` (`onNavigate`, `onDismiss`).
- Suspend functions for all IO/repository/provider work; `Flow`-returning functions are suffixed `AsFlow` when a suspend twin exists (`getAllAsFlow()`, `getAllPlaylistsAsFlow()`, `getSongsForPlaylistAsFlow()`).

**Variables/Constants:**
- `val`/`var` camelCase. Backing state fields are prefixed `_` with a public read-only twin: `private val _query = MutableStateFlow("")` / `val query: StateFlow<String> = _query.asStateFlow()` (`app/src/main/java/com/clibeats/presentation/search/SearchViewModel.kt:40-41`).
- File-private constants are `private const val UPPER_SNAKE_CASE` declared at the **top** of the file, above the class, with a unit/meaning suffix: `DEBOUNCE_MS = 300L`, `MIN_QUERY_LENGTH = 2`, `TRENDING_LIMIT = 15`, `READ_TIMEOUT_SECONDS = 20L`, `SECONDS_PER_MINUTE = 60L`.
- Preference keys live in a `private object Keys` with `UPPER_SNAKE_CASE` properties (`app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:30-49`).
- Theme color tokens are PascalCase vals prefixed `CliBeats`: `CliBeatsBackgroundDark`, `CliBeatsAccent`, `CliBeatsTextSecondary` (`app/src/main/java/com/clibeats/presentation/theme/CliBeatsColors.kt`).

## Code Style

**Formatting:**
- ktlint via `org.jlleitschuh.gradle.ktlint` 12.1.1, declared in `app/build.gradle.kts` (`alias(libs.plugins.ktlint)`), version pinned in `gradle/libs.versions.toml`.
- **There is no `.editorconfig` in the repo.** ktlint runs with its plugin defaults (`ktlint_official` code style). Consequences to follow:
  - 4-space indentation, no tabs.
  - Trailing commas on every multi-line parameter/argument list (see `app/src/main/java/com/clibeats/domain/model/Track.kt`, `app/src/main/java/com/clibeats/data/repository/PlaylistRepositoryImpl.kt`).
  - Continuation lines are indented 4 spaces past the opening construct; chained calls break with `.` at line start (see `app/src/main/java/com/clibeats/presentation/home/HomeViewModel.kt:106-110`).
  - `= runTest {` bodies put the `{` on the same line and the body indented (`fun x() =\n    runTest {`).
- Long lines are permitted: 360 lines exceed 100 chars and 33 exceed 120 in `app/src/main`. Detekt's `MaxLineLength` is suppressed per file when needed (`app/src/main/java/com/clibeats/util/DiagnosticLogger.kt:1`).

**Linting:**
- Detekt 1.23.6 with `buildUponDefaultConfig = true`, `allRules = false`, `maxIssues: 0` — config at `config/detekt/detekt.yml`, wired in `app/build.gradle.kts`.
- Key enforced thresholds (`config/detekt/detekt.yml`):
  - `TooManyFunctions`: 15 per file/class, 10 per interface.
  - `LongMethod`: 60 lines.
  - `LargeClass`: 300 lines.
  - `MagicNumber`: active; `-1, 0, 1, 2, 100, 1000` ignored, property declarations ignored. Introduce a named `private const val` instead of a literal.
  - `ForbiddenImport` for `com.clibeats.data.*` — clean-architecture boundary; see "Clean Architecture Boundary" below.
  - `Indentation` and `ImportOrdering` are **disabled** in detekt because ktlint owns them (`config/detekt/detekt.yml:7-16`).
- Android Lint: `lint { abortOnError = true; checkDependencies = true; warningsAsErrors = false }` in `app/build.gradle.kts`.

**Suppressions are first-class and must be justified:**
- Use `@file:Suppress("Rule")` at the very top, before `package`, and only for a specific rule.
- Add a one-to-two-line comment stating *why* the suppression exists (see `app/src/main/java/com/clibeats/data/repository/PlaylistRepositoryImpl.kt:21-23` and `app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt:20-21`).
- ktlint rule ids are namespaced: `"ktlint:standard:function-naming"`, `"ktlint:standard:multiline-expression-wrapping"` (used for `@Composable` PascalCase functions and compiler-plugin style).
- Common suppressions in this codebase: `ForbiddenImport` (61), `FunctionNaming` (43), `MaxLineLength` (24), `MagicNumber` (20), `LongMethod` (20), `TooManyFunctions` (11), `ReturnCount` (11), `LongParameterList` (11).

## Import Organization

**Order:**
1. Single alphabetical block, no blank lines between groups (ktlint_official). Observed order: `androidx.*` → `app.cash.*` → `com.*` → `dagger.*` → `kotlinx.*` → `java.*`/`javax.*` → `org.*`.
2. No wildcard imports, ever.
3. `javax.inject.Inject` / `javax.inject.Singleton` sort toward the end (see `app/src/main/java/com/clibeats/data/provider/AudiusMusicProvider.kt:12-13`).
4. `@file:Suppress(...)` precedes `package`; blank line after `package`; blank line after imports.

**Path Aliases:**
- None. Use fully qualified imports; Gradle's `libs.` version catalog is only for build scripts (`gradle/libs.versions.toml`).

## Clean Architecture Boundary

The detekt `ForbiddenImport` rule forbids `com.clibeats.data.*` imports outside the data layer. Real-world exceptions exist and each must be suppressed at file level with a comment:

- DI modules binding data implementations: `app/src/main/java/com/clibeats/di/RepositoryModule.kt:1-2`.
- Data-layer files importing sibling data packages: `app/src/main/java/com/clibeats/data/repository/PlaylistRepositoryImpl.kt:1-2`, `app/src/main/java/com/clibeats/data/local/dao/PlaylistDao.kt:1-2`.
- Presentation ViewModels that depend on `AppPreferences` (data layer): `app/src/main/java/com/clibeats/presentation/home/HomeViewModel.kt:1`, `app/src/main/java/com/clibeats/presentation/search/SearchViewModel.kt:1`, `app/src/main/java/com/clibeats/presentation/settings/SettingsViewModel.kt`.
- Tests that interact with data packages: `app/src/test/java/com/clibeats/presentation/home/HomeViewModelTest.kt:1`.

**Rule for new code:** domain packages (`domain/model`, `domain/repository`, `domain/provider`) must not import `com.clibeats.data.*` or `android.*`. Prefer moving new behavior behind a `domain` interface over suppressing the rule.

## Dependency Injection Conventions

- Hilt throughout. Modules in `app/src/main/java/com/clibeats/di/`.
- Interface bindings: `abstract class XxxModule` with `@Binds` and `@Singleton` on each binding (`app/src/main/java/com/clibeats/di/RepositoryModule.kt`).
- Construction: `object XxxModule` with `@Provides @Singleton` functions (`app/src/main/java/com/clibeats/di/NetworkModule.kt`).
- All modules annotated `@Module @InstallIn(SingletonComponent::class)`.
- Multiple instances of the same type use `@Named("qualifier")` with a `const val` qualifier name declared in the module (`NetworkModule.kt:132` `INNERTUBE_OKHTTP_QUALIFIER`).
- Every injectable implementation is `@Singleton` + `@Inject constructor`.
- Base URLs, timeouts and model names are file-private constants at the top of the module (`NetworkModule.kt:28-32`).
- Constructor style (ktlint_official):
  ```kotlin
  @Singleton
  class PlaylistRepositoryImpl
      @Inject
      constructor(
          private val playlistDao: PlaylistDao,
          private val songDao: SongDao? = null,
      ) : PlaylistRepository {
  ```
  Optional collaborators are nullable with a default (`null`) so tests and older call sites keep compiling (`HomeViewModel.kt:36-37`, `PlayerViewModel.kt:44-45`).

## Compose Conventions

- Screens are stateless-ish containers: they call `hiltViewModel()` themselves and `collectAsState()` on `StateFlow`s:
  ```kotlin
  @Composable
  fun HomeScreen(
      onNavigate: (NavDestination) -> Unit = {},
      homeViewModel: HomeViewModel = hiltViewModel(),
  ) {
      val uiState by homeViewModel.uiState.collectAsState()
  ```
  (`app/src/main/java/com/clibeats/presentation/home/HomeScreen.kt:59-69`).
- Pure presentational components live in `app/src/main/java/com/clibeats/presentation/component/` and take primitive parameters, not ViewModels (`PlayerBar(trackTitle, artist, isPlaying, progress)`, `SongTableRow(trackTitle, artist, duration, index, isNowPlaying)`).
- Ephemeral UI state (dialogs, selection) uses `remember { mutableStateOf<Track?>(null) }` inside the screen (`HomeScreen.kt:70`).
- Styling always flows through theme tokens: `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, and the `CliBeats*` color vals from `app/src/main/java/com/clibeats/presentation/theme/CliBeatsColors.kt`. Do not hardcode `Color(0x...)` outside the theme files.
- Composables are PascalCase, which ktlint's `function-naming` rule rejects — put `"ktlint:standard:function-naming"` (and `"FunctionNaming"` for detekt) in the file-level suppression list.
- No `@Preview` composables exist; visual review is done with Paparazzi snapshot tests (see TESTING.md). Add Paparazzi coverage rather than `@Preview`.
- Modifier chains break one call per line with `.` leading (`HomeScreen.kt:76-80`).

## Error Handling

**Patterns:**
- Provider/repository boundaries return a sealed result instead of throwing: `ProviderResult.Success<T>`, `ProviderResult.Error(message, cause)`, `ProviderResult.Loading` (`app/src/main/java/com/clibeats/domain/provider/ProviderResult.kt`).
- Providers wrap all IO in `runCatching { ... }.getOrElse { e -> ProviderResult.Error(e.message ?: "Human readable fallback", e) }`:
  ```kotlin
  override suspend fun search(query: String, limit: Int): ProviderResult<List<Track>> =
      runCatching {
          val response = api.searchTracks(query = query, limit = limit)
          ProviderResult.Success(response.data.mapNotNull { it.toDomainTrack() })
      }.getOrElse { e ->
          ProviderResult.Error(message = e.message ?: "Search failed", cause = e)
      }
  ```
  (`app/src/main/java/com/clibeats/data/provider/AudiusMusicProvider.kt:35-48`).
- Exhaustive `when` over `ProviderResult` in ViewModels maps to UI state (`HomeViewModel.kt:98-104`, `SearchViewModel.kt:88-95`). Keep the `Loading` branch when adding cases.
- `try/catch` is reserved for cases where a typed exception must be handled distinctly (`SecurityException`, JSON parse, Media3 `IOException`) — see `app/src/main/java/com/clibeats/data/provider/youtube/PoTokenGenerator.kt`, `app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`, `app/src/main/java/com/clibeats/data/repository/PlaybackRepositoryImpl.kt`.
- Fire-and-forget convenience APIs swallow failure deliberately and say so in a comment (`playbackRepository?.addToQueue(track)`, `clearRecentlyPlayed()` in `HomeViewModel.kt`).
- UI-visible failures become a `XxxUiState.Error(message)` or a transient message flow (`playerMessage` in `PlayerViewModel.kt:75-82`). Never surface a raw stack trace to UI.
- Never `catch (Throwable)` without a suppression + reason; `TooGenericExceptionCaught` is active in detekt.

## Logging

**Framework:** `android.util.Log`, wrapped in two abstractions.

- Diagnostics: `app/src/main/java/com/clibeats/util/DiagnosticLogger.kt` — tag `CliBeatsDiagnostic`, trace ids (`generateTraceId()`), structured event methods (`logSearchRequest`, `logStreamUrlResolved`, `logError`), fallback to `println` if `Log` throws. Use this for playback/search pipeline tracing.
- Telemetry/crash: `app/src/main/java/com/clibeats/telemetry/TimberTelemetryTracker.kt` (`Log.d`, tag `CLIBeatsTelemetry`) and `app/src/main/java/com/clibeats/telemetry/TimberCrashReporter.kt` (`Log.e`, tag `CLIBeatsCrash`). Despite the "Timber" names, **there is no Timber dependency**; do not add `Timber.*` calls.
- Secrets must be redacted before logging: `TimberCrashReporter` strips `Bearer <token>` (`app/src/main/java/com/clibeats/telemetry/TimberCrashReporter.kt:15`).
- Do not add bare `Log.d(...)` in feature code; go through a tested telemetry/diagnostic seam so `app/src/test/java/com/clibeats/telemetry/` stays meaningful.

## Comments

**When to Comment:**
- Explain *why*, especially for non-obvious platform/workaround decisions: `@Upsert // not REPLACE: that cascades and deletes the playlist's songs on every edit` (`app/src/main/java/com/clibeats/data/local/dao/PlaylistDao.kt:18`); `// BODY buffered + logged every image: big lag` (`NetworkModule.kt:56`).
- Comment deliberately disabled paths and their rationale (`// Local M3U/PLS playlists are read-only…` in `PlayerViewModel.kt:66`).
- Every detekt/ktlint suppression needs an adjacent explanatory comment.

**KDoc/TSDoc:**
- KDoc on public classes and non-trivial public functions, using `[Symbol]` references: `app/src/main/java/com/clibeats/data/provider/AudiusMusicProvider.kt:15-25`, `app/src/main/java/com/clibeats/presentation/player/PlayerViewModel.kt:30`, `app/src/main/java/com/clibeats/presentation/theme/AccentColor.kt:7-13`.
- Property-level KDoc for non-obvious `StateFlow`s (`/** Whether the current track is in Favorites (liked songs). */`).
- No `@param`/`@return` boilerplate; one-line intent first.

## Function Design

**Size:** Target under 60 lines (detekt `LongMethod`). Larger composables are acceptable only with `@Suppress("LongMethod", "FunctionNaming")` at the function or file level (`HomeScreen.kt:1-5`).

**Parameters:**
- Named arguments at call sites once a call has more than ~2 arguments (`PlaylistEntity(id = ..., name = ...)`, `Track(...)` in tests).
- Trailing lambda for the last lambda parameter; Compose slot APIs use trailing lambdas (`TuiBlock(title = "System Status", isActive = true) { ... }`).
- Defaults for optional collaborators and UI parameters rather than overloads (`limit: Int = 20` in `MusicProvider`, `onNavigate: (NavDestination) -> Unit = {}`).
- Group 3+ related parameters into a data class instead of suppressing `LongParameterList`.

**Return Values:**
- Public suspend APIs return `ProviderResult<T>` or domain models; never `Boolean` success flags.
- Flows are cold where possible; expose `StateFlow` from ViewModels via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), initialValue)` (`HomeViewModel.kt:46-49`, `PlayerViewModel.kt:51-57`).
- Use `SharingStarted.Eagerly` only for tiny preference-derived flows (`activeProviderId`).

## Module Design

**Exports:** No barrel/re-export files. Import the declaring file directly. Keep helpers `private` at the bottom of the file.

**Visibility:** Default to `private` for helpers/constants, `internal` only when a test genuinely needs it, `public` only for the API surface (screens, ViewModels, interfaces, DI modules).

**Immutability:**
- Domain models are `data class` with all-`val` fields (`app/src/main/java/com/clibeats/domain/model/Track.kt`).
- Mutate via `copy(...)`; never expose mutable collections from repositories (`.map`, `.toList()`).
- Room entities mirror domain models but add persistence-only nullable columns with defaults (`localPath`, `cachedAt` in `SongEntity.kt`).

## Credit / Reference

- Contributor rules: `README.md` "Contributing" section and `.agents/AGENTS.md`.
- Architecture rationale: `docs/adr/ADR-001-architecture-and-di-strategy.md`, `docs/adr/ADR-009-comprehensive-testing-hardening.md`.

---

*Convention analysis: 2026-11-19*
