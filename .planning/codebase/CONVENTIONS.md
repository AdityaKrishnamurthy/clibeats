---
last_mapped_commit: 916a56379cbb0bf07eb302edd4489c7633418417
last_mapped_at: 2026-11-17
---
# Coding Conventions

**Analysis Date:** 2026-11-17

## Project Shape

- Kotlin 2.0.21, Jetpack Compose (Material 3), Android app module only (`:app`).
- Package root `com.clibeats` with Clean-Architecture layer packages under `app/src/main/java/com/clibeats/`:
  `data/`, `domain/`, `presentation/`, `playback/`, `di/`, `telemetry/`, `util/`.
- Everything is enforced by Gradle quality gates: `ktlintCheck`, `detekt`, `lintDebug`
  (`app/build.gradle.kts`, `config/detekt/detekt.yml`, `scripts/check-quality-gates.sh`).

## Naming Patterns

**Files:**
- One public type per file, file name equals the type name: `Track.kt`, `HomeViewModel.kt`, `SongDao.kt`.
- Exception — grouped DTO files: `app/src/main/java/com/clibeats/data/provider/dto/AudiusDtos.kt`,
  `InternetArchiveDtos.kt`, `JamendoDtos.kt`, `PlayerRequest.kt`, `PlayerResponse.kt`, `SearchResponse.kt`.
- Theme palette files group several top-level color vals: `presentation/theme/CliBeatsColors.kt`.
- Test files: `<ClassUnderTest>Test.kt` mirroring the source path
  (`app/src/test/java/com/clibeats/data/provider/AudiusMusicProviderTest.kt`).
- Screenshot tests: `<Component>ScreenshotTest.kt` in package `com.clibeats.theme`
  (`app/src/test/java/com/clibeats/theme/PlayerBarScreenshotTest.kt`).
- A stray Compose source-set test is named with a `Kt` suffix: `SearchScreenKtTest.kt`.

**Packages:**
- Lowercase, no underscores: `com.clibeats.data.local.dao`, `com.clibeats.presentation.search`.
- Feature packages under `presentation/` hold the triple `FeatureScreen.kt`, `FeatureViewModel.kt`,
  `FeatureUiState.kt` (e.g. `presentation/home/`, `presentation/queue/`, `presentation/library/`).
- Mappers live in a dedicated `mapper` subpackage of the layer that owns them:
  `data/local/mapper/SongMapper.kt`, `data/local/mapper/PlaylistMapper.kt`,
  `data/provider/mapper/TrackMapper.kt`, `AudiusMapper.kt`, `InternetArchiveMapper.kt`, `JamendoMapper.kt`.

**Classes / Interfaces:**
- `PascalCase`. Interfaces have no `I` prefix (`MusicProvider`, `ProviderRegistry`, `TelemetryTracker`,
  `CrashReporter`); implementations are suffixed `Impl` or named by mechanism
  (`SongRepositoryImpl`, `MusicProviderRegistry`, `TimberTelemetryTracker`, `TimberCrashReporter`).
- Layer suffixes are meaningful and consistent: `*Entity` (Room), `*Dto` / `*Dtos` / `*Response` / `*Request`
  (network), `*Repository` (domain port) / `*RepositoryImpl` (data adapter), `*Dao`, `*ViewModel`,
  `*UiState`, `*Screen`, `*Module` (Hilt).
- Sealed hierarchies for result and state: `ProviderResult` (`domain/provider/ProviderResult.kt`),
  `PlaybackException` (`domain/model/PlaybackException.kt`), `HomeUiState`, `SearchUiState`,
  `QueueUiState`, `LibraryUiState`, `PlaylistUiState`, `SettingsUiState`.
- UI state contracts are `sealed interface` with `data object Loading` / `data object Empty` /
  `data class Success(...)` / `data class Error(val message: String)`. See
  `app/src/main/java/com/clibeats/presentation/search/SearchUiState.kt`.

**Functions:**
- `camelCase` for regular functions and methods.
- Compose composables are `PascalCase` (Compose convention), which violates ktlint's standard naming rule;
  every composable file therefore declares
  `@file:Suppress("ktlint:standard:function-naming")` and/or `@Suppress("FunctionNaming")`.
  Example: `app/src/main/java/com/clibeats/presentation/component/TuiBlock.kt`.
