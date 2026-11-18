# CLIBeats

> A free, open-source Android music player with a terminal-inspired interface — stream online or play your own lossless library.

[![Version](https://img.shields.io/badge/version-2.0.0-1DB954)](#)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

CLIBeats is built around three principles:

- **No advertisements**
- **No accounts and no app-side tracking**
- **A fast, terminal-inspired interface** instead of a conventional streaming-app design

**v2.0** is a full overhaul: gapless queue playback, a real local library (FLAC 16/24-bit, WAV, MP3, …),
M3U playlists, a full-screen player, favourites, swipe gestures, light/dark/AMOLED themes, smart caching,
and system media controls with playback resumption.

---

## Screenshots

| | | |
|:---:|:---:|:---:|
| <img src="docs/screenshots/01_home_swipe_right_queue.png" width="230" alt="Home: swipe right to queue"><br>**1. Home** — swipe right → `+ QUEUE` | <img src="docs/screenshots/02_home_swipe_left_favorite.png" width="230" alt="Home: swipe left to favourite"><br>**2. Home** — swipe left → `♥ FAVORITE` | <img src="docs/screenshots/03_search.png" width="230" alt="Search"><br>**3. Search** — Online ▾ / Local switch |
| <img src="docs/screenshots/04_library_online.png" width="230" alt="Library online"><br>**4. Library · Online** — only songs you played | <img src="docs/screenshots/05_library_offline.png" width="230" alt="Library offline"><br>**5. Library · Offline** — your device music | <img src="docs/screenshots/06_playlists_grid.png" width="230" alt="Playlists grid"><br>**6. Playlists** — Favorites, Most Played, local M3U |
| <img src="docs/screenshots/07_settings_theme_local_music.png" width="230" alt="Settings: theme and local music"><br>**7. Settings** — theme, accent, music folders | <img src="docs/screenshots/08_settings_storage_about.png" width="230" alt="Settings: storage and about"><br>**8. Settings** — caches, exchange, about | <img src="docs/screenshots/09_now_playing_online.png" width="230" alt="Now Playing online"><br>**9. Now Playing** — online (`OPUS · 160 kbps`) |
| <img src="docs/screenshots/10_now_playing_local_flac.png" width="230" alt="Now Playing local FLAC"><br>**10. Now Playing** — local hi-res (`FLAC · 24-bit`) | | |

---

## Features

### Playback
- **Queue playback that just works** — every queued song resolves when it's reached (skip, auto-advance, repeat).
- **Fast start** — playback begins after ~0.5 s of buffer; next 2 songs are pre-resolved and their covers prefetched.
- **Repeat** (off → all → one) and **shuffle**, with a tiny `ONE` / `ALL` / `OFF` label above the button.
- **Sleep timer** — 15 / 30 / 45 / 60 min or end of track.
- **Auto-queue related songs** (optional) — tapping a song queues the rest of that list after it.
- **Clear upcoming** keeps the song that's playing.
- Expired YouTube links are refreshed automatically; one silent retry on stream errors.

### Sources
- **Online:** YouTube Music, Internet Archive, Jamendo, Audius — pick from the `ONLINE ▾` drop-down.
- **Local:** your own files — **FLAC (16/24-bit), WAV, MP3, M4A/AAC, OGG/Opus, ALAC, AIFF**.
- **Regional results** — YouTube Music uses your device's country and language; trending shows your country's top songs.

### Local library
- Add **one or more music folders** (system picker, no storage permission); **Rescan** picks up changes.
- Title, artist, album, duration and cover art come from the **file tags**, not file names.
- **M3U / M3U8 / PLS playlists** in your folders appear as read-only `[LOCAL]` playlists
  (with **Save as app playlist** to make an editable copy).

### Library, playlists & favourites
- **Library:** `[ ONLINE ]` shows only songs you've played; `[ OFFLINE ]` shows your device music. Tracks / Artists / Albums.
- **Playlists grid:** ♥ Favorites, Most Played, your playlists (most recent first), a **+** tile, then local playlists.
- **Favourites:** heart in Now Playing, or swipe left on any song.
- **Recently Played** on Home, with `[ CLEAR ]`.
- **Playlist export / import** (`clibeats.json`, any location via the system picker) — includes Favorites.

### Now Playing
- High-res cover art: the low-res thumbnail shows instantly and the full-quality cover fades in.
- Quality line: source + codec + bitrate + sample rate (+ bit depth for lossless), e.g.
  `[LOCAL] FLAC · 1857 kbps · 48 kHz · 24-bit` or `[ONLINE · YouTube Music] OPUS · 160 kbps · 48 kHz`.
- Seek bar · repeat · previous · play/pause · next · shuffle · sleep timer · queue · add to playlist · ♥.

### System integration
- **Background playback** and lock-screen / notification controls with **high-res artwork**.
- Tapping the notification opens the app; the session survives swiping the app away.
- **Resume card** in Quick Settings after the app is closed (Android 11+), restoring your last queue.

### Storage & performance
- **Two caches, each with its own limit and `[ CLEAR ]`:** artwork (64 MB–512 MB) and songs (256 MB–2 GB).
- **Song cache:** streamed songs replay instantly without using data; local files are never duplicated.
- One shared thumbnail per cover, disk-cached images, lightweight list rendering.

### Interface
- Terminal-style UI (JetBrains Mono, bracketed controls, bordered blocks).
- **Themes:** Dark · AMOLED · **Light**, with six accent colours; dialogs and menus follow the theme.
- **First-run setup** (new installs): theme → music folder → default source (Online / Local).

---

## Gestures

| Where | Gesture | Action |
|---|---|---|
| Anywhere outside a song row | Swipe left / right | Next / previous tab (Home → Search → Library → Queue → Playlists → Settings) |
| Any song row | **Swipe right** | Add to queue (`+ QUEUE`) |
| Any song row | **Swipe left** | Add to ♥ Favorites (`♥ FAVORITE`) |
| Any song row | **Long-press** | Add to playlist (with inline "+ New playlist") |
| Bottom bar · **Search** | **Double-tap** | Focus the search box and open the keyboard |
| Bottom bar · **Playlists** | Tap again (inside a playlist) | Back to the playlist grid |
| Mini player | Tap song / artwork | Open Now Playing |
| Now Playing | **Drag down** on the artwork | Close the player |
| Repeat button | Tap repeatedly | Off → All → One |
| Playlist tile | Long-press | Delete playlist (app playlists only) |
| Back | — | Close player / playlist, then return to Home |

---

## Navigation

```
 Home   Search   Library   Queue   Playlists   ⚙ Settings
```

- **Home** — source switch, recently played, trending, mini player.
- **Search** — search the active source; play next / add to playlist / add to queue on each result.
- **Library** — Online (played) / Offline (device) · Tracks / Artists / Albums.
- **Queue** — reorder, remove, clear upcoming; tap to jump.
- **Playlists** — Favorites, Most Played, app playlists, local M3U playlists.
- **Settings** — theme & accent, local music folders, playback, provider, storage, playlist exchange, about.

---

## Architecture

```text
presentation (Compose screens + ViewModels)
        │
domain (models, repository & provider interfaces)
        │
data (providers, Room, DataStore, scanner, caches)  ──  playback (ExoPlayer, MediaLibraryService)
```

- **Providers** implement one `MusicProvider` interface (search, trending, stream), so sources are interchangeable.
- **Lazy stream resolution:** queue items carry a `clibeats://track?...` placeholder that a Media3
  `ResolvingDataSource` turns into a real URL (or cached file) only when played.
- **Song cache:** Media3 `SimpleCache` keyed per track, with an adjustable LRU limit; local files bypass it.
- **Local scanner:** SAF `DocumentsContract` walk + `MediaMetadataRetriever` tags; M3U/PLS parsed into read-only playlists.
- **System controls:** `MediaLibraryService` (notification, lock screen, resume card via the recent-item browse root).

---

## Technology

Kotlin · Jetpack Compose (Material 3) · Media3 ExoPlayer & Session · Hilt · Room · DataStore ·
Retrofit / OkHttp · kotlinx.serialization · Coil · NewPipeExtractor · JUnit / Mockito / Truth / Paparazzi ·
ktlint · detekt.

---

## Requirements

- Android 8.0 (API 26) or newer
- To build: JDK 17, Android SDK 34

---

## Build From Source

```bash
git clone https://github.com/Omprakash-p06/clibeats.git
cd clibeats
```

```powershell
.\gradlew.bat testDebugUnitTest      # unit tests
.\gradlew.bat ktlintCheck detekt     # style & static analysis
.\gradlew.bat lintDebug              # Android Lint
.\gradlew.bat assembleDebug          # debug APK  -> app/build/outputs/apk/debug/
.\gradlew.bat assembleRelease        # release APK -> app/build/outputs/apk/release/
```

Screenshot tests: `.\gradlew.bat verifyPaparazziDebug` (needs `ANDROID_HOME` set).

### Releases

Pushing a tag like `v2.0.0` runs `.github/workflows/release.yml`, which builds a signed APK and publishes a
GitHub Release with SHA-256 checksums. Signing uses the repository secrets `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` (falls back to the debug key if they're missing).
Version name/code come from the tag (`-PversionName` / `-PversionCode`); local builds default to 2.0.0.

---

## Project Structure

```text
app/src/main/java/com/clibeats/
├── data/          # providers (YouTube, IA, Jamendo, Audius, local), Room, DataStore,
│                  # scan (local scanner, M3U parser), cache (song/artwork caches), playlist exchange
├── domain/        # models, repository + provider interfaces
├── playback/      # PlayerAdapter, StreamResolver, TrackUriResolver, PlaybackService
├── presentation/  # home, search, library, queue, playlist, player, settings, onboarding, theme, components
└── di/            # Hilt modules
docs/              # screenshots, ADRs, user guide, licenses
```

---

## Privacy

CLIBeats is local-first: no CLIBeats account and no CLIBeats backend. Library, playlists, favourites,
queue and history are stored on the device. Online providers receive the requests needed to search and
stream; their own policies apply.

---

## Legal Notice

CLIBeats does not host or provide music. Availability depends on the selected provider. Users are
responsible for complying with providers' terms of service and copyright law in their jurisdiction.

---

## Documentation

- [User Guide](docs/USER_GUIDE.md)
- [Release Notes](docs/RELEASE_NOTES.md)
- [Architecture Decision Records](docs/adr/)
- [Third-party Licenses](docs/LICENSES.md)

---

## Contributing

1. Keep provider-specific logic out of the domain layer.
2. Add or update tests for behaviour changes.
3. Run the quality gates (tests, ktlint, detekt, lint) before opening a PR.
4. Keep UI changes consistent with the terminal design system.

---

## Credits

Developed by **[Omprakash Panda](https://github.com/Omprakash-p06/clibeats)**.

## License

CLIBeats is distributed under the MIT License — see [LICENSE](LICENSE).
