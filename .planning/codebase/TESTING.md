---
last_mapped_commit: 916a56379cbb0bf07eb302edd4489c7633418417
last_mapped_at: 2026-11-17
---
# Testing Patterns

**Analysis Date:** 2026-11-17

## Test Framework

**Runner:**
- JUnit 4.13.2 (`libs.junit`) — the only runner. No JUnit 5, no Robolectric, no Kover/JaCoCo.
- Unit tests run on the JVM (`app/src/test`); instrumented tests use `AndroidJUnit4`
  (`androidx.test.ext:junit` 1.2.1) in `app/src/androidTest`.
- Config: `app/build.gradle.kts` (no separate test config file). A `tasks.withType<Test>` block
  exports `ANDROID_HOME` / `ANDROID_SDK_ROOT` to unit tests, which Paparazzi requires.

**Assertion Library:**
- Google Truth 1.x — `import com.google.common.truth.Truth.assertThat` (used in 32 test files).
  `libs.truth` is **not** declared; it arrives transitively via `libs.media3.test.utils`. If that
  dependency is removed, ~32 test files break.
- `org.junit.Assert.*` (`assertEquals`, `assertNull`, `assertNotNull`, `assertFalse`) in 11 older
  test files.
- `androidx.compose.ui.test.junit4` is declared as `testImplementation` but **no test uses
  `createComposeRule`** today.

**Mocking / Support Libraries:**
- `org.mockito.kotlin` 5.4.0 (`mock`, `whenever`, `verify`, `any`) — dominant.
- `org.mockito` 5.12.0 core (`org.mockito.ArgumentMatchers.anyString`, used for `SharedPreferences`).
- `org.jetbrains.kotlinx:kotlinx-coroutines-test` 1.8.1 (`runTest`, `StandardTestDispatcher`,
  `UnconfinedTestDispatcher`, `Dispatchers.setMain`, `advanceTimeBy`, `advanceUntilIdle`).
- `okhttp3:mockwebserver` 4.12.0 — real HTTP stubs against Retrofit/OkHttp.
- `androidx.room:room-testing` — in-memory Room databases (unit + androidTest).
- `app.cash.paparazzi` 1.3.4 — JVM screenshot tests.
- `androidx.media3:media3-test-utils` — pulled in for `ExoPlayer` test doubles/transitives.
- `compose-ui-test-junit4` + `compose-ui-test-manifest` (declared, unused).

**Run Commands:**
```bash
./gradlew testDebugUnitTest          # All JVM unit tests (183 @Test)
./gradlew verifyPaparazziDebug       # Screenshot regression (records on first run)
./gradlew recordPaparazziDebug       # Re-record snapshots after intentional UI changes
./gradlew connectedDebugAndroidTest  # Instrumented DAO tests (14 @Test) — needs emulator/device
./gradlew testDebugUnitTest --tests "com.clibeats.data.provider.AudiusMusicProviderTest"
./scripts/check-quality-gates.sh     # ktlint + detekt + lintDebug + testDebugUnitTest
```

CI (`.github/workflows/ci.yml`) runs `ktlintCheck`, `detekt`, `lintDebug --continue`,
`assembleDebug`, `testDebugUnitTest` on JDK 17 / Ubuntu, then uploads
`app/build/reports/tests/`, `app/build/reports/detekt/`, `lint-results-debug.html`.
It does **not** run `verifyPaparazziDebug` or `connectedDebugAndroidTest`.

## Test File Organization

**Location:**
- Co-located by package mirror: `app/src/test/java/com/clibeats/<same path as main>/<Class>Test.kt`
  (43 files, ~3,245 LOC).
- Instrumented: `app/src/androidTest/java/com/clibeats/data/local/dao/<Dao>Test.kt`
  (4 files: `SongDaoTest`, `PlaylistDaoTest`, `HistoryDaoTest`, `CacheIndexDaoTest`).
- Screenshot tests live in a synthetic `com.clibeats.theme` package regardless of the component's
  real package: `app/src/test/java/com/clibeats/theme/PlayerBarScreenshotTest.kt`.
- Cross-cutting tests use synthetic packages: `com.clibeats.integration`, `com.clibeats.license`.

