# CLIBeats — User Guide

Welcome to CLIBeats, the terminal-styled music player for Android.

## First run

New installs open a short setup: pick a theme, optionally choose your music folder, and choose the default
source (Online or Local). Everything can be changed later in Settings. Reinstalling? The music step offers
**RESTORE FROM A BACKUP FILE**: pick `clibeats-backup.json` from `Download/CLIBeats`.

## Tabs

Swipe left / right anywhere outside a song row to move between tabs.

| Tab | What it does |
|---|---|
| **Home** | Source switch (`ONLINE ▾` / `LOCAL`), Recently Played (last 5, with `[ CLEAR ]`), and trending songs for your country (from your SIM / mobile network). Trending loads once each time you open the app; `[ ↻ REFRESH ]` reloads it. Tap the tab again to switch Online ↔ Local. |
| **Search** | Search the active source. With the box empty it lists songs you played, queued, favourited or added to a playlist from earlier searches (`[ CLEAR ]`). Tap the tab again to switch Online ↔ Local; double-tap it to jump into the search box. |
| **Library** | `[ ONLINE ]` = songs you've played, `[ OFFLINE ]` = music on your device (with the track count). Tap the tab again to switch. Tracks / Artists / Albums. |
| **Queue** | The playing song, then Up Next: drag a song's ≡ handle, or hold its cover and drag, to reorder; remove, shuffle or clear upcoming songs; tap one to jump to it. Shuffle mixes the list you see (the playing song moves to the top); turning it off puts the original order back. Also opens from `[ QUEUE ]` in Now Playing. |
| **Playlists** | ♥ Favorites and Most Played pinned at the top (✕ removes a song, `[ CLEAR ALL ]` asks first), then your playlists (`[ + NEW ]`), then local M3U playlists. The buttons at the top switch between small grid, large grid, list and details. Inside Favorites or your own playlists, drag a song's ≡ handle (or hold its cover) to reorder. Tap the tab again to go back to the overview. |
| **Settings** | Theme & accent (nine colours plus **Custom**: hue, saturation and brightness sliders or a hex code), music folders, playback, provider, storage, playlist exchange, updates, about. |

## Song gestures

- **Tap** — play (with "Auto-queue related songs" on in Settings → Playback, the rest of the list is queued after it; off by default).
- **Swipe right** — add to queue.
- **Swipe left** — add to ♥ Favorites.
- **Long-press** — add to a playlist (or create a new one).

## Player

- **Mini player** — tap the song to open Now Playing. Touch or drag the seek bar: a bubble shows the exact time and
  it lands on that second when you let go.
- **Now Playing** — a frosted-glass background made from the album art, with matching control colours. Drag across
  the waveform behind the controls to scrub: playback keeps going, and dragging back near the current time
  snaps onto it. Repeat (off → all → one), previous, play/pause,
  next, shuffle, sleep timer,
  queue, add to playlist, ♥. Drag the artwork down to close.
- **Audio output** — under the quality line, Now Playing shows where the music is playing (phone speaker, wired
  headphones or your Bluetooth device by name). Tap it to switch output in Android's own picker.
- **Previous** — more than 3 seconds into a song it restarts it; press again to play the song that played
  before, local or online, even from an earlier queue.
- **Next** — after going back, next returns to the song you went back from (and the ones before it, in order);
  otherwise it plays the next song in the queue. Previous and next work the same from the notification, lock
  screen and headphones.
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
saved to your Downloads folder (a progress bar under the top bar shows how far along it is; `[×]` hides it) and
checked against the release's checksum; then tap **INSTALL** and confirm
Android's update screen. The first time, Android asks you to allow installs from CLIBeats.

Settings → Updates sets how often the app checks (**Daily**, **Weekly** or **Off**) and has **CHECK NOW** to
check, download and install by hand. The downloaded APK is deleted after the update is installed.

## Playlist exchange

Settings → Playlist exchange exports your playlists and Favorites to a `clibeats.json` file anywhere you
choose, and imports them back (for example on a new phone or after reinstalling).

## Backup

CLIBeats saves a backup of your library once a day: playlists, Favorites, saved albums and artists, and
settings. On Android 10+ a copy goes to `Download/CLIBeats/clibeats-backup.json`, which stays after an
uninstall. Settings → **SHARE BACKUP** sends it to Drive, email or another phone. Android's own backup
(Google account) also brings the library back when you set up a new phone.
