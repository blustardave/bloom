# Bloom audit — 17 Nov 2026 (build 0.2.157)

Read from the code only; nothing here was measured on a device. "Likely" means a reasoned guess. Ordered by expected payoff.

## Performance

### P1. Image decoding competes with the UI thread (all tile screens) — small change, likely big win
`BlushApplication.newImageLoader` leaves Coil's fetch/decode on `Dispatchers.IO` (up to 64 threads). A new row asks for 7–14
pictures at once; on a 4-core stick that many JPEG decodes starve the main thread → dropped frames exactly when new tiles appear.
Fix: `decoderCoroutineContext(Dispatchers.IO.limitedParallelism(2))`, fetcher limited to ~4.

### P2. Posters are downloaded bigger than they're drawn — small change
`PosterTileWidth = 360` at `quality = 90`. Tiles are drawn ~200–250 px wide on a 1080p stick. Ask for ~260 px at quality 80:
roughly half the bytes and decode time per poster, on Home, libraries, More like this and search.

### P3. Home poster tiles change width under the remote — medium rebuild
`HoverPosterTile` grows from poster to wide thumbnail on focus. Every Left/Right therefore re-measures the whole row, rebuilds two
tiles, loads a 640 px thumbnail and runs a hand-rolled slide (`alignTo`) with re-anchoring. This is the most expensive thing Home
does per key press and the likeliest source of Home jitter. Test first: Settings → Home → tiles stay posters (`tileOpen =
"posters"`). If Home is smooth that way, rebuild the wide tile as an overlay drawn above the row (row layout never changes).

### P4. More like this fires up to ~35 server requests when a title's page opens — medium (introduced in Run 192)
`computeMoreLikeThis` counts each tag with its own request (`tags.take(30)`) plus 4 candidate queries, all in parallel, while the
page is animating in. Fix: build one tag→count table per library type once (one paged scan with `fields = TAGS`, saved to disk like
the credits index), then More like this needs no count requests at all.

### P5. Each grid tile is two GPU layers while it fades in — small change
`PictureTile`/library tiles use `LiteTile` (a clipped `graphicsLayer`) with `fadeInOnAppear` on the image (a second layer with
alpha < 1 → off-screen buffer). A new row = 7–8 extra buffers for 260 ms. Fold the fade into a draw-time alpha on the image
(`drawWithContent` + `alpha`), or drop the fade for pictures already in memory.

### P6. Per-frame work on Home while scrolling — small change
`snapshotFlow { visibleItemsInfo.map { it.key }.toSet() }` allocates a list and a set on every scroll frame. Key it on first/last
visible index instead. The banner clock (`withFrameNanos` loop writing `heroProgress`) forces a frame 60×/s while the banner has
the remote; drive the progress dots from an `Animatable` read only in draw.

### P7. Baseline profile is 14 lines — medium, matters only without the adb compile step
`src/main/baseline-prof.txt` covers almost nothing, so a normal install runs interpreted/JIT until the `adb … compile -m speed`
command is run. Generating a real profile needs a device-run macrobenchmark, which CI can't do; keep running the compile command.

### P8. Focus tracking does work on every move — small
`FocusSurface` recomposes its content on focus (`content(focused)`), sets `rail.last`, plays the nav sound, and on Home also writes
`model.heroFocused`, `focus.remember`, and prefetches. Home poster/Continue tiles could move to the `LiteTile` pattern (focus drawn,
not composed).

### P9. Previews — check, don't change yet
Hover previews start an ExoPlayer and a server-side clip conversion after 1 s rest. Cancelled when moving, but a second video decoder
plus a busy server can hurt the next few moves. Test with Settings → previews off to see how much of the jitter it accounts for.

## Bugs / risks
- B1. `reportStopped` re-saves the position and then (from the credits) `setPlayed(true)` is called. Jellyfin should clear the
  position when marking played; verify Django doesn't linger in Continue watching.
- B2. Intro countdown keeps running while paused; the pill can vanish while the picture is frozen on the intro.
- B3. `openFromCredits` edits `app.stack` directly (bypasses `back()`'s memory handling). Works by reading; untested.
- B4. 190 `runCatching` blocks swallow errors silently (network, focus). Failures are invisible; consider logging them to the
  crash file in debug.
- B5. The 300 ms focus watchdog in `SideRail` papers over lost focus rather than fixing causes (library dropdown flash, nav pill).
- B6. Tag counts and More like this results are cached for the whole session; new tags/titles need an app restart to show.
- B7. Subtitle-driven transcodes (picture subtitles) still unaddressed from the compatibility pass.
- B8. Unconfirmed from earlier runs: nav pill reset, actor→Back focus, library dropdown flash, HEVC Main 10 file, credits tiles.

## UI/UX oddities
- U1. Skip/Next prompts sit at 124 dp from the bottom even with controls hidden — steady, but high on a clean picture.
- U2. Credits tiles disappear the instant the OUTRO ends; a film whose credits run to the end closes straight to its page.
- U3. Two separate grid implementations (`WindowGrid`, `TopAwareGrid`) with different gaps (9/12 vs 14/16 dp) and column maths.
- U4. Statistics/"Lite mode" switches exist in code but aren't all exposed consistently in Settings.

## Structure (helps every future change)
- `PlayerScreen.kt` (1,885 lines) and `HomeScreen.kt` (1,825) are single files with one giant composable each; state reads at the
  top recompose large trees. Splitting into smaller composables with narrow parameters is the durable performance fix.
- No tests and no on-device profiling; every run is "built, not tested". The Settings → statistics overlay is the only measurement.

## Suggested order
1. P1 + P2 + P5 + P6 (one small build, low risk).  2. P4.  3. Test P3/P9 toggles on the stick, then decide on the Home tile rebuild.

## Status (17 Nov 2026, after Runs 196–197)
- Done, untested on device: P1, P2, P5, P6 (scroll watcher only), P4, B2, B6.
- Not done: P3 (Home wide-tile rebuild; wait for the owner's test with tiles-as-posters / previews off), P6 banner clock, P7, P8,
  B3–B5, B7, U1–U4, splitting the two big files. These were left because each is a larger, riskier change with no device to test on.
- To verify on the stick: B1 (Django leaves Continue watching), credits tiles, poster sharpness at 280 px.
