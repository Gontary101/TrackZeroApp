package app.trackzero.follow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class StopsTest
{
  /** 3 km north, then back south 3 km 40 m to the east (an out-and-back on two roads). */
  private static final FollowRoute OUT_AND_BACK =
      FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 3000}, new double[] {40, 3000},
                                new double[] {40, 0});

  private static Stop at(String name, double east, double north)
  {
    final double[] p = FollowTestRoutes.point(east, north);
    return new Stop(name, Stop.DRINKING_WATER, p[0], p[1]);
  }

  @Test
  public void placedOnTheRouteWithinOneKilometreNearestAheadFirst()
  {
    final FollowRoute line = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 10_000});
    final List<Stops.Row> rows = Stops.along(line, List.of(at("far", 2000, 5000), at("b", 300, 6000),
                                                           at("a", -20, 2000), at("behind", 0, 500)),
                                             1000, 41_000);
    assertEquals(2, rows.size());
    assertEquals("a", rows.get(0).stop.name);
    assertEquals(1000, rows.get(0).distanceM, 5);
    assertTrue(rows.get(0).onRoute());
    assertEquals("b", rows.get(1).stop.name);
    assertEquals(5000, rows.get(1).distanceM, 5);
    assertEquals(300, rows.get(1).offRouteM, 5);
  }

  @Test
  public void onAnOutAndBackTheLegStillAheadCounts()
  {
    // Next to both legs; the rider is already on the way back.
    final List<Stops.Row> rows = Stops.along(OUT_AND_BACK, List.of(at("tap", 20, 1000)), 4000, 46_040);
    assertEquals(1, rows.size());
    assertEquals(3040 + 2000, rows.get(0).alongM, 10);
  }

  @Test
  public void aFreeRideListsWithinFiveKilometresByStraightDistance()
  {
    final double[] me = FollowTestRoutes.point(0, 0);
    final List<Stops.Row> rows =
        Stops.near(me[0], me[1], List.of(at("x", 4000, 0), at("y", 0, 1000), at("z", 0, 6000)));
    assertEquals(2, rows.size());
    assertEquals("y", rows.get(0).stop.name);
    assertEquals(4000, rows.get(1).distanceM, 5);
  }

  @Test
  public void detourIsThereAndBackTimesOnePointThree()
  {
    assertEquals(310, Stops.detourM(120), 0);
    assertEquals(0, Stops.detourM(0), 0);
  }
}
