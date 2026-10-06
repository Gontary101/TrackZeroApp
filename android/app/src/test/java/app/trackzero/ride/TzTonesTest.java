package app.trackzero.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TzTonesTest
{
  @Test
  public void eachPatternLastsWhatItsTableSays()
  {
    assertEquals(110 + 40 + 150, TonePattern.RIGHT.durationMs());
    assertEquals(140, TonePattern.STRAIGHT.durationMs());
    assertEquals(180 + 90 + 180, TonePattern.OFF_ROUTE.durationMs());
    assertEquals(200 + 40 + 200 + 40 + 260, TonePattern.GPS_LOST.durationMs());
    for (TonePattern p : TonePattern.values())
      assertEquals(p.name(), TonePattern.samples(p.durationMs()), p.build().length, 2);
  }

  @Test
  public void samplesStayInRangeAndStartAndEndSilent()
  {
    for (TonePattern p : TonePattern.values())
    {
      final short[] s = p.build();
      int peak = 0;
      for (short v : s)
        peak = Math.max(peak, Math.abs(v));
      assertTrue(p.name() + " peak " + peak, peak <= Short.MAX_VALUE && peak > Short.MAX_VALUE / 2);
      assertEquals(0, s[0]);
      assertEquals(0, s[s.length - 1]);
    }
  }

  @Test
  public void notesAreSeparatedBySilence()
  {
    final short[] s = TonePattern.RIGHT.build();
    // Inside the 40 ms gap after the first 110 ms note.
    final int mid = TonePattern.samples(110 + 20);
    assertEquals(0, s[mid]);
  }

  @Test
  public void fadesHaveNoClick()
  {
    // The first millisecond of a note stays far below full level.
    final short[] s = TonePattern.STRAIGHT.build();
    for (int i = 0; i < TonePattern.samples(1); i++)
      assertTrue(Math.abs(s[i]) < Short.MAX_VALUE / 8);
  }
}
