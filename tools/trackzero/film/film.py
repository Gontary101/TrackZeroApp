#!/usr/bin/env python3
"""TrackZero launch film (Phase 40): renders the film from the real app's screen recordings (record.sh), the design
tokens and the app's own fonts, and writes an H.264/AAC file. Everything is drawn here: no stock footage, music or
graphics. See film.md.

  film.py --raw <raw dir> --out <file.mp4> [--aspect 16x9|9x16] [--from s] [--to s] [--scale 0.5] [--fps 30]
"""
import argparse, math, os, subprocess, sys, wave
from functools import lru_cache

import cairo
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
FONTS = os.path.join(REPO, 'android', 'app', 'src', 'main', 'res', 'font')
SHOTS = os.path.join(REPO, 'shots')
sys.path.insert(0, os.path.join(REPO, 'design', 'logo'))
_stderr, sys.stderr = sys.stderr, open(os.devnull, 'w')
import generate as logo  # noqa: E402  (the mark's own geometry)
sys.stderr = _stderr

import sound  # noqa: E402

# ---- Tokens (design.html, Tokens v1/v2; RGB) ----
BLACK = (0, 0, 0)
MINT = (169, 247, 185)
TEXT1 = (244, 245, 242)
TEXT2 = (155, 160, 166)
TEXT3 = (58, 61, 66)
WELL = (11, 12, 13)
SURFACE3 = (32, 34, 37)
AMBER = (255, 181, 71)
LIGHT_GROUND = (244, 244, 239)
LIGHT_TEXT1 = (11, 12, 13)
LIGHT_TEXT2 = (78, 83, 89)
LIGHT_SIGNAL = (0, 95, 53)
LOGO_AHEAD = (94, 143, 104)

BPM = 100
BEAT = 60.0 / BPM
BAR = 4 * BEAT


