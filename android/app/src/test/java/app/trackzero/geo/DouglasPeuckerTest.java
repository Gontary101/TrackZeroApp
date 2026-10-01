package app.trackzero.geo;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DouglasPeuckerTest
{
  @Test
  public void collinearLineKeepsOnlyEndpoints()
  {
    assertArrayEquals(new boolean[] {true, false, false, true},
        DouglasPeucker.simplify(new double[] {0, 1, 2, 3}, new double[] {0, 1, 2, 3}, 0.1));
  }

  @Test
  public void lShapeKeepsCorner()
  {
    assertArrayEquals(new boolean[] {true, false, true, false, true},
        DouglasPeucker.simplify(new double[] {0, 5, 10, 10, 10},
            new double[] {0, 0, 0, 5, 10}, 1));
  }

  @Test
  public void tinyZigzagUnderEpsilonIsRemoved()
  {
    assertArrayEquals(new boolean[] {true, false, false, false, true},
        DouglasPeucker.simplify(new double[] {0, 1, 2, 3, 4},
            new double[] {0, 0.1, -0.1, 0.1, 0}, 0.2));
  }

  @Test
  public void pointBudgetKeepsEndpointsInOrder()
  {
    double[] lat = new double[1000];
    double[] lon = new double[1000];
    for (int i = 0; i < lat.length; i++)
    {
      lat[i] = 45 + Math.sin(i * 0.2) * 0.001;
      lon[i] = 3 + i * 0.0001;
    }
    for (int budget : new int[] {2, 20, 200})
    {
      int[] kept = DouglasPeucker.simplifyToMaxPoints(lat, lon, budget);
      assertTrue(kept.length <= budget);
      assertEquals(0, kept[0]);
      assertEquals(lat.length - 1, kept[kept.length - 1]);
      for (int i = 1; i < kept.length; i++)
      {
        assertTrue(kept[i] > kept[i - 1]);
      }
    }
  }

  @Test
  public void inputsWithinBudgetKeepAllPoints()
  {
    assertArrayEquals(new int[] {0, 1, 2}, DouglasPeucker.simplifyToMaxPoints(
        new double[] {45, 45.001, 45.002}, new double[] {3, 3.001, 3.002}, 3));
  }

  @Test
  public void shortInputsKeepAllPoints()
  {
    for (int n = 0; n <= 2; n++)
    {
      double[] lat = new double[n];
      double[] lon = new double[n];
      boolean[] expected = new boolean[n];
      int[] indices = new int[n];
      for (int i = 0; i < n; i++)
      {
        expected[i] = true;
        indices[i] = i;
      }
      assertArrayEquals(expected, DouglasPeucker.simplify(lat, lon, 1));
      assertArrayEquals(indices, DouglasPeucker.simplifyLatLon(lat, lon, 1));
      assertArrayEquals(indices, DouglasPeucker.simplifyToMaxPoints(lat, lon, 2));
    }
  }

  @Test
  public void latLonEpsilonIsInMeters()
  {
    assertArrayEquals(new int[] {0, 1, 2}, DouglasPeucker.simplifyLatLon(
        new double[] {45, 45.001, 45}, new double[] {3, 3.001, 3.002}, 10));
    assertArrayEquals(new int[] {0, 2}, DouglasPeucker.simplifyLatLon(
        new double[] {45, 45.001, 45}, new double[] {3, 3.001, 3.002}, 200));
  }

  @Test
  public void segmentDistanceHandlesClampingAndCoincidentEndpoints()
  {
    assertEquals(5, DouglasPeucker.segDist(3, 4, 0, 0, 0, 0), 1e-9);
    assertEquals(5, DouglasPeucker.segDist(13, 4, 0, 0, 10, 0), 1e-9);
  }
}
