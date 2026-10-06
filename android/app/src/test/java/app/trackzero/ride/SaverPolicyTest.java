package app.trackzero.ride;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SaverPolicyTest
{
  private static SaverPolicy.Gps gps(boolean saver, boolean guided, double next, boolean warning, boolean screen)
  {
    return SaverPolicy.choose(saver, guided, next, warning, screen, false);
  }

  @Test
  public void normallyEverySecond()
  {
    assertEquals(1000, gps(false, true, 5000, false, true).intervalMs);
    assertEquals(0, gps(false, true, 5000, false, true).maxDelayMs);
    assertEquals(15_000, gps(false, false, Double.NaN, false, false).maxDelayMs);
    // Something may be said: no batching.
    assertEquals(0, SaverPolicy.choose(false, true, 5000, false, false, true).maxDelayMs);
  }

  @Test
  public void inTheSaverByDistanceToTheManeuver()
  {
    assertEquals(3000, gps(true, true, 601, false, true).intervalMs);
    assertEquals(0, gps(true, true, 601, false, true).maxDelayMs);
    assertEquals(9000, gps(true, true, 601, false, false).maxDelayMs);
    assertEquals(1000, gps(true, true, 600, false, false).intervalMs);
    assertEquals(0, gps(true, true, 600, false, false).maxDelayMs);
  }

  @Test
  public void inTheSaverWarningsAndUnknownsGetEverySecond()
  {
    assertEquals(1000, gps(true, true, 5000, true, false).intervalMs);
    assertEquals(1000, gps(true, true, Double.NaN, false, false).intervalMs);
  }

  @Test
  public void aFreeRideInTheSaver()
  {
    assertEquals(3000, gps(true, false, Double.NaN, false, true).intervalMs);
    assertEquals(15_000, gps(true, false, Double.NaN, false, false).maxDelayMs);
  }
}
