# CLIBeats v2.8.1 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.8.1.apk` (verify with `SHA256SUMS.txt`)

## New

- **Gesture settings.** A new Gestures block in Settings (above Storage and Cache) lets you choose what swiping a
  song right or left does — add to queue, play next, add to ♥ Favorites, or nothing — and turn off swiping between
  tabs or Now Playing's swipe down (close) and up (cover ↔ lyrics). The ↺ icon at the top right of the block puts
  everything back to the defaults.
- **Playlist views.** An icon next to sort switches the songs between details, bigger details, grid and bigger
  grid with each tap; your choice is remembered for every playlist.
- **One-tap sort.** The sort button sorts straight away; each further tap moves to the next order (Title A→Z, Z→A,
  Artist A→Z, Z→A), shown in a small pop-up.

## Fixes

- **Lyrics for downloaded songs offline.** Songs downloaded before lyrics were saved with downloads now fetch theirs
  in the background soon after the app starts, so they show offline too.
- **Song lengths in Recently played.** Songs started from a Home card no longer show "–"; the length is saved as
  soon as the song plays.
- **View artist shows all their songs.** The artist page now loads the artist's full song list from YouTube Music
  (for example Ado's "ビンクスの酒 - Binks' Sake"), not just the top search results.
- **Smooth tab switching.** Tapping Library from Search no longer gets stuck on Search, and going back to a tab
  (e.g. a playlist open on Home) no longer lags: all four tabs stay loaded.
- **Readable lyrics buttons.** The 文A and abc buttons keep the song's colour but lighten it when it's too dark to
  see on the background.
- **Clearing lyrics asks first.** Settings → Storage and Cache → Lyrics → [ CLEAR ] now asks for confirmation.

---

# CLIBeats v2.8.0 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.8.0.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 6 MB

Lyrics, done properly: syllable by syllable for your own files and for YouTube Music, with translations and
pronunciations. This replaces the LRCLIB-only lyrics from v2.7.0.

## Lyrics

- **Syllable sync.** Lyrics with word timing fill in syllable by syllable as they're sung; line-synced lyrics light
  up line by line; songs with only the words show them as text. The line being sung stays in view; scroll yourself
  and it waits a few seconds before following again. Tap a line to jump there.
- **Swipe between cover and lyrics.** Swipe up on the cover (or tap the lyrics button) and the cover shrinks into a
  small row above the controls; swipe up on that row to bring it back, swipe down to close Now Playing.
- **Your own files.** Local songs read their lyrics from the file itself (FLAC, Opus/Ogg, MP3 and M4A tags) or from a
  `.lrc` file with the same name beside the song. Local songs are never searched for online.
- **YouTube Music songs.** Lyrics are looked up from several sources at once — LyricsPlus, BetterLyrics, LRCLIB,
  YouTube Music itself and lyrics.ovh — and the best is kept: syllable sync first, then line sync, then plain.
  Versions that don't match the song (a different length, or another language's version) are skipped.
- **Saved, and offline for downloads.** Found lyrics are kept on the phone; a downloaded song saves its lyrics when
  the download finishes, so they show without a connection.
- **文A Translate and abc Pronunciation.** Two buttons at the top of the lyrics: the meaning of each line in your
  phone's language, and how non-Latin lines (Japanese, Hindi, Korean, Kannada…) are pronounced, in Latin letters.
  On syllable-synced songs the pronunciation fills in along with the line, and the sung line's translation turns
  white. Both can be on together and are
  remembered; a song's translations are saved, so switching back and forth is instant.
- **A clearer typeface.** Lyrics use Poppins, big and rounded, for every language.
- **Loaded only when you open them.** Now Playing opens on the cover for a new song and looks lyrics up only when you
  tap the lyrics button or swipe up; coming back to the same song brings its lyrics back.
- **Lyrics in Settings → Storage and Cache.** Shows how many songs have saved lyrics, their size, and the most kept
  (2,000; the oldest are dropped past that, downloaded songs always keep theirs), with a Clear button.

## Fixes

- **A tidier small player row.** With lyrics open, the quality line above the controls is shortened
  (`[ONLINE] OPUS 160k · 48kHz`) so it no longer runs off the edge.
- **Download notifications.** Two songs downloading at once each keep their own notification, and progress updates
  only when the percentage changes.

---

# CLIBeats v2.7.0 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.7.0.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 5.8 MB

Synced lyrics, a sleep timer, and downloads that pick up where they stopped.

## Synced lyrics

- **Lyrics in Now Playing.** A new lyrics button swaps the cover for the song's words, scrolling to the line being
  sung and highlighting it in your accent colour. Tap any line to jump to that moment.
