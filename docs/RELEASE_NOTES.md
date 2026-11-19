# CLIBeats v2.1.0 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.1.0.apk` (verify with `SHA256SUMS.txt`)

v2.1.0 is the first public release of the v2 line. It adds in-app updates and ships with a permanent signing
key, so from here on every update installs from inside the app. It also includes the full v2.0 overhaul of
playback, the local library and the interface.

## New in 2.1.0

- **In-app updates** — when a new version is out, a pop-up offers **DOWNLOAD** or **LATER** (hides it for a day).
  The APK is saved to Downloads, checked against the release's SHA-256 checksum, and **INSTALL** hands it to
  Android's installer. The previous update APK is cleaned up automatically.
- **Settings → Updates** — auto-check **Daily** (default), **Weekly** or **Off**, plus **CHECK NOW**, download,
  install and a link to the release notes. Checks are light: at most once per interval, silent when offline.
- **Permanent release key** — releases are signed with the CLIBeats key
  (certificate SHA-256 `bef6949089c552944cc9691d543326d42537a20eef163bb3961b30dbd146b26a`).

## Included from 2.0

- **Reliable queue playback** — every queued song plays when it's reached (skip, auto-advance, repeat).
  Streams resolve lazily, so playback starts after ~0.5 s and expired YouTube links refresh themselves.
- **Your own music** — add one or more folders; FLAC (16/24-bit), WAV, MP3, M4A/AAC, OGG/Opus, ALAC and
  AIFF with tags and cover art. M3U / M3U8 / PLS playlists show up as read-only local playlists.
- **Full-screen Now Playing** — high-res artwork, quality line (codec · bitrate · sample rate · bit depth),
  repeat, shuffle, sleep timer, queue, add to playlist and favourite.
- **Favourites & playlists** — playlist grid with ♥ Favorites and Most Played; export / import includes
  Favorites.
- **Gestures** — swipe between tabs; swipe a song right to queue it, left to favourite it; long-press to add
  it to a playlist.
- **System integration** — background playback, notification and lock-screen controls with high-res art,
  and a working **resume card** after the app is closed (same song, same position).
- **Themes** — Dark, AMOLED and Light with six accent colours; first-run setup on new installs.
- **Storage** — separate song and artwork caches, each with its own limit and clear button.
- **New app icon** — terminal window with a prompt and spectrum bars, plus a themed (monochrome) icon.

### Fixes

- Songs couldn't be added to playlists; queued songs didn't play; local results were slow.
- A song that fails to play is skipped with a short notice instead of stalling the queue.
- The "end of track" sleep timer works with repeat-one.
- Changing the artwork cache limit applies immediately.
- Search results follow your region (country and language).

## Under the hood

- Media3 `MediaLibraryService` with playback resumption; Room upserts that no longer wipe playlists.
- NewPipeExtractor v0.27.1; Coil image pipeline tuned for instant thumbnails.
- Update checks use the GitHub Releases API with ETags; downloads use Android's DownloadManager.
- 215 unit tests, ktlint, detekt and Android Lint in CI.

## Updates

From this version on, new releases update from inside the app (Settings → Updates).
