"""TrackZero launch film: the score and the sound design, synthesised here (no samples, no stock music).

The score is in A major at 100 BPM because the app's own turn tones already live in it: 440 (A), 660 (E), 880 (A),
1100 (C#). The tones themselves are the app's TonePattern, note for note, played where the picture shows them.
"""
import math
import wave

import numpy as np

RATE = 48_000

# The app's TonePattern (android/.../ride/TonePattern.java): warning, gap ms, Hz, ms, Hz, ms...
TONES = {
    'RIGHT': (False, 40, (660, 110, 880, 150)),
    'LEFT': (False, 40, (880, 110, 660, 150)),
    'STRAIGHT': (False, 40, (760, 140)),
    'ARRIVE': (False, 40, (660, 110, 880, 110, 1100, 180)),
    'OFF_ROUTE': (True, 90, (330, 180, 330, 180)),
    'GPS_LOST': (True, 40, (520, 200, 440, 200, 350, 260)),
}

# Cues per scene: (seconds into the scene, kind, argument). Times match the recordings' moments (film.md).
CUES = {
    'open': [(0.4, 'rise', 2.2), (4.1, 'tone', 'ARRIVE')],
    'map': [(0.0, 'whoosh', 0.5)],
    'plan': [(0.0, 'whoosh', 0.5), (0.35, 'tick', None), (2.86, 'tick', None), (6.03, 'tick', None)],
    'loop': [(0.0, 'whoosh', 0.5), (1.54, 'tick', None), (2.0, 'spin', 1.8), (5.42, 'tick', None)],
    'ride': [(0.0, 'whoosh', 0.5), (0.9, 'tick', None), (2.25, 'tone', 'RIGHT'), (2.8, 'tone', 'RIGHT')],
    'pages': [(0.0, 'whoosh', 0.5), (0.6, 'swipe', None), (2.9, 'swipe', None)],
    'tones': [(0.0, 'whoosh', 0.5), (0.7, 'tone', 'LEFT'), (2.4, 'tone', 'STRAIGHT'), (4.1, 'tone', 'RIGHT')],
    'offroute': [(0.0, 'whoosh', 0.5), (2.4, 'tone', 'OFF_ROUTE')],
    'stops': [(0.0, 'whoosh', 0.5), (0.15, 'tick', None), (1.05, 'tick', None), (4.55, 'tick', None)],
    'saver': [(0.0, 'whoosh', 0.5), (1.35, 'tick', None), (3.45, 'tick', None), (3.7, 'down', 1.4)],
    'share': [(0.0, 'whoosh', 0.5), (0.5, 'tick', None), (3.8, 'tick', None)],
    'light': [(0.0, 'whoosh', 0.5), (0.55, 'tick', None), (0.9, 'shimmer', 1.2)],
    'end': [(0.0, 'whoosh', 0.5), (1.37, 'hold', 1.15), (2.55, 'tone', 'ARRIVE')],
    'outro': [(5.3, 'tone', 'ARRIVE')],
}

# Chords, two bars each: A, F#m, D, E (root, third, fifth), Hz of the root.
CHORDS = [(110.0, (0, 4, 7)), (92.5, (0, 3, 7)), (73.42, (0, 4, 7)), (82.41, (0, 4, 7))]


