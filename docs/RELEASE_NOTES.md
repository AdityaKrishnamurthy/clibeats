# CLIBeats v2.4.0 — Release Notes

**Build target:** Android 8.0+ (API 26+) · **Download:** `CLIBeats-v2.4.0.apk` (verify with `SHA256SUMS.txt`)

## Playlists

- **New views:** small grid, large grid, list and details. Switch with the buttons at the top; your choice is
  remembered.
- **Clear sections:** ♥ Favorites and Most Played are pinned at the top, then your playlists (with `[ + NEW ]`),
  then local M3U/PLS playlists in their own section.
- **Reordering rebuilt** for the queue, ♥ Favorites and your own playlists: drag the ≡ handle, or hold a song's cover
  and drag. It scrolls at the edges, the other songs move out of the way smoothly, and the new order is saved when you
  let go.

## Playback

- **Next goes back where you came from:** after pressing Previous, Next returns to the song you went back from (and
  the ones before it, in order), at any point in the song. The same in the app, notification, lock screen and
  headphones.
- **Shuffle really shuffles the queue:** the playing song moves to the top and the rest is mixed, so the Queue
  screen, Next and Previous all match what you see. Turning it off restores the original order.
- **Precise seeking in the mini player:** touch or drag the bar; a bubble shows the exact time and it lands on that
  second.

## Now Playing

- **Audio output:** see where the music is playing — phone speaker, wired headphones or your Bluetooth device by name
  — updated as you connect, disconnect or switch. Tap it to open Android's output switcher.
- The classic waveform is back (the live loudness waveform from v2.3.0 has been removed).

## Home & Search

- **Trending for your country:** Home shows YouTube Music's own chart for the country your SIM / mobile network is in,
  e.g. *Daily Top Music Videos – India*.
- Trending loads once each time you open the app and no longer reloads when you switch tabs; `[ ↻ REFRESH ]` next to
  the title reloads it. If the connection isn't ready when the app opens, it waits and retries instead of showing
  an error.
- **Recent searches** now include songs you queued, favourited or added to a playlist from search, not only ones you
  played.
- The bottom bar has its six tabs again, and the mini player no longer has a queue button.

## Settings

- **New accent colours:** Green, Lime, Amber, Orange, Magenta, Lavender, Cyan, Teal and White, plus a **custom colour
  picker** (hue, saturation and brightness sliders, or type a hex code). If you used a colour that was removed, you
  keep it as your custom colour.
- Every box spans the full width with an accent outline.
- **Update downloads** show a progress bar under the top bar (`[VIEW]` / `[×]`), so you can keep using the app while
  it downloads. Update checks stay once a day (or weekly / off).

## Under the hood

- Uses less battery: Wi-Fi is only kept awake while streaming, not while playing your own files.
- YouTube streams play the best audio available without an account (Opus, about 160 kbps).
- README: new screenshots, a star history chart and a daily-updated downloads chart.

Thanks to **Omprakash Panda** for the audio output switcher and update progress bar ideas (#8).
