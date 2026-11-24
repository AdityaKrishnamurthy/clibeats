# CLIBeats v2.5.2 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.5.2.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 5.7 MB

## Open links in CLIBeats

- **YouTube Music and Spotify links** open in CLIBeats: Settings → Apps → CLIBeats → **Open by default →
  Add link**, then tick the sites. Or **Share** a link to CLIBeats from any app.
- Songs play right away (a Spotify song is found on YouTube Music like an imported one). YouTube Music playlists
  and albums play as your queue. Spotify playlists and albums are imported (up to 100 songs from a link).

## Import

- **Find** now lets you listen first: tap a result to play it, tap again to pause, and `[ + ]` adds it.

## Backups

- The daily library backup is now saved to **Download/CLIBeats/Playlists**.
- Settings → Playlist Exchange: a **trash button** deletes the saved backups (also the older ones in
  Download/CLIBeats that this install made).

# v2.5.1

## Playlist import fixes

- Songs no longer show as "couldn't match" when YouTube Music briefly refused searches during a long import;
  those searches are tried again before giving up.
- Songs Spotify files under a compilation named after a language ("… Love Songs", "… Hits") are found again.
- Titles with a dash before a word ("Song -Reprised Version") are found again.
- Artist names spelt differently on Spotify and YouTube Music are recognised more often.
- Film songs in a named language take only the official audio track: a lyric video or a fan upload in another
  language is never used. Such songs are listed under "couldn't match", with **Find** and **Copy**.
- Files without song lengths (TuneMyMusic) no longer pick a cover video; another song from the same album is
  no longer taken by mistake.

# What's new in v2.5.0

## New: import your playlists

- **Library → Playlists → `[ ↓ IMPORT ]`** brings playlists in from **Spotify** (Exportify file),
  **YouTube Music** and **other services** like Apple Music, Amazon Music, Deezer or Tidal (TuneMyMusic file).
  Step-by-step instructions for each are on the import screen.
- A `.zip` or a file with many playlists shows a checklist; pick the ones you want.
- Each song is found on YouTube Music by title, artist, album, length, language and its official ISRC code. It
  never guesses: songs it can't be sure about are listed at the end, with **Find** to pick them yourself and
  **Copy** to copy the list as text. Songs no longer playable on Spotify are found too.
- The right language version of film songs (no dubs), covers from the song's album, live progress, a
  notification while it runs, and "Replace / Keep both" when a playlist already exists. Optional
  **Use the closest version** for songs YouTube Music only has in another version.

## Navigation and screens

- **Four tabs:** Home, Search, Library, Settings. Playlists moved into Library (Playlists · Online · Local);
  tapping Library again steps back out of a playlist, then through the sections.
- **No top bar**, so screens start right under the status bar (fixes the bar showing on some phones).
- **Mini player:** a queue button with the number of songs up next; tap it again to go back where you were.
- **Home** has the same views as playlists (List, Details, grids); Details is the default.
- **Now Playing:** cleaner header (the cover sits higher), action icons spaced from the waveform, and dark covers
  use your theme colour instead of an unrelated purple.
- **Holding a song** opens a menu: Play next, Add to queue, Add to playlist, Add to favorites. Swipes show a short
  confirmation.

## Queue

- Tapping a song no longer wipes the queue (this caused "0 songs next" while shuffling); added songs land among
  the upcoming ones when shuffle is on.
- The playing song is 1 and up next counts from 2; swipe right removes a song with **Undo**; drag the ≡ handle or
  hold a cover to reorder.

## Playlists

- **Rename** a playlist from the pencil (it edits the name and the cover).
- **Sort** songs by title or artist, A–Z or Z–A.

## Notification and lock screen

- **Repeat** and **♥ Favorite** buttons next to the playback controls, and the CLIBeats logo as the small icon.
- The sharp, high-resolution cover shows reliably.

## Local music

- **Rescan** re-reads every file's tags and cover, about 4× faster; a file that can't be read keeps its details.
- Covers and names of local songs no longer disappear after queue changes or favouriting.
- Removing a music folder asks first.

## Under the hood

- **YouTube Music only** for online music (Audius, Jamendo and Internet Archive were removed).
- Search results show every artist of a song and its real album.
- App updates download much faster: a 3× smaller app, the download starts at once and is checked in one pass.
- Heading capitalisation fixed across the app.