- Mapper functions are extensions named `toDomain()`, `toEntity()`, `toDomainTrack()`, `toTrackList()`
  (`app/src/main/java/com/clibeats/data/local/mapper/SongMapper.kt`).
- Boolean accessors/properties use `is` / `has` prefixes: `isPlaying`, `isActive`, `isNowPlaying`,
  `shuffleEnabled` (state noun form is also used).
- Domain-facing suspend API: `suspend fun search(...)`, `suspend fun trending(...)`.

**Variables / Properties:**
- `camelCase`. Backing-state pair convention for `StateFlow`:
  `private val _playbackState = MutableStateFlow(...)` + `val playbackState: StateFlow<...> = _playbackState.asStateFlow()`
  (`app/src/main/java/com/clibeats/playback/PlayerAdapter.kt`).
- Injected constructor params are `private val` named after the collaborator in camelCase derived from the type:
  `private val playlistDao: PlaylistDao`, `private val providerRegistry: ProviderRegistry`.
- Constants: `private const val` at file top in `SCREAMING_SNAKE_CASE` —
  `private const val TRENDING_LIMIT = 15` (`HomeViewModel.kt`),
  `private const val READ_TIMEOUT_SECONDS = 20L` (`di/NetworkModule.kt`).
- Named-string DI qualifiers are public `const val` on the module object:
  `NetworkModule.INNERTUBE_OKHTTP_QUALIFIER`, `ProviderModule.JAMENDO_CLIENT_ID_QUALIFIER`.
- Preference/secure key names are grouped in private objects `Keys` / `SecureKeys`
  (`app/src/main/java/com/clibeats/data/preferences/AppPreferences.kt`).
- Numeric literals use underscores: `180_000L`, `1_700_000_000_000L`, `5_000L`.

**Types / Data classes:**
- Domain models are immutable `data class` with no Android or library types:
  `Track(id, title, artist, album, durationMs, artworkUrl, streamUrl, providerId)` in
  `app/src/main/java/com/clibeats/domain/model/Track.kt`.
- Nullable `String?` is used for genuinely absent metadata (`artworkUrl`, `streamUrl`) rather than sentinels.
- IDs are composite strings prefixed by provider: `"audius:95wro"`, `"internet_archive:wonderwall-1995"`,
  parsed with `ProviderId.rawSourceId(providerId, id)` (`domain/provider/ProviderId.kt`).
- Enum state values are `SCREAMING_SNAKE`: `RepeatMode { OFF, ONE, ALL }`
  (`app/src/main/java/com/clibeats/domain/model/PlaybackState.kt`).

## Code Style

**Formatting:**
- Enforced by `org.jlleitschuh.gradle.ktlint` 12.1.1 (`app/build.gradle.kts`). There is **no**
  `.editorconfig` in the repository, so ktlint runs with its default `ktlint_official` code style.
- 4-space indentation, no tabs, no semicolons, expression-bodied functions allowed.
- Trailing commas are used in multi-line parameter/argument lists, including single-parameter ones:
  ```kotlin
  override suspend fun search(
      query: String,
      limit: Int,
  ): ProviderResult<List<Track>> =
  ```
- Multi-line builder chains put the `.` at the start of the continuation line, and the whole
  expression is often wrapped so the assignment `=` ends the line:
  ```kotlin
  val builder =
      OkHttpClient.Builder()
          .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
  ```
- `@Inject`/`@Provides` annotation-on-own-line constructor style is used everywhere
  (`ktlint_official` wrapping):
  ```kotlin
  @Singleton
  class AudiusMusicProvider
      @Inject
      constructor(
          private val api: AudiusApi,
      ) : MusicProvider {
  ```
- CSV of numeric UI dimensions always carry units explicitly in Compose: `8.dp`, `48.dp`, `labelSmall`.
- Compose `Modifier` is the last parameter for components that accept one, with a default of `Modifier`:
  `fun TrackArtwork(artworkUrl: String?, size: Int = 40, modifier: Modifier = Modifier)`
  (`presentation/component/TrackArtwork.kt`). Callback lambdas default to `{}`.

