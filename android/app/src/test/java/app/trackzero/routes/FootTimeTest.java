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

  @Test
  public void hikingTime()
  {
    // 12 km (3 h walking), 600 m up and 500 m down (3 h climbing): 3 + 3 / 2 = 4.5 h.
    assertEquals(Math.round(4.5 * 3600), FootTime.hikeSeconds(12_000, 600, 500));
    // Flat 8 km: 2 h, no climbing.
    assertEquals(2 * 3600, FootTime.hikeSeconds(8000, 0, 0));
    // Steep and short: 2 km (0.5 h), 900 m up (3 h): 3 + 0.25.
    assertEquals(Math.round(3.25 * 3600), FootTime.hikeSeconds(2000, 900, 0));
    // No altitudes: distance only.
    assertEquals(2 * 3600, FootTime.hikeSeconds(8000, Double.NaN, Double.NaN));
  }
}
