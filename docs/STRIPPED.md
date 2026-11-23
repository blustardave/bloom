# Stripped-down build (2026-11-09)

To perfect Home, everything except these was taken out of the app:
Home, the side bar (Home icon + profile button only), and the server / profile / sign-in screens.

Removed from the tree (all still in git at commit `bfcca37`, the last build with everything):
`ui/browse/` (Libraries, Library, Watchlist, History, Search, Category), `ui/details/` (Details, Person),
`ui/settings/`, `ui/player/` (+ SubtitleLook), `ui/home/ExpandingPoster.kt` (the opening preview tile).
Bring one back with e.g. `git checkout bfcca37 -- app/src/main/java/app/blush/tv/ui/settings`, then re-add its route in
`ui/App.kt` (`Screen()`), its side-bar item in `ui/SideRail.kt` (`railItems`, and the `order` list / Settings button),
and any Home click that should open it (tile click, View all) in `ui/home/HomeScreen.kt`.
The back-end (`data/`, `playback/`) is untouched. The banner is specced in `docs/HERO_SPEC.md`.

Plan: add them back one at a time, perfecting each. Stats overlay: it was switched on in Settings (stored on the stick);
without Settings it stays as it was.
