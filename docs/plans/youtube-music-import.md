# Plan: import playlists from YouTube Music

Status: planned, not built. Written alongside the Spotify (Exportify) import in 2.5.0.

## Why it is simpler than Spotify

A YouTube Music playlist already holds YouTube video ids, the same ids CLIBeats plays. Nothing has to be
searched or guessed, so the import is exact: 100% of available songs, no wrong matches. The Spotify
import's matcher is not needed.

## Two ways in

### 1. Paste a playlist link (public and unlisted playlists)

- Accepts `music.youtube.com/playlist?list=…`, `youtube.com/playlist?list=…`, `youtu.be/…?list=…` and a
  bare list id. Only the `list` parameter is used. Anything else gets "That's not a YouTube playlist link".
- Fetch with InnerTube `browse` on `VL<listId>`. The app already does this for Trending:
  `BrowseRequest.page(id)` plus `BrowseResponse.playlistTracks()`.
- **New work:** pages past the first 100 songs. Read the `continuationItemRenderer` token and call
  `browse` again with it until there is none. Needed for big playlists.
- Playlist name comes from the page header. Songs that are deleted or region-blocked have no video id
  and are listed as "Unavailable".
- Also accept links from Android's share menu: register an intent filter for YouTube playlist links, so
  "Share → CLIBeats" from the YouTube Music app opens the import with the link filled in.

### 2. Google Takeout (private playlists, Liked Music)

Private playlists need the user's Google login, which CLIBeats doesn't have and shouldn't ask for.
Google Takeout gives the same data as a file:

- takeout.google.com → select only "YouTube and YouTube Music" → playlists → export.
- The zip holds `playlists/<name>.csv` (or `… videos.csv`) with a `Video ID` column, plus
  `music-library-songs.csv` for liked songs.
- Reuse `ExportifyFiles`' zip handling: detect the Takeout layout by its header (`Video ID`) and read
  ids instead of titles.
- Takeout has ids but no titles. Fill in title, artist, length and cover for each id with InnerTube
  `next` (the "up next" data has full details and is light), 3 at a time like the Spotify import.
  Ids that return nothing are "Unavailable".

## UI

Same place and same screens as Spotify: Library → Playlists → [ ↓ IMPORT ] opens a chooser:

```
Import from…
  Spotify        (Exportify file)
  YouTube Music  (link or Google Takeout file)
```

The YouTube Music page:

1. "Paste a playlist link" field plus a Paste button, then Import.
2. "Private playlists or Liked Music?" with Takeout steps (same numbered style as Exportify) and
   Choose File.
3. Reuse the progress, summary and duplicate-name ("Replace / Keep both") screens unchanged. The
   "Couldn't match" list becomes "Unavailable on YouTube" (no Find button: there is nothing to match).

## Code

| Piece | Work |
|---|---|
| `YouTubePlaylistLink` | parse a link or id into a list id (unit-tested with every link shape) |
| `YouTubeMusicProvider.playlist(listId)` | browse `VL<id>` and follow continuations; returns name + tracks |
| `TakeoutFiles` | read Takeout CSV/zip into list ids and video ids (unit-tested) |
| `YouTubeMusicProvider.trackDetails(ids)` | `next` per id for title, artist, length, cover |
| `PlaylistImporter` | new source type; skips matching; same saving, progress and notification |
| Import screens | chooser, link field, Takeout instructions |

## Testing

- Unit: link parsing; continuation parsing from a saved response; Takeout CSV and zip.
- Live check, like `ImportAccuracyLiveTest`: a public 300+ song playlist must import every available
  song in order, and the count must match the playlist page.
- On a phone: paste link, Share from the YouTube Music app, a Takeout zip, and a playlist with a
  deleted video.

## Speed

Link import: about one request per 100 songs, so a 500-song playlist takes a few seconds. Takeout: one
light request per song, 3 at a time; about 30 to 40 seconds for 300 songs. That's the same pace as the
Spotify import, and only for Takeout files.
