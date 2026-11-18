---
last_mapped_commit: a77dc37a72fa6a498fb1ac17a68d814fbe3770cb
last_mapped_at: 2026-11-19
---
# Testing Patterns

**Analysis Date:** 2026-11-19

## Test Framework

**Runner:**
- JUnit 4.13.2 (`libs.junit`, declared in `app/build.gradle.kts` as `testImplementation(libs.junit)`).
- No JUnit 5, no Robolectric, no Turbine in the project.
- Instrumented tests use `androidx.test.ext:junit` 1.2.1 with `@RunWith(AndroidJUnit4::class)`.
- No dedicated test config file; all wiring is in `app/build.gradle.kts`.

**Assertion Library:**
- Google Truth — `com.google.common.truth.Truth.assertThat`, used in 34 of 45 unit test files (e.g. `app/src/test/java/com/clibeats/presentation/home/HomeViewModelTest.kt:10`).
- Plain JUnit assertions (`org.junit.Assert.assertEquals/assertNull/assertFalse`) remain in 9 files (e.g. `app/src/test/java/com/clibeats/data/local/mapper/MapperTest.kt`, `app/src/test/java/com/clibeats/theme/CliBeatsColorsTest.kt`).
- Truth is **not** declared explicitly in `app/build.gradle.kts`; it arrives transitively (via `libs.media3.test.utils` / `libs.compose.ui.test.junit4`). When adding a new test module/source set, declare it explicitly.
- **Use Truth for new tests.** Prefer subject-specific assertions: `.hasSize(1)`, `.isInstanceOf(X::class.java)`, `.containsExactly(...).inOrder()`, `.isSameInstanceAs(...)`.

**Coroutine Testing:**
- `kotlinx-coroutines-test` 1.8.1 (`libs.coroutines.test`).
- Mocking: Mockito-Kotlin 5.4.0 (`libs.mockito.kotlin`) plus `mockito-core` 5.12.0.
- HTTP: OkHttp MockWebServer 4.12.0 (`libs.okhttp.mockwebserver`).
- Room: `androidx.room:room-testing` in both `testImplementation` and `androidTestImplementation`.
- Screenshots: Paparazzi 1.3.4 (`libs.plugins.paparazzi`, applied in `app/build.gradle.kts`).
- Media3 test utilities: `libs.media3.test.utils` (available but used only indirectly today).

**Run Commands:**

```bash
./gradlew testDebugUnitTest          # all JVM unit tests (CI gate)
./gradlew connectedDebugAndroidTest  # Room DAO instrumented tests (needs device/emulator)
./gradlew ktlintCheck detekt lintDebug  # static quality gates
./gradlew verifyPaparazziDebug       # screenshot regression check
./gradlew recordPaparazziDebug       # regenerate snapshots after an intentional UI change
scripts/check-quality-gates.sh       # ktlint -> detekt -> lintDebug -> unit tests, in order
```

Paparazzi requires `ANDROID_HOME`; `app/build.gradle.kts` propagates `ANDROID_HOME`/`ANDROID_SDK_ROOT` to every `Test` task.

## CI Enforcement

`.github/workflows/ci.yml` (job `quality-and-test`, JDK 17 Temurin, 20-minute timeout) runs, in order:

1. `./gradlew ktlintCheck --continue`
2. `./gradlew detekt --continue`
3. `./gradlew lintDebug --continue`
4. `./gradlew assembleDebug`
5. `./gradlew testDebugUnitTest`

Artifacts uploaded on every run: `app/build/reports/tests/`, `app/build/reports/detekt/`, `app/build/reports/lint-results-debug.html`.

`.github/workflows/release.yml` runs `./gradlew testDebugUnitTest` before building a signed APK on `v*.*.*` tags.

**Gaps to know before relying on CI:** `connectedDebugAndroidTest` and `verifyPaparazziDebug` are **not** part of CI, and no coverage task exists. Local runs are the only guard for those.

## Test File Organization

