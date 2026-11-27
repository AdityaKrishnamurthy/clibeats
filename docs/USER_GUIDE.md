# CLIBeats — User Guide

Welcome to CLIBeats, the terminal-styled music player for Android.

## First run

New installs open a short setup: pick a theme, optionally choose your music folder, and choose the default
source (Online or Local). Everything can be changed later in Settings. Reinstalling? The music step offers
**RESTORE FROM A BACKUP FILE**: pick the `clibeats-backup-DDMMYYYY.json` file from `Download/CLIBeats`.

## Tabs

Swipe left / right anywhere outside a song row to move between tabs.

| Tab | What it does |
|---|---|
| **Home** | Source switch (`ONLINE ▾` / `LOCAL`), Recently Played (last 5, with `[ CLEAR ]`), and trending songs for your country (from your SIM / mobile network). Trending loads once each time you open the app; `[ ↻ REFRESH ]` reloads it. Tap the tab again to switch Online ↔ Local. |
| **Search** | Search the active source. With the box empty it lists songs you played, queued, favourited or added to a playlist from earlier searches (`[ CLEAR ]`). Tap the tab again to switch Online ↔ Local; double-tap it to jump into the search box. |
| **Library** | Three sections: **Playlists** (♥ Favorites and Most Played pinned, then your playlists with `[ ↓ IMPORT ]` and `[ + NEW ]`, then local M3U playlists; small grid, large grid, list or details), **Online** (songs you've played) and **Local** (music on your device: Tracks / Artists / Albums). Tap the tab again to leave an open playlist, then to move through the sections. |
| **Settings** | A list of sections, each opening its own page: the theme (colour scheme, themes, accent and **Custom** colours), Listening (high quality streaming, gestures, local music), Your data (storage & cache, downloads, backup), Updates, and About (with a copy button for the GitHub link). Back or tapping the tab again returns to the list. |

**Queue:** tap the queue button in the mini player (it shows how many songs are up next); tap it again to go back.
The playing song is 1 and Up Next starts at 2. Swipe a song right to remove it (**Undo** brings it back); drag ≡ or
hold a cover to reorder.

## Importing playlists

Library → Playlists → `[ ↓ IMPORT ]`, then choose where your playlists are:

- **Spotify:** on [exportify.net](https://exportify.net), log in and tap **Export** (one playlist, `.csv`) or
  **Export All** (`.zip`).
- **YouTube Music** or **Others** (Apple Music, Amazon Music, Deezer, Tidal…): on
  [tunemymusic.com](https://www.tunemymusic.com), choose your service as the source, select playlists, choose
  **Export to file** and pick CSV.

Tap **Choose File**, tick the playlists and tap **Import**. Songs that couldn't be matched are listed at the end:
**Find** searches YouTube Music for one (tap a result to play it, `[ + ]` to add it), **Copy** copies the list as
text.

## Opening links

CLIBeats can open YouTube Music and Spotify links. To make it the default: Settings → Apps → CLIBeats →
**Open by default → Add link**, and tick the sites (if the Spotify or YouTube Music app is installed, turn its own
"Open supported links" off first). You can also **Share** a link to CLIBeats.

- A song plays right away. A Spotify song is found on YouTube Music the same way as an import.
- A YouTube Music playlist or album plays as your queue.
- A Spotify playlist or album is imported into your playlists (up to 100 songs; use Exportify for bigger ones).

## Editing playlists

Inside one of your playlists, the pencil renames it or changes its cover, and the sort button orders songs by
title or artist (A–Z or Z–A).

## Song gestures

- **Tap** — play (with "Auto-queue related songs" on in Settings → Playback, the rest of the list is queued after it; off by default).
- **Swipe right** — add to queue.
- **Swipe left** — add to ♥ Favorites.
- **Hold** — menu: Play next, Add to queue, Add to playlist, Add to favorites.
- What each swipe does (queue, play next, ♥, or nothing), tab swiping and Now Playing's swipes can be changed in
  **Settings → Gestures**; the ↺ icon at its top right restores the defaults.

## Player

- **Mini player** — tap the song to open Now Playing. Touch or drag the seek bar: a bubble shows the exact time and
  it lands on that second when you let go.
- **Now Playing** — a frosted-glass background made from the album art, with matching control colours. Drag across
  the waveform behind the controls to scrub: playback keeps going, and dragging back near the current time
  snaps onto it. Repeat (off → all → one), previous, play/pause,
  next, shuffle, sleep timer,
  queue, add to playlist, ♥. Drag the artwork down to close.
- **Lyrics** — tap the lyrics button at the bottom of Now Playing, or swipe up on the cover. The cover shrinks
  into a small row and the words take its place. Syllable-synced lyrics fill in syllable by syllable; line-synced
  ones light up line by line; otherwise the plain text is shown. Tap a line to jump there; scroll yourself and it
  waits a few seconds before following again. Swipe up on the small row to bring the cover back, swipe down to
  close Now Playing.
  - **Local songs** read their own lyrics: the file's LYRICS tag (FLAC, Opus/Ogg, MP3, M4A) or a `.lrc` file with
    the same name next to the song. They are never searched for online.
  - **YouTube Music songs** get theirs from several sources at once (LyricsPlus, BetterLyrics, LRCLIB, YouTube
    Music, lyrics.ovh): syllable sync first, then line sync, then plain. Once found they're saved; downloaded
    songs save theirs when the download finishes, so they show offline.
  - **文A Translate** shows each line's meaning in your phone's language; **abc Pronunciation** (for Japanese,
    Hindi, Korean and other non-Latin lyrics) shows it in Latin letters, filling in time with the singing on
    syllable-synced songs. Both can be on together, and are remembered.
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

Settings → **Storage & cache** has two caches, each with a size limit, a usage bar and **Clear**:

- **Songs** — streamed songs replay instantly without using data (local files are never copied). 500 MB, 1, 2, 3
  or 5 GB; 1 GB by default.
- **Cover art** — cover images. 500 MB, 1, 2 or 3 GB; 500 MB by default.
- When a cache is full, what you haven't played or seen for the longest is removed automatically. Changing a limit
  applies immediately.
- **Lyrics** — saved lyrics for up to 5,000 songs (the oldest are dropped past that; downloaded songs keep theirs).

Settings → **Downloads** lists every downloaded song with remove one / delete all. Downloads have no size limit
and are never removed by the caches: each keeps its own cover and lyrics for offline.

## Controls outside the app

Playback continues in the background with notification and lock-screen controls. After the app is closed,
the media card in Quick Settings resumes the last song from where you left off.

## Updates

When a new version is released, a pop-up offers **DOWNLOAD** or **LATER** (hides it for a day). The APK is
saved to your Downloads folder (a progress bar above the player shows how far along it is; `[×]` hides it) and
checked against the release's checksum; then tap **INSTALL** and confirm
Android's update screen. The first time, Android asks you to allow installs from CLIBeats.

Settings → Updates sets how often the app checks (**Daily**, **Weekly** or **Off**) and has **CHECK NOW** to
check, download and install by hand. The downloaded APK is deleted after the update is installed.

## Backup

Settings → **Backup** keeps a full backup of the app, saved automatically once a day (each day's replaces the
last, so there's only ever one): your playlists, ♥ Favorites, saved albums and artists, play history (Most played,
Recently played, Library → Online), which songs are downloaded, and all your settings (theme, gestures, languages, sorts…). The music
files, caches and lyrics aren't included, nor are plays you've cleared from every list (Home's Recently played,
Library → Online, Most played). It shows when it last saved; **[ BACK UP NOW ]** saves one straight away.

- **Keep a copy in Downloads** (on by default) also saves it to `Download/CLIBeats/clibeats-backup-DDMMYYYY.json` (today's date, e.g. `clibeats-backup-27112026.json`),
  which stays after an uninstall; setup offers to restore it.
- **Save a copy…** puts the backup file anywhere (Downloads, Drive, a USB stick); **Share** sends it to another
  phone or app; **Restore…** brings a backup file back. After a restore, the songs that were downloaded are offered
  for download again.
- Android's own backup (Google account) also brings the library back when you set up a new phone.
