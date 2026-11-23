# Bloom: handoff notes

Read this first in a new session. It's the source of truth for where the project stands. The top half is the
current state; everything under **Change history** is a build-by-build log (newest first) explaining why things
are the way they are.

## What this is

**Bloom** (called Blush until build 45; the name may change again before any public release) is a Jellyfin
client for Amazon Fire TV (Fire OS 8, Android 11), styled after the Disney+ app. Native Kotlin + Jetpack Compose
(plain Compose foundation, no Material, no tv-material), Jellyfin SDK 1.8.12, Media3/ExoPlayer, Coil 3.

Repo: `blustardave/bloom`. Package/applicationId is still **`app.blush.tv`** on purpose (changing it would make
the Fire Stick treat Bloom as a different app: sign-in and settings lost). Kotlin names (`BlushType`,
`Blush.colors`, `Theme.Blush`, the "Blush" accent colour) are also left as they are.

## Working with the owner

- Not a developer. Explain plainly, no jargon; they test by installing builds, never by reading code.
- They send numbered lists of feedback after testing on the Fire Stick. Answer point by point with the same
  numbers, say plainly what was changed and why, and flag anything that's a guess.
- Work in batches: change → push → wait for the GitHub Actions build → fix compile errors → reply with the version
  number and the install commands below. Keep this file updated with each build.
