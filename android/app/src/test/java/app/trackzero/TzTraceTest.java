package app.trackzero;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class TzTraceTest
{
  @Before
  public void setUp()
  {
    TzTrace.clear();
  }

  @Test
  public void keepsEventsInOrder()
  {
    TzTrace.put(10, "map", "followRider");
    TzTrace.put(20, "line", "show");
    final String[] lines = TzTrace.lines();
    assertEquals(2, lines.length);
    assertEquals("10\tmap\tfollowRider", lines[0]);
    assertEquals("20\tline\tshow", lines[1]);
  }

  @Test
  public void keepsOnlyTheLastEvents()
  {
    final int extra = 25;
    for (int i = 0; i < TzTrace.SIZE + extra; i++)
      TzTrace.put(i, "t", "e" + i);
    final String[] lines = TzTrace.lines();
    assertEquals(TzTrace.SIZE, lines.length);
    assertEquals(extra + "\tt\te" + extra, lines[0]);
    final int last = TzTrace.SIZE + extra - 1;
    assertEquals(last + "\tt\te" + last, lines[lines.length - 1]);
  }

  @Test
  public void coordinatesHaveFiveDecimalsWhateverTheLocale()
  {
    assertEquals("45.76070,3.11239", TzTrace.ll(45.760695, 3.112394));
  }
}
