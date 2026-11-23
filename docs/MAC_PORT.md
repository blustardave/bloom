# Bloom for macOS — plan

Decision (owner left it to us, wants one codebase and no extra CI cost): **shared code with Compose Multiplatform**, not a separate Swift app.

Owner's direction (Nov 2026): the Mac app is **mouse and trackpad first**. The TV app's arrow-key / focus model is not carried
over as the main way to use it; the keyboard is a shortcut layer on top. Apple Silicon first (Intel is a second build of the same thing).

## Input on the Mac (mouse and trackpad first)
What changes from the TV app, screen by screen, as each one is ported:
- **Nothing is "focused" at rest.** Things light up under the pointer (hover) and act on a click. No focus ring, no zoom-on-focus
  following the remote; a tile grows a little under the pointer instead. The pointer turns into a hand over anything clickable.
- **Rows scroll sideways** with a two-finger trackpad swipe or Shift + mouse wheel, and show round ‹ › arrows at each end while the
  pointer is over the row (for mice without sideways scrolling). Pages scroll with the wheel / two fingers, smoothly, no "pivot" scrolling.
- **The top bar is always there** and clickable (Home, libraries, Watchlist, Search, profile); no "press Up to enter it".
- **Right-click / two-finger click** on a tile opens the tile menu (what "hold OK" does on the TV).
- **Back** = the ‹ button at the top left, the mouse's back button, two-finger swipe right, or Esc / ⌘[.
- **Previews on Home** start when the pointer rests on a tile (same delays as the TV), stop when it leaves.
- **Player:** controls appear when the mouse moves and hide after ~2.5 s still; click the picture to pause, double-click for full
  screen; a scrubbable timeline with a hover time tooltip; volume slider; skip intro / credits prompts are plain buttons.
  Keys: Space play/pause, ←/→ 10 s, ↑/↓ volume, F full screen, Esc leave full screen, M mute.
- **Text fields** (server address, search) take the Mac keyboard directly; no on-screen keyboard.
- **Pop-ups / dropdowns** close when clicking outside them.
- Arrow keys still move between tiles where it's cheap to support (Compose's own focus handling), but nothing depends on them.

Sizes: the TV lays out at 960 dp wide for a sofa; the Mac window is ~1100–1500 dp wide and read from arm's length, so text is a
step larger and more tiles fit per row. Each screen takes a `Layout` (TV / Mac) for those numbers rather than separate code.

## Shape
- `shared/` Kotlin Multiplatform module (targets: Android + `desktop` JVM): `commonMain` (data layer: Repo, Store interface, models,
  SurpriseOptions, TrackPick/Languages, SRT parser; theme colours; most UI: components, Home/Library/Details/Search/Settings screens,
  SubtitleLayer), `androidMain` (SharedPreferences store, ExoPlayer player, D-pad focus handling), `desktopMain` (file-based store,
  VLC player via vlcj, hover/click handling).
- `app/` stays the Android TV app, now thin: Activity + Android player + platform glue.
- `desktop/` Compose Desktop app: window, menu, keyboard shortcuts, Mac packaging (`desktop/mac/`).

## Playback on Mac
- vlcj (libVLC) — plays everything (MKV, HEVC 10-bit, DTS/TrueHD, PGS) so the server converts far less than on the Fire Stick. Needs VLC.app installed (or libVLC bundled later). Device profile for desktop says "direct play almost everything".
- Text subtitles drawn by our own `SubtitleLayer` (already shared); picture subs by VLC.

## Build / CI (no macOS runner — those cost 10× minutes)
- Built on the existing Linux runner: `./gradlew :desktop:appJar -PdesktopTarget=macos-arm64` makes one jar with the macOS
  graphics library; `desktop/mac/make-app.sh` wraps it with a Temurin 21 JRE for macOS aarch64 (downloaded, cached monthly) and a
  shell launcher into `Bloom.app`, zipped as `Bloom-mac.zip` and uploaded to the `latest` release next to the APK.
- Unsigned: first launch needs System Settings → Privacy & Security → "Open Anyway" (or, in Terminal,
  `xattr -dr com.apple.quarantine /Applications/Bloom.app`).
- Screenshots: `./gradlew :desktop:shots` renders the desktop screens at 2x with a simulated mouse (hover and clicks) to
  `desktop/build/shots`; CI copies them into the `shots` branch as `desktop-*.png`.
- Later: trim the runtime with jlink (cross-linking needs a matching macOS JDK's jmods), ad-hoc signing (rcodesign runs on Linux).

## Phases (each keeps the Android build green)
1. **Done (build 237):** Gradle: KMP `shared` module (AGP 9's `com.android.kotlin.multiplatform.library` + `jvm("desktop")`),
   Compose Multiplatform 1.12.2 (= Jetpack Compose 1.12.1, same as the TV app). Moved to `commonMain`: SurpriseOptions,
   Languages/TrackPick, SRT parser + SubtitleStyle/SubtitleOptions/SubtitleLayer, theme colours. Desktop window (welcome screen
   with a live subtitle-look preview driven by mouse) builds into `Bloom.app` on CI.
2. Data layer: Store behind an interface (Android prefs / desktop JSON file); Repo into commonMain (jellyfin-sdk is multiplatform: JVM artifact). Desktop: sign in, list libraries — with the mouse-first top bar.
3. UI: components, Home, Library, Details, Search, Settings into commonMain; replace `LocalContext`, `nativeKeyEvent`, Android Coil bits with expect/actual. Focus/D-pad behaviour becomes an Android-side layer; desktop gets hover/click, sideways row scrolling, right-click menus.
4. Player on desktop: vlcj surface in Compose, mouse-driven controls, prompts (skip intro/credits), subtitles, reporting to the server.
5. Polish: menu bar, full screen, keyboard shortcuts, window size memory, Intel build, smaller runtime.

Biggest risks: splitting the two 1,900-line screens (PlayerScreen, HomeScreen) — PlayerScreen will be rewritten per platform around a shared controls/prompts layer rather than moved whole. HomeScreen's focus-driven behaviour (tile opening, previews, pivot scrolling) needs a pointer-driven counterpart, not just a move.