**Location:**
- JVM unit tests: `app/src/test/java/...` — package path mirrors `app/src/main/java/...` exactly (e.g. `app/src/main/java/com/clibeats/data/repository/PlaylistRepositoryImpl.kt` ↔ `app/src/test/java/com/clibeats/data/repository/PlaylistRepositoryImplTest.kt`).
- Instrumented tests: `app/src/androidTest/java/...` — currently only Room DAOs, at `app/src/androidTest/java/com/clibeats/data/local/dao/` (`SongDaoTest.kt`, `PlaylistDaoTest.kt`, `HistoryDaoTest.kt`, `CacheIndexDaoTest.kt`).
- Screenshot tests: `app/src/test/java/com/clibeats/theme/` (`CliBeatsThemeScreenshotTest.kt`, `PlayerBarScreenshotTest.kt`, `SongTableRowScreenshotTest.kt`); baselines committed under `app/src/test/snapshots/images/*.png`.
- Cross-cutting/placeholder tests: `app/src/test/java/com/clibeats/integration/PlaybackIntegrationTest.kt`, `app/src/test/java/com/clibeats/license/LicenseComplianceTest.kt`.

**Naming:**
- File: `<ClassUnderTest>Test.kt`; screenshot files: `<Subject>ScreenshotTest.kt`; integration: `<Flow>IntegrationTest.kt`.
- Class: same name as the file, no `Tests` plural.
- Test methods: **backticked descriptive sentences** dominate (135 occurrences) — ``fun `trending success populates tracks`()``. Older files use camelCase snake-ish names (`fun track_equality_is_structural()`, `fun onPlayPauseClick_whenPlaying_callsPause()`); prefer backticks for new tests, and keep any existing file consistent with its own style.
- Screenshot methods use `<subject>_<state>` (`playerBar_idleState`, `songRow_nowPlayingState`).

**Structure:**

```text
app/src/test/java/com/clibeats/
├── data/{cache,download,local,network,playlist,preferences,provider,repository,scan}/
├── domain/model/
├── integration/
├── license/
├── playback/
├── presentation/{component,home,library,player,playlist,queue,search,settings}/
├── telemetry/
└── theme/
```

## Test Structure

**Suite Organization (canonical ViewModel test):**

```kotlin
@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.home

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var musicProvider: MusicProvider
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        musicProvider = mock()
        whenever(musicProvider.trending(any())).thenReturn(ProviderResult.Success(listOf(fakeTrack())))
        viewModel = HomeViewModel(providerRegistry, appPreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `trending success populates tracks`() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.uiState.value).isInstanceOf(HomeUiState.Success::class.java)
        }

    private fun fakeTrack(): Track = Track(/* ... */)
}
```

Source: `app/src/test/java/com/clibeats/presentation/home/HomeViewModelTest.kt`.

**Patterns:**
- File-level `@file:Suppress("ForbiddenImport")` before `package` whenever the test imports `com.clibeats.data.*` (detekt rule + `config/detekt/detekt.yml`).
- Dependencies are `private lateinit var` fields assigned in `@Before`.
- Test bodies are expression-bodied: `fun x() =\n    runTest { ... }` (ktlint_official style).
- Assert on `StateFlow.value` after `testDispatcher.scheduler.advanceUntilIdle()`, not with delays.
- No `@Nested`, no shared base classes, no test rule abstractions. Each test file is self-contained.

## Coroutine Testing

Three patterns are in use — pick by test subject:

1. **ViewModel bound to `viewModelScope`** (preferred):
   ```kotlin
   private val testDispatcher = StandardTestDispatcher()

   @Before fun setUp() { Dispatchers.setMain(testDispatcher); /* mocks */ }
   @After  fun tearDown() { Dispatchers.resetMain() }

   @Test
   fun `state updates`() =
       runTest {
           backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
               viewModel.uiState.collect()
           }
           testDispatcher.scheduler.advanceUntilIdle()
           assertThat(viewModel.uiState.value).isEqualTo(...)
       }
   ```
   Used in `app/src/test/java/com/clibeats/presentation/home/HomeViewModelTest.kt`, `SettingsViewModelTest.kt`, `LibraryViewModelTest.kt`, `QueueViewModelTest.kt`, `PlaylistViewModelTest.kt`.