- Lyrics come from LRCLIB and are kept on the phone, so a song you've played once shows them again without a
  connection. A song with no lyrics shows `[ NO SYNCED LYRICS AVAILABLE ]`; one tap brings the cover back.

## Sleep timer

- **Falls asleep with you.** The bedtime icon at the bottom of Now Playing offers **15, 30, 45 or 60 minutes**, or
  **End of track**. The time left shows beside the icon, and **Off** cancels it.

## Queue

- **Your songs stay ahead of the radio.** Queuing a song while a suggestion is playing puts it after the songs you
  already queued and before the suggestions still to come, instead of at the very end of the queue.
- **The radio line survives a restart.** The queue remembers which songs are suggestions, so after closing and
  reopening the app your songs still come first and `∞ FROM RADIO` still marks where they end.

## Downloads

- **Interrupted downloads resume.** Downloads run in the background and keep the part already fetched, so a song
  stopped by a closed app or a dropped connection carries on from where it stopped instead of starting over. Two
  songs still download at once.

## Library

- **Artist and album pages.** Tapping an artist or album now opens a page of its own — "Artist · 12 tracks" — with
  ▶ PLAY, ⤨ SHUFFLE, the Find box, hold to select, and the back arrow returning you where you came from.
- **Faster scans.** Songs appear in the library as they're found, and covers and tags are read in parallel
  afterwards, so a large folder is playable while it finishes.

## Settings

- **Backup mirroring is your choice.** A new toggle in Settings → Backup stops the daily backup being copied to
  `Download/CLIBeats`; the copy kept inside the app is always made.
- **Tidier Settings.** Theme, Local Library, Storage and Cache, Audio Quality and Backup each have their own block,
  and the old Active Provider row is gone (Online or Local is still chosen in Search).
- **History stops at 3,000 plays.** Older entries are trimmed daily, so the app stays light.

## Privacy

- **HTTPS only.** The app now refuses plain HTTP connections.
- **Backups leave out private data.** The local database, downloaded audio and preferences are excluded from
  Android's cloud backup and device transfer.

## Fixes

- **A smoother scrubber.** Dragging the progress bar no longer redraws the whole screen twice a second; the cover,
  title and controls stay put.

---

# CLIBeats v2.6.1 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.6.1.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 5.7 MB

## Play a playlist in one tap

- Every playlist in **Library → Playlists** has a small **▶** button: on the cover in the grid views, and at the
  end of the row in the List and Details views. Tap it to play the whole playlist from the first song without
  opening it ("Playing from" shows the playlist's name).
- Works for your playlists, M3U/PLS file playlists, and the pinned ♥ Favorites, Most Played and Downloaded
  (Downloaded plays only finished downloads). Empty playlists don't show it.
- Tapping the cover or name still opens the playlist as before.
- Inside a playlist, **[ ▶ PLAY ]** and **[ ⤨ SHUFFLE ]** sit at the top of your playlists, M3U/PLS playlists,
  ♥ Favorites, Most Played and Downloaded, like on Home cards. They play the list in the order shown (after a
  sort too); Downloaded leaves out songs still downloading.

## Search finds playlists

- Online Search now shows a **PLAYLISTS** row of cards above the songs: YouTube Music's own and community
  playlists matching what you typed (e.g. "tamil hits", "workout", "lofi").
- Tap one to open it in the same view as Home cards: **▶ PLAY**, **SHUFFLE**, **+ SAVE** (saves it as one of
  your playlists), the Find box and hold to select. Back returns to your results.
- Playlists that are on your **Home** page come first when they match (Top 50s, charts, your languages'
  playlists), marked "On your Home", then the rest.
- Local search is unchanged (songs on the phone only).
- Search results show two buttons per song, **Play next** and **Add to playlist**; swipe right to queue.

## Home (online): your own rows

- After YouTube Music's rows, the online Home ends with rows made from what **you** play online, like the
  Local Home: **Mixes for you** (a mix for this time of day and one per top artist, with the artists you play
  alongside them), **Jump back in**, **Your top artists**, **Forgotten gems**, **On repeat** (your most played
  in the last four weeks) and **Albums to revisit**. Rows with nothing in them are left out.
- Cards open in the playlist view with ▶ PLAY / SHUFFLE / + SAVE. `[ ↻ REFRESH ]` reshuffles the mixes.
- Your rows respect every clear: after clearing Recently played, the Library's played list or Most Played (or
  taking a song off Most Played), those plays stop counting right away; after clearing everything the rows are
  gone until you play again.

## Library: Artists and Albums

- **Grid, list or details** (the same four views as Playlists, remembered), with covers: artists round, albums
  square. **Sort** by most played, most songs or A–Z.
