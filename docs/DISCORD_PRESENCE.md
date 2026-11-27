# Discord Rich Presence

CLIBeats shows a "Listening to CLIBeats" card through Discord's **official Social SDK** for Android.
It does **not** connect to the Gateway with a user token: automating a user account (a self-bot) is
forbidden by Discord and can terminate the account.

## What this build does

- Settings → Discord has an opt-in toggle and a live preview of the card.
- `DiscordPresenceManager` maps `PlaybackState` to a `DiscordActivity` and calls
  `DiscordPresenceGateway.publish`.
- The default `UnavailableDiscordPresenceGateway` reports `SDK_REQUIRED`, so the preview works but
  nothing is published. There is no token storage, login web view or fallback transport.

## Adding the official SDK

Discord's Android SDK is a C++ prefab AAR downloaded from the Developer Portal behind a login, so it
cannot be committed to this repository.

1. Create an application in the [Discord Developer Portal](https://discord.com/developers/applications)
   and enable the Social SDK. Note its **Application ID**.
2. Download the plain C++ Social SDK (1.10 or newer) and copy
   `discord_social_sdk/lib/release/discord_partner_sdk.aar` to `app/libs/`.
3. Add NDK/CMake and the AAR to the Android build, then implement a small JNI bridge that calls
   `discordpp::Client`:
   - `Client::SetApplicationId(<id>)`
   - configure an activity with type **Listening (2)**, `details` = track title,
     `state` = artist, and the start/end timestamps from `DiscordActivity`
   - publish with the unauthenticated RPC transport (Android 1.10+); the Discord Android app must be
     installed and signed in
   - clear the activity when playback stops or the toggle is off.
4. Replace the `DiscordModule` binding with the bridge implementation. The gateway contract is:

   ```kotlin
   interface DiscordPresenceGateway {
       val sdkStatus: DiscordSdkStatus
       fun publish(activity: DiscordActivity?)
   }
   ```

5. Keep the Application ID and any SDK files out of git. The SDK is a build-time dependency, not a
   secret, but Discord's developer terms forbid embedding developer credentials in open source.

## Notes

- Cover art must be an HTTPS URL the Discord client can fetch. YouTube Music artwork works; local
  `content://` artwork needs a public URL or the SDK's asset upload path.
- Publish only on track/play-pause changes or with a debounce; playback position ticks every 500 ms
  and Discord rate-limits presence updates.
- Rich Presence without authentication is Android-only (Social SDK 1.10+); iOS requires account
  linking. Discord Activities (Path B) are a separate hosted web app and are not part of this APK.
