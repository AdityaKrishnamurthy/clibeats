# CLIBeats v2.5.6 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.5.6.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 5.8 MB

## Playlists

- **Search inside a playlist.** A **Find** box at the top of every playlist (Favorites, Most Played,
  Downloaded and yours) filters by title, artist or album; case and accents don't matter, and several words
  narrow it down. Tapping a found song still plays the whole playlist from that song.
- **Select several songs.** Hold a song to start selecting, then tap more. A bar at the top shows how many are
  selected, with **ALL** / **NONE**, and:
  - **↓ DOWNLOAD** — downloads all of them (see below),
  - **+ PLAYLIST** — adds them to one of your playlists or a new one,
  - **≡ QUEUE** — adds them to the queue, in order,
  - **♥** — adds them to Favorites,
  - **✕ REMOVE** — removes them from the playlist (asks first),
  - **⋯** — the usual song menu, when one song is selected.

  **Back** or **✕** ends the selection. While selecting, swiping sideways no longer flips to the next tab.

## Downloads

- **Download many songs at once.** Selected songs go into **Downloaded** straight away, greyed out with
  `QUEUED` or their progress, and each gets full colour as it finishes. Two download at a time, so even a whole
  playlist downloads smoothly; the banner shows how many are still to come. Removing a queued song cancels it.

## Now playing

- **Playing from …** The player bar and Now Playing show where the music comes from: the playlist's name,
  Search, Home, Library, a link, or `∞ Radio` when autoplay's suggestions are playing.
- **The playing song is highlighted** in every list (playlists, search results, Home, Library), in your
  accent colour with a `▶`, so you can see it at a glance.
- **Sharp cover on the lock screen and media card.** When the high-res cover arrived a moment after the song
  started, the notification card kept the blurry small one; it now switches to the sharp cover.

# CLIBeats v2.5.5 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.5.5.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 5.6 MB

## Downloads

- **Much faster downloads.** Songs now download in small pieces, several at once, instead of one slow stream.
  A song you've already played to the end is copied from the song cache almost instantly.
- **Downloaded songs play offline.** Before, a downloaded song only played if it was also in the song cache.
  Now Playing shows `[DOWNLOADED · YouTube Music]` when a song plays from its download.
- **Removing downloads works properly.** Removing a song, or **Clear all**, empties the Downloaded playlist at
  once, stops downloads still running, and a queued song that loses its download streams instead. Leftover
  files from interrupted downloads are cleaned up.
- **Downloaded shows only what's really on your phone,** and its size in Settings is measured from the files.
- **Downloading a song doesn't change it anywhere else:** playlists, Favorites, backups and playlist export
  keep it exactly as before.

## Settings

- **Storage and Cache:** a total at the top, and every size in **MB** or **GB** (the limits read 256 MB, 512 MB,
  1 GB, 2 GB). Downloads show their real size and song count, with Manage and Clear all.
- **Data saver works again:** songs stream at the lower quality when Settings → Audio Quality is set to save
  data.

## Autoplay and queue

- **Radio that stays on track.** Autoplay now keeps to the radio of the song you started with, page after
  page, like the YouTube Music app, instead of starting over from each new song (which let it wander into
  other languages). Songs in the same language as your song come first; songs in other languages only play
  when there are none left. A song's language comes from its script (Kannada, Japanese, ...) or a language
  in its title, and for titles in English letters, from YouTube Music's own suggestions.
- **Queue:** a `∞ FROM RADIO` line shows where your songs end and the suggestions begin, and `[ ↻ RADIO ]`
  starts a fresh radio from the song playing now.
- **A new song starts its own radio.** Play a song from Search, Home or Library and the suggestions follow
  that song (a Kannada song after a Japanese radio gets Kannada suggestions).
- **Songs you add play next.** While a suggestion plays, **Add to queue** puts your song right after it,
  ahead of the other suggestions.
- **Offline at the end of a playlist:** autoplay waits for the connection, adds suggestions and carries on
  playing, even if the last song already finished.

## Smaller fixes

- Sorting a playlist or shuffling the queue goes back to the top of the list.
- The search key closes the keyboard (Search and the import Find box).
- Every clear or bulk delete asks first, in the terminal style (`> rm -r cache/songs`).
- The notification and lock-screen card redraw once per tap (play, pause, seek), with the cover kept.
- Fewer stutters on weak connections after the music pauses to load.

# CLIBeats v2.5.4 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.5.4.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 5.8 MB

## Offline Downloads & Library

- **Pinned Downloaded Playlist**: Library screen now features a pinned `Downloaded` playlist (`↓`) to view, manage, and play all offline tracks.
- **Settings Storage Management**: Settings → Storage shows total downloaded audio footprint with options to clear all downloads or remove individual tracks.
- **Local Track Filtering**: "Download for offline" and "Remove download" are automatically hidden when holding local device tracks.
- **Reliable Removal**: Removing downloads immediately purges physical files and clears database records.
- **Queue Autoplay Setting Cleanup**: Removed redundant auto-queue toggle from Settings; infinite autoplay radio remains accessible directly from the Queue header.

# CLIBeats v2.5.3 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.5.3.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 5.8 MB

## Infinite Radio & Autoplay

- **Continuous Radio**: When your queue approaches the end (at the second-to-last track), CLIBeats automatically fetches related tracks from YouTube Music and appends them to your queue.
- **Queue Toggle**: `[ ∞ AUTOPLAY: ON / OFF ]` header button in Queue screen lets you turn infinite radio on or off anytime.
- **Deduplication**: Tracks already played in the current listening session are never repeated.

## Durable Offline Downloads

- **Offline-First Playback**: Downloaded songs are stored safely in app storage and prioritized during playback with instant `file://` local playback.
- **Song Actions**: Hold any song to open the action sheet and select "Download for offline" or "Remove download".
- **Visual Badging**: Downloaded songs display a `[↓]` badge indicator in table rows.

## Instant Playback & Performance

- **Warm HTTP/2 Connection Pooling**: ExoPlayer Media3 connects over shared OkHttp connection pools, cutting stream setup latency.
- **Lazy Audio Cache**: Audio cache initialization is deferred off the main thread for faster app launch.
- **Pre-Tap Stream Warming**: Quick picks on Home and search results prefetch audio streams to enable sub-second playback.

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