- Each shows its **songs and how often you played them** ("12 songs · 48 plays").
- Group credits are split, so "A.R. Rahman, Ravi Basrur & Karthik" counts for each of them, and a long name no
  longer squeezes the count into a column of letters.
- **Tap** an artist or album to open its songs (most played first for an artist) with ▶ PLAY, ⤨ SHUFFLE, Find
  and hold to select; **▶** on the card plays it straight away. Works in Online and Local.
- Songs without an album name aren't listed under Albums.

## Now Playing

- **Playing from** is right for every song, not just the list you last started: songs you queue show
  "Queue", and when ⏮ goes back to a song from an earlier search or playlist it shows that one again
  ("Recently played" when it isn't known, e.g. after a restart).
- **PLAYING FROM** is softened toward white (black on Light) until it reads clearly, instead of the raw cover
  colour (a hot-pink cover made it hard to read).

## Lists keep their place

- Coming back from a playlist, artist, album or Home card lands where you were: the Playlists grid (e.g. down
  at Local playlists), the Library's Tracks, Artists and Albums, Search results and its playlist row, and each
  sideways row on Home.

## Settings

- **Storage and Cache:** Downloads is laid out like Artwork and Songs: its size and song count, a framed
  **↓ MANAGE** button, `[ CLEAR ALL ]` on the right, and a short note.
- Cache sizes: **Artwork** 128 MB (default), 256 MB, 512 MB, 1 GB; **Songs** 512 MB (default), 1, 1.5, 2, 2.5,
  3 GB. A smaller size saved before (64 MB artwork, 256 MB songs) is raised to the new smallest one. A new artwork
  limit applies right away.

---

## v2.6.0

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.6.0.apk` (verify with `SHA256SUMS.txt`)
· **Size:** about 5.6 MB

A new Home, online and local.

## Home (online)

- **Trending in your country** comes first: YouTube's daily top songs for the country your phone is in (from the
  SIM or mobile network), the same list the old Trending used. Someone in Brazil sees Brazil's, in Japan Japan's.
- **Charts & Top 50:** Spotify's **Top 50 – Global** and your country's Top 50 (25 countries), the Top 50 for each
  language you pick, and YouTube's charts (Top 100 Music Videos, Trending 20, and India's weekly chart per
  language). A Spotify Top 50 card finds its songs on YouTube Music as it opens, showing them as they're found;
  after that it opens instantly.
- **Your languages:** a **✎ Languages** chip picks the languages you listen to (33, alphabetical: Arabic, Bengali,
  … English, … Hindi, … Kannada, … Vietnamese). Home hides rows and songs in other languages and adds songs and
  playlists in yours. Pick none to see everything.
- **YouTube Music's own feed** follows: Quick picks, new releases, mood and community playlists. Song rows show four
  songs per column and page sideways; playlist, album and artist rows are cards.
- **Mood chips** (Feel good, Relax, Workout, Focus, …) filter the feed; tap again for the full page.
- **Cards open in the playlist view** (Find box, hold to select) with **▶ PLAY**, **SHUFFLE** and **+ SAVE**
  (saves it as one of your playlists). Back returns to the same spot on Home.
- Swiping a row sideways stays in the row instead of flipping to the next tab.
- `[ ↻ REFRESH ]` reloads everything, including the Spotify charts. The last page is kept for offline starts.

## Home (local)

Built from your library and play history, no internet needed:

- **Mixes for you:** a mix for the time of day (*Late night mix*: what you usually play around this hour) and a
  mix per top artist, with the artists you tend to play in the same sitting.
- **Jump back in** (albums played lately), **Recently added**, **Your top artists**, **Forgotten gems** (songs you
  loved but haven't played for a month), **Never played** and **Albums to revisit**.
- **⇄ Shuffle all** and **✦ Surprise album** (a random album from track 1).
- Picks stay the same for a day; `[ ↻ REFRESH ]` reshuffles them. A rescan updates the page by itself.
- **No music yet?** Home shows what to do next: choose your music folder (scanned right away), the scan's
  progress, or, if the folder has no music or can't be read, a prompt to pick another.

## Playlists

- **No bin icon on every row.** Remove songs by holding one to select, then **Remove** (it asks first).
- **The selection bar is one line of icons:** Play next (new), Add to queue, Download, Add to playlist,
  Favourite, Remove, and More for a single song. Hold an icon to see its name. Play next puts the songs right after
  the current one, in order.

## Fixes

- **Now Playing looks the same on every phone.** On taller screens (or smaller display size) the spare room
  piled up under the bottom icons; it now goes around the cover, so the controls sit at the same height everywhere.
- **Downloads:** with two songs downloading at once, one could overwrite the other's status and leave a finished
  song showing "Downloading". Statuses now update safely.

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