**Naming:**
- Class: `<ClassUnderTest>Test` (or `<Feature>ScreenshotTest`, `<Feature>IntegrationTest`).
- Two method-naming styles coexist:
  - Backticked behavioural sentences (dominant in recent work, 123 occurrences):
    ```kotlin
    @Test
    fun `trending error surfaces message`() = runTest { ... }
    ```
  - camelCase with underscores (older files, 99 occurrences):
    ```kotlin
    @Test
    fun upsertAndGetById() = runTest { ... }
    fun track_construction_succeeds_with_valid_fields() { ... }
    ```
  Use the backticked style for new tests: it states observable behaviour
  (`search returns Error state on provider error`).
- Snapshot files are auto-named `<package>_<Class>_<method>.png` and committed:
  `app/src/test/snapshots/images/com.clibeats.theme_PlayerBarScreenshotTest_playerBar_playingState.png`.

**Structure:**
```
app/src/
├── test/java/com/clibeats/
│   ├── data/{cache,download,local,network,playlist,preferences,provider,repository}/
│   ├── domain/model/
│   ├── integration/
│   ├── license/
│   ├── playback/
│   ├── presentation/{component,home,library,player,playlist,queue,search,settings}/
│   ├── telemetry/
│   └── theme/
├── test/snapshots/images/            # Paparazzi goldens (committed)
└── androidTest/java/com/clibeats/data/local/dao/
```

## Test Structure

**Suite Organization (dominant shape):**
```kotlin
@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.home

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var musicProvider: MusicProvider
    private lateinit var providerRegistry: ProviderRegistry
    private lateinit var appPreferences: AppPreferences
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        musicProvider = mock()
        providerRegistry = mock()
        appPreferences = mock()

        whenever(providerRegistry.getProvider(any())).thenReturn(musicProvider)
        whenever(providerRegistry.defaultProvider()).thenReturn(musicProvider)
        whenever(appPreferences.activeProviderId).thenReturn(flowOf("audius"))

        viewModel = HomeViewModel(providerRegistry, appPreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() {
        assertThat(viewModel.uiState.value).isEqualTo(HomeUiState.Loading)
    }
}
```

**Patterns:**
- `@Before` method name is `setUp` (newer) or `setup` (older) — both are present; prefer `setUp`.
- Fields are `private lateinit var` for collaborators and the system under test; constructed in `@Before`.
- No dependency-injection framework in tests: the constructor is called directly
  (`HomeViewModel(providerRegistry, appPreferences)`, `AudiusMusicProvider(api)`).
- `@After` always restores globals it changed (`Dispatchers.resetMain()`, `server.shutdown()`,
  `db.close()`, `tempFile?.delete()`).
- Tests are grouped in a single class with no `@Nested` blocks.
- Assertions use Truth `assertThat(...)` with `isEqualTo`, `hasSize`, `isInstanceOf`, `isEmpty`,
  `contains`. Cast-then-assert is the idiom for sealed results:
  ```kotlin
  assertThat(result).isInstanceOf(ProviderResult.Success::class.java)
  val tracks = (result as ProviderResult.Success).data
  assertThat(tracks).hasSize(1)
  ```

## Mocking

**Framework:** Mockito-Kotlin (`org.mockito.kotlin.*`). No fake/stub classes, no MockK, no Mockito
annotations (`@Mock`/`@InjectMocks`) — mocks are created inline with `mock()`. There is no
`MockitoExtension`/`MockitoAnnotations.initMocks`.

**Patterns:**
```kotlin
// Stub
whenever(api.searchTracks(any(), any(), any())).thenReturn(AudiusSearchResponse(data = listOf(dto)))

// Stub a Flow property
whenever(appPreferences.activeProviderId).thenReturn(flowOf("audius"))

// Stub an exception
whenever(api.searchTracks(any(), any(), any())).thenThrow(RuntimeException("network error"))

// Verify interaction
verify(exoPlayer).seekTo(5000L)

// Java-style matchers when needed (varargs/overloads)
whenever(editor.putString(anyString(), anyString())).thenReturn(editor)
```
- Mocked types are interfaces or framework classes (`MusicProvider`, `ProviderRegistry`, `SongDao`,
  `ExoPlayer`, `SharedPreferences`); no `mockito-inline` final-class mocking is configured.
- Room is never mocked in DAO tests — a real in-memory database is used instead.

