package app.trackzero.routes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.trackzero.routes.SurfaceProfile.Surface;
import java.util.List;
import org.junit.Test;

public class SurfaceProfileTest
{
  private static int[] repeat(int code, int n)
  {
    final int[] a = new int[n];
    java.util.Arrays.fill(a, code);
    return a;
  }

  private static int[] concat(int[]... parts)
  {
    int n = 0;
    for (int[] p : parts)
      n += p.length;
    final int[] out = new int[n];
    int k = 0;
    for (int[] p : parts)
      for (int c : p)
        out[k++] = c;
    return out;
  }

  @Test
  public void stepAndSamples()
  {
    assertEquals(50, SurfaceProfile.step(1000), 1e-9);
    assertEquals(50, SurfaceProfile.step(150_000), 1e-9);
    assertEquals(100, SurfaceProfile.step(300_000), 1e-9);
    assertEquals(21, SurfaceProfile.samples(1000, 50));
    assertEquals(22, SurfaceProfile.samples(1010, 50)); // The last one at the end.
    assertEquals(1010, SurfaceProfile.at(21, 1010, 50), 1e-9);
  }

  @Test
  public void runsInRouteOrderCoverTheRoute()
  {
    // 2 km: 1 km paved, 600 m gravel, 400 m rough; 41 samples every 50 m.
    final int[] codes = concat(repeat(1, 20), repeat(3, 12), repeat(4, 9));
    final SurfaceProfile p = SurfaceProfile.of(codes, 2000, 50);
    final List<SurfaceProfile.Run> runs = p.runs;
    assertEquals(3, runs.size());
    assertEquals(Surface.PAVED, runs.get(0).surface);
    assertEquals(Surface.GRAVEL, runs.get(1).surface);
    assertEquals(Surface.ROUGH, runs.get(2).surface);
    assertEquals(0, runs.get(0).fromM, 1e-9);
    assertEquals(975, runs.get(0).toM, 1e-9); // Halfway between the last paved and the first gravel sample.
    assertEquals(runs.get(0).toM, runs.get(1).fromM, 1e-9);
    assertEquals(2000, runs.get(2).toM, 1e-9);
    assertFalse(p.noMap);
    assertFalse(p.someNoMap);
  }

  @Test
  public void percentagesSumToHundred()
  {
    // Thirds: 33.3 % each rounds to 34 + 33 + 33.
    final int[] codes = concat(repeat(1, 10), repeat(3, 10), repeat(0, 10));
    final SurfaceProfile p = SurfaceProfile.of(codes, 1450, 50);
    int sum = 0;
    for (int v : p.percent)
      sum += v;
    assertEquals(100, sum);
    assertEquals(0, p.percent[Surface.POOR.ordinal()]);
    assertEquals(0, p.percent[Surface.ROUGH.ordinal()]);
    assertTrue(p.percent[Surface.NONE.ordinal()] >= 33);
  }

  @Test
  public void shortRunsJoinTheirLongerNeighbourOnTheBar()
  {
    // 10 km of paved with one gravel sample (50 m, 0.5 %) between 5 km of paved and 5 km of rough.
    final int[] codes = concat(repeat(1, 100), repeat(3, 1), repeat(4, 100));
    final SurfaceProfile p = SurfaceProfile.of(codes, 10_000, 50);
    assertEquals(2, p.runs.size());
    assertEquals(Surface.PAVED, p.runs.get(0).surface);
    assertEquals(Surface.ROUGH, p.runs.get(1).surface);
    // The legend still shows it: 0.5 % shows as 1 %, taken from the largest.
    assertEquals(1, p.percent[Surface.GRAVEL.ordinal()]);
    assertEquals(99, p.percent[Surface.PAVED.ordinal()] + p.percent[Surface.ROUGH.ordinal()]);
  }

  @Test
  public void aShortRunBetweenTwoOfTheSameSurfaceDisappears()
  {
    final int[] codes = concat(repeat(1, 100), repeat(4, 1), repeat(1, 100));
    final SurfaceProfile p = SurfaceProfile.of(codes, 10_000, 50);
    assertEquals(1, p.runs.size());
    assertEquals(Surface.PAVED, p.runs.get(0).surface);
    assertEquals(10_000, p.runs.get(0).toM, 1e-9);
  }

  @Test
  public void noDataIsItsOwnClass()
  {
    final int[] codes = concat(repeat(1, 50), repeat(0, 30), repeat(-1, 21));
    final SurfaceProfile p = SurfaceProfile.of(codes, 5000, 50);
    assertEquals(Surface.NONE, p.runs.get(p.runs.size() - 1).surface);
    assertEquals(100 - p.percent[Surface.PAVED.ordinal()], p.percent[Surface.NONE.ordinal()]);
    assertTrue(p.someNoMap);
    assertFalse(p.noMap);
  }

  @Test
  public void noMapAnywhere()
  {
    final SurfaceProfile p = SurfaceProfile.of(repeat(-1, 41), 2000, 50);
    assertTrue(p.noMap);
    assertTrue(p.someNoMap);
  }

  @Test
  public void readAgainOnlyWhenAMapWasMissingAndTheMapsChanged()
  {
    final SurfaceProfile complete = SurfaceProfile.of(repeat(1, 41), 2000, 50);
    assertTrue(complete.upToDate(1, 2));
    final SurfaceProfile partial = SurfaceProfile.of(concat(repeat(1, 20), repeat(-1, 21)), 2000, 50);
    assertTrue(partial.upToDate(5, 5));
    assertFalse(partial.upToDate(5, 6));
  }

  @Test
  public void largestRemainders()
  {
    assertArrayEquals(new int[] {50, 25, 25, 0, 0}, SurfaceProfile.percent(new double[] {2, 1, 1, 0, 0}));
    assertArrayEquals(new int[] {67, 33, 0, 0, 0}, SurfaceProfile.percent(new double[] {2, 1, 0, 0, 0}));
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, SurfaceProfile.percent(new double[] {0, 0, 0, 0, 0}));
    assertArrayEquals(new int[] {99, 0, 1, 0, 0}, SurfaceProfile.percent(new double[] {999, 0, 1, 0, 0}));
  }
}
