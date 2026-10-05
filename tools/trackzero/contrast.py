#!/usr/bin/env python3
"""Route-line contrast under ambient light (TrackZero, FEAT-005).

contrast = (L_colour + L_reflected) / (L_background + L_reflected), larger over smaller.
L_reflected = lux * reflectance / pi. A phone's cover glass reflects roughly 4-5 %.

  python3 contrast.py            # route colours on the dark and the light map
  python3 contrast.py --pairs    # WCAG contrast of UI token pairs, both appearances
"""
import math, sys

def lum(h):
    h = h.lstrip('#'); r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)

def ambient(fg, bg, nits, lux, reflectance=0.045):
    lr = lux * reflectance / math.pi + 0.5
    a, b = nits * lum(fg) + lr, nits * lum(bg) + lr
    return max(a, b) / min(a, b)

def wcag(fg, bg):
    a, b = lum(fg) + 0.05, lum(bg) + 0.05
    return max(a, b) / min(a, b)

CONDITIONS = [("indoors", 500, 300), ("shade", 800, 10_000), ("sun", 800, 50_000), ("direct sun", 1000, 100_000)]
DARK_MAP = {"ground": "#000000", "major road": "#2A2D32"}
LIGHT_MAP = {"ground": "#F4F4EF", "major road": "#FFFFFF"}
DARK_LINES = {"mint (built)": "#A9F7B9", "bright mint": "#C8FFD4", "white": "#FFFFFF", "yellow": "#FFE14A",
              "cyan": "#4FE3FF", "orange": "#FF9A3D", "magenta": "#FF4B8C", "blue": "#0087FF"}
LIGHT_LINES = {"signal green": "#005F35", "ink": "#0B0C0D", "blue": "#0057D9", "magenta": "#C2187F",
               "purple": "#6A1FD0", "red": "#D32F2F", "mint": "#A9F7B9"}

def table(title, lines, grounds):
    print(f"\n{title}")
    print(f"{'line':16}" + "".join(f"{c[0]:>12}" for c in CONDITIONS) + "   (against " + ", ".join(grounds) + ")")
    for name, colour in lines.items():
        for gname, g in grounds.items():
            print(f"{name:16}" + "".join(f"{ambient(colour, g, n, lx):12.2f}" for _, n, lx in CONDITIONS) + f"   {gname}")

DARK = {"bg": "#000000", "surface1": "#141517", "surface2": "#161719", "surface3": "#202225", "text1": "#F4F5F2",
        "text2": "#9BA0A6", "signal": "#A9F7B9", "amber": "#FFB547", "amberBg": "#2A1E06", "danger": "#FF7A7A", "dangerBg": "#2D1316"}
LIGHT = {"bg": "#FFFFFF", "surface1": "#F3F4F1", "surface2": "#FFFFFF", "surface3": "#E4E6E1", "text1": "#0B0C0D",
         "text2": "#4E5359", "signal": "#005F35", "amber": "#8A5300", "amberBg": "#FFF0D2", "danger": "#B3261E", "dangerBg": "#FCE4E2"}

def pairs():
    for name, t in (("DARK", DARK), ("LIGHT", LIGHT)):
        print(f"\n{name}  (text needs 4.5, large numbers 3.0)")
        for fg in ("text1", "text2", "signal"):
            for bg in ("bg", "surface1", "surface2", "surface3"):
                r = wcag(t[fg], t[bg]); print(f"  {fg:8} on {bg:9} {r:5.2f} {'ok' if r >= 4.5 else 'LOW'}")
        for fg, bg in (("amber", "amberBg"), ("danger", "dangerBg")):
            r = wcag(t[fg], t[bg]); print(f"  {fg:8} on {bg:9} {r:5.2f} {'ok' if r >= 4.5 else 'LOW'}")

if __name__ == "__main__":
    if "--pairs" in sys.argv:
        pairs()
    else:
        table("Dark map", DARK_LINES, DARK_MAP)
        table("Light map", LIGHT_LINES, LIGHT_MAP)
