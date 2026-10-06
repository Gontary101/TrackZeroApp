package app.trackzero.routes;

import java.util.Arrays;

import app.trackzero.TzConfig;

import org.junit.Test;

import static org.junit.Assert.*;

public class RouteStatsTest
{
  @Test
  public void ascentAndDescent()
  {
    // Up 100 m, down 60 m, in 10 m steps (smoothing keeps a plateau's level).
    final double[] ele = new double[41];
    for (int i = 0; i <= 10; i++)
      ele[i] = 400 + 10 * i;
    for (int i = 11; i <= 20; i++)
      ele[i] = 500;
    for (int i = 21; i <= 26; i++)
      ele[i] = 500 - 10 * (i - 20);
    for (int i = 27; i <= 40; i++)
      ele[i] = 440;
    final double[] upDown = RouteStats.ascentDescent(ele);
    org.junit.Assert.assertNotNull(upDown);
    // The same smoothing as the gain: the edges of a short climb are rounded off a little.
    assertEquals(100, upDown[0], 15);
    assertEquals(60, upDown[1], 15);
    org.junit.Assert.assertTrue(upDown[0] > upDown[1]);
    org.junit.Assert.assertNull(RouteStats.ascentDescent(new double[] {Double.NaN, Double.NaN}));
  }

  @Test
  public void measuresParisToLondon()
  {
    double[] lat = {48.8566, 51.5074}, lon = {2.3522, -0.1278};
    RouteStats stats = RouteStats.compute(lat, lon, new double[] {Double.NaN, Double.NaN});
    assertEquals(343560, stats.distanceM, 343560 * 0.005);
    assertArrayEquals(new double[] {0, stats.distanceM}, RouteStats.cumulativeDistance(lat, lon), 0);
    assertEquals(RouteStats.Kind.POINT_TO_POINT, stats.kind);
  }

  @Test
  public void countsGainUsingTheExactY3Algorithm()
  {
    assertEquals(0, RouteStats.gain(new double[] {0, 2, 0, 2, 0, 2}, 3), 0);
    // Each drop resets the reference: these three rises contribute 10 m each.
    assertEquals(30, RouteStats.gain(new double[] {0, 10, 5, 15, 10, 20}, 3), 0);
    assertEquals(20, RouteStats.gain(new double[] {0, 10, 5, 15}, 3), 0);
    assertEquals(0, RouteStats.gain(new double[] {0, 3}, 3), 0);
    assertEquals(4, RouteStats.gain(new double[] {0, 3, 4}, 3), 0);
    assertEquals(0, RouteStats.gain(new double[] {10, 5, 0}, 3), 0);
  }

  @Test
  public void interpolatesGapsBeforeAveraging()
  {
    double[] ele = {Double.NaN, 0, Double.NaN, 10, 20, Double.NaN};
    double[] original = ele.clone();
    assertArrayEquals(new double[] {5.0 / 3, 3.75, 7, 11, 13.75, 50.0 / 3},
                      RouteStats.smoothedElevation(ele), 1e-10);
    assertArrayEquals(original, ele, 0);
  }

  @Test
  public void requiresAtLeastHalfOfThePointsToHaveElevation()
  {
    assertNull(RouteStats.smoothedElevation(new double[0]));
    assertNull(RouteStats.smoothedElevation(new double[] {Double.NaN, Double.NaN}));
    assertNull(RouteStats.smoothedElevation(new double[] {10, Double.NaN, Double.NaN}));
    assertArrayEquals(new double[] {10, 10},
                      RouteStats.smoothedElevation(new double[] {10, Double.NaN}), 0);
    assertArrayEquals(new double[] {5}, RouteStats.smoothedElevation(new double[] {5}), 0);
  }

  @Test
  public void derivesGainAndHighFromSmoothedElevation()
  {
    double[] lat = {45, 45.01, 45.02, 45.03, 45.04, 45.05};
    double[] lon = {2, 2, 2, 2, 2, 2};
    double[] ele = {0, 10, 20, 30, 40, 50};
    RouteStats stats = RouteStats.compute(lat, lon, ele);
    assertTrue(stats.hasElevation);
    assertEquals(40, stats.highM, 0);
    assertEquals(RouteStats.gain(RouteStats.smoothedElevation(ele), TzConfig.ELE_HYSTERESIS_M),
                 stats.gainM, 0);
  }

  @Test
  public void classifiesCircleAsLoop()
  {
    int n = 101;
    double[] lat = new double[n], lon = new double[n];
    double scale = Math.cos(Math.toRadians(45)) * 111320;
    for (int i = 0; i < n; i++)
    {
      double angle = 2 * Math.PI * i / (n - 1);
      lat[i] = 45 + 1000 * Math.sin(angle) / 110540;
      lon[i] = 2 + 1000 * Math.cos(angle) / scale;
    }
    assertEquals(RouteStats.Kind.LOOP, RouteStats.classify(lat, lon));
  }

  @Test
  public void classifiesJitteredReturnAsOutAndBack()
  {
    int n = 121;
    double[] lat = new double[n], lon = new double[n];
    double scale = Math.cos(Math.toRadians(45)) * 111320;
    for (int i = 0; i < n; i++)
    {
      double north = (i <= 60 ? i : 120 - i) * 50;
      lat[i] = 45 + north / 110540;
      lon[i] = 2 + (i <= 60 ? 0 : 5 * Math.sin(i)) / scale;
    }
    assertEquals(RouteStats.Kind.OUT_AND_BACK, RouteStats.classify(lat, lon));
  }

  @Test
  public void classifiesFiveKilometreLineAsPointToPoint()
  {
    assertEquals(RouteStats.Kind.POINT_TO_POINT,
                 RouteStats.classify(new double[] {45, 45 + 5000.0 / 110540},
                                     new double[] {2, 2}));
  }

  @Test
  public void handlesCoincidentPoints()
  {
    double[] lat = new double[10], lon = new double[10], ele = new double[10];
    Arrays.fill(lat, 45);
    Arrays.fill(lon, 2);
    RouteStats stats = RouteStats.compute(lat, lon, ele);
    assertEquals(0, stats.distanceM, 0);
    assertEquals(0, stats.gainM, 0);
    assertEquals(RouteStats.Kind.OUT_AND_BACK, stats.kind);
  }

  @Test
  public void rejectsMismatchedOrTooShortInputs()
  {
    assertThrows(IllegalArgumentException.class,
                 () -> RouteStats.compute(new double[1], new double[1], new double[1]));
    assertThrows(IllegalArgumentException.class,
                 () -> RouteStats.compute(new double[2], new double[3], new double[2]));
    assertThrows(IllegalArgumentException.class,
                 () -> RouteStats.compute(new double[2], new double[2], new double[3]));
    assertThrows(IllegalArgumentException.class,
                 () -> RouteStats.cumulativeDistance(new double[2], new double[3]));
  }
}
