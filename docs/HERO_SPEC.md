# Home banner ("featured films"): layout spec

Taken out of Home on 2026-11-09 so Home could be rebuilt row by row. The owner calls this layout **visually perfect**:
when it comes back it must look exactly like this. The original code is parked, untouched, in `docs/parked/Hero.kt.txt`
(not compiled). Shared sizes live in `ui/components/BannerInfo.kt` (still compiled; the Details page uses them too).

## Layout (dp, 1920x1080 TV; all measured from the top-left of the screen)
- **Banner height** `HeroHeight` = 456. The list below starts with a spacer of `HeroHeight - 18`, so the first row sits just under it.
- **Artwork**: the title's backdrop, full screen, `ContentScale.Crop`, requested at screen size. Two layers take turns: the next
  picture loads invisibly on the top layer and fades in over the current one (900 ms) once loaded; the first one fades in
  from the background. A layer that was already loaded fades in at once.
- **Gradients over the artwork** (colour = page background):
  - horizontal: 0 -> alpha .95, 0.42 -> alpha .55, 0.75 -> transparent
  - vertical: 0.45 -> transparent, 0.78 -> alpha .85, 1.0 -> solid. So the bottom edge melts into the page.
- Artwork + gradients + text block all **scroll up with the list as one piece** (offset = list scroll while the spacer is the
  first item), so going down to the rows is one smooth slide.
- **Text block** (a box `HeroHeight` tall): bottom-left column, `padding(start = ContentStart [74], bottom = HeroHeight - BannerButtonsBottom [404] = 52)`,
  children spaced `BannerButtonsGap` = 14. Its alpha = `heroAlpha` (300 ms, 1 when the banner has focus, 0 when a row does) x
  `heroIntro` (600 ms fade-in once something is loaded).
  1. **Title info** (`BannerTitleInfo`, width 350, children spaced 9):
     - logo box 350 x 120, bottom-start aligned; logo via `LogoImage(area 22000, maxWidth 340, maxHeight 120)`, else the name in
       `BlushType.display`, max 2 lines;
     - details line, 18 tall, spaced 7: certification badge, then `year • runtime (or "N Seasons") • first two genres`,
       `caption` W500, `textSoft`;
     - description: 2 lines, `body`, `textSoft`, fixed height 36.
     - One title cross-fades into the next over **700 ms**.
     - **Idle**: 5 s with no remote input while on the banner -> description fades out and logo + details drop by 36 + 9 over
       1200 ms; any key brings them back over 400 ms.
  2. **Buttons**, spaced 8: `PrimaryButton` "Play" (or "Resume" with progress) with play icon, **fixed width 112**; `SecondaryButton` "More info".
     Play on a series -> next episode in the player (else Details). More info -> Details with the item as preview.
     Right on More info -> the dots.
- **Dots** (when more than one title): centred under the banner, `bottom = 14`. Window of 5 dots, slot 13 wide, dot 7 (current, white) /
  4 (others, white 40%), animated 300 ms; window slides 300 ms; 14 px fade at each end if more lie beyond. Padding 10 x 8, pill
  that turns white 14% when focused. Left/Right flick through titles, Up -> More info.
- **Content**: `repo.featured(8)`. Rotates to the next title every **9 s** while the banner is focused and the dots are not.
  A title only becomes the shown one once its backdrop + logo are loaded (4 s limit), then the next is warmed.
- **Focus**: default focus on Play. Down from any banner button -> first tile of the first row. Focusing the banner
  scrolls the list back to the top. Returning to Home from another tab -> top, on Play.
- Empty/error states: "Can't load Home" + message + "Try again", or "Welcome to Bloom" + hint.

## Putting it back
Restore the pieces in `docs/parked/Hero.kt.txt` as `home/Hero.kt` (HeroBackdrop, HeroBlock, Dots), give the new Home model
`featured / heroIndex / showHero`, put the spacer item first in the column and offset the banner by the list scroll.
