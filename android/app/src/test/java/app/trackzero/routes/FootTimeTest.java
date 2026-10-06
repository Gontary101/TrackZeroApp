package app.trackzero.routes;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FootTimeTest
{
  @Test
  public void walkingSpeedPlusClimbing()
  {
    assertEquals(3600, FootTime.walkSeconds(4800, 0));
    assertEquals(3600 + 1800, FootTime.walkSeconds(4800, 300)); // 300 m up: half an hour more.
    assertEquals(3600, FootTime.walkSeconds(4800, Double.NaN)); // No altitudes: distance only.
    assertEquals(0, FootTime.walkSeconds(0, 0));
  }
}
