# How Home's rows are built (settled 2026-11-09, Bloom 0.3.5). Keep this design.

The owner has confirmed this scrolling setup works well. Read this before touching `ui/home/HomeScreen.kt`.

## One rule: focus never scrolls anything
Both the page (a `LazyColumn`) and every row (a `LazyRow`) are wrapped in `PivotScrolling(FocusDoesNotScroll)`: the
`BringIntoViewSpec` that always answers 0. Moving focus therefore cannot nudge either one. Every scroll is explicit:

- **Up/Down** (`HomeModel.goToRow`): focus is moved to the row's left-most visible tile, then the page glides so the
  row's top sits at `anchorPx` (30% of screen height minus 34dp). A tap eases out over 150 ms. A held key (repeatCount > 0)
  moves at constant speed (linear tween, 2.0 px/ms x density) and a repeat is only taken once the page is within half a
  row of where it was heading (`lag`), so the next row is always already built. If a row is not built yet it NEVER jumps
  (a jump threw the focused row off screen and lost the cursor): it scrolls towards an estimate, waits, then focuses.
- **Left/Right** (`HomeRowView`): a preview key handler works out the next index from `focusedIdx` and calls `alignTo`,
  which slides the row so that item sits at the heading (item.offset == 0, because `contentPadding.start = ContentStart`).
  The target is `min(next, lastStart)`, so the row stops once the last window of items is showing.
- Item offsets from `LazyListItemInfo.offset` count from the start of the content, i.e. below the content padding.

## Row end
`lastStart`/`endPad` are computed in whole pixels from the row's measured width (`onSizeChanged`): up to six items
(View all counts as one, 112dp) fit when scrolled to the end, `endPad` = room left + 8dp spare so the last position is
always reachable. Rows with that many items or fewer do not scroll.

## Other decisions
- Tiles are plain (`PosterCard`, wide `TitleCard` for Continue watching): nothing runs until a tile has focus.
- Left edge fade: opaque at RailWidth-30dp, clear at ContentStart-12dp.
- Background warming (`model.warmed`): after 2.5 s, one picture at a time (`loader.execute`, so it can never queue up
  behind the pictures you are waiting for), first 10 tiles per row, pauses 1 s after any key, memory cache off (disk only).
- The grey "placeholder while rushing" tiles were tried and cut: no measurable benefit, odd pop-in.
- Row order: Continue watching, New on [server], watchlist, New releases, then everything else shuffled each launch,
  with five of nine mood rows (`mood-*`) chosen at random.
- Stats overlay (`Perf`) shows version, hitches by activity, and the background-warming line.
