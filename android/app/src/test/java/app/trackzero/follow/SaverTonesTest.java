package app.trackzero.follow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import app.trackzero.ride.SaverPolicy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The battery saver's GPS (Phase 35): fixes every 3 s, every second within 600 m of a maneuver. Every "turn now" must
 * still come between 80 m and 20 m before the turn, at 25 and at 40 km/h.
 */
public class SaverTonesTest
{
  /** A zigzag: 20 turns, legs of 1 to 2.9 km. */
  private static FollowRoute zigzag()
  {
    final List<double[]> corners = new ArrayList<>();
    double e = 0, n = 0;
    corners.add(new double[] {e, n});
    for (int i = 0; i < 21; i++)
    {
      final double leg = 1000 + (i % 3) * 950;
      if (i % 2 == 0)
        n += leg;
      else
        e += (i % 4 == 1 ? 1 : -1) * leg;
      corners.add(new double[] {e, n});
    }
    return FollowTestRoutes.polyline(corners.toArray(new double[0][]));
  }

  private static void ride(double kmh)
  {
    final FollowRoute route = zigzag();
    final List<RouteManeuver> maneuvers = ManeuverBuilder.build(route, null);
    final List<Double> missBy = new ArrayList<>();
    final double[] along = {0};
    final FollowEngine engine = new FollowEngine(route, maneuvers, new FollowEngine.Events() {
      @Override
      public void onTurnNow(RouteManeuver m)
      {
        if (m.type != Maneuver.ARRIVE)
          missBy.add(m.at - along[0]);
      }
    });
    final double mps = kmh / 3.6;
    long ms = 0;
    while (along[0] < route.totalM() - 1)
    {
      final double[] ll = {route.latAt(along[0]), route.lonAt(along[0])};
      engine.update(ll[0], ll[1], Double.NaN, mps, ms, along[0], ms);
      final double next = engine.placed() ? engine.turnState().distanceM : Double.NaN;
      final long step = SaverPolicy.choose(true, true, next, false, false, false).intervalMs;
      ms += step;
      along[0] = Math.min(route.totalM(), along[0] + mps * step / 1000.0);
    }
    assertEquals(20, missBy.size());
    for (double d : missBy)
      assertTrue(kmh + " km/h: turn now " + Math.round(d) + " m before", d >= 20 && d <= 80);
  }

  @Test
  public void at25kmh()
  {
    ride(25);
  }

  @Test
  public void at40kmh()
  {
    ride(40);
  }
}