- **This repo is public.** Never write personal details into it (names, addresses or IPs, server details, what's in the
  owner's library or what they watch). Keep those in the conversation only.
- Things they've asked for as standing preferences: smooth, quiet, Disney+-like UI; small text, unobtrusive
  buttons; no pop-up labels; consistent alignment between screens; anything jittery or flashing is a bug.

## Building, versions and installing

- The cloud session can't compile Android code locally (no Google Maven access). **GitHub Actions builds every push
  to `main`** (`.github/workflows/build.yaml`): read the job log for `e: ` lines. Useful:
  `gh api repos/blustardave/bloom/actions/runs --jq '.workflow_runs[0]'`, then
  `gh api repos/blustardave/bloom/actions/jobs/<id>/logs | grep -E '^.{29}e: '`. The log also prints
  `aapt2 dump badging` (package, version, icon/banner) in the "Show what the APK declares" step.
- APK: `bloom-release.apk` on the `latest` release (the old `bloom-apks` artifact is gone). Signed with `app/blush.keystore` (password
  `blushblush`), so every build installs over the last.
- **Versions:** versionCode = **build number = Actions run number + 236** (the repo was recreated on 23 Nov 2026 and its
  runs restarted at 1; "run N" earlier in this file means build N). versionName = `0.3.<build − 83>`, set in
  `app/build.gradle.kts` (`series` / `seriesStart`). Settings shows "Bloom 0.3.N" next to its heading: when a fix
  "didn't work", first confirm which version is installed.
- **Fire Stick**, from Terminal on the owner's Mac, APK saved straight into Downloads (the owner knows the Fire Stick's
  address; it isn't kept in this public repo):
  ```
  ~/Library/Android/sdk/platform-tools/adb connect <fire-stick-ip>
  ~/Library/Android/sdk/platform-tools/adb install -r ~/Downloads/bloom-release.apk
  ~/Library/Android/sdk/platform-tools/adb shell cmd package compile -m speed -f app.blush.tv
  ```
  The last line compiles the app ahead of time (much smoother scrolling); it's undone by every install, so always
  include it. "No route to host" although ping works = macOS Local Network privacy: `adb kill-server`, then connect
  again ("Always allow" on the TV). The home-screen tile can be cached: unplugging the Fire Stick for 10 s refreshes it.
- **Mac app** (from build 237): `Bloom-mac.zip` on the `latest` release (https://github.com/blustardave/bloom/releases/tag/latest),
  built on the Linux runner (see `docs/MAC_PORT.md`). Unzip, drag Bloom to Applications, first open via Privacy & Security → Open Anyway.
- **Home-screen tile:** settled as a square icon in a grey 16:9 box. Fire OS ignores `android:banner` for
  sideloaded apps; using a wide image as the icon hid the app from the home screen entirely (build 37).
- The owner's Jellyfin server runs on another computer on the home network (port 8096).
  The Android TV emulator on the Mac was used before the Fire Stick arrived; it's a poor test of performance/audio.
- Diagnostics in the app: the player's ⋮ → **Playback info** (how it's playing, codecs, decoders, dropped frames,
  speed); crashes are saved and shown on next launch ("Bloom crashed last time"). Ask for photos of either.

## Code map (`app/src/main/java/app/blush/tv/`)

- `data/Repo.kt`: every server call. Browsing (`browse`/`browsePage`), search (`searchAll`, credit index
  `credits()`), images, favourites, watched state, media segments, `resume()` (one per series), `moreLikeThis()`
  (scored recommendations), `previewSource()`.
- `data/Store.kt`: SharedPreferences (file "blush"): server, saved users, accent, previews mode, library view,
  hidden home rows, pinned libraries, subtitle look, bitrate.
- `playback/Playback.kt`: device profile (decoders, 10-bit, max size, stereo/passthrough), playback info, stream
  URL, progress reporting, `stopConversion`. `playback/AudioSink.kt`: `AudioMode` + renderers factory that can force
  decoding sound on the device (used for playback speed and for previews).
- `ui/App.kt`: screen switching (new screen fades over the old) + crash screen. `ui/AppState.kt`: route stack,
  `CategorySpec` for "View all", per-session memory (search text, library filters).
- `ui/SideRail.kt`: side bar + `WithRail` (Left/Back opens the bar; the bar is a focus group with its own Up/Down;
  `railArmed` stops it grabbing focus on its own). Also `RailWidth`, `ContentStart`, `ContentEnd`, `PageTop`.
- `ui/home/HomeScreen.kt`: banner (`HeroBackdrop`, `shownHero`), rows loaded top-down, endless carousel rows
  (`LoopCycles`, `tileKey`), `ExpandingPoster`, the preview player (`FocusWatcher`, `previewWindow`,
  `PreviewVideo`).
- `ui/details/DetailsScreen.kt` (banner shares `components/BannerInfo.kt` with Home; tabs; blurred lower half),
  `ui/details/PersonScreen.kt`, `ui/player/PlayerScreen.kt` (+ `SubtitleLook.kt`), `ui/browse/BrowseScreens.kt`
  (search, libraries, library with filters, "View all", watchlist), `ui/settings/SettingsScreen.kt`,
  `ui/auth/AuthScreens.kt` (server, sign-in, profiles, `Wordmark()`).
- `ui/components/`: `FocusSurface` (zoom + ring on focus), cards, `LogoImage`, `Panel.kt` (`SidePanel`, dropdowns),
  `Blur.kt` (`BlurredImage`), `Focus.kt` (`FocusMemory`), `Scrolling.kt` (`PivotScroll`), `fadeInOnAppear`.
- Text sizes: `ui/theme/Theme.kt` (`BlushType`). Logo artwork: mipmaps (square icon), `drawable-xhdpi/xxxhdpi/banner.png`,
  `drawable-nodpi/bloom_wordmark.png`; the source SVGs aren't in the repo (simple flat jellyfish, #F4A6B8).

Shared with the Mac app: `shared/src/commonMain/kotlin/app/blush/tv/` (same packages as above). Mac app: `desktop/` (see
`docs/MAC_PORT.md`).

## Pitfalls learned the hard way

- Inside composables, the parameter `app` shadows the `app.blush.tv` package: use imports, never fully-qualified
  `app.blush.tv.…` names (this broke builds 42 and 47).
- The SDK needs SLF4J at runtime (`slf4j-api` + `slf4j-nop` are bundled); without it connecting fails.
- ExoPlayer may only be touched on the main thread; read position etc. before launching background reports.
- Back: act on **key-up** when handling it inside a panel. Acting on key-down lets the key-up reach the screen's
  `BackHandler` too (that hid the player's controls and lost focus).
- Focus moving between two children briefly reports "no focus" on their parent unless the parent is a
  `focusGroup()` (that broke the side bar's Up/Down).
- A TextureView inside a layer drawn at alpha 0 never gets its surface: keep it at a tiny alpha while it must
  render (previews).
- Animations: read animated values in `layout`/`graphicsLayer`/draw lambdas, not in composition, or every frame
  rebuilds the UI (main source of jitter on the Fire Stick). Avoid off-screen compositing layers (`Offscreen`,
  `DstIn` masks) on lists.
- Requesting focus right after data loads fails until the item has been drawn: retry for a few frames.
- A screen with nothing focusable traps the remote in the side bar: empty states need a focusable button.
- Surround audio on stereo outputs must be converted to stereo by the server (audio only); decoding DTS/TrueHD on
  the device stutters (build 17 vs 19).
- Fire OS caches launcher tiles; uninstall/replug before judging icon changes.

## Current state and open items (as of 0.2.0, 6 Nov 2026)

- 0.2.0 (run 49) is built but **not yet tested** on the Fire Stick. It's a previews/animation round (see history).
- The owner's last message had an unfinished line: "It seems that moving left or right": ask what they meant.
- Player UI dimming/brightening with an HDR film: almost certainly HDR/Dolby Vision tone mapping by the
  TV. The owner was given TV and Fire Stick settings to try; an optional "play HDR as SDR" setting was offered (server
  tone-mapping, costly) but not requested.
- "More like this" improvements offered, not chosen: prefer unwatched, learn from watch history, use the owner's
  ratings, treat same-collection films specially.
- Player ideas offered, not chosen: trickplay thumbnails while scrubbing, chapters, zoom/aspect, audio sync offset,
  "Still watching?".
- The owner plans a full audit (bugs, odd design, performance) in a fresh session; fragile areas worth a close look:
  focus handling around the side bar and panels, the Home preview player and tile animation, the endless carousel
  rows, `PivotScroll` pausing, and Back handling everywhere.

## Change history (newest first)

### Build 248 → app 0.3.165 (phase 3 starts: Mac Home in the TV's layout; TV unchanged apart from two moved files)
- Owner wants the Mac to look close to the TV app. Phase 3 brings the TV's screens over one at a time, Home first.
- Shared: `ui/components/Icons.kt` (pure Compose vectors) and `ui/CategorySpec.kt` moved to shared commonMain (same packages).
- Mac `Home.kt`: `HomeState` (featured + rows, loaded top-down, 6 at a time, 15 s give-up) with the TV's row recipe copied
  from `HomeModel.load()` (same titles, order, daily dice, Settings hidden rows / custom order / row size / genre count),
  minus Starring / Directed by (need the credits index), the numbered list and chips. TODO: share one recipe with the TV.
  Banner as on the TV (backdrop crossfade, the two gradients, logo or name, details line, 2-line description, Play pill /
  More info / watchlist +/✓ which works, dots that fill over 9 s and are clickable; pauses while the pointer is over it).
  Rows: posters 150 dp, Continue watching 16:9 cards 252 dp with progress, hover ring + grow, ‹ › arrows while the pointer
  is over a row (trackpad swipe / Shift+wheel also scroll). Top bar: Home and Libraries tabs over a soft shade; Home runs
  under the bar. Go menu: Home ⇧⌘H.
- Title pages and playback aren't on the Mac yet: Play / More info / tiles show a short message (`Session.toast`).
- Screenshot `desktop-home.png` (invented titles).

### Build 247 → app 0.3.164 (CI builds only what changed; Mac: ⌘W, library names; TV unchanged)
- Owner: Mac sign-in and libraries work on the real server; window drags fine by the top edge. Asked whether every change
  rebuilds the APK.
- CI now has a "Which apps changed" step (diff of the push): `app/` → TV only, `desktop/` → Mac only, `shared/`, Gradle
  files or `.github/` → both; manual runs build both. TV steps run under `env.BUILD_TV`, Mac under `env.BUILD_MAC`.
  Release notes are one line per app (`.github/release-notes.sh`), so a Mac-only build doesn't claim a new APK. The shots
  branch is rebuilt from its previous content plus the new pictures. The 2-day `bloom-apks` artifact upload was dropped
  (the release has the APK). A skipped app keeps its last release file; versionCode still = run + 236 (gaps are fine).
- Mac: Window menu with Close Window (⌘W, quits — single-window app) and Minimise (⌘M). Library tiles: Jellyfin's
  library pictures have the name painted on, so the name is now written under the tile (beside the count), and on the tile
  only when there's no picture.

### Builds 244–246 → app 0.3.161–0.3.163 (Mac port phase 2: sign in and browse libraries on the Mac; TV unchanged)
- Shared data layer: `Repo`, `Store`, `HomeCache`, `FilteredPager` and `ui/Format.kt` moved to `shared/src/jvmShared/kotlin`
  (same packages), a folder compiled into both the Android and desktop targets because it needs the JDK and Jellyfin SDK.
  `Repo(jellyfin, baseDevice, cacheDir, store)`: each app builds its own SDK instance (Android needs a Context) — see
  `BlushApplication.onCreate`. `Store(prefs: Prefs)`: `Prefs` mirrors the SharedPreferences calls Store uses; TV wraps
  SharedPreferences (`data/AndroidPrefs.kt`), Mac uses `DesktopPrefs` (JSON in ~/Library/Application Support/Bloom,
  owner-only file). Playback's device profile now takes `BlushApplication.context` (Repo has no context any more).
- Mac screens (`desktop/`): `Session.kt` (screens + history, Back = Esc / ⌘[ / mouse back button / "‹" links),
  `SignIn.kt` (server address + servers found on the network → "Who's watching?" → password; remembered sign-ins are
  checked, password-less profiles go straight in), `Browse.kt` (top bar with draggable strip, profile menu: switch profile /
  sign out / change server; Libraries as 16:9 tiles with title counts; a library as a poster grid, loaded 120 at a time,
  sorted by name), `Controls.kt` (PrimaryButton, QuietButton, TextLink, Field, Avatar, HoverTile, `mouseTarget`).
  Posters don't open anything yet (details page is phase 3). Menus: Go (Back ⌘[, Libraries ⇧⌘L), View (performance).
- Coil 3 on the Mac (disk cache in ~/Library/Caches/app.blush.mac/images), slf4j-nop for the SDK.
- Window dragging: the window is drawn under a see-through title bar, so `WindowDraggableArea` strips (28 dp along the
  top, and the 56 dp top bar) are where it can be dragged. Untested whether macOS also drags natively there.
- Screenshots now: desktop-server/profiles/password/libraries/library/menu.png, from invented names and titles. Checked by eye
  (build 246): hover rings, profile menu, accent outline while typing, grids. TV compiled; owner to confirm sign-in, settings
  and playback still behave after the data-layer move (same SharedPreferences file "blush", so nothing should be lost).

### Build 240 → app 0.3.157 (Mac: instant button states, View → Show performance; TV unchanged)
- Owner: the slowness is buttons reacting to the pointer/clicks. `HoverPill` now has no fades or press-dip animation (states
  snap, like Mac buttons). New `Perf.kt`: View → Show performance (⌥⌘P) overlay with click→drawn, pointer→drawn and draw time
  (last + worst), measured by `Modifier.perfMeasured()` on the window root. `Shots.kt` prints the CI hover-frame time.
- Owner on 0.3.158 (M-series Mac): resize white edge gone; "feels somewhat faster". Overlay after 18 s: click→drawn 3.5 ms
  (worst 170), pointer→drawn 28.9 ms (worst 114), drawing 0.5 ms (worst 1.2). So our UI code is cheap; the worst cases are
  Java warming up in the first seconds, and hover lands ~2 frames after the move (Compose handles moves on the next frame).
  Possible later: bundle Java 25 and its AOT cache (JEP 483/515) to cut warm-up. Kept the overlay for future screens.
- Reading the overlay: big click/pointer→drawn with small "Drawing" = delay
  before Bloom gets the event or after it draws (Java/Metal pipeline, JIT warm-up); big "Drawing" = our UI code.

### Build 239 → app 0.3.156 (Mac: no white edge while resizing, faster launches; TV unchanged)
- Owner tried Bloom.app on the Mac: opens, hover/click work, Dock icon shows. Reported: "feels a little slow" and a trailing
  white border while resizing.
- White border: the AWT window's own background was white and macOS fills new space with it before Skia draws; `Main.kt` now
  sets window/contentPane/rootPane background to Graphite (synchronously in `remember`, before the first paint).
- Launch speed: the launcher uses `-XX:+AutoCreateSharedArchive` (class data archive in ~/Library/Caches/app.blush.mac, per
  version), so the second launch of each version starts faster. Hover fades 120 → 60 ms (Mac buttons react near-instantly).
- CI prints the native libraries inside bloom.jar after `appJar` to confirm only macOS arm64 graphics code ships.
- "Slow" not yet pinned down (launch vs. hover vs. resizing); asked the owner.

### Build 237 (new repo's run 1) → app 0.3.154 (Mac port phase 1: shared module + Bloom.app; no visible change on the TV)
- Owner wants the Mac app **mouse/trackpad first** (not arrow keys); `docs/MAC_PORT.md` now has an "Input on the Mac" section.
- New `shared/` Kotlin Multiplatform module (Android via AGP 9's `com.android.kotlin.multiplatform.library`, plus `jvm("desktop")`),
  Compose Multiplatform 1.12.2 (built on Jetpack Compose 1.12.1, same as the app). `:app` depends on it.
  Moved (same package names, so no imports changed): `data/SurpriseOptions.kt`, `playback/Languages.kt` (Languages, LangPrefs,
  TrackPick), `ui/player/SubtitleLayer.kt` (SubLine, TimedCue, SubtitleStyle, `SubtitleOptions` lists, `Srt` parser, SubtitleLayer),
  `ui/theme/Colors.kt` (Accent(s), Backdrop(s), BlushColors, `Blush.colors`, BlushTheme). The app keeps the Android parts:
  `SubtitleText` (ExoPlayer cues, fetch; `parseSrt`/`at` delegate to `Srt`), `SubtitleLook` (lists delegate to `SubtitleOptions`;
  fonts, `of(store)`, picture subs), `Theme.kt` (Manrope from R.font, BlushType, Text).
- New `desktop/` module (Compose for Desktop): `Main.kt` (window, see-through Mac title bar), `WelcomeScreen.kt` (wordmark +
  subtitle preview with hover/click pills for edge and background — proves shared UI + mouse handling), `Pointer.kt` (`HoverPill`,
  `PointerProbe`), `DesktopTheme.kt` (Manrope as 5 static weights in resources/fonts, made with fonttools from the variable font,
  whose default is its lightest weight), `Shots.kt` (screenshot harness).
- CI: `:desktop:shots` renders `desktop-welcome/hover/clicked.png` at 2x with a simulated mouse into the `shots` branch; then
  `:desktop:appJar -PdesktopTarget=macos-arm64` + `desktop/mac/make-app.sh` (Temurin 21 JRE for macOS arm64, cached per month,
  shell launcher, `bloom.icns` drawn in `desktop/mac/bloom-mac-icon.svg`) → `Bloom-mac.zip` on the `latest` release.
  The Mac steps run after the APK is published, so a Mac failure can't hold back a TV build.
- Bloom.app is unsigned and untested on a real Mac: first launch = System Settings → Privacy & Security → Open Anyway.
- Repo recreated as **public** on 23 Nov 2026 (the old private one ran out of GitHub Free's Actions allowance), starting
  from one clean commit with personal details removed; this file is the record of the old history. Builds 237–238 compiled
  first time: APK 0.3.154 (versionCode 237) and `Bloom-mac.zip` (91 MB, Bloom.app 194 MB) on the release; desktop
  screenshots checked (fonts at the right weights, hover and clicks work). Bloom.app not yet opened on a real Mac.
- This repo is meant to be public: never write the owner's name, addresses, IPs, server details, library titles or viewing
  habits into it.

### Runs 232–234 → app 0.3.149–0.3.151 (subtitles: Bloom draws text subtitles itself; many more options; timing shift)
- New `ui/player/Subtitles.kt`: `SubtitleText` (ExoPlayer `Cue` → `SubLine` keeping italic/bold/underline and top placement; SRT parser; `fetch` of `/Videos/{id}/{msId}/Subtitles/{idx}/0/Stream.srt`), and `SubtitleLayer` (Compose renderer: font, weight, colour, edge Shadow/Outline/Thick outline/None, per-line rounded background None/Light/Dark/Solid, size, height; lifts 118 dp while controls show). Also used by the Settings preview, so it matches exactly. Platform-neutral apart from the Cue conversion — reusable for the Mac port.
- PlayerView's `SubtitleView` now only shows for picture subtitles (PGS/VobSub); text cues come via `onCues` into `playerLines`.
- Timing shift (player panel → "Subtitle timing", Left/Right ±0.1 s, OK resets; ±30 s): first non-zero shift fetches the whole track as SRT and reads it at position − shift (40 ms clock). Resets per title.
- Player panel also has "Subtitle size" (Left/Right), saved to Settings.
- Screenshot test `subtitles.png` (six looks). CI caches Robolectric's android-all jar (Maven Central returned 429).
- Mac port planned in `docs/MAC_PORT.md` (phase 1 done in build 237).
- `SubtitleLook` rewritten: sizes (6), fonts (Bloom/Classic/Serif/Condensed/Typewriter), weights, colours (6), edges, backgrounds, positions. Store keys `subEdge`, `subBg`, `subFont`, `subWeight`, `subPos`; old `subStyle` (Box) migrates to edge None + background Dark.

### Run 231 → app 0.3.148 (run 228 cancelled, 229 failed on `app` shadowing, 230 compiled but artifact upload hit the storage quota; all ship here)
- CI now also publishes the APK to the GitHub release `latest` (https://github.com/blustardave/bloom/releases/tag/latest), which doesn't count towards Actions storage; the artifact upload is continue-on-error. (library genre rows; Surprise me options)
- Library "By genre" rows: the selected tile's right edge was clipped at the screen edge (row scrolled only enough for the unscaled tile). LazyRow now inside `GentleScrolling` (8% margin each side).
- Surprise me: new round filter button to its right (Right/Left move between them) opens a pop-up: Pick from (Films/Series/Both), Genre (scrollable list from `repo.genres()`), Length (any / <1½h / <2h / >2h; films only, filtered client-side from 40 random), Rating (any/6+/7+/8+), Already watched (left out/included), Reset, Done. Saved in `Store.surpriseOptions` (`data/SurpriseOptions.kt`); the button's second line is `SurpriseOptions.describe()`. No silent fallback to watched titles any more: "Nothing matches, try other options".
- `rememberFirstFocus` (ConfirmDialog.kt) is now public.

### Run 228 → app 0.3.145 (Continue watching order: Bloom sets the watched date itself)
- Owner: titles watched in Bloom go to the END of Continue watching; titles watched in the official Jellyfin app go to the front.
- Checked the server source (SessionManager.OnPlaybackStart/Progress/Stopped set `LastPlayedDate = now`; `SaveUserData(UpdateUserItemDataDto)` only changes the date when one is sent). So Bloom-watched items with old/no date means Bloom's playstate reports are not landing, while the direct `updateItemUserData` resume-place save (no date) still worked → resume point present, date missing → sorted last.
- `reportStopped` now always (≥15 s watched) does one `updateItemUserData` with `lastPlayedDate = LocalDateTime.now()` (SDK serialises with the device zone offset) plus the place when under 90%. Report failures are logged: `adb logcat -s BloomReport`.
- Root cause of failing reports still unknown; if the log shows errors, fix them next.

### Run 227 → app 0.3.144 (Continue watching includes the next episode, ordered by watch history)
- Owner: shows vanished from Continue watching when an episode finished, and recent things ended up at the back. Cause (Bloom): the row was only the server's resume list (part-watched items), with no Next Up.
- `Repo.resume()` now merges three requests in parallel: resume items, Next Up (`enableResumable=false, enableRewatching=false`), and `history(0, 80)` (DATE_PLAYED desc). Walks the history newest first, one tile per series/film: series whose last-played episode was finished → its Next Up episode; part-watched → the resume item; finished film → dropped. Part-watched items older than the history window are appended. Max 20.
- Removing a not-yet-started next episode stores its id in `Store.hiddenNextUp` (no progress to clear); it returns naturally once a later episode is watched. Dialog text differs for that case.

### Runs 224–225 → app 0.3.141–0.3.142 (player prompts polish)
- Skip intro countdown default is now **Fill** (`PromptFace` style default), the owner's pick.
- Every prompt has a hairline edge (white 24% at rest, black 16% selected), a dark layer under the resting white, a soft shadow when selected, and shadowed white text/✕, so they show over white pictures. New screenshot `bright-frame.png`.
- ✕ shadow is a plain offset copy (no blur: Fire Stick ignores blur below Android 12). The dark rectangle behind the title in tiles-frame.png is a Robolectric artefact of the stand-in background, not app code.
- Dismiss (credits tiles) is a compact pill: ✕ and word 5 dp apart; offset left by `CloseInset` so the ✕ lines up with the first tile.

### Run 222 → app 0.3.139 (CI housekeeping only, no app change)
- GitHub warned Actions storage was 90% full (0.45 of 0.5 GB). Deleted ~178 old artifacts (569 MB → 5 MB). APK artifact retention 30 → 2 days; removed the `bloom-shots` artifact upload (screenshots still go to the `shots` branch).

Runs 202–206 → app 0.3.134–0.3.138 (built; prompts checked by eye in screenshots, not on a device).
- SCREENSHOTS: `app/src/test/java/app/blush/tv/PromptShots.kt` (Robolectric, native graphics, 960×540 dp, plain Application) renders the player prompts on a fake frame. CI runs it after the APK and force-pushes the PNGs to the `shots` branch (artifact downloads are blocked from the agent workspace). Fetch: `git fetch -f origin +shots:refs/remotes/origin/shots && git show origin/shots:buttons.png > x.png`. Add new screens to the test to check them by eye.
- `ui/player/Prompts.kt`: prompt faces (`PromptFace`, `PromptIconFace`, `CloseFace`) with no focus logic, wrapped by `PromptPill` / `PromptIconButton` / `SubtleClose` (FocusSurface). Countdown styles Edge / Bar / Fill drawn inside the clipped shape; default Bar (thin bar along the bottom).

Run 201 → app 0.3.132 (not tested on a device). Owner's B31 on an HEVC Main 10 1080p EAC3 5.1 MKV: direct play stalled, the forced conversion (reasons AudioCodecNotSupported, DirectPlayError — video should be copied) delivered nothing. Cause not found yet; asked for the server's FFmpeg log. B31 now also reports the first try (method, file bitrate, where it stuck, how much was buffered); "stuck 0 s" bug fixed.

Run 200 → app 0.3.130 (unconfirmed, not tested on a device).
- Prompt buttons back in the player's style: 32dp, 8dp corners, white 14% → white when selected, no outline. Countdown line drawn in the button's own modifier on its exact edge (BlendMode.Difference, so it shows on both states).
- Credits row: quiet ✕ (`SubtleClose`, no box until focused) on the left, Next episode, round skip button (`PromptIconButton`, solid `Icons.SkipAhead`, centred).
- Credits tiles: full width, 5 wide `TitleCard`s (thumb + logo, like Continue watching) with the focused title's name, year · runtime · genres and two lines of overview above; ✕ Dismiss quiet, above on the left; deeper gradient.

Run 199 → app 0.3.129 (unconfirmed, not tested on a device). NOTE: app version = 0.3.(run number − 83).
- Prompt buttons restyled after Netflix's Skip Intro: 4dp corners, dark see-through with a white outline, white when selected, text truly centred (no font padding). Countdown line now on the button's own edge (dark when selected).
- Skip intro pressed with the controls up also hides them.
- Credits: Next episode + square skip-icon button (`Icons.SkipAhead`) + small ✕ (`creditsDismissed`, watch the credits). Prompt content frozen while visible (`toEnd`), and `playNext` hides it at once — fixes other buttons flashing up when Next episode was pressed.
- Loading: spinner always centred; note under it with `describeSource` (method, codecs, container, server's TranscodeReasons). Stall only counts when the buffer isn't growing either.
- Error codes: B10 server refused to start, B11 no reply in 25 s, B20 device can't play (ExoPlayer code), B21 connection lost, B31 conversion too slow; each with the stream description. Error text centred.

Run 198 (unconfirmed, not tested on a device): the next episode keeps the exact audio / subtitle track.
- Before: only the track's language was remembered per series, so a second track in the same language (anime "Signs & Songs" vs "Full", commentary) or an untagged track went back to the default.
- `TrackPick` (Languages.kt): language, name, forced, codec, position within its language; saved per series (`Store.serAP_`/`serSP_`, "off" for subtitles off) when a track is chosen; `TrackPick.best` scores the next episode's tracks (same language required, then name 8, forced 4, codec 2, position 1). Used first in `preparePlayback`'s first-time choice; falls back to the old language rules.

Run 197 (unconfirmed, not tested on a device): audit P4, B2, B6.
- More like this: `Repo.tagTable(type)` builds tag→count once per kind by one paged pass (fields = TAGS, 1,000 a page), kept 6 h; `warmTags()` runs 4 s after Home loads. Replaces the per-tag count requests. Results cached 30 min (`similarAt`).
- Player: the Skip intro countdown only runs while playing.
- NEXT (see docs/AUDIT.md "Status"): owner to test Home with tiles-as-posters and previews off; if smooth, rebuild the wide Home tile as an overlay (P3). Then P8, banner clock (P6), image-subtitle burn-in (B7), splitting PlayerScreen/HomeScreen.

Run 196 (unconfirmed, not tested on a device): audit step 1 (docs/AUDIT.md P1, P2, P5, part of P6).
- Coil: decode limited to 2 at a time, fetch to 4 (`limitedParallelism`). Posters asked for at 280 px (was 360), quality 82 for pictures <= 400 px wide (posters re-download once).
- Tile pictures fade in with `fadeInOver` (tile colour painted over and thinned; no extra layer) in PosterCard, library and details tiles.
- Home: the "row scrolled out of sight" watcher keys on first/last visible index instead of building a set every frame. Banner clock left as is.

Run 195: no code change. Whole-app audit written to `docs/AUDIT.md` (performance findings P1–P9, bugs, UX, suggested order).

Run 194 (unconfirmed, not tested on a device): prompt tweaks.
- PromptPill: 30dp tall, 7dp corners, lighter; always 3dp padding (the countdown line no longer shifts it); one fixed spot (PromptEnd/PromptBottom = 44/124dp) with or without the controls. Intro prompt now 8 s.
- Credits: "Next episode" first and selected, "Skip credits" second (round button gone).
- Focus: `promptHadFocus` (onFocusChanged on prompt containers); when the prompt goes with the controls up, focus goes to Play / Pause (pressing Skip credits with the controls up left nothing selected).
- Tiles: effect keyed on `creditsTiles.isEmpty()` too, so reaching the credits before the titles arrive no longer loses them. Playback info gains "Intro & credits" rows (segment times, tile count) for checking Bookend timings.

Run 193 (unconfirmed, not tested on a device): intro / credits rebuilt around Bookend segments.
- Segments: only the server's media segments (Bookend sends one INTRO + one OUTRO, episodes and films), fetched each playback. Chapter fallback and the "Skip intros automatically" setting removed; 40-second credits fallback removed.
- Intro: `PromptPill` "Skip intro" bottom right, selected, with a white outline that runs out over 5 s (`introTimer`), then slow fade; with the controls up it shows above them and Up from the seek bar reaches it. Pressing skips to INTRO end with a quick fade.
- Credits (`inCredits` = OUTRO start..end only): episode with a next episode → "Skip credits" (to OUTRO end) + round Next button, or just "Next episode" when the OUTRO runs to the end (within 1 s). Film / last episode of a show (seasons flow on) → 5 s in, up to 5 More like this tiles (series' for an episode) slide up with a small Dismiss above; first tile selected; Back dismisses; a tile replaces the player with that title's page over the watched title's page.
- Watched: leaving at or after the OUTRO start also calls `setPlayed(true)` after the stop report.

Run 192 (unconfirmed, not tested on a device): tag-weighted More like this, chapter fallback for skip intro.
- `Repo.computeMoreLikeThis`: counts how many films/series carry each of the title's tags (limit=0 queries, cached in `tagCountCache`), weights shared tags by rarity (ln(N/n)/ln(N), 4 pts each, capped 14), and adds a candidate query by the 10 most distinctive tags (on <= 1/5 of the library).
- Skip intro already existed (server media segments, needs a segment plugin such as Intro Skipper on Jellyfin 10.10+). New `Repo.segments(item)`: with no server segments, an episode's chapters named Intro/Opening/Recap/Credits/Ending become segments (intro/recap only if short and in the first third; credits only in the last third).

Run 191 (unconfirmed, not tested on a device): themes + settings + details/back tweaks.
- Themes: `Backdrop`/`Backdrops` in Theme.kt (Graphite #161618 default from the owner's reference, Midnight = old #0C0D12, Black, Navy, Plum, Espresso, Forest); `Store.backdropId`, `AppState.setBackdrop`, swatches in Settings → Appearance → Theme. Window background colour now #161618.
- Settings: tab blurbs removed under each right-side title; title gap 16dp so the first option lines up with the profile card. Pane top fade now paints the page colour over the strip instead of an Offscreen DstIn layer (suspected of softening text).
- Details: tab labels don't switch tab while a Back-restore is pending; `restorable` retries focus up to ~1 s (Back from an actor landed on More like this).
- Nav bar: on reappearing it waits for the screen change (260 ms + 2 frames) before sliding in, and slides 24dp not 60dp.

Run 190 (unconfirmed, not tested on a device): nav pill reset, Home first scroll, no trailer.
- Nav pill: `NavBar.entries` counter bumped by `enterRail`; NavBarHost then forces focus onto the current screen's control if it landed elsewhere (focus group restoring last child was the suspect) and snaps the cursor there; cursor also snaps the moment focus leaves.
- Home: outer `LazyListState` uses `LazyListPrefetchStrategy(nestedPrefetchItemCount = 7)` (default 2 built the rest of a row's tiles on the frame it appeared); pictures of the next rows are enqueued as `firstVisibleItemIndex` changes (the quiet warming waits while the remote is in use).
- Details: trailer button and trailer lookup removed (Extras tab stays).

Run 189 (unconfirmed, not tested on a device): maximum playback compatibility.
- `Playback.profile`: per-decoder profile list (H.264 baseline/main/high/high 10/high 4:2:2 as the hardware lists them; HEVC main/main 10) sent as a `VIDEO_PROFILE` EQUALS_ANY condition, so 4:2:2/4:4:4/12-bit files are converted up front instead of failing after a long wait; HEVC/AV1 capped at 10-bit. If h264 lists no main/high, no profile limit is sent. NOTE: `ProfileConditionType.EQUALS_ANY` was not compile-checked before push.
- Surround outputs: DTS/TrueHD (when not passed through) are mixed to at most 5.1 by the server (audio only). Dolby Vision P7 deliberately not blocked (plays as HDR10 base layer).
- Player: direct-play stall fallback shortened to 15 s (conversion keeps 25 s). Not done: image-subtitle burn-in avoidance.

Run 188 (unconfirmed, not tested on a device): Continue watching + looks.
- `Repo.resume()` sorts by lastPlayedDate desc (newest was at the end). `reportStopped` re-saves the position (>=15 s, <90%) with updateItemUserData because the server drops places under its minimum-resume share (5%), so short watches never reached Continue watching.
- Player top icons are white-10% circles that turn white (like RoundIconButton); play button 14% idle. Hold-down pop-up (`PopupPanel`) 230dp wide, see-through (0x99101116), no outline.

Run 187 (unconfirmed, not tested on a device): feedback round.
- Playback profile: HEVC always counts as 10-bit-capable when a hardware decoder exists (user's HEVC Main10 SDR film was being converted); stereo downmix by the server only for dts/dca/truehd/mlp (was every non-passthrough codec, i.e. AAC/AC3 5.1 films were converted too).
- Search: Up from the top tiles -> first suggestion/recent pill, else the nav bar; picking a pill -> remote on the first result tile once results arrive (`pickedFromPill`).
- Watched button on details is an eye (`Icons.Eye`, accent + tinted circle when watched).
- Player look: SidePanel floating = 18dp, hairline border, bolder title; `LocalRowCorner` default 12dp; ChipButton/OutlineButton pills (no caps); layout unchanged.
- Nav pill: drawn at the current screen's name whenever the remote is out of the bar; snapped there on every focus change (in and out).
- Settings -> About: "Show statistics on screen" toggle brings the overlay back (`Perf.overlay`, saved in `store.showStats`) to catch Home lag.

Run 186 (unconfirmed, not tested on a device): nav pill, preview logo, player watchdog.
- Nav pill: snaps to the current screen's name the moment the remote leaves the bar (no 350ms delay), so it dims on the right name and never jumps on return.
- Preview logo (`PreviewSlot`): sized from the tile (BoxWithConstraints) and pinned BottomStart. Cause of "middle-left / too small" NOT confirmed.
- Player: `preparePlayback` has a 25s timeout (message instead of an endless spinner); buffering for 30s -> converts once (transcode), then shows "taking too long".
  No player code had changed since the earlier builds, so a spinner that never ends is probably the server/stream; the real cause is unknown.

Run 186 (unconfirmed, not tested on a device): nav pill snaps to the current screen the moment the remote leaves the bar (no slide-then-fade on the scrolled-to name);
preview logo sized from its tile (BoxWithConstraints) and pinned bottom-left. Player: the "not starting" watchdog now watches the picture not advancing for 25 s (`stalledFor`), not the buffering flag (which could flap and never reach 30 s); a "Still starting…" line (method) shows under the spinner after 8 s. Root cause of the stuck files NOT found (needs the file's stats: Method / codec).

Run 185 (unconfirmed, not tested on a device): Surprise me, More like this.
- Surprise me: Right is swallowed (it jumped to the banner); Icons.Dice tumbles (720deg, pop, hop, 800ms) while the pick is fetched, page opens when both are done.
- `TopAwareGrid` Column now `wrapContentHeight(unbounded = true)` (like the library grid): rows past the bottom edge were squeezed to zero height, so 4+ rows of More like this broke when scrolling down.
- Home "can't keep up with the cursor": not diagnosed (no data).

Run 184 (unconfirmed, not tested on a device): watchlist button, pop-ups, library spacing.
- `Repo.setFavorite` returned the new favourite state, so REMOVING always looked like a failure and was reverted (why the banner heart "couldn't be unpressed");
  it now returns `isFavorite == requested`. Banner + details watchlist button is now Plus / Check (accent tint when listed), not a heart; a banner press resets the
  banner clock. Titles added from the banner lead the Home watchlist row (`HomeModel.listedItems`; the server can't sort by date favourited).
- Watchlist grid: `GridMemory.focusedPos`; if the remembered title is gone, the remote starts on the one before it.
- Library: "Favourites" -> "Watchlist"; toggle options padded 13dp; tile gaps 9dp / 12dp.
- Pop-ups: `PopupPanel` / `PopupRow` (ConfirmDialog.kt) share Settings' look; `TileMenu` and `ConfirmDialog` use them, and keep asking for focus until the first row takes it.
  Settings -> About -> Statistics opens `StatisticsPopup` (live `perfText()`, measures only while open); the on-screen overlay stays off (`Perf.overlay`).
- NOT done: preview logo position (needs the screenshot / which title), "Nav bar zoom problem" (no description given).

Run 183 (unconfirmed, not tested on a device): two fixes on 182.
- PaneScroll: the "go all the way up" rule no longer needs the target to be above the fade (`state.value > 0 && state.value + offset < titlePx`); before, a top option that was already visible never triggered it.
- Library dropdown close: the `canFocus = !closing` trick is REMOVED (it probably made the buttons unfocusable, so the SideRail watchdog handed the remote to the first tile after ~1s).
  Back to the 180 behaviour (request, remove menu, keep requesting up to ~640ms) plus `quiet` (no focus highlight on the other toolbar controls while `closing`) to hide the brief fall to "All"/"Genres".
  Root cause of the first requestFocus failing is still unknown.

Run 182 (unconfirmed, not tested on a device): feedback round on 181.
- Banner buttons: Play, Info, Heart (Right from the heart -> dots). Splash back to the disc (r 50, centre 62) after the outline mask looked worse.
- Settings: page title/blurb/headings/notes all start at 14dp = row text (cards' borders hang left of the text); accent swatches unboxed (`card(boxed=false)`);
  PaneScroll titlePx 260dp; style options without detail text; LibraryRow Down -> first icon (`focusProperties { down = firstIcon }`).
- Nav bar: pill cursor snaps to the current screen 350ms after the remote leaves (LaunchedEffect(nav.focused)).
- Library: dropdown close: page Column `canFocus = !closing` (buttons override true) so nothing else can catch the remote, retry loops before and after the menu is removed.
  Toolbar vertical alignment with the heading NOT changed (unclear request).

Run 181 (unconfirmed, not tested on a device): polish round.
- Home/details: description text 11sp/15sp (`BannerDescriptionStyle`, height 32dp); details lower description 12sp. Details button row gap 8dp like Home;
  `RoundIconButton` default bg 0.10. Banner text now fades in 110ms when focus leaves and also fades with the scroll (160px).
- Settings: Nav Bar tab caches libraries (`cachedLibraries`) and refreshes quietly; pinned + others are ONE keyed loop (pin toggle no longer drops focus
  to the tabs); nav bar Style dropdown moved into Nav Bar tab; `LocalOptionIndent` = 0 in Settings; card headings/notes start at 14dp; accent swatches no ring;
  "Back to the usual order" greys out (`ActionRow(enabled)`); PaneScroll top rule uses `state.value + offset < titlePx`.
- Library: pill shapes, softer focus fill on swap toggles, "Abc" sort icon, short sort labels (wrap content, min 64dp), year spacing 2dp;
  dropdown close keeps the (hidden) menu until the button really has focus (`closing`); Back on tiles -> "All"; Back from a library -> nav bar (App.kt `backOpensRail = true`).
- Splash: word is wiped by the mark's own outline (DstOut of bloom_mark, 4px dilation) + rect left of its middle, instead of a disc.

Run 180 (unconfirmed, not tested on a device): library page round 3.
- Controls are rounded rectangles (ControlShape 6dp). Genres menu 150dp, sort menu 138dp; Any/All is one `SwapLabels` button; by-genre
  toggle icons sit in round highlights. Sort button stays in By genre mode with GenreSort (A-Z, Most/Least items = by each genre's total,
  Random). "Shuffle" renamed "Random". Count reads "n items".
- Menus: BackHandler lives inside the open menu (registered last = wins); closeMenu refocuses the button now and again 60 ms later.
- Grid: Up from any tile with a row above goes explicitly to the tile above (fixes jumping to the toolbar when scrolled down).
- Caption (name + year) in one tight column, 34dp.

Run 179 (unconfirmed, not tested on a device): library toolbar shrink, details buttons, Settings scroll, nav bar cursor.
- Library toolbar: 24dp controls, 10sp text; Segmented has a sliding highlight; poster/wide switch removed (posters only); one icon toggle
  (poster/thumbnail icon) swaps whole library <-> by genre; Up from top tiles goes to "All"; Any/All toggle + X clear button in the genres
  menu; genres button fixed width; Back closes a menu by focusing its button first (no flash on the toggle). Name/year under tiles 9.5sp.
- Banner/Details: progress line is drawn INSIDE Play/Resume (grows with it). Details uses `HeroPlayButton` (104dp) and a trailer icon button.
  Round icon buttons default background 0.09. Tabs smaller (11.5sp), page lowered a touch.
- Nav bar: cursor travels to -1.3 / n+0.3 (fully off the pill), position uses a spring (smooth when a key is held).
- Tiles: `open` stays true while a title is opening, so the poster doesn't fall back to the thumbnail.
- Settings: Statistics tab removed; Pane is now Column + verticalScroll (everything composed, so Down always finds a next row);
  PaneScroll(ScrollState); scale 0.88, margins 96dp; arrows have no circle (dim -> white/bigger on focus).
- Sign-in: Up from "Switch server" goes to the first profile. Search suggestions pills `small`.
- Note: in Lite mode the banner does not rotate (by design) and the first dot stays full. Held Up/Down cursor loss in Lite mode: not diagnosed.

Run 178 (unconfirmed, not tested on a device): library toolbar, search, splash, banner pause.
- Banner clock now pauses only in the nav bar, on the dots, or when scrolled off the banner (not on button presses).
- Splash: the word is wiped by a round front (disc + everything behind it cleared with BlendMode.Clear), not a square. Logo colours
  re-derived from the ORIGINAL art with a mild boost (saturation x1.22 + 0.03).
- Search: 6 result columns, bar 32dp, text 12sp.
- Library/Watchlist page: 8 posters per row with name + year under each; the Sort & filter panel is gone. Right of the title:
  All/Unwatched/Watched/Favourites segmented control, Genres dropdown (multi-select, "Any of these"/"All of these" = `genres` vs `feel`),
  Sort dropdown (each with an icon; hides, sliding, in "By genre" mode), poster/wide toggle, Library|By genre segmented toggle.
  Dropdown menus are overlays placed under their button (`anchors`); Back closes and refocuses the button.

Run 177 (unconfirmed, not tested on a device): banner clock, buttons, nav bar tweaks, Settings polish.
- Banner: one shared clock `HomeModel.heroProgress` (0..1) drives both the dots' fill and the rotation. It only runs while the remote is on
  the banner, not on the dots, not in the nav bar, and 1.5 s after the last move; otherwise it pauses. Dots fade their fill out when they
  stop being active (no more flash). Play button is translucent until focused (like the other banner buttons).
- Buttons app-wide (`QuietButton`): pill shape, sentence case, no letter spacing, 1.07 focus scale, like the banner's.
- Nav bar: top highlight removed (pill + circles), resting size 0.95, cursor leaves/returns faster (170 ms) and is clipped to the pill's
  rounded outline.
- Home tiles: opening a title no longer releases the preview (kept playing under the fade, stopped when Home is disposed).
- Settings: text scale 1.0, 64dp side margins, "Settings" and pane titles share style/height, account card smaller with chevron, tabs with
  icons (selected = same fill as the card, brighter on focus), cards use `Divided` (hairlines between rows), wider animated `Switch`,
  subtle arrows, `PaneScroll` BringIntoViewSpec keeps focus clear of the faded top and returns to the very top on the way up. "Nav Bar".

Run 176 (unconfirmed, not tested on a device): Settings overhaul, profile -> Settings, brighter logo, splash polish.
- Profile circle always opens Settings (the little menu is gone). Switch profile / sign out / change server live in Settings > Account.
- Settings rebuilt: big title, rounded profile card (avatar, name, server in accent colour) as the first tab, then Appearance, Nav bar
  (was Side bar), Playback, Subtitles, Statistics, About (Maintenance merged in). Each tab is one LazyColumn of rounded cards; the title
  scrolls away with the page and fades behind the pill (Offscreen layer + DstIn gradient in `Pane`).
- Appearance: accent, nav bar style dropdown, Theme & size / Featured / Detail / Top 10 as dim "Coming soon" rows, trimmed Home options,
  Home rows list with on/off + up/down arrows. "Posters under the remote" option removed from the UI.
- Home: rows no longer shuffle (fixed default order; custom order in `store.homeRowOrder`). Removed rows: chips, epic, unwatched, short.
- Nav bar tab: pinned libraries can be re-ordered (arrows) as well as given icons.
- Bloom mark / wordmark / launcher / banner PNGs saturation-boosted (originals were pale pink). Splash: eased intro with small scale-in,
  word follows the jellyfish slightly later, softer exit with a slight zoom.

Run 175 (unconfirmed, not tested on a device): nav bar tweaks, banner restyle, pins per server, More like this.
- Pill: resting size 0.88x, full size while the remote is in the bar. When the remote goes to Search/Profile the cursor
  carries on off the end of the pill (clipped by it) instead of fading, and slides back in from that side.
- Pinned libraries ("custom headings" in the pill) are now stored per server AND profile (`railPins:<server>:<user>`);
  an older list goes to the first server that asks, once. `AppState.railPins` is cleared on profile/server switch.
- Banner (Hero.kt): Play is a white pill (`HeroPlayButton`), then a heart (watchlist; `HomeModel.listed` keeps the state, and
  it refreshes the watchlist row), then a round Info button; resting backgrounds 10 % white (`RoundIconButton(background=)`).
  Dots: 7 shown, 4 dp, the active one a 20 dp pill that fills over `HeroRotateMs` (9 s); hover background 8 %.
  Banner crossfades shortened (backdrop 900 to 550 ms, text 700 to 450 ms) and rotation never fires while the remote is in
  the nav bar or within 2.5 s of a key press (was: could land on top of a move).
- Sub-line under the banner logo (Home and the details page share `BannerTitleInfo`) is greyer (0xFF9B9EA8).
- Pill and Search circle: stronger drop shadow (10 / 8 dp) and a thin top highlight (`TopHighlight` gradient border).
- More like this (`TopAwareGrid`): Right at a row end / Left at a row start now play the arrival click (the key is consumed
  there, so `NavSound.pressed` is called), and Left at the start of a row goes to the end of the row above.

Run 173 (unconfirmed, not tested on a device): navigation bar third round, and every page re-spaced for it.
- Pill: words only (no icons), constant size (items 26 dp, a touch above the old "active" size), each name has a fixed
  width so nothing resizes; the white cursor still slides, bright in the bar and dim out of it. Search and Profile circles are
  the same size as the pill's height (30 dp) and vertically centred with it.
- Layout constants in `ui/SideRail.kt`: `ContentStart` = `ContentEnd` = 22 dp (was 74 dp on the left, to clear the side bar);
  `PageTop` = 56 dp (was 30 dp) so every page header sits under the bar, left-aligned with the Search circle. The bar's own
  side padding is `ContentStart`. Every page uses these constants (Home, Search, Libraries, Library/Watchlist/Category,
  Settings, Details, Person), so they all moved together. Home's rows no longer fade under the left edge (no side bar).
- Settings → Appearance → Navigation bar: `pillStyle` (Store, mirrored in `AppState.pillStyle`): "text" (default),
  "icons", "iconsText", "sliding" (icons; the active item's name slides out). Applies live.
- Watch for: pages that are tall (Search keyboard, Settings) now have 26 dp less height under the header.

Run 172 (unconfirmed, not tested on a device): navigation bar, second round.
- The bar is now ONE element (`NavBarHost`, drawn in `BlushApp` above `AnimatedContent`), not one per screen, so it no
  longer fades with each tab change and is always there on main screens (it slides away on titles/player). Shared state
  is `NavBar` (`armed`, `focused`, `entry`, `toContent`), provided as `LocalNavBar`; `WithRail` is now only the screen's side
  (Up-at-top enters, `toContent` restores the remote, safety net).
- Smaller: pill items 24 dp, circles 26 dp, 10.5 sp names, 12 dp icons.
- Active vs not: remote in the bar = bright white cursor and the pill full size; out of it = cursor dim (30 %) and pill 0.9x.
- Remote on Search or Profile = nothing lit in the pill (cursor off, names closed). Search is just a circle that turns white;
  Profile is the picture filling the circle (no dark ring), growing a little with a white ring under the remote.
- Back from the bar returns to the screen (`BackHandler(enabled = nav.focused)`), and the cursor slides back to the current
  screen's item. Consequence: Back no longer leaves the app from Home (the Home button does); say if a double-Back exit
  is wanted.

Run 170 (unconfirmed, not tested on a device): navigation bar moved to the top.
- `ui/SideRail.kt`: the side bar is now `TopBar`. Search = circle top left (drop shadow), Profile = circle top right (no
  shadow), a pill between with Home, pinned libraries, Libraries, Watchlist. Each item is an icon; the active one gets a
  white cursor that slides along the pill (fractional `pos`, `openness(i)`) and its name slides out. Search's name slides out
  to the right, the profile's to the left, when the circle is active. OK on the profile opens `TileMenu`: Settings, Switch
  profile (Settings is no longer a bar item).
- Entering the bar: Back (as before), or Up when nothing above takes focus (`WithRail.onKeyEvent`). Left no longer enters it.
  Down leaves it back to where you were. Back again from the bar goes on as usual.
- The bar slides down from the top edge (`shown`); it rests visible only on Home at the top of the page (`showAtRest`),
  elsewhere it only appears when entered. Pages are NOT reformatted: `ContentStart`/`PageTop` are unchanged, so the bar
  overlaps page headings while it is open (formatting comes later).
- Stuck cursor when holding Up/Down on Home (reported): the held-key page scroll can outrun the rows being built, the row
  holding the remote is discarded and focus is lost. Reverted the faster held-Up/Down pace, and added a safety net in
  `WithRail`: once the remote has been used, if focus is in neither the page nor the bar for ~0.6 s (and no pop-up has the
  window) `backToContent()` puts it back. Applies to every tab page. Root cause in `goToRow` (early estimate scroll) not changed.
- Dropped the "posters open when you stop" mechanic (it felt unnatural): Settings now offers "Open straight away"
  (default) and "Stay as posters" (default in Lite); a saved "rest" choice counts as "now".

Run 169 (unconfirmed, NOT yet tested on a device; check the Actions run compiled): Home speed and row movement.
- Saved Home (`data/HomeCache.kt`, per profile, in cacheDir): the finished page is shown at once on the next launch
  (Continue watching is asked for first, 1.2 s at most) and brought up to date in one go when the server has answered.
  Within the same day its rows are reused, and only the rows that change during a day are fetched (`live` in `load()`).
- Home is the same all day: every random pick in `load()` uses `dice(n)`, seeded from the date and profile.
- Rows of the first screens are fetched first (6 at a time), the rest after (3 at a time).
- Cast index older than a day is used at once and renewed in the background (`Repo.credits()` / `scanCredits()`).
- First rows' pictures are requested the moment the rows are known. Poster tiles are filled with the poster's own
  colour (from its blurhash, `posterTint()`) until the poster arrives.
- OK held on a Home tile opens `TileMenu`: watched, watchlist, go to series/details, remove from Continue watching.
- Settings > Home: "Posters under the remote" (`tileOpen`: rest = default, now = old behaviour, posters = default in
  Lite), "Quicker row movement" (`quickNav`, default on; off = exactly the old key handling), "Row order"
  (`homeRowOrder`; empty = daily mix).
- Quick movement (`quickMove` in `HomeRowView`): focus is moved by the app, taps are queued (never dropped) and Left
  can no longer slip out to the side bar mid-row; a held Up/Down speeds up after 1 s.
- Not done: keeping extra rows/tiles built off-screen (needs an experimental Compose API that could not be checked
  without a compiler). "Because you watched", neighbour-thumbnail prefetch and freeing the preview player on leaving
  Home already existed.

Run 168 (unconfirmed): the audit's untraced UX points, traced and fixed where real.
- Library "By genre": a row that is full (24 titles) ends in a "View all" tile, which opens that genre (under the
  same filters) as a Category page; Back returns to the genre rows.
- Actor page: remembers the poster the remote was on (`gridMemory["person:<id>"]`) and returns to it after Back
  from a title; forgotten when the actor page itself is left with Back.
- "Surprise me": says "Couldn't find a film just now" for 3 s when nothing comes back, instead of doing nothing.
- Series with no episodes: the Episodes tab says "No episodes here yet."
- Player: paused the instant Back leaves it (sound used to run on through the 240 ms fade).
- Lite mode: screens swap at once (no cross-fade, no page fade-in). Ordinary mode is unchanged.
- Long overview on a title's page: no change needed, `ExpandableOverview` already opens the full text.
- Player header: the episode line under the logo ("S1 · E3 · Name") is now body-sized and white (it was the
  smallest, greyed style). The Next-episode button at the top right already existed (shown when there is one).

Run 167 (unconfirmed): the audit's "Compatibility" list (D1 to D8), the parts that could be done safely unseen.
- D1: the main player and the preview player send `Authorization: MediaBrowser Token="…"` on every request
  (`Repo.mediaHeaders()`), ready for Jellyfin 12, which stops accepting `api_key=` in addresses. `api_key=` is
  still added too, so nothing changes on older servers. **Not tested against a Jellyfin 12 server.**
- D3: devices with no Dolby Vision decoder tell the server not to send Dolby Vision profile 5 untouched
  (`VIDEO_RANGE_TYPE != DOVI` on hevc/av1 in `DeviceCapabilities.profile`). No change on devices that have one.
- D5: `android:enableOnBackInvokedCallback="false"` in the manifest (Back key handlers keep working on Android 16).
- Not changed: D2 (minSdk stays 28; Fire OS 6 untestable here), D4 (FFmpeg decoder still 1.9.0+1: couldn't check
  from the cloud session whether a build matching Media3 1.11 exists), D6 (audio output guesses: needs a
  surround system to test), D7 (player controls' bottom margin: a visible design change), D8 (repo is private).

Run 166 (unconfirmed; 165 failed to compile): the audit's "UI / UX" list (C1 to C5).
- C1: `LoadingDots` is still empty for the first 1.5 s, then shows a quiet "Loading…"; Libraries and the paged grids
  now use it while they wait (they drew nothing at all).
- C2: `Repo.libraries()` leaves out music, music-video, book, photo and playlist libraries (they opened empty).
- C3: Left on a title's Play button no longer goes Back (it does nothing). **This reverses an earlier deliberate
  choice**; restore the `app.back()` call in `DetailsScreen` if the owner wants it back.
- C4: already done in run 163 (plain-English player errors, one Back to leave).
- C5: the smallest text is half a point bigger (body 12, caption/kicker 10.5, button 11). Kept small on purpose:
  the owner prefers small text and several buttons have fixed widths.

Run 164 (unconfirmed): the audit's "Optimisation" list (B1 to B8).
- B1: the credits index (actors/directors/studios) is saved per profile in the cache folder (`credits-<server>-<user>.tsv`)
  and reused for 24 hours, so the whole-library scan no longer runs on every launch.
- B2: Home's rows are fetched six at a time, top of the page first (`Semaphore(6)` in `HomeModel.load`).
- B3: every poster tile asks for one width, `PosterTileWidth` (360), so a poster is downloaded once for all screens.
  WebP was considered and left out: it decodes slower than JPEG on a Fire Stick.
- B4: coming back to a grid asks for everything up to your place in one request (up to 600), not page after page.
- B5: "More like this" looks at about 90 candidates instead of 150.
- B6: splash trimmed by about 0.6 s when everything is ready. B7: no preview player is built when previews are off
  or Lite mode is on. B8: unused `media3-datasource-okhttp` removed; APK carries ARM libraries only (`abiFilters`).

0.3.80 (run 163, unconfirmed): fixes for the "Bugs and errors" list (A1 to A14) of the November code audit.
- A1: Home's load now belongs to `HomeModel` (its own scope, `ensureLoading()`), not the screen, so opening a title
  while Home is still filling in no longer cancels it and rebuilds Home on the way back. `AppState` calls
  `close()` on a Home it replaces.
- A2: grids in a shuffled order keep what they loaded while their page is on the back stack (`AppState.gridFeeds`,
  `PagedGrid(keep = …)`): every "View all" except the watchlist, and a library sorted by Shuffle. Dropped on Back
  from the page and whenever `gridMemory` is cleared.
- A3/A13: `FilteredPager` never returns a title twice and returns exactly the number asked for (extras are kept
  for the next page).
- A4: Left on Play goes back once (no key repeats, no second press during the fade). A10: OK on the heart does
  nothing on a series until its next episode is known. A12: banner Play ignores a second press mid look-up.
- A5: Back leaves an error screen at once. A7: after 5 minutes paused the screen is no longer held awake.
  A8: plain-English playback errors (code in brackets); Libraries says when the server can't be reached.
- A6: "More like this" cache, name index, search text and page memory are cleared on a profile/server change;
  side-bar pins are stored per profile (`railPins:<userId>`; the old list goes to the first profile to sign in).
- A9: the blank blocks above/below the built rows of a grid use `Modifier.tallSpace`, which has no height ceiling.
- A11: Continue watching keeps its row object when refreshed (place along the row kept unless the order changed).
- A14: "Change server" wording corrected.

0.2.0 (run 49; runs 47–48 were the same work, 47 failed to compile), Home previews round:
- Clip window (`previewWindow`): random 30–60 s, starting after 10:00 and ending before the halfway mark; if that
  doesn't fit, anywhere before halfway. Plays for its length, then fades sound and picture out.
- Faster start: server lookup from 250 ms (cached per title in `previewSources`), clip prepared paused from 400 ms,
  tile opens at 1.5 s, play() 120 ms later. **Key fix:** the wide layer (holding the TextureView) is kept at alpha
  ≥ 0.004 while a clip is attached — at alpha 0 HWUI never draws the TextureView, so its SurfaceTexture was never
  created and no frame could render until the tile opened (the 1–2 s second fade). Preview fade 200 ms in / 300 out.
- Louder: preview player decodes sound itself (`AudioMode.decodeHere = true`, so no passthrough and fades work) and
  runs a `LoudnessEnhancer` (+6 dB) on its session; 5.1→stereo downmix made dialogue quiet.
- Tile animation: `ExpandingPoster` uses `Animatable`s read only in `Modifier.layout` / `graphicsLayer` (no
  recomposition per frame); the poster stays composed underneath (it used to be removed when open and re-created
  on close → a blink); focus zoom origin follows the poster's centre (`FocusSurface(transformOrigin = …)`) so the
  left edge doesn't drift while opening/closing.
- Home no longer recomposes the whole screen per tile move: focus is read in `FocusWatcher`.
- Library sort/filters persist per library for the session (`AppState.libraryFilters`).
- Player: Back on the volume slider is handled by the slider (on key-up) and returns focus to the volume button;
  closing settings (Back on key-up, or Left) returns focus to the settings button (`focusWhenShown`). Acting on
  Back key-down let key-up reach the player's BackHandler, which hid the controls and lost focus.

Build 45: **renamed to Bloom** (a group of jellyfish; owner's working name, may change before any release).
- New logo: pink jellyfish mark + "bloom" in Manrope 800, no trailing dot (the old "blush." read like Booking.com).
  Square icon (mipmaps), banner, and `drawable-nodpi/bloom_wordmark.png` (used by `Wordmark()` on the sign-in
  screens). Build 46 swapped to the simpler mark (owner's pick "A"): flat #F4A6B8 half-dome (x18–82, y22–54) + three
  rounded lines (x 34/50/66), centred in the icon at ~64% of its height. Source SVGs aren't in the repo.
- Visible name, client name sent to Jellyfin, device profile name, Settings version line: "Bloom".
- CI artifact is now `bloom-apks` / `bloom-release.apk`.
- Deliberately unchanged: `applicationId`/package `app.blush.tv` (changing it = a separate app, lost sign-in and
  settings), signing key, prefs file, Kotlin names (BlushType, Blush.colors…), the "Blush" accent colour name.

Build 44 (unconfirmed):
- Details tabs: no highlight box (white text + accent underline only). Up from the tab content: the content Box is a
  `focusGroup` with `focusProperties { onExit = … }` that sends an Up exit to the current tab (focusRestorer didn't
  work: 2D search picked the tab above the tile).
- Blurred backgrounds (details lower half, person page): `components/Blur.kt` `BlurredImage` = 240×135 copy,
  3-pass box blur in a Coil `Transformation` (cached), scaled up; replaces the blocky 48px stretch.
- Player: settings card top-right; volume 0–150% (above 100% via `LoudnessEnhancer` on the player's audio
  session, created only while boosted; slider shows a notch at 100% and the boost in accent colour).
- Continue watching: one entry per series (`Repo.resume()` → `distinctBy { seriesId ?: id }`).
- Performance: Coil `allowRgb565(true)`; player clock no longer recomposes the whole screen (segment/credits via
  `derivedStateOf`); home credit index starts 1.2 s after load begins.
- Owner saw the player UI dim/brighten with an HDR film: probably HDR (TV tone-maps the whole picture, UI
  included, in HDR mode). Asked for the Playback info line to confirm; not an app fix if so.

Build 43 (unconfirmed), owner's details-page round (42 failed on a missing import):
- Details tabs: three looks (focused: white + accent underline + soft highlight; shown-but-not-focused: soft text +
  faint white line; others dim). Tab row has `focusRestorer(current tab)`, so Up from content returns to the
  current tab rather than the one above the tile.
- "More like this" = `Repo.moreLikeThis(item)`, max 14: candidates from same director(s), shared genres (150 by
  rating) and the server's similar list; scored director 10, writer 3, shared genre 3 + up to 3 for genre-mix
  overlap, top-6 cast 2 each, studio 1, tags up to 3, within 5 yrs 1.5 (10 yrs 0.75), rating nudge; needs ≥ 3.
  Home's "Because you watched" still uses the server's `similar()`.
- Home banner: `shownHero` only advances once the next title's backdrop (screen-sized request, `heroRequest`) and
  logo are in Coil's cache, so all parts change together; the following title is pre-loaded.
- Previews: clip goes into the (still hidden) tile as soon as it's prepared, starts at the nearest keyframe
  (`SeekParameters.CLOSEST_SYNC`, the main delay); leaving a tile pauses + mutes at once and keeps the frame in the
  closing tile for `TileCloseMs`, then stops; Home losing focus pauses the preview player (stray audio).
- Side bar / tiles: fades use `ModulateAlpha` / `drawBehind` alpha (no off-screen layers) for smoother animation.
- Library: switching layout keeps focus on the switch (`stayOnToolbar`). Watchlist subheading removed. Every page
  title at `PageTop` (30dp; Search field 4dp higher to line up). No "blush." loading mark: blank, then the page
  fades in (`Modifier.fadeInOnAppear`).
- Player: spinning accent arc while loading; OK on the timeline plays/pauses (or jumps to a scrubbed spot); the
  playhead stays on the line while scrubbing; settings panel is a smaller translucent floating card
  (`SidePanel(floating = true)`).

Build 41 (unconfirmed), owner's notes on 40:
- **Side bar Up/Down shut the bar** (real cause found): moving focus between two bar items made the Column's
  `onFocusChanged` report "no focus" for an instant mid-transfer, which disarmed the bar (`railArmed = false`) so the
  move failed and focus fell back to the page. Fixed by making the bar's Column a `focusGroup()` (so it stays
  ActiveParent during moves) and only disarming a frame later if focus has really gone.
- Right from an opened tile: `PivotScroll.paused` stops focus scrolling for that move, and the row does
  `animateScrollBy(poster width + gap)` with the same 320 ms ease as the tile closing (`TileCloseMs`), so the next
  tile glides into place instead of overshooting and jumping back.
- Previews: the server is asked while you're still on the poster (from 250 ms), the clip is prepared paused from
  700 ms, and the tile opens at 1.5 s with play() 250 ms later. Preview player buffers only 0.5 s before starting
  (`DefaultLoadControl` 3 s/20 s/0.5 s/1 s).

Build 40 (unconfirmed), owner's notes on 39:
- Side bar: Up/Down are handled by the bar itself (ordered list of item requesters), so focus can't slip back into the
  page (which also left a preview playing behind the "open" bar); once armed, every item is focusable (no more
  `expanded || selected` gating). Opens over 320 ms ease-out, closes 240 ms; labels slide in; the reveal value is
  read only in draw lambdas.
- Home tiles close smoothly too (300 ms width, 220 ms artwork) instead of snapping; the "close first, then move"
  key handler was removed, so focus moves straight away while the old tile shrinks.
- Smoothness: Continue-watching stills and tile expansion wait until the page has stopped scrolling; row fade-in
  only during the first 5 s of loading (no alpha layers while scrolling later); home rows prefetch 7 tiles
  (`LazyListPrefetchStrategy(nestedPrefetchItemCount = 7)`).

Build 39 (unconfirmed), owner's second Fire Stick round (run 38 was only the icon commit):
- Icons square again (see above).
- Home: widening tile no longer zooms (pictures drawn at final size, pinned left; wide art loads on focus).
  Banner artwork is two layers (`HeroBackdrop`): the next picture fades in only once loaded, so no dip to black.
  First load: banner text fades in, rows fade in once (`HomeModel.appeared`) and are published strictly top-down
  (`pending` set, 3 s deadline), reusing existing `HomeRow` objects. Long rows are an endless carousel to the right
  (`LoopCycles` = 40 rounds; tile keys are `tileKey(row, id, cycle)` and double as focus keys).
- Back on a main tab opens the side bar (`BackHandler` in `WithRail`, registered before the screen's own).
- Continue watching pushes Details (series page for episodes) under the Player, so Back goes Details → Home.
- Details opens immediately with the tile/hero's data (`Route.Details.preview`); screen changes fade the new screen
  over the old one (old one held 240 ms), removing the black flash on "More info".
- Player: logo (series logo for episodes) top-left instead of the title, name if no logo / it fails to load
  (`LogoImage(onFailed)`). Leaving the app (Home button) pauses, reports and closes the player (`ON_STOP`); Home's
  preview also stops on `ON_STOP`.
- Library: posters/wide and "By genre" are independent (`Store.libraryView` = Posters/Wide/Genres/GenresWide).
- Sign-in/server fields: the keyboard's Next/Done closes the keyboard and moves focus to the next field/button
  (`TvTextField(next = …)`); it no longer submits by itself.

Build 36 (unconfirmed), from the owner's first Fire Stick test of 0.1.35:
- Scrolling: home rows no longer mask through an off-screen layer (now a background-coloured wash at the left
  edge); rows only receive the open tile/preview when it's theirs, so other rows skip recomposing on each move;
  Continue watching waits 400 ms before starting the decoder for its still.
- Boot: the side bar only accepts focus when entered with Left (`railArmed` in `WithRail`), so it no longer grabs
  first focus while Home loads and flashes open.
- Player: top icons have no pop-up labels; controls hide after 3.5 s with a 650 ms fade, top/bottom bars drifting off;
  Back in the settings panel is handled by the panel itself (closes an open dropdown, else the panel) and focus is
  pulled back to Play/Pause with retries. Speed: passthrough (AC3/EAC3 etc. to the TV) can't be sped up, so
  `playback/AudioSink.kt` lets the sink refuse encoded audio while speed ≠ 1 and the audio track is re-selected,
  making the device decode it. Quality: choosing a cap below the file's bitrate forces the server to convert; the
  quality dropdown shows what's being sent now. Playback info shows the speed.
- Details banner laid out exactly like the home banner (`components/BannerInfo.kt` shared by both: left edge
  `ContentStart`, logo box, details line, description, buttons' bottom at 404dp; same backdrop shading).
- Continue watching: episodes show their own still only (no series logo); caption is "Series · S1:E3".
- Home rows wider than the screen wrap: Right on the last tile (or View all) goes back to the first.
- Side bar "b." / "blush." mark removed. Palette cooled (less red/purple): every dark tone shifted to a neutral
  blue-grey (background `#0C0D12`).

Owner notes on 0.1.35: previews good (a second to load is the server); playback excellent on everything tested.

Working (per the owner): connecting, profiles, home layout, search caret, side bar entry from most screens,
details page (basic), playback starts, Down/Up between the home banner and rows, previews play, Mac no longer hot.

Build 20 (confirmed): version label visible; playback info box works.

Build 32 (unconfirmed): polish before the first Fire Stick install (owner: "maybe 1 more revision").
- Home banner: logos larger and more even (area 22000, up to 340×120dp); Play/Resume fixed at 112dp wide.
- Side bar: Settings directly under the last item; a "b." mark (full "blush." when open) at the bottom.
- Details: Down from the banner goes to the *current* tab; tab underline always accent; banner description 2 lines.
- Person page: portrait 186dp wide, posters 8 to a row.

Build 31 (unconfirmed):
- Details: banner stack anchored to the bottom (just above the tabs); `focusProperties { down = firstTab }` on the
  banner so Down always lands on the first tab; tab labels keep one font weight (no width jumps); the Trailer button
  prefers any extra whose extraType/name/path says trailer, playing it in Blush.
- Person page: photo beside name + "N titles in your library" + 3-line bio; a quiet "Read more ›" link opens the full
  biography in a side panel that scrolls with Up/Down (Left/Back closes). Born/died line removed.
- Settings: Right from the sections always enters the options at the top (options pane is a focusGroup with a
  requester); Left from the options always returns to the current section. Text larger (`LocalPanelTextScale` 1.18
  for panel rows in Settings). Pinned libraries sit between Home and Libraries in the side bar.
- Home: preview fades in but vanishes instantly (no flash on the next tile); Continue watching remembers each tile's
  stream so its frame comes up faster; banner idle mode after 5 s.

Build 29 (unconfirmed):
- **Settings** is two panes: sections (Appearance, Home, Side bar, Playback, Subtitles, Account, About) on the
  left, options on the right as dropdowns/switches (`ToggleRow`, `Switch` in Panel.kt).
  Home: previews mode, number of genre rows, pinned genres (always shown), and on/off for every row type
  (`HomeRowOptions`; stored in `Store.hiddenHomeRows`); changes set `AppState.homeChanged`, which rebuilds Home the
  next time its tab is opened. Side bar: pin any library (`Store.railPins`, mirrored in `AppState.railPins`) with one
  of 8 icons (`Icons.pinIcons`, outline + filled).
- Side bar: the current item's icon is filled in (`*Filled` icons); pinned libraries appear after Watchlist and are
  highlighted when open (`WithRail(selectedLibrary = …)`).
- Server / sign-in / profiles restyled (`AuthFrame`: soft accent glow, centred 340dp form, quiet fields, full-width
  primary button). Fields use `focusProperties { down = … }` so Down goes to the next field, then the main button.
- Home: posters 116dp, wide tiles 186dp; tiles open after 1.5 s; Continue watching shows its frame almost at once;
  series preview S1E1; previews only start between 6% and 25% of the runtime (no spoilers).

Build 28 (unconfirmed): **Details page rebuilt** (`ui/details/DetailsScreen.kt`) after the owner's mockup:
- Two "magnet" positions in one hand-scrolled column (auto bring-into-view disabled with `NoAutoScroll`):
  the banner (logo pinned left via `LogoImage`, details, description, buttons, more artwork visible) and a
  full-screen lower half (tabs + content). Focus moving into either half snaps the page there; the lower half
  swaps the backdrop for a tiny stretched copy (a cheap blur that works on Android 11) plus a dark wash.
- Tabs: Episodes / In this collection / More like this (poster grid, 28 similar) / Extras (local trailers +
  special features, tab hidden when there are none) / Details (Prime-style: title + overview left, facts column,
  people column; names open the person page).
- Trailer button: a local trailer plays in the player, else the first online (YouTube) trailer opens via an
  ACTION_VIEW intent, with a message if nothing can open it.
- New `PersonScreen` (Route.Person): photo, born/died/age/place, biography with Read more, everything of theirs in
  the library. Search's People chips open it.
- Buttons rounder again (11dp).

Build 27 (unconfirmed):
- Home banner: 8 featured titles; dots are a sliding window of 5 that fade at the edges; buttons rounder (8dp);
  hero text has fixed heights and simply crossfades; after 10 s with no key presses on the banner the description
  fades and the logo + details settle into its place (any key restores).
- New home rows: On your watchlist, Starring <popular actor> (×2, from the credit index), More <genre> for you
  (last watched's genre), Hidden gems, Watch it again, From <big studio>, Epic watches; "Surprise me" at the bottom.
- Continue watching tiles fade into a paused still of the exact resume frame (preview player, forPreview).
- Row twitching: the page's PivotScroll now ignores corrections under 28dp.
- Library: grids are shifted so the left tile isn't clipped when it grows; toggle/genre button 28dp tall.
- Side panels (library filters, player settings): Left closes them, no X, title lined up with the rows.
- Subtitles: picture-based tracks (PGS/VobSub) can't be restyled; they're marked in the menu and the subtitles
  button prefers text tracks. The owner's film only had PGS (and on the emulator the server burns them in).

Build 26 (unconfirmed):
- Library toolbar: view controls pushed right; posters/wide is one toggle (`LayoutToggle`), "By genre" a separate
  pill. Filter panel keeps focus on the dropdown just used; grids don't grab focus while the panel is open.
- Search: no "Recently added" heading; new results always scroll the grid back to the top.
- Subtitles: one uniform look (`ui/player/SubtitleLook.kt`, Manrope semibold, embedded styles ignored), chosen in
  Settings → Subtitles (size, shadow/outline/box, colour) with a live preview. Subtitles confirmed working.
- Skip flash now in the centre of the screen.

Build 25 (unconfirmed):
- Libraries page shows each library's item count (accent colour) instead of its kind.
- **Library screen**: header with live count; toolbar = "Sort & filter" button (opens `FilterPanel`: sort,
  show all/unwatched/watched, favourites, genre, decade), a summary of what's applied, Clear, and a view switch
  (Posters 7-wide / Wide 4-wide / By genre rows; remembered in `Store.libraryView`).
- Shared panel pieces now live in `ui/components/Panel.kt` (`SidePanel`, `DropdownRow`, `OptionRow`): no white
  outlines, focus is a soft fill. Player settings use them too.
- Grids scroll fully back to the top when focus returns to their first row (fixes headings left cut off);
  `PagedGrid` cards now get their width from the grid.
- Search: titles first (no heading), then People, then extra rows; results arrive in stages and stay on screen
  until replaced (no "nothing matches" flash); people search never waits for the credit index; posters sized to
  fill the space.
- Banner dots: no white outline when focused. Volume slider: no outline, translucent.
- Playback info now includes a subtitles line (how they're delivered, whether the player picked them up), to
  diagnose the owner's "subtitles on but nothing shows".

Build 24 (unconfirmed):
- **Search**: quieter keyboard (30dp keys, transparent until focused), blinking caret, 5 posters a row (96dp),
  real-keyboard typing (emulator), recent searches (Store.recentSearches), query kept when coming back.
  Ranking: `Repo.credits()` builds a per-session index of everyone credited (actors + directors) and studios
  across all films/series (one paged request with People/Studios fields), so matches are ordered by how many
  titles they're in. A "People" strip shows headshots; picking one lists their titles (Back returns).
  Titles are ordered: name starts with the text, then a word does, then better-rated first.
- Home rows: Up/Down land on the tile lined up at the left (`HomeModel.focusRow`), a widened tile closes
  *before* Left/Right moves on (fixes the jump), tiles fade out under the side bar (DstIn gradient), rows
  scrolled out of view reset to their start.
- Player: skip shows a "−10 s/+10 s" flash (adds up on repeats) and loading dots only after 0.7 s of real
  buffering; settings panel is dropdown rows; subtitle icon fills in when on and every top icon shows a label
  when focused; external subtitles get the default flag and track choices are applied to converted streams too.
  **Subtitles/audio "not working correctly" was not reproduced: ask the owner exactly what happens.**

Build 23 (unconfirmed):
- CI builds only the release APK now (debug dropped to make the download smaller).
- **Player redesign** (owner's reference: a Prime Video-style screen): title top-left; top-right icons for
  subtitles on/off (`toggleSubtitles`: last used, else English, else first), volume (vertical slider popup,
  Up/Down, app-level `player.volume`) and settings (⋮). Bottom: elapsed · timeline · total, then back 10 /
  play-pause / forward 10. The timeline scrubs a marker (accelerating with hold) and seeks 450 ms after release.
  The ⋮ panel (`SettingsPanel`) holds subtitle track, audio track, quality (per-film bitrate cap, reloads the
  stream via `preparePlayback(maxBitrate = …)`), speed, and the playback info that used to sit on screen.
- Home: banner dots are focusable (Left/Right flick, Up back to buttons; Right from MORE INFO reaches them);
  bigger banner logo; tiles open out after ~2.2 s and close instantly (the closing animation caused the jump);
  the side bar collapses any open tile; returning from the side bar restores the scroll position exactly
  (the restore hook was being cleared after leaving Home once, which was the "moves up a row" bug); switching
  back to Home from another tab goes to the top (`HomeModel.goToTop`).

Build 22 (design pass, owner wants the app to look much more like Disney+, using a Disney+ home screenshot as reference):
- Smaller type scale everywhere (`BlushType`: body 11.5sp, title 14sp, headline 21sp, new `rowHeader` 12.5sp and
  `button` 10.5sp spaced capitals). Owner's rule: text too big and buttons too intrusive → keep things quiet.
- Primary/Secondary buttons are one quiet style: 30dp tall, translucent, small capitals, solid white when focused.
- Side bar: wider (64dp), icons centred and evenly spaced with no highlight box; focused icon grows and its label
  brightens.
- Home banner: smaller logo (max 220×84dp), 2-line description, small buttons, dots centred under the banner (no
  longer focusable; Right past "More info" flicks to the next title), banner taller (456dp) so the first row peeks.
- Player controls, search keyboard, profile picker and Settings swatches shrunk to match.

Build 21 changes (unconfirmed):
1. Home rows (except Continue watching) are **2:3 posters**; resting on one for ~1.2 s widens it to 16:9
   (`ExpandingPoster`), showing the backdrop + logo, then the preview fades in inside it.
2. Previews ask the server with `forPreview = true`, which skips the stereo audio-conversion rule, so surround
   films preview too. Collections preview one of their films. Files the device can't decode (10-bit HEVC on the
   emulator) still never preview, by design.
3. Removed the title/description summary that sat at the top while browsing rows.
4. The banner artwork now scrolls up with the list as one piece (no fade/shrink when moving down).
5. Focus going into the side bar no longer counts as leaving the banner; the banner buttons are remembered as the
   return point, so coming back from the bar doesn't jump to a row.
6. Empty Watchlist button (build 20), not yet confirmed.

Older open notes (emulator era):
- **Audio stutter / cut-out** in the emulator on Blade Runner (HEVC 10-bit HDR10, AAC 7.1). Confirmed by the info
  box: the emulator has no 10-bit HEVC decoder, so the Mac's Jellyfin server converts the whole video to H.264
  (with HDR tone-mapping) live, and can't keep up. Not an app bug. Options: test on the Fire Stick (hardware
  HEVC/HDR), turn on hardware acceleration (VideoToolbox) in Jellyfin's transcoding settings, or test with an
  8-bit SDR file in the emulator.
- Owner has seen more home-screen bugs, to be listed once core functions are solid.

Ideas the owner was offered for the player: trickplay thumbnails while scrubbing, chapters, playback speed,
zoom/aspect, audio sync offset, subtitle size/style, "Still watching?". None chosen yet.
Later: theme/accent picker exists in Settings; the details page needs a proper design pass.