**What to Mock:**
- Every collaborator crossing a layer boundary (DAO in repository tests, `MusicProvider` in ViewModel
  tests, `ExoPlayer` in `PlayerAdapterTest`, `SharedPreferences` in `AppPreferencesTest`).
- Android framework objects that cannot be instantiated on the JVM (`Context` via `mock<Context>()`).

**What NOT to Mock:**
- The class under test — always construct the real object.
- Room (`Room.inMemoryDatabaseBuilder`), Retrofit/OkHttp (use `MockWebServer`), serialization
  (`CliBeatsFileCodec` round-trips real JSON), Compose rendering (Paparazzi renders for real).
- Domain data classes (`Track`, `Playlist`) — construct real instances.

## Fixtures and Factories

**Test Data:**
- No shared fixture module. Each test class carries small private builders with defaulted params:
  ```kotlin
  // app/src/test/java/com/clibeats/data/local/mapper/MapperTest.kt
  private fun testTrack(id: String = "t1") =
      Track(id = id, title = "Title", artist = "Artist", album = "Album",
            durationMs = 200_000L, artworkUrl = null, streamUrl = null, providerId = "local")

  // app/src/androidTest/java/com/clibeats/data/local/dao/SongDaoTest.kt
  private fun testSong(id: String = "s1") = SongEntity(...)

  // app/src/test/java/com/clibeats/presentation/home/HomeViewModelTest.kt
  private fun fakeTrack(): Track = Track(id = "audius:abc", title = "Trending Track", ...)
  ```
- Network fixtures are raw JSON triple-quoted strings `.trimIndent()`-ed and enqueued on
  `MockWebServer`:
  ```kotlin
  private fun enqueueJson(body: String) {
      server.enqueue(
          MockResponse().setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody(body),
      )
  }
  ```
  (`app/src/test/java/com/clibeats/data/provider/InternetArchiveMusicProviderTest.kt`,
  `app/src/test/java/com/clibeats/data/provider/api/InnerTubeHeaderInterceptorTest.kt`).
- DataStore fixtures use a temp file + `UnconfinedTestDispatcher`:
  ```kotlin
  private fun createTestDataStore(): DataStore<Preferences> =
      PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher() + Job())) {
          File.createTempFile("clibeats_test", ".preferences_pb").also { tempFile = it }
      }
  ```
  (`app/src/test/java/com/clibeats/data/preferences/AppPreferencesTest.kt`).

**Location:**
- Builders live at the bottom of the test class that uses them. If two classes need the same fixture,
  extract a `app/src/test/java/com/clibeats/testing/Fixtures.kt` object rather than copy-pasting.

## Coverage

**Requirements:** None enforced. There is no coverage tool (`jacoco`, `kover`, `testCoverageEnabled`
all absent) and no coverage threshold in CI. Do not claim coverage numbers; state what is tested.

**View Coverage:**
- Not configured. HTML test reports are uploaded by CI from `app/build/reports/tests/`.
- Local report: `app/build/reports/tests/testDebugUnitTest/index.html`.

## Test Types

**Unit Tests (JVM, 43 files / 183 `@Test`):**
- Mappers: `data/local/mapper/MapperTest.kt`, `data/provider/mapper/{AudiusMapperTest,TrackMapperTest,InternetArchiveMapperTest}.kt`.
- Repositories against mocked DAOs: `data/repository/{SongRepositoryImplTest,PlaylistRepositoryImplTest,
  PlaybackRepositoryImplTest,LibraryRepositoryImplTest,HistoryRepositoryImplTest}.kt`.
- Providers against mocked APIs or `MockWebServer`: `data/provider/*Test.kt`.
- ViewModels with coroutine test dispatchers: `presentation/*/*ViewModelTest.kt`.
- Playback engine: `playback/PlayerAdapterTest.kt`, `playback/PlayerAdapterQueueTest.kt` (mock `ExoPlayer`).
- Codecs / pure logic: `data/playlist/CliBeatsFileCodecTest.kt`, `domain/model/TrackTest.kt`.

**Instrumented Tests (14 `@Test`):**
```kotlin
@RunWith(AndroidJUnit4::class)
class SongDaoTest {
    private lateinit var db: CliBeatsDatabase
    private lateinit var dao: SongDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CliBeatsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.songDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `searchAsFlow finds matching titles`() = runTest { ... }
}
```
Scope is Room DAO behaviour only (upsert/conflict, flow ordering, LIKE escaping, delete).
Requires a device/emulator and is not part of the CI gate.