**Linting:**
- detekt 1.23.6 with `buildUponDefaultConfig = true`, `allRules = false`, `maxIssues: 0`
  (`config/detekt/detekt.yml`, `app/build.gradle.kts`). CI fails on any issue.
- Key thresholds: `LongMethod` 60 lines, `LargeClass` 300 lines, `TooManyFunctions` 15 per
  file/class (10 per interface), `MagicNumber` active with `-1, 0, 1, 2, 100, 1000` ignored and
  property declarations/hashCode exempt.
- `ForbiddenImport` bans `com.clibeats.data.*` to protect the layer boundary. The rule is far broader
  than intended (it also matches data-layer files importing their own siblings), so data-layer and
  test files open with `@file:Suppress("ForbiddenImport")`.
- `formatting` ruleset is active, but `Indentation` and `ImportOrdering` are **disabled** with the
  in-file rationale that detekt 1.23.6 misparses the ktlint_official `@Inject constructor()` layout and
  that ktlint owns import ordering. Do not re-enable them.
- Android Lint: `abortOnError = true`, `checkDependencies = true`, `warningsAsErrors = false`
  (`app/build.gradle.kts`).

**Suppression policy (important for new code):**
76 of 131 main-source Kotlin files start with at least one `@file:Suppress(...)`. The recurring set is:
- `"ForbiddenImport"` — mandatory in `data/` and `playback/` files that import their own layer
  (`data/repository/PlaylistRepositoryImpl.kt`, `playback/PlayerAdapter.kt`).