2. **Forwarding test dispatcher into `runTest`** — when assertions may trigger emissions after `setMain`:
   ```kotlin
   @Test
   fun `initial state reflects preferences`() =
       runTest(testDispatcher) {
           testDispatcher.scheduler.advanceUntilIdle()
           ...
       }
   ```
   (`SettingsViewModelTest.kt:62-70`).

3. **`UnconfinedTestDispatcher` + `setMain`** — for simple delegation tests without `runTest` (`app/src/test/java/com/clibeats/presentation/player/PlayerViewModelTest.kt:39-47`).

Every test that touches `Dispatchers.Main` must `resetMain()` in `@After`; do not leave a global main dispatcher behind.

For suspend functions with no ViewModel, just `runTest { ... }` (`app/src/test/java/com/clibeats/data/provider/AudiusMusicProviderTest.kt`, `app/src/test/java/com/clibeats/data/repository/PlaylistRepositoryImplTest.kt`).

## Mocking

**Framework:** Mockito-Kotlin (`org.mockito.kotlin.*`). No mockk, no Mockito `@Mock`/`MockitoExtension`.

**Patterns:**

```kotlin
private lateinit var playlistDao: PlaylistDao

@Before
fun setUp() {
    playlistDao = mock()
    repository = PlaylistRepositoryImpl(playlistDao)
}

@Test
fun `reorderPlaylistSongs delegates to playlistDao`() =
    runTest {
        repository.reorderPlaylistSongs("p1", listOf("s2", "s1"))
        verify(playlistDao).reorderPlaylistSongs("p1", listOf("s2", "s1"))
    }
```

Source: `app/src/test/java/com/clibeats/data/repository/PlaylistRepositoryImplTest.kt`.

- Stub with `whenever(mock.method(any())).thenReturn(value)` / `.thenThrow(...)`.
- Verify with `verify(mock).method(args)`, `verify(mock, never()).method(any())`; use `any()`, `eq()` matchers.
- Android framework objects are mocked, not Robolectric'd: `mock<Context>()` plus `whenever(context.cacheDir).thenReturn(...)` (`app/src/test/java/com/clibeats/data/cache/CacheManagerTest.kt:20-27`), `mock<ExoPlayer>()` (`app/src/test/java/com/clibeats/playback/PlayerAdapterTest.kt`).
- `org.mockito.kotlin.verify` is sometimes fully qualified inline (`org.mockito.kotlin.verify(playlistDao)...` at `PlaylistRepositoryImplTest.kt:53`) — prefer the import.

**What to Mock:**
- DAOs, repositories, `MusicProvider` instances, `PlayerAdapter`/`ExoPlayer`, `Context`, `AppPreferences` (DataStore flows stubbed with `flowOf(...)`).
- Network boundaries when the behavior under test is mapping/error handling (prefer MockWebServer when the Retrofit stack itself is under test).

**What NOT to Mock:**
- Pure functions/mappers (`toDomain`, `toEntity`, `parseDurationMs`, `formatDuration`, `artworkUrlFor`) — call them directly (`app/src/test/java/com/clibeats/data/provider/mapper/TrackMapperTest.kt`, `app/src/test/java/com/clibeats/presentation/search/SearchScreenKtTest.kt`).
- Room itself — use an in-memory database in `androidTest` instead.
- Domain models and sealed results — construct real instances.

**Fakes:** hand-written fakes are the pattern for multi-method interfaces:

```kotlin
private class FakeProvider(
    override val providerId: String,
    override val displayName: String,
) : MusicProvider {
    override suspend fun search(query: String, limit: Int) = ProviderResult.Success(emptyList())
    // ...
}
```

Source: `app/src/test/java/com/clibeats/data/provider/ProviderRegistryTest.kt:14-32`.

## Fixtures and Factories

**Test Data:**
- Private builder helpers inside each test class, with sensible defaults and a `id` parameter for variation:
  ```kotlin
  private fun fakeTrack(): Track =
      Track(
          id = "audius:abc",
          title = "Trending Track",
          artist = "Artist",
          album = "Album",
          durationMs = 180_000L,
          artworkUrl = null,
          streamUrl = "https://cdn.example/stream",
          providerId = "audius",
      )
  ```
  (`HomeViewModelTest.kt:108-118`; see also `testTrack(id: String = "t1")` in `app/src/test/java/com/clibeats/data/local/mapper/MapperTest.kt:15-25`).
