# Route line visibility in daylight (FEAT-005, Phase 20)

The question: what makes the route line easy to see at a glance on a phone on the handlebar in bright daylight, and
what should change? Only the map's route overlay changes in this phase. The light appearance is Phase 36.

## 1. What sunlight does to contrast

A phone screen reflects part of the light falling on it, about 4–5 % for a typical glass front. That reflection adds
to every pixel, the black ground included. The contrast the rider sees is

    (L_line + L_reflected) / (L_ground + L_reflected)

where L_reflected = reflectance × illuminance / π (a diffuse approximation). Indoors the reflection is negligible and
a bright line on black is extremely contrasty. In sun the reflected light is comparable to the screen's own maximum
brightness, and every colour converges towards the background.

`tools/trackzero/contrast.py` computes this for the colours a route line could use. Conditions: indoors = 500 nits,
300 lux; shade = 800 nits, 10,000 lux; sun = 800 nits, 50,000 lux; direct sun = 1,000 nits, 100,000 lux; 4.5 %
reflectance. Output (contrast ratio of the line against the ground, and against a major road where they cross):

```
Dark map
line                 indoors       shade         sun  direct sun   (against ground, major road)
mint (built)           82.78        5.37        1.88        1.55   ground
mint (built)           22.32        4.69        1.82        1.52   major road
bright mint            93.30        5.93        1.99        1.62   ground
bright mint            25.15        5.18        1.93        1.59   major road
white                 105.23        6.57        2.12        1.70   ground
white                  28.37        5.74        2.06        1.67   major road
yellow                 79.80        5.21        1.84        1.53   ground
cyan                   67.52        4.55        1.71        1.45   ground
orange                 47.60        3.49        1.50        1.31   ground
magenta                30.38        2.57        1.31        1.20   ground
blue                   26.59        2.37        1.27        1.17   ground

Light map
signal green            9.69        4.09        1.83        1.54   ground
ink                    68.90        5.90        2.00        1.63   ground
blue                    7.13        3.63        1.77        1.51   ground
magenta                 6.24        3.42        1.74        1.49   ground
mint                    1.15        1.12        1.07        1.05   ground
```
(Rows trimmed for major roads except the first three; `python3 tools/trackzero/contrast.py` prints them all.)

What the table shows:
- **Hue does not help; luminance does.** In sun every candidate ends between 1.2:1 and 2.1:1, ranked by its
  luminance alone. Mint already has about 78 % of white's luminance. The brighter mint `#C8FFD4` gains about 6 %
  (1.88 → 1.99 in sun). Magenta and blue, common route colours in other apps, are the worst on a dark map.
- **The line matters most where it crosses lighter map features.** Against a major road the indoor contrast is a
  quarter of that against the ground (22:1 vs 83:1), and in sun the two converge.
- **On a light map a mint line is invisible** (about 1.1:1). The signal green of the light palette, or dark ink, is
  needed there, with a white casing (Phase 36).

## 2. So what helps

1. **Width.** A low-contrast feature is found by its size. At 1.9:1 a 5.5dp line is a thin thread at arm's length;
   8dp is half as wide again (about 1.3 mm on a 420 dpi phone). It costs nothing to draw: the same two subroutes.
2. **A hard edge.** The built line had a 16dp glow at 18 % mint. The table shows a colour that dim contributes nothing in
   sun (it lies below the reflection). A 2dp black casing on each side keeps a sharp boundary where the line crosses
   lighter roads and labels, which is exactly where the line is hardest to follow.
3. **Direction marks inside the line**, in the casing colour (Phase 21): the eye finds a textured line more easily
   than a flat one, and the marks answer "which way?" without a second look.
4. **A light map for the brightest conditions** (Phase 36). The literature on display polarity is about text, not
   map lines, but it points the same way:
   - Buchner, Mayr & Brandt (2009) found the usual advantage of dark-on-light text disappears when the two displays
     have the same overall luminance: the advantage comes from the brighter display, not from polarity as such.
   - Piepenbrock, Mayr, Mund & Buchner (2013) found dark-on-light displays better for both younger and older readers in
     acuity and proofreading, and argue brighter displays compensate for age-related vision loss.
   - Dobres, Chahine & Reimer (2017) measured legibility under glance-like reading (short exposures, as on a
     handlebar) and found the worst thresholds for light-on-dark text in dark surroundings. In bright surroundings both
     polarities did well. Their reading is that more light makes the pupil smaller and the image sharper.
   For TrackZero: in strong daylight the dark map is not a legibility problem in itself; the line's contrast is (§1).
   A light appearance offers the rider a mostly bright screen, which keeps its own contrast better in sun, and it is
   the rider's choice (Phase 36). None of these studies measured a route line on a map, so the outdoor comparison
   below decides.

## 3. Candidates and the choice

Implemented in `Framework.nativeShowTrackLine` and switchable in About (hidden tools, tracing on) for the owner's check:

| | Line | Under it | Note |
|---|---|---|---|
| **A** | 5.5dp mint | 16dp glow (18 % mint) | as built before v2 |
| **B** | 8dp mint | 12dp black casing | **shipped** |
| **C** | 8dp `#C8FFD4` | 12dp black casing | about 6 % more luminance |
| **D** | 10dp mint | 14dp black casing | widest |

The dimmed off-route line (R3) is the same in all four: dashed, 5.5dp, 45 % mint, no casing. The leg line and the
place path use the same style. The ring at the start or the place keeps its on-screen size whatever the width. Cost:
still two subroutes per line (casing + core instead of glow + core), so no extra draw work.

**B ships** unless the owner's outdoor check prefers another (NEEDS OWNER N20). The losing styles and the switch are
then removed.

## Sources

- Wikipedia, "Contrast ratio" (ambient contrast ratio): https://en.wikipedia.org/wiki/Contrast_ratio
- Buchner, A., Mayr, S., & Brandt, M. (2009). The advantage of positive text-background polarity is due to high display
  luminance. *Ergonomics*, 52(7), 882–886.
  https://www.researchgate.net/publication/26326659_The_advantage_of_positive_text-background_polarity_is_due_to_high_display_luminance
- Piepenbrock, C., Mayr, S., Mund, I., & Buchner, A. (2013). Positive display polarity is advantageous for both younger
  and older adults. *Ergonomics*, 56(7), 1116–1124.
  https://www.semanticscholar.org/paper/Positive-display-polarity-is-advantageous-for-both-Piepenbrock-Mayr/1605c4f7d561f6cca0fcce6062f788562ccbae6f
- Dobres, J., Chahine, N., & Reimer, B. (2017). Effects of ambient illumination, contrast polarity, and letter size on
  text legibility under glance-like reading. *Applied Ergonomics*, 60, 68–73. https://doi.org/10.1016/j.apergo.2016.11.001
