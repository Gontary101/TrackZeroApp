package app.trackzero.ride;

import androidx.annotation.NonNull;

/**
 * The tones (FEAT-008, Phase 32; Tokens v2 › Tones): sine notes, 40 ms apart, built as 16-bit mono PCM at 44.1 kHz with
 * 12 ms fades, so no audio file is shipped. Rising is right, falling is left; warnings are low, with 30 % of the third
 * harmonic so a small phone speaker still sounds them. Pure.
 */
public enum TonePattern
{
  RIGHT(false, 40, 660, 110, 880, 150),
  LEFT(false, 40, 880, 110, 660, 150),
  STRAIGHT(false, 40, 760, 140),
  UTURN(false, 40, 880, 110, 660, 110, 880, 150),
  ROUNDABOUT(false, 40, 760, 90, 760, 90),
  ARRIVE(false, 40, 660, 110, 880, 110, 1100, 180),
  OFF_ROUTE(true, 90, 330, 180, 330, 180),
  WRONG_WAY(true, 40, 330, 110, 330, 110, 330, 110),
  GPS_LOST(true, 40, 520, 200, 440, 200, 350, 260),
  GPS_BACK(true, 40, 350, 90, 520, 130);

  public static final int RATE = 44_100;
  static final int FADE_MS = 12;
  /** Peak level of the loudest sample: a little headroom under 16-bit full scale. */
  static final double PEAK = 0.8;
  static final double HARMONIC = 0.3;

  /** Low warnings get the third harmonic. */
  final boolean warning;
  final int gapMs;
  /** Hz, ms, Hz, ms… */
  final int[] notes;

  TonePattern(boolean warning, int gapMs, int... notes)
  {
    this.warning = warning;
    this.gapMs = gapMs;
    this.notes = notes;
  }

  /** The whole pattern, notes and the gaps between them, milliseconds. */
  public int durationMs()
  {
    int ms = 0;
    for (int i = 1; i < notes.length; i += 2)
      ms += notes[i];
    return ms + gapMs * (notes.length / 2 - 1);
  }

  /** The pattern as samples: each note faded in and out, silence between notes. */
  @NonNull
  public short[] build()
  {
    final short[] out = new short[samples(durationMs())];
    int at = 0;
    for (int i = 0; i < notes.length; i += 2)
    {
      final int n = samples(notes[i + 1]);
      note(out, at, n, notes[i]);
      at += n + (i + 2 < notes.length ? samples(gapMs) : 0);
    }
    return out;
  }

  static int samples(int ms)
  {
    return (int) Math.round(ms * (RATE / 1000.0));
  }

  private void note(@NonNull short[] out, int from, int n, double hz)
  {
    final int fade = samples(FADE_MS);
    final double scale = PEAK * Short.MAX_VALUE / (warning ? 1 + HARMONIC : 1);
    for (int i = 0; i < n && from + i < out.length; i++)
    {
      final double t = (double) i / RATE;
      double v = Math.sin(2 * Math.PI * hz * t);
      if (warning)
        v += HARMONIC * Math.sin(2 * Math.PI * 3 * hz * t);
      // Linear fade in and out: from 0 and back to 0, no click.
      final double env = Math.min(1, Math.min(i, n - 1 - i) / (double) fade);
      out[from + i] = (short) Math.round(v * env * scale);
    }
  }
}