**Screenshot Tests (Paparazzi, 3 files / 7 snapshots):**
```kotlin
class PlayerBarScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun playerBar_playingState() {
        paparazzi.snapshot {
            CliBeatsTheme {
                PlayerBar(trackTitle = "Midnight City", artist = "M83", isPlaying = true, progress = 0.4f)
            }
        }
    }
}
```
- Always render inside `CliBeatsTheme { }` on `DeviceConfig.PIXEL_5`.
- Goldens are committed in `app/src/test/snapshots/images/`; any visual change requires
  `./gradlew recordPaparazziDebug` and a reviewed PNG diff in the commit.

**E2E Tests:**
- Not used (no Espresso flows, no UIAutomator). End-to-end validation is done manually with
  emulator + `adb` and archived under `.planning/evidence/` and `.planning/debug/`.

**Quality / Compliance Tests:**
- `app/src/test/java/com/clibeats/license/LicenseComplianceTest.kt` asserts `docs/LICENSES.md` exists
  and contains required attributions. It resolves the repo root via
  `File(".").canonicalFile.parentFile ?: File(".")` — fragile if the Gradle working dir changes.
- `app/src/test/java/com/clibeats/integration/PlaybackIntegrationTest.kt` is currently a tautology
  (mocks `PlayerAdapter`, asserts a locally constructed `Track` field). Do not treat it as coverage of
  the playback pipeline.

## Common Patterns

**Async Testing (ViewModels / StateFlow):**
```kotlin
@Test
fun `trending success populates tracks`() =
    runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        whenever(musicProvider.trending(any())).thenReturn(ProviderResult.Success(listOf(fakeTrack())))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
    }
```
- `Dispatchers.setMain(StandardTestDispatcher())` in `@Before`, `Dispatchers.resetMain()` in `@After`,
  `@OptIn(ExperimentalCoroutinesApi::class)` on the class.
- Hot flows require a collector before advancing; `advanceUntilIdle()` for immediate emission,
  `advanceTimeBy(400L)` for debounce windows (`SearchViewModelTest`).
- `runTest` is used as an expression body (`fun x() = runTest { ... }`) in newer tests.

**Exception / Error Testing:**
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
- Assert on the sealed result variant and its `message`/`cause`, not on thrown exceptions — production
  code converts failures to `ProviderResult.Error`.
- Error-path UI tests assert `XUiState.Error` and the exact message string.

**Network Testing (MockWebServer):**
```kotlin
@Before
fun setUp() {
    server = MockWebServer()
    server.start()
    val api: InternetArchiveApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(InternetArchiveApi::class.java)
    provider = InternetArchiveMusicProvider(api)
}
```
- Enqueue one `MockResponse` per expected HTTP call, in call order; assert on mapped domain objects,
  not raw JSON.

**Interaction Testing (delegation):**
```kotlin
@Test
fun `reorderPlaylistSongs delegates to playlistDao`() =
    runTest {
        repository.reorderPlaylistSongs("p1", listOf("s2", "s1"))
        verify(playlistDao).reorderPlaylistSongs("p1", listOf("s2", "s1"))
    }
```
Use only where the value is a side effect; prefer observable-output assertions otherwise.

## Gaps and Risks for New Tests

- **Truth is an undeclared transitive dependency** — add `testImplementation(libs.truth)` (and a
  `[libraries]` entry) before touching the Media3 test deps.
- **Paparazzi is not in CI** and `connectedDebugAndroidTest` is not in CI, so visual regressions and
  DAO behaviour are only caught locally.
- **Compose UI-behaviour tests are effectively absent.** `presentation/component/PlayerBarTest.kt` and
  `SongTableRowTest.kt` only assert `Class.forName("...Kt")` loads — they would pass even if the
  composable were empty. Replace with `createComposeRule()` + semantics assertions when adding UI tests.
- **No shared fixtures**; each class redefines `Track`/`SongEntity` builders. Drift in defaults is likely.
- **`internal fun parseDurationMs`** in `data/provider/mapper/TrackMapper.kt` is the only
  visibility-relaxed test hook; keep test hooks `internal` rather than public.

---

*Testing analysis: 2026-11-17*