- JSON fixtures are `private val` raw strings with `.trimIndent()` in the provider test, enqueued via a helper (`InternetArchiveMusicProviderTest.kt:48-68`).
- Expected-value helpers are functions, e.g. `private fun streamEndpoint(rawId: String): String` (`AudiusMusicProviderTest.kt:120-122`).

**Location:**
- No shared fixture module or `testFixtures` source set. Keep builders inside the test class; if the same model is needed by 3+ classes, add a `private` builder in each rather than a shared mutable object.

## MockWebServer (Retrofit/provider tests)

Use when the Retrofit/OkHttp stack is part of the behavior (serialization, interceptors, error codes):

```kotlin
@Before
fun setUp() {
    server = MockWebServer().also { it.start() }
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val api: InternetArchiveApi =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(InternetArchiveApi::class.java)
    provider = InternetArchiveMusicProvider(api)
}

@After fun tearDown() { server.shutdown() }
```

Source: `app/src/test/java/com/clibeats/data/provider/InternetArchiveMusicProviderTest.kt:24-46`. Also used in `app/src/test/java/com/clibeats/data/provider/api/InnerTubeHeaderInterceptorTest.kt`.

- Enqueue one response per expected HTTP call — the queue order matters.
- Assert the semantic result (`ProviderResult.Success.data`), not the raw HTTP request, unless testing header injection.

## Screenshot Tests (Paparazzi)

**Framework:** Paparazzi 1.3.4, JVM-based, no emulator.

```kotlin
class PlayerBarScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun playerBar_idleState() {
        paparazzi.snapshot {
            CliBeatsTheme {
                PlayerBar(trackTitle = "Not playing", artist = "", isPlaying = false, progress = 0f)
            }
        }
    }
}
```

Source: `app/src/test/java/com/clibeats/theme/PlayerBarScreenshotTest.kt`.

- Always wrap snapshots in `CliBeatsTheme { ... }` so theme tokens are applied.
- One test per visual state (`idleState`, `playingState`, `nowPlayingState`, `longTitleTruncation`).
- Baselines: `app/src/test/snapshots/images/com.clibeats.theme_<Class>_<method>.png`.
- After an intentional UI change run `./gradlew recordPaparazziDebug` and commit the regenerated PNGs; otherwise `verifyPaparazziDebug` (and any future CI step) fails.
- Add a new screenshot test whenever you add a reusable component in `app/src/main/java/com/clibeats/presentation/component/`.

## Instrumented Tests (Room DAOs)

```kotlin
@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    private lateinit var db: CliBeatsDatabase
    private lateinit var playlistDao: PlaylistDao

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CliBeatsDatabase::class.java,
            ).allowMainThreadQueries().build()
        playlistDao = db.playlistDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun addAndGetSongsForPlaylist() =
        runTest {
            // upsert -> insert cross-ref -> assert via flow.first()
        }
}
```

Source: `app/src/androidTest/java/com/clibeats/data/local/dao/PlaylistDaoTest.kt`.

- In-memory DB only; never touch the on-disk database.
- `runTest` is available (`androidTestImplementation(libs.coroutines.test)`).
- Always `db.close()` in `@After`.
- Schema changes are validated by committed Room schemas in `app/schemas/com.clibeats.data.local.CliBeatsDatabase/`.
- These are **not** executed in CI today — run `connectedDebugAndroidTest` locally before changing DAOs/entities.

## Coverage

**Requirements:** None enforced by tooling. No JaCoCo/Kover plugin is configured in `app/build.gradle.kts` or the root `build.gradle.kts`, and `.github/workflows/ci.yml` has no coverage step.

The stated product target is ≥85% unit/integration coverage (`docs/adr/ADR-009-comprehensive-testing-hardening.md`), but nothing verifies it.

**View Coverage:**
- Not available out of the box. To add: apply `jacoco` in `app/build.gradle.kts`, enable `testCoverageEnabled`, add a `jacocoTestReport` task, and upload `app/build/reports/jacoco/` as a CI artifact.

