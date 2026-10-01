package app.trackzero.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TzTextSizeTest
{
  @Test
  public void followsFontScaleUpToCap()
  {
    // Rendered size = cappedSp * fontScale.
    assertEquals(208f, TzTextSize.cappedSp(1f, 208f) * 1f, 1e-3);
    assertEquals(208f * 1.15f, TzTextSize.cappedSp(1.15f, 208f) * 1.15f, 1e-3);
    assertEquals(208f * 1.3f, TzTextSize.cappedSp(2f, 208f) * 2f, 1e-3);
    assertEquals(208f * 0.85f, TzTextSize.cappedSp(0.85f, 208f) * 0.85f, 1e-3);
  }
}