# ---- Easing ----
def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def ease_out(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def ease_in_out(t):
    t = clamp(t)
    return 4 * t ** 3 if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2


def ease_in(t):
    t = clamp(t)
    return t ** 3


def lerp(a, b, t):
    return a + (b - a) * t


def seg(t, t0, t1):
    """0 before t0, 1 after t1, linear between."""
    if t1 <= t0:
        return 1.0 if t >= t1 else 0.0
    return clamp((t - t0) / (t1 - t0))


# ---- Fonts and text ----
@lru_cache(maxsize=None)
def font(name, px):
    return ImageFont.truetype(os.path.join(FONTS, name + '.ttf'), int(round(px)))


@lru_cache(maxsize=4096)
def glyph(ch, name, px, rgb):
    """One character as premultiplied BGRA float32 with its advance; drawn 2x and reduced for smooth edges."""
    f2 = font(name, px * 2)
    asc, desc = f2.getmetrics()
    w = max(1, int(math.ceil(f2.getlength(ch))) + 8)
    im = Image.new('L', (w, asc + desc + 8), 0)
    ImageDraw.Draw(im).text((4, 4), ch, font=f2, fill=255)
    im = im.resize((max(1, w // 2), max(1, (asc + desc + 8) // 2)), Image.LANCZOS)
    a = np.asarray(im, dtype=np.float32) / 255.0
    out = np.zeros(a.shape + (4,), np.float32)
    for i, c in enumerate((rgb[2], rgb[1], rgb[0])):
        out[..., i] = a * (c / 255.0)
    out[..., 3] = a
    return out, font(name, px).getlength(ch)


def text_width(s, name, px, track=0.0):
    f = font(name, px)
    return sum(f.getlength(c) for c in s) + track * px * max(0, len(s) - 1)


def wrap(s, name, px, max_w):
    """Greedy lines, then the last two balanced (no lone word on a line)."""
    lines = _greedy(s, name, px, max_w)
    if len(lines) >= 2:
        words = (lines[-2] + ' ' + lines[-1]).split()
        best = None
        for k in range(1, len(words)):
            a, b = ' '.join(words[:k]), ' '.join(words[k:])
            wa, wb = text_width(a, name, px), text_width(b, name, px)
            if wa <= max_w and wb <= max_w and (best is None or abs(wa - wb) < best[0]):
                best = (abs(wa - wb), a, b)
        if best:
            lines[-2:] = [best[1], best[2]]
    return lines


def _greedy(s, name, px, max_w):
    words, lines, cur = s.split(), [], ''
    for w in words:
        nxt = (cur + ' ' + w).strip()
        if cur and text_width(nxt, name, px) > max_w:
            lines.append(cur)
            cur = w
        else:
            cur = nxt
    if cur:
        lines.append(cur)
    return lines


# ---- Canvas (float32 BGR, 0..1) ----
class Canvas:
    def __init__(self, w, h, bg=BLACK):
        self.w, self.h = w, h
        self.img = np.zeros((h, w, 3), np.float32)
        self.img[:] = (bg[2] / 255.0, bg[1] / 255.0, bg[0] / 255.0)

    def over(self, bgra, x, y, alpha=1.0):
        """Composites premultiplied BGRA (float32) with its top-left at (x, y)."""
        if alpha <= 0:
            return
        h, w = bgra.shape[:2]
        x, y = int(round(x)), int(round(y))
        x0, y0, x1, y1 = max(0, x), max(0, y), min(self.w, x + w), min(self.h, y + h)
        if x1 <= x0 or y1 <= y0:
            return
        src = bgra[y0 - y:y1 - y, x0 - x:x1 - x]
        a = src[..., 3:4] * alpha
        dst = self.img[y0:y1, x0:x1]
        dst *= (1 - a)
        dst += src[..., :3] * alpha

    def fill(self, rgb, alpha=1.0):
        c = np.array((rgb[2], rgb[1], rgb[0]), np.float32) / 255.0
        self.img = self.img * (1 - alpha) + c * alpha

    def text(self, s, x, y, name, px, rgb, alpha=1.0, track=0.0, reveal=None, rise=0.0):
        """Draws s with its top-left at (x, y). reveal(i) -> 0..1 per character (staggered entrances)."""
        cx = x
        for i, ch in enumerate(s):
            g, adv = glyph(ch, name, px, rgb)
            k = 1.0 if reveal is None else reveal(i)
            if k > 0 and ch != ' ':
                self.over(g, cx - 2, y - 2 + rise * (1 - k), alpha * k)
            cx += adv + track * px

    def bgr8(self):
        return (np.clip(self.img, 0, 1) * 255 + 0.5).astype(np.uint8)


def cairo_layer(w, h):
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, w, h)
    return surf, cairo.Context(surf)


def layer_to_bgra(surf):
    buf = np.ndarray((surf.get_height(), surf.get_stride() // 4, 4), np.uint8, surf.get_data())
    return buf[:, :surf.get_width()].astype(np.float32) / 255.0  # premultiplied BGRA already


def rgbf(c):
    return c[0] / 255.0, c[1] / 255.0, c[2] / 255.0


# ---- Screen recordings ----
class Clip:
    """A screen recording read forward in time; frames resized on demand (variable frame rate, real timestamps)."""

    def __init__(self, path):
        self.path = path
        self._open()

    def _open(self):
        self.cap = cv2.VideoCapture(self.path)
        self.cur, self.cur_t = None, -1.0
        self.nxt, self.nxt_t = self._read()
        self.cur, self.cur_t = self.nxt, self.nxt_t
        self.nxt, self.nxt_t = self._read()

    def _read(self):
        ok, f = self.cap.read()
        if not ok:
            return None, float('inf')
        return f, self.cap.get(cv2.CAP_PROP_POS_MSEC) / 1000.0

    def at(self, t):
        if t < self.cur_t - 0.05:
            self._open()
        while self.nxt is not None and self.nxt_t <= t:
            self.cur, self.cur_t = self.nxt, self.nxt_t
            self.nxt, self.nxt_t = self._read()
        return self.cur


class Still:
    def __init__(self, path):
        self.img = cv2.imread(path)

    def at(self, t):
        return self.img


@lru_cache(maxsize=64)
def round_mask(w, h, r):
    m = np.zeros((h * 4, w * 4), np.uint8)
    rr = r * 4
    cv2.rectangle(m, (rr, 0), (w * 4 - rr, h * 4), 255, -1)
    cv2.rectangle(m, (0, rr), (w * 4, h * 4 - rr), 255, -1)
    for cx, cy in ((rr, rr), (w * 4 - rr - 1, rr), (rr, h * 4 - rr - 1), (w * 4 - rr - 1, h * 4 - rr - 1)):
        cv2.circle(m, (cx, cy), rr, 255, -1)
    return cv2.resize(m, (w, h), interpolation=cv2.INTER_AREA).astype(np.float32)[..., None] / 255.0


def phone(screen_bgr, sh, light=False):
    """The phone: the screen at height sh in a thin frame (the boards' phone: well bezel, surface3 edge)."""
    sw = int(round(sh * 1080 / 2400))
    bez = max(4, int(round(sh * 0.012)))
    r_out = int(round(sh * 0.062))
    W, H = sw + 2 * bez, sh + 2 * bez
    scr = cv2.resize(screen_bgr, (sw, sh), interpolation=cv2.INTER_AREA).astype(np.float32) / 255.0
    surf, cr = cairo_layer(W, H)
    edge = (205, 208, 202) if light else SURFACE3
    cr.set_source_rgb(*rgbf(WELL if not light else (236, 238, 233)))
    rounded(cr, 0.5, 0.5, W - 1, H - 1, r_out)
    cr.fill_preserve()
    cr.set_source_rgb(*rgbf(edge))
    cr.set_line_width(max(1.5, sh * 0.0022))
    cr.stroke()
    out = layer_to_bgra(surf).copy()
    m = round_mask(sw, sh, max(2, r_out - bez))
    region = out[bez:bez + sh, bez:bez + sw]
    region[..., :3] = region[..., :3] * (1 - m) + scr * m
    region[..., 3:4] = np.maximum(region[..., 3:4], m)
    return out


def touch(img, x, y, t, hold):
    """A finger on the real screen: a soft disc that lands, stays while held, and leaves as a ring."""
    img = img.copy()
    over = img.copy()
    land = ease_out(seg(t, 0.0, 0.12))
    leave = seg(t, hold, hold + 0.45)
    r = int(54 + 40 * ease_out(leave))
    alpha = 0.32 * land * (1 - leave)
    if alpha <= 0:
        return img
    cv2.circle(over, (int(x), int(y)), r, (242, 245, 244), -1, cv2.LINE_AA)
    return cv2.addWeighted(over, alpha, img, 1 - alpha, 0)


def rounded(cr, x, y, w, h, r):
    cr.new_sub_path()
    cr.arc(x + w - r, y + r, r, -math.pi / 2, 0)
    cr.arc(x + w - r, y + h - r, r, 0, math.pi / 2)
    cr.arc(x + r, y + h - r, r, math.pi / 2, math.pi)
    cr.arc(x + r, y + r, r, math.pi, 3 * math.pi / 2)
    cr.close_path()


def place(canvas, bgra, cx, cy, scale=1.0, alpha=1.0):
    """Composites bgra centred at (cx, cy), scaled."""
    if scale != 1.0:
        h, w = bgra.shape[:2]
        nw, nh = max(1, int(round(w * scale))), max(1, int(round(h * scale)))
        bgra = cv2.resize(bgra, (nw, nh), interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_LINEAR)
    h, w = bgra.shape[:2]
    canvas.over(bgra, cx - w / 2, cy - h / 2, alpha)


# ---- The mark (design/logo/generate.py) ----
def draw_mark(cr, cx, cy, size, ridden, dots_shown, ring, ahead=LOGO_AHEAD, line=MINT, ground=BLACK):
    """The mark at size px (its 108 grid's 54-centred extent), the solid line drawn to `ridden` (0..1 of its part),
    the first `dots_shown` dots (fractional fades the next), the start ring scaled by `ring`."""
    k = size / 40.0
    ox, oy = cx - logo.CX * k, cy - logo.CY * k
    cr.save()
    cr.translate(ox, oy)
    cr.scale(k, k)
    cr.set_line_cap(cairo.LINE_CAP_ROUND)
    cr.set_line_join(cairo.LINE_JOIN_ROUND)
    if ridden > 0:
        end = logo.solid_end * ridden
        cr.set_source_rgb(*rgbf(line))
        cr.set_line_width(logo.LINE)
        cr.move_to(*logo.pts[0])
        for i in range(1, len(logo.pts)):
            if logo.cum[i] > end:
                break
            cr.line_to(*logo.pts[i])
        cr.line_to(*logo.at(end))
        cr.stroke()
    for i, (x, y) in enumerate(logo.dots):
        a = clamp(dots_shown - i)
        if a <= 0:
            break
        cr.set_source_rgba(*rgbf(ahead), a)
        cr.arc(x, y, logo.DOT * (0.6 + 0.4 * a), 0, 2 * math.pi)
        cr.fill()
    if ring > 0:
        cr.set_source_rgb(*rgbf(ground))
        cr.arc(logo.RX, logo.RY, logo.RING_BG * ring, 0, 2 * math.pi)
        cr.fill()
        cr.set_source_rgb(*rgbf(line))
        cr.set_line_width(logo.RING_W * ring)
        cr.arc(logo.RX, logo.RY, logo.RING_R * ring, 0, 2 * math.pi)
        cr.stroke()
        cr.arc(logo.RX, logo.RY, logo.RING_DOT * ring, 0, 2 * math.pi)
        cr.fill()
    cr.restore()


# ---- Timeline ----
# Each scene: start/length in seconds (whole bars), the phone's content (a clip and its source keyframes, or a
# function), the words beside it. Source in-points are seconds into the recordings made by record.sh.
def S(name, bars, **kw):
    return dict(name=name, bars=bars, **kw)


SCENES = [
    S('open', 3),
    S('map', 2, clip='map', keys=[(0, 1.5), (4.8, 6.3)], n='01', head='OFFLINE',
      sub='The whole region on your phone. No signal needed.'),
    S('plan', 3, clip='plan', keys=[(0, 0.8), (2.4, 5.6), (4.4, 12.6), (7.2, 18.8)],
      taps=[(0.35, 760, 700, 0.45), (2.86, 330, 1250, 0.0), (6.03, 540, 2190, 0.0)], n='02', head='PLAN',
      sub='Press and hold the map. The route follows the roads.'),
    S('loop', 3, clip='loop', keys=[(0, 5.0), (2.0, 7.6), (3.8, 47.6), (5.0, 49.5), (5.6, 69.8), (7.2, 72.5)],
      taps=[(1.54, 540, 2190, 0.0), (5.42, 540, 560, 0.0)], n='03', head='LOOPS',
      sub='Pick a distance. Get round trips from where you are.'),
    S('ride', 3, clip='ride', keys=[(0, 4.8), (1.8, 7.2), (1.8, 30.6), (7.2, 40.5)],
      taps=[(0.9, 540, 2160, 0.0)], n='04', head='RIDE', sub='The next turn, big and clear.'),
    S('pages', 2, clip='pages', keys=[(0, 1.0), (4.8, 10.2)], n='05', head='MAP·SPEED·CLIMB',
      sub='Swipe between pages.'),
    S('tones', 2, clip='tutorial', keys=[(0, 11.4), (4.8, 16.2)],
      taps=[(0.7, 204, 868, 0.0), (2.4, 540, 868, 0.0), (4.1, 872, 868, 0.0)], n='06', head='LISTEN',
      sub='Each turn has its own tone. No need to look.'),
    S('offroute', 3, clip='offroute', keys=[(0, 4.6), (7.2, 11.8)], n='07', head='OFF ROUTE',
      sub='A low double tone, then the way back.'),
    S('stops', 3, clip='stops', keys=[(0, 1.6), (1.8, 4.8), (4.0, 6.5), (4.6, 11.9), (7.2, 14.5)],
      taps=[(0.15, 540, 2229, 0.0), (1.05, 200, 1086, 0.0), (4.55, 540, 700, 0.0)], n='08', head='STOPS',
      sub='Water, repairs, a place to sleep. Along your route.'),
    S('saver', 3, clip='saver', keys=[(0, 1.4), (7.2, 11.6)],
      taps=[(1.35, 868, 1086, 0.0), (3.45, 540, 2120, 0.0)], n='09', head='SAVER',
      sub='For long days. The screen goes dark, the turns keep coming.'),
    S('share', 2, clip='share', keys=[(0, 3.5), (4.8, 10.3)],
      taps=[(0.5, 539, 1086, 0.0), (3.8, 784, 1316, 0.0)], n='10', head='SHARE',
      sub='Send where you are. You choose who gets it.'),
    S('light', 2, taps=[(0.55, 540, 837, 0.0)], n='11', head='LIGHT', sub='For bright sun. Same app, same layout.'),
    S('end', 2, clip='end', keys=[(0, 1.2), (3.2, 5.4)], after=(3.2, 'dark_r8'),
      taps=[(1.37, 540, 2151, 1.15)], n='12', head='SAVED', sub='Every ride stays on your phone.'),
    S('outro', 4),
]
_t = 0.0
for _s in SCENES:
    _s['t0'] = _t
    _s['t1'] = _t + _s['bars'] * BAR
    _t = _s['t1']
DURATION = _t


def scene_at(t):
    for s in SCENES:
        if s['t0'] <= t < s['t1']:
            return s
    return SCENES[-1]


def src_time(s, t):
    """Film time t -> (seconds in the scene's recording, seconds since the last cut, or None). Keys are
    (scene seconds, source seconds), interpolated linearly; two keys at the same scene time are a cut; the
    last key's speed carries on past it."""
    local = t - s['t0']
    keys = s['keys']
    cut_at = None
    for (a, sa), (b, sb) in zip(keys, keys[1:]):
        if a == b:
            if local >= a:
                cut_at = a
            continue
        if local < b:
            src = sa + (sb - sa) * (max(local, a) - a) / (b - a)
            return src, (None if cut_at is None else local - cut_at)
    (a, sa), (b, sb) = keys[-2], keys[-1]
    rate = (sb - sa) / (b - a) if b > a else 1.0
    return sb + (local - b) * rate, (None if cut_at is None else local - cut_at)


# ---- Layout ----
class Layout:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.portrait = h > w
        if self.portrait:
            self.phone_h = int(h * 0.66)
            self.phone_y = h * 0.625
            self.text_x = w * 0.5
            self.text_y = h * 0.075
            self.head_px = w * 0.105
            self.sub_px = w * 0.034
            self.text_w = w * 0.82
        else:
            self.phone_h = int(h * 0.84)
            self.phone_y = h * 0.5
            self.head_px = h * 0.105
            self.sub_px = h * 0.033
            self.text_w = w * 0.34
            self.text_y = h * 0.36

    def phone_x(self, side):
        if self.portrait:
            return self.w * 0.5
        return self.w * (0.70 if side > 0 else 0.30)

    def text_left(self, side):
        return self.w * (0.09 if side > 0 else 0.57)


def side_of(i):
    return 1 if i % 2 == 1 else -1


# ---- Words ----
def draw_words(cv, L, s, t, side, light=False):
    local = t - s['t0']
    dur = s['t1'] - s['t0']
    out = ease_in(seg(local, dur - 0.45, dur - 0.05))
    alpha = 1 - out
    if alpha <= 0:
        return
    rise_out = -24 * out * (L.h / 1080)
    c1 = LIGHT_TEXT1 if light else TEXT1
    c2 = LIGHT_TEXT2 if light else TEXT2
    sig = LIGHT_SIGNAL if light else MINT
    hp, sp = L.head_px, L.sub_px
    head = s['head']
    lines = wrap(s['sub'], 'barlow_medium', sp, L.text_w)
    tr = 0.06
    if L.portrait:
        x_of = lambda w: L.text_x - w / 2  # noqa: E731
        y = L.text_y
    else:
        x_of = lambda w: L.text_left(side)  # noqa: E731
        y = L.text_y - hp * 0.6
    # The number, with the signal dot.
    k0 = ease_out(seg(local, 0.15, 0.55))
    num_px = sp * 0.8
    nw = text_width(s['n'], 'barlow_semibold', num_px, 0.06) + num_px
    nx = x_of(nw)
    surf, cr = cairo_layer(int(num_px), int(num_px))
    cr.set_source_rgb(*rgbf(sig))
    cr.arc(num_px * 0.3, num_px * 0.5, num_px * 0.22, 0, 2 * math.pi)
    cr.fill()
    cv.over(layer_to_bgra(surf), nx, y + rise_out, alpha * k0)
    cv.text(s['n'], nx + num_px * 0.75, y - num_px * 0.08 + rise_out, 'barlow_semibold', num_px, c2, alpha * k0,
            0.06)
    y += num_px * 2.0
    # The headline: characters arrive one after another.
    hw = text_width(head, 'doto', hp, tr)
    if not L.portrait and hw > L.text_w * 1.25:
        hp = hp * L.text_w * 1.25 / hw
        hw = text_width(head, 'doto', hp, tr)
    reveal = lambda i: ease_out(seg(local, 0.25 + i * 0.045, 0.7 + i * 0.045))  # noqa: E731
    cv.text(head, x_of(hw), y + rise_out, 'doto', hp, c1, alpha, tr, reveal, rise=hp * 0.35)
    y += hp * 1.35
    for j, line in enumerate(lines):
        k = ease_out(seg(local, 0.7 + j * 0.12, 1.2 + j * 0.12))
        lw = text_width(line, 'barlow_medium', sp)
        cv.text(line, x_of(lw), y + (1 - k) * sp * 0.6 + rise_out, 'barlow_medium', sp, c2, alpha * k)
        y += sp * 1.42


# ---- Scenes ----
class Film:
    def __init__(self, raw, w, h):
        self.L = Layout(w, h)
        self.clips = {}
        for name in ('map', 'plan', 'loop', 'ride', 'pages', 'offroute', 'stops', 'saver', 'share', 'end', 'tutorial',
                     'lightride'):
            p = os.path.join(raw, name + '.mp4')
            if os.path.exists(p):
                self.clips[name] = Clip(p)
        self.stills = {k: Still(os.path.join(SHOTS, d, v + '.png')) for k, d, v in
                       (('dark_more', 'dark', 'B7'), ('light_more', 'light', 'B7'), ('light_r2', 'light', 'R2'),
                        ('light_b1', 'light', 'B1'), ('dark_r2', 'dark', 'R2'),
                        ('dark_r8', 'dark', 'R8'))}
        self.route = self._route()
        # The last screen shown per scene (scene changes and cuts blend from it without seeking back).
        self.held = {}

    def _route(self):
        """The Aubière ride's real track, normalised (the opening's line)."""
        import re
        gpx = os.path.expanduser('~/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx')
        try:
            s = open(gpx).read()
            pts = [(float(a), float(b)) for a, b in re.findall(r'lat="([-0-9.]+)"\s+lon="([-0-9.]+)"', s)]
        except OSError:
            pts = []
        if len(pts) < 2:
            pts = [(0, 0), (1, 1)]
        lat0 = sum(p[0] for p in pts) / len(pts)
        xy = [((lon) * math.cos(math.radians(lat0)), -lat) for lat, lon in pts]
        xs, ys = [p[0] for p in xy], [p[1] for p in xy]
        span = max(max(xs) - min(xs), max(ys) - min(ys))
        cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
        return [((x - cx) / span, (y - cy) / span) for x, y in xy]

    def frame(self, t):
        L = self.L
        s = scene_at(t)
        i = SCENES.index(s)
        if s['name'] == 'open':
            return self.opening(t, s)
        if s['name'] == 'outro':
            return self.outro(t, s)
        light = s['name'] == 'light'
        cv = Canvas(L.w, L.h)
        if light:
            self.light_ground(cv, t, s)
        side = side_of(i)
        draw_words(cv, L, s, t, side, light=light and seg(t - s['t0'], 0.2, 0.9) > 0.5)
        self.draw_phone(cv, t, s, i, side)
        if s['name'] == 'end':
            pass
        return cv

    def light_ground(self, cv, t, s):
        """The light scene: the ground opens to the light map's colour from the phone, and closes again."""
        L = self.L
        local, dur = t - s['t0'], s['t1'] - s['t0']
        k_in = ease_in_out(seg(local, 0.15, 0.85))
        k_out = ease_in_out(seg(local, dur - 0.55, dur - 0.05))
        r_max = math.hypot(L.w, L.h)
        cx, cy = L.phone_x(side_of(SCENES.index(s))), L.phone_y
        surf, cr = cairo_layer(L.w, L.h)
        cr.set_source_rgb(*rgbf(LIGHT_GROUND))
        cr.arc(cx, cy, r_max * k_in * (1 - k_out), 0, 2 * math.pi)
        cr.fill()
        cv.over(layer_to_bgra(surf), 0, 0)

    def screen(self, s, t):
        """The phone's screen for scene s at film time t (BGR 1080 x 2400), with its touches."""
        img = self._screen(s, t)
        local = t - s['t0']
        for (at, x, y, hold) in s.get('taps', ()):
            if at <= local <= at + hold + 0.5:
                img = touch(img, x, y, local - at, hold)
        self.held[s['name']] = img
        return img

    def _screen(self, s, t):
        if s['name'] == 'light':
            local = t - s['t0']
            # More in dark, the Appearance row, the switch to Light, then the light ride.
            if local < 0.9:
                return self.stills['dark_more'].at(0)
            if local < 2.6:
                k = ease_in_out(seg(local, 0.9, 1.5))
                return self.circle_wipe(self.stills['dark_more'].at(0), self.stills['light_more'].at(0), k,
                                        (540, 837))
            # Then a ride in Light (the lightride recording).
            k = ease_in_out(seg(local, 2.6, 3.0))
            ride = self.clips.get('lightride')
            b = ride.at(4.0 + (local - 2.6)) if ride is not None else self.stills['light_r2'].at(0)
            if k >= 1:
                return b
            a = self.stills['light_more'].at(0).astype(np.float32)
            return (a * (1 - k) + b.astype(np.float32) * k).astype(np.uint8)
        clip = self.clips.get(s.get('clip'))
        if clip is None:
            return np.zeros((2400, 1080, 3), np.uint8)
        local = t - s['t0']
        after = s.get('after')
        if after and local >= after[0]:
            # The recording ends here: the real screen that follows, from the shots.
            a = ease_in_out(seg(local, after[0], after[0] + 0.3))
            held = self.held.get((s['name'], 'cut'))
            still = self.stills[after[1]].at(0)
            if held is None or a >= 1:
                return still
            return (held.astype(np.float32) * (1 - a) + still.astype(np.float32) * a).astype(np.uint8)
        src, since_cut = src_time(s, t)
        img = clip.at(src)
        key = (s['name'], 'cut')
        if since_cut is None:
            self.held[key] = img
        elif since_cut < 0.3 and key in self.held:
            a = ease_in_out(since_cut / 0.3)
            img = (self.held[key].astype(np.float32) * (1 - a) + img.astype(np.float32) * a).astype(np.uint8)
        return img

    @staticmethod
    def circle_wipe(a, b, k, centre):
        h, w = a.shape[:2]
        r = k * math.hypot(w, h)
        mask = np.zeros((h, w), np.uint8)
        cv2.circle(mask, centre, int(r), 255, -1, cv2.LINE_AA)
        m = mask.astype(np.float32)[..., None] / 255.0
        return (a * (1 - m) + b * m).astype(np.uint8)

    def draw_phone(self, cv, t, s, i, side):
        L = self.L
        local, dur = t - s['t0'], s['t1'] - s['t0']
        prev_side = side_of(i - 1) if i > 1 else side
        # Glide from the last scene's side over the first 0.7 s; the first scene rises from below.
        if i == 1:
            k = ease_out(seg(local, 0.0, 0.9))
            x = L.phone_x(side)
            y = L.phone_y + (1 - k) * L.h * 0.9
            scale = lerp(0.92, 1.0, k)
        else:
            k = ease_in_out(seg(local, 0.0, 0.7))
            x = lerp(L.phone_x(prev_side), L.phone_x(side), k)
            y = L.phone_y
            scale = 1.0 - 0.05 * math.sin(math.pi * k)
        # Screen: the previous scene's last frame crossfades into this one's during the glide.
        scr = self.screen(s, t)
        if i > 1 and local < 0.45:
            ps = SCENES[i - 1]
            if ps['name'] not in ('open',):
                prev = self.held.get(ps['name'])
                if prev is not None:
                    a = ease_in_out(seg(local, 0.05, 0.4))
                    scr = (prev.astype(np.float32) * (1 - a) + scr.astype(np.float32) * a).astype(np.uint8)
        # The last scene before the outro leaves: the phone sinks and fades.
        alpha = 1.0
        if s['name'] == 'end':
            k_out = ease_in(seg(local, dur - 0.8, dur - 0.05))
            y += k_out * L.h * 0.08
            alpha = 1 - k_out
        ph = phone(scr, L.phone_h, light=s['name'] == 'light' and seg(local, 0.2, 0.9) > 0.5)
        place(cv, ph, x, y, scale, alpha)

    def opening(self, t, s):
        """Black; the real ride's line draws itself and folds into the mark; the name."""
        L = self.L
        cv = Canvas(L.w, L.h)
        local, dur = t - s['t0'], s['t1'] - s['t0']
        surf, cr = cairo_layer(L.w, L.h)
        u = min(L.w, L.h)
        cx, cy = L.w / 2, L.h * (0.42 if not L.portrait else 0.44)
        # 1) The route draws (0.4 - 2.6 s), mint, a 6 px line with the rider's dot at its head.
        k = ease_in_out(seg(local, 0.4, 2.6))
        fade = 1 - ease_in(seg(local, 2.4, 3.2))
        if k > 0 and fade > 0:
            pts = self.route
            n = max(2, int(len(pts) * k))
            span = u * 0.62
            cr.set_line_cap(cairo.LINE_CAP_ROUND)
            cr.set_line_join(cairo.LINE_JOIN_ROUND)
            cr.set_source_rgba(*rgbf(MINT), fade)
            cr.set_line_width(u * 0.0065)
            cr.move_to(cx + pts[0][0] * span, cy + pts[0][1] * span)
            for p in pts[1:n]:
                cr.line_to(cx + p[0] * span, cy + p[1] * span)
            cr.stroke()
            hx, hy = cx + pts[n - 1][0] * span, cy + pts[n - 1][1] * span
            cr.set_source_rgba(*rgbf(MINT), 0.18 * fade)
            cr.arc(hx, hy, u * 0.028, 0, 2 * math.pi)
            cr.fill()
            cr.set_source_rgba(*rgbf(MINT), fade)
            cr.arc(hx, hy, u * 0.010, 0, 2 * math.pi)
            cr.fill()
        # 2) The mark rides itself (2.6 - 4.4 s): the solid part from the start, the dots ahead, the ring.
        size = u * 0.30
        ridden = ease_out(seg(local, 2.7, 3.6))
        dots = len(logo.dots) * seg(local, 3.4, 4.2)
        ring = ease_out(seg(local, 4.1, 4.5)) * (1 + 0.15 * math.sin(math.pi * seg(local, 4.1, 4.5)))
        out = ease_in(seg(local, dur - 0.6, dur))
        if ridden > 0:
            draw_mark(cr, cx, cy - u * 0.04, size * (1 - 0.1 * out), ridden, dots, ring)
        cv.over(layer_to_bgra(surf), 0, 0, 1 - out)
        # 3) The name (4.4 s) and one line.
        name_px = u * 0.085
        tw = text_width('TRACKZERO', 'doto', name_px, 0.12)
        reveal = lambda j: ease_out(seg(local, 4.4 + j * 0.05, 4.9 + j * 0.05))  # noqa: E731
        ny = cy + size * 0.62
        cv.text('TRACKZERO', cx - tw / 2, ny, 'doto', name_px, TEXT1, 1 - out, 0.12, reveal, rise=name_px * 0.3)
        sub = 'Cycling navigation. Offline. Private.'
        sp = u * 0.032
        sw = text_width(sub, 'barlow_medium', sp)
        k2 = ease_out(seg(local, 5.2, 5.8))
        cv.text(sub, cx - sw / 2, ny + name_px * 1.45 + (1 - k2) * sp * 0.5, 'barlow_medium', sp, TEXT2,
                k2 * (1 - out))
        return cv

    def outro(self, t, s):
        L = self.L
        cv = Canvas(L.w, L.h)
        local, dur = t - s['t0'], s['t1'] - s['t0']
        u = min(L.w, L.h)
        cx, cy = L.w / 2, L.h * 0.4
        # Two lines, one after the other.
        sp = u * 0.05
        for j, line in enumerate(('No account.', 'No tracking.')):
            k = ease_out(seg(local, 0.3 + j * 0.6, 0.9 + j * 0.6)) * (1 - ease_in(seg(local, 3.4, 3.9)))
            lw = text_width(line, 'barlow_semibold', sp)
            cv.text(line, cx - lw / 2, cy - sp * 0.9 + j * sp * 1.4 + (1 - k) * sp * 0.4, 'barlow_semibold', sp,
                    TEXT1, k)
        # The mark again, complete, then the name; the credit line.
        surf, cr = cairo_layer(L.w, L.h)
        km = seg(local, 4.0, 5.6)
        size = u * 0.22
        draw_mark(cr, cx, cy - u * 0.02, size, ease_out(seg(local, 4.0, 4.8)),
                  len(logo.dots) * seg(local, 4.6, 5.4), ease_out(seg(local, 5.3, 5.7)))
        fade = 1 - ease_in(seg(local, dur - 0.9, dur - 0.1))
        cv.over(layer_to_bgra(surf), 0, 0, fade if km > 0 else 0)
        name_px = u * 0.07
        tw = text_width('TRACKZERO', 'doto', name_px, 0.12)
        reveal = lambda j: ease_out(seg(local, 5.6 + j * 0.05, 6.1 + j * 0.05))  # noqa: E731
        ny = cy + size * 0.62
        cv.text('TRACKZERO', cx - tw / 2, ny, 'doto', name_px, TEXT1, fade, 0.12, reveal, rise=name_px * 0.3)
        credit = 'Map data © OpenStreetMap contributors'
        cp = u * 0.022
        cw = text_width(credit, 'barlow_medium', cp)
        k3 = ease_out(seg(local, 6.6, 7.2))
        cv.text(credit, cx - cw / 2, L.h * 0.9, 'barlow_medium', cp, TEXT2, k3 * fade)
        return cv


# ---- Render ----
def render(raw, out, aspect, t_from, t_to, scale, fps, audio=True):
    w, h = (1920, 1080) if aspect == '16x9' else (1080, 1920)
    w, h = int(w * scale) // 2 * 2, int(h * scale) // 2 * 2
    film = Film(raw, w, h)
    t_to = min(t_to, DURATION)
    tmp_video = out + '.video.mp4'
    ff = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'bgr24', '-s', f'{w}x{h}', '-r',
                           str(fps), '-i', '-', '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-pix_fmt',
                           'yuv420p', '-movflags', '+faststart', tmp_video], stdin=subprocess.PIPE)
    n = int(round((t_to - t_from) * fps))
    for f in range(n):
        t = t_from + f / fps
        ff.stdin.write(film.frame(t).bgr8().tobytes())
        if f % fps == 0:
            print(f'\r{t:6.1f} / {t_to:.1f} s', end='', file=sys.stderr, flush=True)
    ff.stdin.close()
    ff.wait()
    print(file=sys.stderr)
    if not audio:
        os.replace(tmp_video, out)
        return
    wav = out + '.wav'
    sound.write(wav, SCENES, BAR, DURATION, t_from, t_to)
    # Web loudness: -14 LUFS integrated, true peak under -1.5 dBTP.
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', tmp_video, '-i', wav, '-c:v', 'copy', '-af',
                    'loudnorm=I=-14:TP=-1.5:LRA=11', '-ar', '48000', '-c:a', 'aac', '-b:a', '256k', '-shortest',
                    '-movflags', '+faststart', out], check=True)
    os.remove(tmp_video)
    os.remove(wav)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--raw', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--aspect', default='16x9', choices=('16x9', '9x16'))
    ap.add_argument('--from', dest='t_from', type=float, default=0.0)
    ap.add_argument('--to', dest='t_to', type=float, default=1e9)
    ap.add_argument('--scale', type=float, default=1.0)
    ap.add_argument('--fps', type=int, default=30)
    ap.add_argument('--still', type=float, help='write one frame at this time as PNG to --out')
    ap.add_argument('--no-audio', action='store_true')
    a = ap.parse_args()
    if a.still is not None:
        w, h = (1920, 1080) if a.aspect == '16x9' else (1080, 1920)
        film = Film(a.raw, int(w * a.scale) // 2 * 2, int(h * a.scale) // 2 * 2)
        cv2.imwrite(a.out, film.frame(a.still).bgr8())
        return
    render(a.raw, a.out, a.aspect, a.t_from, a.t_to, a.scale, a.fps, not a.no_audio)


if __name__ == '__main__':
    main()