- `"ktlint:standard:function-naming"` (and detekt's `"FunctionNaming"`) — every composable file.
- `"ktlint:standard:multiline-expression-wrapping"` — Compose layout expressions.
- `"MaxLineLength"` / `"MaximumLineLength"` — dense one-liners and DAO `@Query` SQL.
- `"MagicNumber"`, `"LongMethod"`, `"LongParameterList"`, `"TooManyFunctions"`, `"Indentation"`,
  `"UnusedParameter"` — narrow, per-file exceptions.
Each suppression in the codebase is accompanied by a comment explaining why it is legitimate;
follow that pattern rather than suppressing silently.

## Import Organization

**Order (ktlint_official layout — `*, java.**, javax.**, kotlin.**, aliases`):**
1. Android/AndroidX (`androidx.*`), KotlinX (`kotlinx.*`), third-party (`com.google.*`, `dagger.*`,
   `okhttp3.*`, `retrofit2.*`, `coil.*`, `app.cash.*`) — one block, ASCII-sorted.
2. `java.*` (e.g. `java.util.concurrent.TimeUnit`).
3. `javax.*` (`javax.inject.Inject`, `javax.inject.Singleton`).
4. `kotlin.*` (e.g. `kotlin.coroutines.cancellation.CancellationException`).
5. Aliased imports last, alphabetically by alias:
   `import okhttp3.Request as OkHttpRequest`
   (`app/src/main/java/com/clibeats/data/provider/youtube/NewPipeExtractorResolver.kt`).

**Path Aliases:**
- None. No Gradle `import` aliases or Kotlin type aliases in use; all imports are fully qualified.
- Wildcard imports are not used.
- Test files import sibling `data.*` packages legitimately — they need
  `@file:Suppress("ForbiddenImport")` at the top (see `SearchViewModelTest.kt`).

## Error Handling

**Strategy:** recoverable failures are values, not exceptions.

- Boundaries return `ProviderResult<T>` (`Success` / `Error` / `Loading`) and never throw to callers:
  ```kotlin
  override suspend fun search(query: String, limit: Int): ProviderResult<List<Track>> =
      runCatching {
          ...
          ProviderResult.Success(tracks)
      }.getOrElse { e ->
          ProviderResult.Error(message = e.message ?: "Search failed", cause = e)
      }
  ```
  (`app/src/main/java/com/clibeats/data/provider/AudiusMusicProvider.kt`, same shape in
  `InternetArchiveMusicProvider.kt`, `JamendoMusicProvider.kt`, `LocalMusicProvider.kt`,
  `YouTubeMusicProvider.kt`).
- ViewModels translate results into sealed UI states with `when (val result = ...)` over every branch;
  `Error` carries a human-readable `message` string (no exception leaking to UI):
  `app/src/main/java/com/clibeats/presentation/home/HomeViewModel.kt`.
- Playback-domain failures use typed exceptions with stable codes:
  `PlaybackException` subclasses with `code` strings such as `"STREAM_RESOLUTION_FAILED"`
  (`app/src/main/java/com/clibeats/domain/model/PlaybackException.kt`).
- Non-critical best-effort work (logging, JSON navigation, cookie extraction) uses `runCatching { }.getOrNull()`
  / `.onFailure { }` (`data/provider/mapper/TrackMapper.kt`, `util/DiagnosticLogger.kt`).
- Cancellation is preserved explicitly where `runCatching` would swallow it:
  `catch (e: CancellationException) { throw e }` (`data/provider/InternetArchiveMusicProvider.kt`).
- `try/catch` is reserved for Android framework edges (only 8 occurrences in `app/src/main`).
- No `!!` force-unwraps are used in main code; prefer `?: return`, `?.let`, `requireNotNull`-free flows.

## Logging

**Framework:** Android `android.util.Log` wrapped by a single object — no Timber tree is planted.

- Structured, trace-scoped diagnostics go through
  `app/src/main/java/com/clibeats/util/DiagnosticLogger.kt`:
  `DiagnosticLogger.logSearchRequest(traceId, query)`, `logPlayerResponse(traceId, client, status)`,
  `logError(traceId, errorCode, message)`. Message format is `[$traceId] EVENT_NAME: key=value, key=value`.
- Trace IDs come from `DiagnosticLogger.generateTraceId()` (8-char UUID prefix) and are threaded through
  search → resolve → prepare → playing.
- `safeLog` wraps `Log.*` in `runCatching` and falls back to `println` under JVM unit tests.
- Never call `Log.d`/`println` directly from feature code; add a named `logXxx` function to `DiagnosticLogger`.
- Telemetry is event-based, not log-based: implement `TelemetryTracker.trackEvent(AnalyticsEvent)`
  (`app/src/main/java/com/clibeats/telemetry/`). Adding an event means adding a subclass of
  `AnalyticsEvent(name, params)` with snake_case `name` and snake_case param keys.

## Comments

**When to Comment:**
- KDoc on every public class/composable that owns behavior or has non-obvious contract
  (`AudiusMusicProvider`, `PlayerBar`, `TuiBlock`, `SongTableRow`, `CacheManager`).
- KDoc lists `@param` entries for component parameters and documents visual/behavioural states.
- Section banners inside large files / drawable-style code use box-drawing characters:
  `// ── Navigation helpers ─────`, `// ── Static fallback colours ─────`.
- Workarounds are documented inline immediately above the suppression they justify
  (`// Indentation: detekt 1.23.6 misparses ktlint_official @Inject constructor() style (false positive).`).
- `// TODO` / `// FIXME` are absent from `app/src/main`; do not introduce them — track work in `.planning/`.

**JSDoc/TSDoc (KDoc):**
- KDoc blocks are prose-first, then bullet lists for states/behaviour, then `@param` tags.
- Link referenced types with brackets: ``See [TuiBlock]``, ``[LocalAccentColor]``.

## Function Design

**Size:** detekt caps methods at 60 lines and classes at 300 lines. Composables are the usual
offenders and are split into private sub-composables (`ArtworkPlaceholder` inside `TrackArtwork.kt`).

**Parameters:**
- One parameter per line with trailing comma once there is more than one parameter.
- Compose components take plain display values + callbacks (`trackTitle: String`, `artist: String`,
  `onPlayPauseClick: () -> Unit = {}`) and content slots (`artworkContent: (@Composable () -> Unit)? = null`),
  never a ViewModel or domain object — parents unwrap state. See
  `app/src/main/java/com/clibeats/presentation/component/PlayerBar.kt`.
- Screens take `viewModel: XViewModel = hiltViewModel()` as a defaulted last parameter plus navigation
  callbacks before it: `fun SearchScreen(onTrackClick: (Track) -> Unit = {}, viewModel: SearchViewModel = hiltViewModel())`.
- UI-state-mutating ViewModel entry points are `onXxx(...)`: `onQueryChange`, `onTrackClick`, `onMove`,
  `onRemove`, `onClear`, `onPlayPauseClick`, `onSeek`.

**Return Values:**
- Collections returned from repositories as `Flow<List<T>>` (never `LiveData`); one-shot reads are `suspend`.
- ViewModel exposure is always `val uiState: StateFlow<XUiState>` built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), initialValue = ...)`.
  Always use `5_000L` — every ViewModel does.
- Boolean-returning helpers use `is*`/`can*` naming.

## Module Design

**Exports:**
- Keep visibility minimal: `private` for helpers (`private fun JsonElement.nav(...)`,
  `private fun startPositionTicker()`), `internal` only for test-visible helpers
  (`internal fun parseDurationMs(duration: String): Long` in `TrackMapper.kt`).
- Public API of a package is its interfaces + data classes; implementations are `@Singleton`
  and bound via Hilt modules.

**Barrel Files:** none. No `index`/`Exports.kt` aggregators — import the concrete file.

**Dependency Injection:**
- Hilt with KSP. Constructor injection everywhere (`@Inject constructor`), `@Singleton` on stateful
  components, `@HiltViewModel` + `@Inject constructor` for ViewModels.
- Modules are `object` with `@Module @InstallIn(SingletonComponent::class)` + `@Provides @Singleton`
  (`app/src/main/java/com/clibeats/di/`); interface bindings use `abstract class ... { @Binds ... }`
  (`di/TelemetryModule.kt`).
- `@Named("...")` string qualifiers for same-typed providers; qualifier constants live on the module.
- Repositories with a swappable dispatcher expose a secondary `@Inject` constructor that delegates
  to the injectable one with `Dispatchers.IO` (`data/repository/PlaybackRepositoryImpl.kt`).

**Layer Boundaries (do not violate):**
- `presentation/` → `domain/` only. It must not import `data.*`; the few exceptions carry
  `@file:Suppress("ForbiddenImport")` and should not be copied (e.g. `HomeViewModel` importing
  `com.clibeats.data.preferences.AppPreferences` is a known accepted leak driven by the pre-existing
  `ForbiddenImport` bug).
- `domain/` has zero Android imports.
- `data/` may import `domain/`; never `presentation/`.
- Provider-specific code stays inside `data/provider/<provider>/` and always maps to domain `Track`
  before leaving the data layer.

## Android / Compose Specifics

- Entry points: `app/src/main/java/com/clibeats/MainActivity.kt` (`setContent { ... }`) and
  `CLIBeatsApp.kt` (`@HiltAndroidApp`).
- Theme colors are raw top-level vals in `presentation/theme/CliBeatsColors.kt`
  (`CliBeatsBackground`, `CliBeatsSurface`, `CliBeatsTextPrimary`, …) plus a CompositionLocal
  `LocalAccentColor`. Never hardcode `Color(0x…)` in screens — use the theme vals.
- Spacing/typography constants come from `presentation/theme/` (`CliBeatsTypography.kt`,
  `CliBeatsShapes.kt`); TUI aesthetic: 48dp rows, 1dp borders, monospace, no elevation.
- Accessibility: interactive/visual elements set `contentDescription` via
  `Modifier.semantics { contentDescription = "..." }` or the Compose parameter.
- No `@Preview` composables exist in the repo today.

## Documentation Conventions

- Architecture decisions are recorded as ADRs in `docs/adr/ADR-0NN-<topic>.md` following
  `ADR-000-template.md`; add a new ADR rather than editing history.
- Release/user docs live in `docs/LICENSES.md`, `docs/RELEASE_NOTES.md`, `docs/USER_GUIDE.md`.
- Planning artifacts (plans, verification, UAT) live under `.planning/`; do not put delivery notes in code.

---

*Convention analysis: 2026-11-17*