def _env(n, a, r):
    e = np.ones(n)
    a, r = min(a, n // 2), min(r, n // 2)
    if a:
        e[:a] = np.linspace(0, 1, a)
    if r:
        e[-r:] = np.linspace(1, 0, r)
    return e


def tone(name):
    warning, gap, notes = TONES[name]
    parts = []
    for i in range(0, len(notes), 2):
        hz, ms = notes[i], notes[i + 1]
        n = int(RATE * ms / 1000)
        t = np.arange(n) / RATE
        v = np.sin(2 * math.pi * hz * t)
        if warning:
            v = (v + 0.3 * np.sin(2 * math.pi * 3 * hz * t)) / 1.3
        f = int(RATE * 0.012)
        env = np.minimum(1, np.minimum(np.arange(n), n - 1 - np.arange(n)) / f)
        parts.append(v * env)
        if i + 2 < len(notes):
            parts.append(np.zeros(int(RATE * gap / 1000)))
    return np.concatenate(parts)


def lowpass(x, hz):
    a = math.exp(-2 * math.pi * hz / RATE)
    y = np.empty_like(x)
    acc = 0.0
    # One pole; vectorised in blocks would be faster, this is fine for a 90 s film.
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def lp_fast(x, hz):
    """One-pole low-pass via scipy if present (same filter as lowpass)."""
    try:
        from scipy.signal import lfilter
        a = math.exp(-2 * math.pi * hz / RATE)
        return lfilter([1 - a], [1, -a], x)
    except ImportError:
        return lowpass(x, hz)


def hp_fast(x, hz):
    return x - lp_fast(x, hz)


def noise(n, seed):
    return np.random.default_rng(seed).standard_normal(n)


def sfx(kind, arg, seed):
    if kind == 'tone':
        return tone(arg) * 0.5
    if kind == 'whoosh':
        n = int(RATE * arg)
        x = hp_fast(lp_fast(noise(n, seed), 2400), 300)
        e = np.sin(np.linspace(0, math.pi, n)) ** 2
        return x * e * 0.06
    if kind == 'swipe':
        n = int(RATE * 0.28)
        x = hp_fast(lp_fast(noise(n, seed), 4000), 900)
        e = np.sin(np.linspace(0, math.pi, n)) ** 3
        return x * e * 0.07
    if kind == 'tick':
        n = int(RATE * 0.06)
        t = np.arange(n) / RATE
        return np.sin(2 * math.pi * 1800 * t) * np.exp(-t * 90) * 0.12
    if kind == 'rise':
        n = int(RATE * arg)
        t = np.arange(n) / RATE
        f = 220 * (2 ** (t / arg))
        ph = 2 * math.pi * np.cumsum(f) / RATE
        return np.sin(ph) * (t / arg) ** 2 * 0.05 + lp_fast(noise(n, seed), 1200) * (t / arg) ** 2 * 0.02
    if kind == 'spin':
        # Loops being found: soft 8th-note pings walking up the A major arpeggio.
        out = np.zeros(int(RATE * arg))
        notes = [440, 554.37, 659.25, 880, 1108.73, 1318.5]
        step = 60 / 100 / 2
        for i in range(int(arg / step)):
            t0 = int(i * step * RATE)
            n = int(0.18 * RATE)
            t = np.arange(n) / RATE
            v = np.sin(2 * math.pi * notes[i % len(notes)] * t) * np.exp(-t * 18) * 0.06
            out[t0:t0 + n] += v[:len(out) - t0]
        return out
    if kind == 'down':
        n = int(RATE * arg)
        t = np.arange(n) / RATE
        f = 440 * (0.25 ** (t / arg))
        ph = 2 * math.pi * np.cumsum(f) / RATE
        return np.sin(ph) * np.exp(-t * 2.5) * 0.08
    if kind == 'shimmer':
        n = int(RATE * arg)
        t = np.arange(n) / RATE
        v = sum(np.sin(2 * math.pi * hz * t) for hz in (1760, 2217.46, 2637.0)) / 3
        x = hp_fast(lp_fast(noise(n, seed), 9000), 3000)
        e = np.sin(np.linspace(0, math.pi, n)) ** 2
        return (v * 0.05 + x * 0.03) * e
    if kind == 'hold':
        # The hold-to-end fill: a quiet rising tone for 1.5 s, like the real hold.
        n = int(RATE * arg)
        t = np.arange(n) / RATE
        f = 330 * (2 ** (t / arg))
        ph = 2 * math.pi * np.cumsum(f) / RATE
        return np.sin(ph) * _env(n, int(0.05 * RATE), int(0.02 * RATE)) * 0.05
    return np.zeros(1)


def score(duration, bar, music_from):
    """Pad all through; pulse and hats from the first app scene; the last bar resolves on A."""
    n = int(RATE * duration) + RATE
    L = np.zeros(n)
    R = np.zeros(n)
    t = np.arange(n) / RATE
    # Pad: two bars per chord, slow swells, two detuned voices per note, panned apart.
    ci = 0
    pos = 0.0
    while pos < duration:
        root, iv = CHORDS[ci % len(CHORDS)]
        if pos >= duration - 2 * bar:
            root, iv = CHORDS[0]
        i0, i1 = int(pos * RATE), min(n, int((pos + 2 * bar + 0.6) * RATE))
        m = i1 - i0
        tt = np.arange(m) / RATE
        env = _env(m, int(0.9 * RATE), int(1.2 * RATE))
        for k, semi in enumerate(iv + (12,)):
            hz = root * 2 * 2 ** (semi / 12)
            for d, side in ((-0.18, L), (0.18, R)):
                v = np.sin(2 * math.pi * (hz + d) * tt) + 0.25 * np.sin(2 * math.pi * 2 * (hz + d) * tt)
                side[i0:i1] += v * env * 0.018
        # A low root.
        v = np.sin(2 * math.pi * root * tt) * env * 0.05
        L[i0:i1] += v
        R[i0:i1] += v
        pos += 2 * bar
        ci += 1
    # Pulse: a soft kick on beats 1 and 3, hats on the 8ths, from music_from to the outro.
    beat = bar / 4
    end = duration - 4 * bar
    k = 0
    while music_from + k * beat / 2 < end:
        at = music_from + k * beat / 2
        i0 = int(at * RATE)
        if k % 4 == 0:
            m = int(0.35 * RATE)
            tt = np.arange(m) / RATE
            f = 45 + 75 * np.exp(-tt * 30)
            v = np.sin(2 * math.pi * np.cumsum(f) / RATE) * np.exp(-tt * 9) * 0.22
            L[i0:i0 + m] += v[:max(0, n - i0)]
            R[i0:i0 + m] += v[:max(0, n - i0)]
        m = int(0.05 * RATE)
        h = hp_fast(noise(m, k), 6000) * np.exp(-np.arange(m) / RATE * 70) * (0.035 if k % 2 else 0.02)
        L[i0:i0 + m] += h * 0.9
        R[i0:i0 + m] += h * 1.1
        k += 1
    return L, R


def write(path, scenes, bar, duration, t_from=0.0, t_to=None):
    music_from = next(s['t0'] for s in scenes if s['name'] == 'map')
    L, R = score(duration, bar, music_from)
    for si, s in enumerate(scenes):
        for j, (at, kind, arg) in enumerate(CUES.get(s['name'], [])):
            x = sfx(kind, arg, si * 100 + j)
            i0 = int((s['t0'] + at) * RATE)
            m = min(len(x), len(L) - i0)
            if m <= 0:
                continue
            pan = 0.5 if kind in ('tone', 'tick', 'hold') else 0.5 + 0.2 * math.sin(si)
            L[i0:i0 + m] += x[:m] * (1 - pan) * 2 * 0.9
            R[i0:i0 + m] += x[:m] * pan * 2 * 0.9
    # Fade the last 1.5 s, cut to the rendered span, limit softly and normalise to -1 dBFS.
    fade = int(1.5 * RATE)
    e = int(duration * RATE)
    for ch in (L, R):
        ch[e - fade:e] *= np.linspace(1, 0, fade)
        ch[e:] = 0
    t_to = duration if t_to is None else t_to
    a, b = int(t_from * RATE), int(t_to * RATE)
    st = np.stack([L[a:b], R[a:b]], axis=1)
    st = np.tanh(st * 1.2) / np.tanh(1.2)
    peak = np.max(np.abs(st)) or 1.0
    st = st / peak * 10 ** (-1 / 20)
    pcm = (st * 32767).astype(np.int16)
    with wave.open(path, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
