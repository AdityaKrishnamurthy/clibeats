# CLIBeats v2.0.0 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.0.0.apk` (verify with `SHA256SUMS.txt`)

v2.0 is a full overhaul of playback, the local library and the interface.

## Highlights

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

## Fixes

- Songs couldn't be added to playlists; queued songs didn't play; local results were slow.
- A song that fails to play is now skipped with a short notice instead of stalling the queue.
- The "end of track" sleep timer works with repeat-one.
- Changing the artwork cache limit applies immediately.
- Search results follow your region (country and language).

## Under the hood

- Media3 `MediaLibraryService` with playback resumption; Room upserts that no longer wipe playlists.
- NewPipeExtractor v0.27.1; Coil image pipeline tuned for instant thumbnails.
- 198 unit tests, ktlint, detekt and Android Lint in CI; signed release builds from tags.

## Upgrading

If you installed an earlier build signed with a different key, uninstall it first. Export your playlists
(Settings → Playlist exchange) before uninstalling and import them afterwards.
