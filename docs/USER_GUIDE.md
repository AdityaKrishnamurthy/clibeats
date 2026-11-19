# CLIBeats — User Guide

Welcome to CLIBeats, the terminal-styled music player for Android.

## First run

New installs open a short setup: pick a theme, optionally choose your music folder, and choose the default
source (Online or Local). Everything can be changed later in Settings.

## Tabs

Swipe left / right anywhere outside a song row to move between tabs.

| Tab | What it does |
|---|---|
| **Home** | Source switch (`ONLINE ▾` / `LOCAL`), Recently Played (last 5, with `[ CLEAR ]`), trending songs. Tap the tab again to switch Online ↔ Local. |
| **Search** | Search the active source. With the box empty it lists songs you picked from earlier searches (`[ CLEAR ]`). Tap the tab again to jump into the search box. |
| **Library** | `[ ONLINE ]` = songs you've played, `[ OFFLINE ]` = music on your device (with the track count). Tap the tab again to switch. Tracks / Artists / Albums. |
| **Queue** | Hold a song (or drag its ≡ handle) and move it to reorder; remove, clear upcoming songs (the playing song stays); tap a song to jump to it. |
| **Playlists** | ♥ Favorites, Most Played, your playlists, a **+** tile and local M3U playlists. Tap the tab again to go back to the grid. |
| **Settings** | Theme & accent, music folders, playback, provider, storage, playlist exchange, about. |

## Song gestures

- **Tap** — play (with "Auto-queue related songs" on in Settings → Playback, the rest of the list is queued after it; off by default).
- **Swipe right** — add to queue.
- **Swipe left** — add to ♥ Favorites.
- **Long-press** — add to a playlist (or create a new one).

## Player

- **Mini player** — tap the song to open Now Playing.
- **Now Playing** — colours follow the album art. Drag across the waveform behind the controls to scrub: playback
  keeps going, and dragging back near the current time snaps onto it. Repeat (off → all → one), previous, play/pause,
  next, shuffle, sleep timer,
  queue, add to playlist, ♥. Drag the artwork down to close.
- **Previous** — always plays the song that played before, local or online, even from an earlier queue
  (also from the notification and lock screen).
- **Sleep timer** — 15 / 30 / 45 / 60 minutes or end of the current song (works with repeat-one too).
- If a song can't be played, CLIBeats skips to the next one and shows a short notice above the player.

## Local music

Settings → Local music → add one or more folders. Titles, artists, albums and covers come from the file tags.
Use **Rescan** after adding files. Playlist files (`.m3u`, `.m3u8`, `.pls`) in your folders appear as
read-only `[LOCAL]` playlists; use **Save as app playlist** to get an editable copy.

## Storage

Settings → Storage has two caches, each with a size limit and `[ CLEAR ]`:

- **Songs** — streamed songs replay instantly without using data (local files are never copied).
- **Artwork** — cover images. Changing the limit applies immediately.

## Controls outside the app

Playback continues in the background with notification and lock-screen controls. After the app is closed,
the media card in Quick Settings resumes the last song from where you left off.

## Updates

When a new version is released, a pop-up offers **DOWNLOAD** or **LATER** (hides it for a day). The APK is
saved to your Downloads folder and checked against the release's checksum; then tap **INSTALL** and confirm
Android's update screen. The first time, Android asks you to allow installs from CLIBeats.

Settings → Updates sets how often the app checks (**Daily**, **Weekly** or **Off**) and has **CHECK NOW** to
check, download and install by hand. The downloaded APK is deleted after the update is installed.

## Playlist exchange

Settings → Playlist exchange exports your playlists and Favorites to a `clibeats.json` file anywhere you
choose, and imports them back (for example on a new phone or after reinstalling).