**Practical coverage map** (unit tests, 45 files):
- Well covered: `data/provider/*` (5 providers + mappers), `data/repository/*` (5 impls), ViewModels (`home`, `search`, `library`, `player`, `playlist`, `queue`, `settings`), theme tokens, playlist codec/parser.
- Thin: `playback` (`PlayerAdapter`, queue), `data/download`, `data/scan`.
- Placeholder-only: `integration/PlaybackIntegrationTest.kt` (asserts a constructed `Track` — no real flow), `data/network/NetworkMonitorTest.kt` (class-existence only), `presentation/component/PlayerBarTest.kt` (reflection on `PlayerBarKt`).

## Test Types

**Unit Tests (JVM):**
- Scope: providers, repositories, mappers, ViewModels, theme values, parsers, formatters.
- Approach: Mockito mocks for collaborators, `runTest` for suspend, Truth assertions.
- 45 files under `app/src/test/`.

**Integration Tests:**
- `app/src/test/java/com/clibeats/integration/PlaybackIntegrationTest.kt` is currently a structural placeholder; there is no end-to-end playback integration test despite ADR-009 claiming one.
- `app/src/androidTest/.../dao/*` are the closest thing to integration tests (real Room + in-memory DB).

**Screenshot Tests:**
- Paparazzi, 3 files / 6 baselines in `app/src/test/java/com/clibeats/theme/` and `app/src/test/snapshots/images/`.

**E2E / UI Tests:**
- Not used. `testImplementation(libs.compose.ui.test.junit4)` and `debugImplementation(libs.compose.ui.test.manifest)` are declared but no test calls `createComposeRule()` or `createAndroidComposeRule()`. Espresso is declared for `androidTest` but unused.

## Common Patterns

**Async Testing:**

```kotlin
@Test
fun `search returns Success with mapped composite tracks`() =
    runTest {
        whenever(api.searchTracks(any(), any(), any())).thenReturn(response)
        val result = provider.search("lofi")
        assertThat(result).isInstanceOf(ProviderResult.Success::class.java)
    }
```

Never use `Thread.sleep` or `delay()` in tests — advance virtual time with `testDispatcher.scheduler.advanceUntilIdle()`.

**Error Testing:**

```kotlin
@Test
fun `search returns Error when api throws exception`() =
    runTest {
        whenever(api.searchTracks(any(), any(), any())).thenThrow(RuntimeException("network error"))
        val result = provider.search("lofi")
        assertThat(result).isInstanceOf(ProviderResult.Error::class.java)
        assertThat((result as ProviderResult.Error).message).contains("network error")
    }
```

(`AudiusMusicProviderTest.kt:60-69`). For MockWebServer errors, enqueue `MockResponse().setResponseCode(500)` (`InternetArchiveMusicProviderTest.kt:121-128`).

**Sealed-state assertions:** cast after an `isInstanceOf` check, or use `when` with an exhaustive branch:

```kotlin
val state = viewModel.uiState.value
assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
assertThat((state as HomeUiState.Success).tracks).hasSize(1)
```

**Pure-function table testing:** there is no `@Parameterized` usage; separate `@Test` methods per case (`TrackMapperTest.kt`, `SearchScreenKtTest.kt`, `ArtworkUrlTest.kt`). Keep that style unless a case matrix grows beyond ~10 rows.

## Repo-wide test hygiene rules

1. Test package must equal the production package of the class under test.
2. Add `@file:Suppress("ForbiddenImport")` (plus a one-line reason) whenever importing `com.clibeats.data.*` inside a non-data test.
3. No network, no device storage, no real clock dependence: stub time (`1000L`-style fixed epochs) and use in-memory/temp paths (`CacheManagerTest` uses `System.getProperty("java.io.tmpdir")`).
4. One behavior per test; name the behavior, not the method.
5. Fix or delete placeholder tests rather than adding more; `LicenseComplianceTest.kt` is a useful guard for `docs/LICENSES.md` and should stay.

---

*Testing analysis: 2026-11-19*
