package app.trackzero.routes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.trackzero.base.TzRouter;
import app.trackzero.follow.BikeWalkPlanner;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.Maneuver;
import app.trackzero.follow.RouteManeuver;
import app.trackzero.follow.RoutedPath;
import app.trackzero.geo.Geo;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class LoopPlannerTest
{
  private static final double LAT = 45.77;
  private static final double LON = 3.08;
  private static final double D = 30_000;

  /**
   * A grid city: every leg goes east-west first, then north-south (so roads are longer than straight lines), the land
   * rising 2 m per 100 m north. Optionally an out-and-back to the east, and no road at all to the far north.
   */
  private static final class GridRouter implements LoopPlanner.Router
  {
    int calls;
    boolean outAndBackEast;
    boolean noRoadFarNorth;
    LoopPlanner planner;
    boolean cancelOnFirst;
    /** No map beyond this distance from the start (a loop bigger than the downloaded region). */
    double mapsWithinM = Double.MAX_VALUE;

    @Override
    public void route(double[] lat, double[] lon, Profile profile, BikeWalkPlanner.Answer answer)
    {
      calls++;
      if (cancelOnFirst)
      {
        planner.cancel();
        answer.onPath(null, TzRouter.Reason.CANCELLED);
        return;
      }
      for (int i = 0; i < lat.length; i++)
        if (Geo.distance(LAT, LON, lat[i], lon[i]) > mapsWithinM)
        {
          answer.onPath(null, TzRouter.Reason.NO_MAP);
          return;
        }
      final double bearing = Geo.bearing(lat[0], lon[0], lat[1], lon[1]);
      if (noRoadFarNorth && Math.abs(Geo.normalizeAngle(bearing)) < 40
          && Geo.distance(lat[0], lon[0], lat[1], lon[1]) > 7000)
      {
        answer.onPath(null, TzRouter.Reason.NO_END);
        return;
      }
      final List<double[]> pts = new ArrayList<>();
      if (outAndBackEast && bearing > 60 && bearing < 120)
      {
        // Out along one road and back the same way.
        pts.add(new double[] {lat[0], lon[0]});
        pts.add(new double[] {lat[1], lon[1]});
        pts.add(new double[] {lat[0], lon[0]});
      }
      else
      {
        pts.add(new double[] {lat[0], lon[0]});
        for (int i = 1; i < lat.length; i++)
        {
          pts.add(new double[] {lat[i - 1], lon[i]}); // East-west first...
          pts.add(new double[] {lat[i], lon[i]});     // ...then north-south.
        }
      }
      final List<double[]> dense = densify(pts);
      final double[] la = new double[dense.size()], lo = new double[dense.size()], ele = new double[dense.size()];
      for (int i = 0; i < la.length; i++)
      {
        la[i] = dense.get(i)[0];
        lo[i] = dense.get(i)[1];
        ele[i] = 400 + (la[i] - LAT) * Geo.M_PER_DEG * 0.02;
      }
      final FollowRoute line = new FollowRoute(la, lo);
      final List<RouteManeuver> arrive = List.of(new RouteManeuver(Maneuver.ARRIVE, line.totalM(), 0, ""));
      answer.onPath(new RoutedPath(line, arrive, ele), TzRouter.Reason.OK);
    }

    /** A point every 50 m at most, dropping repeated points. */
    private static List<double[]> densify(List<double[]> pts)
    {
      final List<double[]> out = new ArrayList<>();
      for (int i = 0; i < pts.size(); i++)
      {
        final double[] p = pts.get(i);
        if (i == 0)
        {
          out.add(p);
          continue;
        }
        final double[] q = out.get(out.size() - 1);
        final double d = Geo.distance(q[0], q[1], p[0], p[1]);
        if (d < 1)
          continue;
        final int steps = (int) Math.ceil(d / 50);
        for (int s = 1; s <= steps; s++)
          out.add(new double[] {q[0] + (p[0] - q[0]) * s / steps, q[1] + (p[1] - q[1]) * s / steps});
      }
      return out;
    }
  }

  private static final class Result implements LoopPlanner.Listener
  {
    List<LoopPlanner.Loop> loops;
    boolean mapsMissing;
    int lastDone;
    int of;

    @Override
    public void onProgress(int done, int of)
    {
      lastDone = done;
      this.of = of;
    }

    @Override
    public void onDone(List<LoopPlanner.Loop> loops, boolean mapsMissing)
    {
      this.loops = loops;
      this.mapsMissing = mapsMissing;
    }
  }

  private static Result plan(GridRouter router, double viaLat, double viaLon)
  {
    final LoopPlanner planner = new LoopPlanner(router);
    router.planner = planner;
    final Result r = new Result();
    planner.plan(LAT, LON, D, LoopPlanner.Climb.ROLLING.mPerKm, Profile.GRAVEL, viaLat, viaLon, 0, r);
    return r;
  }

  @Test
  public void threeLoopsThatCloseAndFitTheSize()
  {
    final Result r = plan(new GridRouter(), Double.NaN, Double.NaN);
    assertEquals(3, r.loops.size());
    for (LoopPlanner.Loop l : r.loops)
    {
      final FollowRoute line = l.path.line;
      assertEquals(0, Geo.distance(line.startLat(), line.startLon(), LAT, LON), 1);
      final int last = line.size() - 1;
      assertEquals(0, Geo.distance(line.lats()[last], line.lons()[last], LAT, LON), 1);
      assertTrue("length " + l.lengthM(), Math.abs(l.lengthM() - D) <= 0.3 * D);
      assertTrue(l.retrace <= 0.25);
    }
    assertEquals(8, r.lastDone);
    assertEquals(8, r.of);
  }

  @Test
  public void theThreeHeadDifferentWays()
  {
    final List<LoopPlanner.Loop> loops = plan(new GridRouter(), Double.NaN, Double.NaN).loops;
    for (int i = 0; i < loops.size(); i++)
      for (int j = i + 1; j < loops.size(); j++)
        assertTrue(Math.abs(Geo.normalizeAngle(loops.get(i).directionDeg - loops.get(j).directionDeg)) >= 90);
  }

  @Test
  public void anOutAndBackIsRejected()
  {
    final GridRouter router = new GridRouter();
    router.outAndBackEast = true;
    final List<LoopPlanner.Loop> loops = plan(router, Double.NaN, Double.NaN).loops;
    assertFalse(loops.isEmpty());
    for (LoopPlanner.Loop l : loops)
      assertTrue("retrace " + l.retrace, l.retrace <= 0.25);
  }

  @Test
  public void retraceOfAnOutAndBackIsAboutHalf()
  {
    final double[] far = LoopPlanner.offset(LAT, LON, 90, 5000);
    final FollowRoute line = new FollowRoute(new double[] {LAT, far[0], LAT}, new double[] {LON, far[1], LON});
    assertEquals(0.5, LoopPlanner.retrace(line), 0.05);
  }

  @Test
  public void theBudgetIsEightTriesAndFourRetries()
  {
    final GridRouter router = new GridRouter();
    router.noRoadFarNorth = true; // Every northern candidate moves closer once: retries.
    plan(router, Double.NaN, Double.NaN);
    assertTrue("calls " + router.calls, router.calls <= 8 + 4);
  }

  @Test
  public void cancelStopsEverything()
  {
    final GridRouter router = new GridRouter();
    router.cancelOnFirst = true;
    final Result r = plan(router, Double.NaN, Double.NaN);
    assertEquals(1, router.calls);
    assertNull(r.loops);
  }

  @Test
  public void everyLoopPassesTheViaPoint()
  {
    final double[] via = LoopPlanner.offset(LAT, LON, 30, 4000);
    final List<LoopPlanner.Loop> loops = plan(new GridRouter(), via[0], via[1]).loops;
    assertFalse(loops.isEmpty());
    for (LoopPlanner.Loop l : loops)
    {
      double nearest = Double.MAX_VALUE;
      final FollowRoute line = l.path.line;
      for (int i = 0; i < line.size(); i++)
        nearest = Math.min(nearest, Geo.distance(line.lats()[i], line.lons()[i], via[0], via[1]));
      assertTrue("nearest " + nearest, nearest < 1);
    }
  }

  @Test
  public void aFourHundredKilometreLoopFitsItsSize()
  {
    // A size typed in L1 (beyond the chips): the same planner, scaled.
    final GridRouter router = new GridRouter();
    final LoopPlanner planner = new LoopPlanner(router);
    router.planner = planner;
    final Result r = new Result();
    final double d = 400_000;
    planner.plan(LAT, LON, d, 4000 / 400.0, Profile.ROAD, Double.NaN, Double.NaN, 0, r);
    assertFalse(r.loops.isEmpty());
    for (LoopPlanner.Loop l : r.loops)
      assertTrue("length " + l.lengthM(), Math.abs(l.lengthM() - d) <= 0.3 * d);
  }

  @Test
  public void aLoopBeyondTheMapsSaysSo()
  {
    final GridRouter router = new GridRouter();
    router.mapsWithinM = 20_000;
    final LoopPlanner planner = new LoopPlanner(router);
    router.planner = planner;
    final Result r = new Result();
    planner.plan(LAT, LON, 400_000, Double.NaN, Profile.ROAD, Double.NaN, Double.NaN, 0, r);
    assertTrue(r.loops.isEmpty());
    assertTrue(r.mapsMissing);
    // Within the maps, nothing is missing.
    final Result ok = plan(new GridRouter(), Double.NaN, Double.NaN);
    assertFalse(ok.mapsMissing);
  }

  @Test
  public void directionNames()
  {
    assertEquals(0, LoopPlanner.directionIndex(10));
    assertEquals(1, LoopPlanner.directionIndex(45));
    assertEquals(2, LoopPlanner.directionIndex(100));
    assertEquals(7, LoopPlanner.directionIndex(330));
    assertEquals(0, LoopPlanner.directionIndex(-10));
  }
}
