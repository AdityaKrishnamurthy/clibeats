# CLIBeats v2.3.0 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.3.0.apk` (verify with `SHA256SUMS.txt`)

## Now Playing

- **Real waveform:** the seek bars now show each song's actual loudness. Local songs (and fully downloaded
  ones) are measured in the background in a few seconds; streamed songs fill in as they play and are
  remembered for next time. Bars that aren't measured yet show a dim placeholder.
- **Previous** works like other players: more than 3 seconds into a song it restarts it, and pressing again
  plays the song before. Also from the notification, lock screen and headphones.
- The quality line (`[LOCAL] FLAC · …`) is always readable: on covers whose colour matches the background it
  switches to a lighter shade or a light amber, never red.
- `[ QUEUE (n) ]` opens the queue right from Now Playing.

## Navigation

- **Four tabs:** Home, Search, Library, Settings. Playlists moved into Library (Tracks / Artists / Albums /
  Playlists) and the queue opens over any screen (≡ in the top bar, the mini player or Now Playing).
- Boxes have their title in the border, with no extra header line; Home no longer has the Quick Navigation box.

## Library & playlists

- **Reorder ♥ Favorites** and your own playlists by dragging the ≡ handle.
- **Recent searches** only list songs you played from search, not ones you queued.
- Local folders: **FLAC, MP3, WAV, M4A (AAC/ALAC), OGG, Opus and AAC** are checked with real sample files;
  AIFF files (which can't be played) are no longer listed.

## Backup & safety

- **Automatic daily backup** of playlists, Favorites, saved albums/artists and settings, also saved to
  `Download/CLIBeats` (Android 10+) so it survives an uninstall. Setup offers to restore it; Settings →
  **SHARE BACKUP** sends a copy anywhere. Android's own backup now brings the library back on a new phone.
- A failed or unreadable music folder no longer deletes songs from the library.
- Updates are only installed when they are signed with the same key as the installed app.
- Faster start (no blocking reads at launch), steadier playback queue, and the sleep timer keeps running when
  the app is closed.

## Updates

Installed v2.1.0 or later? This update appears in the app (Settings → Updates).
