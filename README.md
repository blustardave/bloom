# Bloom

A Jellyfin app for Fire TV, built for a smooth, Disney+-style experience with a remote.

- "Who's watching?" profiles from your Jellyfin users (password or Quick Connect sign-in)
- Home with a random film from your library, your libraries, Continue watching, Next up and new arrivals
- Details pages with seasons and episodes, favourites (your Watchlist) and watched toggles
- A player built on Media3 with the FFmpeg audio decoder, so DTS, TrueHD and friends play without converting. Anything the Fire Stick genuinely can't play is converted by your server on the fly
- Audio and subtitle switching, skip intro/recap (needs your server to detect intros), next episode
- Search with an on-screen keyboard, library grids, and a choice of accent colours in Settings

## Getting the app

Every change pushed to `main` is built automatically.

1. Open the repo on GitHub and click the **Actions** tab.
2. Click the newest run with a green tick.
3. Scroll to **Artifacts** and download **bloom-apks**. Unzip it to get `bloom-release.apk`.

## Trying it on a Mac (Android TV emulator)

1. Install [Android Studio](https://developer.android.com/studio) and open it.
2. On the welcome screen choose **More Actions → Virtual Device Manager**, then **+** (Create device).
3. Pick the **TV** category → **Television (1080p)** → Next.
4. Choose a system image with API level 30 or newer (download it if needed) → Next → Finish.
5. Press ▶ next to the new TV to start it.
6. Drag `bloom-release.apk` onto the emulator window. It installs in a few seconds, and Bloom appears in the TV's apps row.

Use the arrow keys, Enter and Esc (back) as the remote.

When it asks for your server, use your Mac's network address (for example `192.168.1.20:8096`), not `localhost`.

## Installing on the Fire Stick

One-time setup on the Fire Stick:

1. **Settings → My Fire TV → About**, then click the device name (e.g. "Fire TV Stick 4K") 7 times until it says you're a developer.
2. Go back to **Settings → My Fire TV → Developer options**: turn on **ADB debugging**, and **Install unknown apps** (or **Apps from unknown sources**).
3. **Settings → My Fire TV → About → Network** and note the **IP address**.
4. Make sure the Fire Stick and the computer running Jellyfin are on the same Wi-Fi network.

Then on the Mac, in Terminal (the `adb` tool comes with Android Studio):

```sh
~/Library/Android/sdk/platform-tools/adb connect 192.168.1.50   # your Fire Stick's IP
~/Library/Android/sdk/platform-tools/adb install -r ~/Downloads/bloom-apks/bloom-release.apk
```

The first time, the Fire Stick asks you to allow the connection; tick **Always allow** and choose OK.
Bloom then shows up under **Your Apps & Channels**. Installing a newer build the same way keeps your sign-in.

## Building locally

Open the folder in Android Studio and press Run, or run `./gradlew assembleRelease`.

Manrope font © The Manrope Project Authors, used under the SIL Open Font License (see `licenses/`).
