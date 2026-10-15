package app.trackzero.routes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LoopChoiceTest
{
  @Test
  public void distancesInRange()
  {
    assertEquals(400, LoopChoice.distance("400", false), 0);
    assertEquals(12.5, LoopChoice.distance(" 12,5 ", false), 1e-9);
    assertEquals(1000, LoopChoice.distance("1000", false), 0);
    assertNull(LoopChoice.distance("1001", false));
    assertNull(LoopChoice.distance("1.5", false));
    assertNull(LoopChoice.distance("150", true));
    assertEquals(42, LoopChoice.distance("42", true), 0);
    assertNull(LoopChoice.distance("", false));
    assertNull(LoopChoice.distance("abc", false));
    assertNull(LoopChoice.distance("NaN", false));
  }

  @Test
  public void climbs()
  {
    assertEquals(0, LoopChoice.climb("0"), 0);
    assertEquals(4500, LoopChoice.climb("4 500"), 0);
    assertNull(LoopChoice.climb("-10"));
    assertNull(LoopChoice.climb("40000"));
  }

  @Test
  public void perKmAndLabels()
  {
    assertEquals(10, LoopChoice.perKm(4000, 400_000), 1e-9);
    assertEquals(0, LoopChoice.perKm(100, 0), 0);
    assertEquals("400", LoopChoice.label(400));
    assertEquals("12.5", LoopChoice.label(12.5));
  }
}
