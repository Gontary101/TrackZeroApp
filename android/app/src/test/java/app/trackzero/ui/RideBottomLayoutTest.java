package app.trackzero.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RideBottomLayoutTest
{
  @Test
  public void everythingStacksOnTheHandle()
  {
    // Pixel 7 at 2.625 px/dp: gesture strip 126, handle 34dp = 89, 4dp = 11, 12dp = 32, 16dp = 42.
    final RideBottomLayout l = new RideBottomLayout(126, 89, 11, 32, 42);
    assertEquals(215, l.peek);
    assertEquals(226, l.islandMargin);
    assertEquals(247, l.resumeMargin);
    assertEquals(215, l.pagePadding);
    assertEquals(168, l.controlsPadding);
    // The island never reaches the handle: 4dp between them.
    assertEquals(11, l.islandMargin - l.peek);
  }

  @Test
  public void atTheBottomEdge()
  {
    final RideBottomLayout l = new RideBottomLayout(0, 89, 11, 32, 42);
    assertEquals(89, l.peek);
    assertEquals(100, l.islandMargin);
    assertEquals(42, l.controlsPadding);
  }
}
