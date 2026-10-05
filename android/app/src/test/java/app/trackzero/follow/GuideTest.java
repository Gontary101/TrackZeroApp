package app.trackzero.follow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.trackzero.geo.Geo;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GuideTest
{
  private static final double SPEED_MPS = 6;

  /** The track: 1 km due north from the origin. */
  private static final FollowRoute TRACK = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 1000});

  @Test
  public void startFarAwayGetsALegToTheStartThenTheTrackTakesOver()
  {
    final Rider r = Rider.onTrack();
    r.at(500, -300);
    assertEquals(1, r.paths.requests.size());
    final double[] req = r.paths.requests.get(0);
    // From the rider to the track's first point.
    assertEquals(0, Geo.distance(req[2], req[3], TRACK.startLat(), TRACK.startLon()), 1);
    assertEquals(Maneuver.HEAD_TO_START, r.guide.turnState().maneuver);

    // East 500 m to the corner, then north 300 m to the start.
    r.guide.onPath(path(new double[] {500, -300}, new double[] {0, -300}, new double[] {0, 0}));
    final TurnState onLeg = r.guide.turnState();
    assertEquals(Maneuver.TURN_RIGHT, onLeg.maneuver);
    assertEquals("Rue du Coin", onLeg.street);
    assertNotNull(r.guide.legLine());
    // The leg, then the whole track.
    assertEquals(800 + 1000, r.guide.remainingM(), 10);

    r.rideTo(0, -300);
    r.rideTo(0, 100);
    assertNull(r.guide.legLine());
    assertEquals(Maneuver.ARRIVE, r.guide.turnState().maneuver);
    assertFalse(r.guide.offRoute());
  }

  @Test
  public void nearStartNoLeg()
  {
    final Rider r = Rider.onTrack();
    r.at(0, -150);
    assertTrue(r.paths.requests.isEmpty());
    assertEquals(Maneuver.HEAD_TO_START, r.guide.turnState().maneuver);
  }

  @Test
  public void offRouteRecalculatesThenGuidesBackAhead()
  {
    final Rider r = Rider.onTrack();
    r.at(0, 0);
    r.rideTo(0, 300);
    // 100 m east, held long enough to be off route.
    r.rideTo(100, 300);
    r.hold(6);
    assertEquals(1, r.paths.requests.size());
    final TurnState waiting = r.guide.turnState();
    assertTrue(waiting.offRoute);
    assertTrue(waiting.recalculating);

    // Back onto the track at least 200 m further on (its closest point there to the rider).
    final double[] req = r.paths.requests.get(0);
    final double[] end = FollowTestRoutes.point(0, 500);
    assertEquals(0, Geo.distance(req[2], req[3], end[0], end[1]), 15);

    r.guide.onPath(path(new double[] {100, 300}, new double[] {100, 500}, new double[] {0, 500}));
    assertFalse(r.guide.offRoute());
    assertEquals(Maneuver.TURN_LEFT, r.guide.turnState().maneuver);
    assertEquals(300 + 500, r.guide.remainingM(), 15);

    r.rideTo(100, 500);
    assertEquals(Maneuver.JOIN, r.guide.turnState().maneuver);
    r.rideTo(0, 500);
    r.rideTo(0, 560);
    assertNull(r.guide.legLine());
    assertFalse(r.guide.offRoute());
    assertEquals(440, r.guide.remainingM(), 15);
  }

  @Test
  public void backOnTheTrackWhileWaitingCancels()
  {
    final Rider r = Rider.onTrack();
    r.at(0, 0);
    r.rideTo(0, 300);
    r.rideTo(100, 300);
    r.hold(6);
    assertEquals(1, r.paths.requests.size());
    r.rideTo(0, 320);
    assertEquals(1, r.paths.cancels);
    // A late answer is dropped.
    r.guide.onPath(path(new double[] {100, 300}, new double[] {0, 500}));
    assertNull(r.guide.legLine());
  }

  @Test
  public void failuresBackOff()
  {
    final Rider r = Rider.onTrack();
    r.at(500, -300);
    r.guide.onPath(null);
    // The engine's own guidance stands meanwhile.
    assertEquals(Maneuver.HEAD_TO_START, r.guide.turnState().maneuver);
    r.hold(9);
    assertEquals(1, r.paths.requests.size());
    r.hold(2);
    assertEquals(2, r.paths.requests.size());
    r.guide.onPath(null);
    r.hold(15);
    assertEquals(2, r.paths.requests.size());
    r.hold(6);
    assertEquals(3, r.paths.requests.size());
  }

  @Test
  public void rideToPlaceIsReroutedFromWhereTheRiderLeftIt()
  {
    final Paths paths = new Paths();
    final double[] place = FollowTestRoutes.point(0, 1000);
    final Guide g = Guide.forPlace(place[0], place[1], path(new double[] {0, 0}, new double[] {0, 1000}), paths,
                                   new FollowEngine.Events() {});
    final Rider r = new Rider(g, paths);
    r.at(0, 0);
    r.rideTo(0, 300);
    final int lines = g.linesVersion();
    r.rideTo(150, 300);
    r.hold(6);
    assertEquals(1, paths.requests.size());
    assertEquals(0, Geo.distance(paths.requests.get(0)[2], paths.requests.get(0)[3], place[0], place[1]), 1);
    assertTrue(g.turnState().recalculating);

    g.onPath(path(new double[] {150, 300}, new double[] {150, 1000}, new double[] {0, 1000}));
    assertTrue(g.linesVersion() > lines);
    assertFalse(g.offRoute());
    assertEquals(Maneuver.TURN_LEFT, g.turnState().maneuver);
    assertEquals(850, g.remainingM(), 15);
  }

  @Test
  public void rideToPlaceWithoutPathAsksForOneFirst()
  {
    final Paths paths = new Paths();
    final double[] place = FollowTestRoutes.point(0, 1000);
    final Guide g = Guide.forPlace(place[0], place[1], null, paths, new FollowEngine.Events() {});
    assertFalse(g.placed());
    new Rider(g, paths).at(0, 0);
    assertEquals(1, paths.requests.size());
    assertFalse(g.placed());
    g.onPath(path(new double[] {0, 0}, new double[] {0, 1000}));
    assertTrue(g.placed());
    assertEquals(Maneuver.ARRIVE, g.turnState().maneuver);
  }

  @Test
  public void wrongWayOnTheTrackIsPassedThrough()
  {
    final int[] wrongWay = {0};
    final Rider r = Rider.onTrack(new FollowEngine.Events() {
      @Override
      public void onWrongWay()
      {
        wrongWay[0]++;
      }
    });
    r.at(0, 0);
    r.rideTo(0, 500);
    r.rideTo(0, 400); // 100 m back.
    assertTrue(r.guide.turnState().wrongWay);
    assertFalse(r.guide.offRoute());
    assertEquals(1, wrongWay[0]);
    assertEquals(500, r.guide.remainingM(), 10); // As when the rider turned.
    assertTrue("No way back is asked for: the rider is on the track", r.paths.requests.isEmpty());
  }

  @Test
  public void wrongWayOnALegIsPassedThrough()
  {
    final int[] wrongWay = {0};
    final Rider r = Rider.onTrack(new FollowEngine.Events() {
      @Override
      public void onWrongWay()
      {
        wrongWay[0]++;
      }
    });
    r.at(500, -300);
    r.guide.onPath(path(new double[] {500, -300}, new double[] {0, -300}, new double[] {0, 0}));
    r.rideTo(200, -300); // 300 m along the leg...
    r.rideTo(400, -300); // ...and 200 m back along it.
    assertNotNull(r.guide.legLine());
    assertTrue(r.guide.turnState().wrongWay);
    assertEquals(1, wrongWay[0]);
  }

  /** A routed path through corners (metres east / north), turning at each inner corner, named "Rue du Coin". */
  private static RoutedPath path(double[]... corners)
  {
    final FollowRoute line = FollowTestRoutes.polyline(corners);
    final List<RouteManeuver> maneuvers = new ArrayList<>();
    double at = 0;
    for (int i = 1; i < corners.length - 1; i++)
    {
      at += Math.hypot(corners[i][0] - corners[i - 1][0], corners[i][1] - corners[i - 1][1]);
      final double in = Math.atan2(corners[i][0] - corners[i - 1][0], corners[i][1] - corners[i - 1][1]);
      final double out = Math.atan2(corners[i + 1][0] - corners[i][0], corners[i + 1][1] - corners[i][1]);
      final double turn = Geo.normalizeAngle(Math.toDegrees(out - in));
      maneuvers.add(new RouteManeuver(turn > 0 ? Maneuver.TURN_RIGHT : Maneuver.TURN_LEFT, at, 0, "Rue du Coin"));
    }
    maneuvers.add(new RouteManeuver(Maneuver.ARRIVE, line.totalM(), 0, ""));
    return new RoutedPath(line, maneuvers, null);
  }

  private static final class Paths implements Guide.Paths
  {
    final List<double[]> requests = new ArrayList<>();
    int cancels;

    @Override
    public void request(double fromLat, double fromLon, double toLat, double toLon, double bearingDeg)
    {
      requests.add(new double[] {fromLat, fromLon, toLat, toLon});
    }

    @Override
    public void cancel()
    {
      cancels++;
    }
  }

  /** Rides at 6 m/s in straight lines, one fix a second. */
  private static final class Rider
  {
    final Guide guide;
    final Paths paths;
    long ms;
    double distM;
    double east;
    double north;

    Rider(Guide guide, Paths paths)
    {
      this.guide = guide;
      this.paths = paths;
    }

    static Rider onTrack()
    {
      return onTrack(new FollowEngine.Events() {});
    }

    static Rider onTrack(FollowEngine.Events events)
    {
      final Paths paths = new Paths();
      return new Rider(Guide.forTrack(new FollowEngine(TRACK, events), paths, events), paths);
    }

    void at(double e, double n)
    {
      east = e;
      north = n;
      fix(Double.NaN, 0);
    }

    void rideTo(double e, double n)
    {
      final double len = Math.hypot(e - east, n - north);
      final double course = Math.toDegrees(Math.atan2(e - east, n - north));
      final int steps = (int) Math.ceil(len / SPEED_MPS);
      final double e0 = east, n0 = north;
      for (int i = 1; i <= steps; i++)
      {
        east = e0 + (e - e0) * i / steps;
        north = n0 + (n - n0) * i / steps;
        distM += len / steps;
        fix(course, SPEED_MPS);
      }
    }

    void hold(int seconds)
    {
      for (int i = 0; i < seconds; i++)
        fix(Double.NaN, 0);
    }

    private void fix(double course, double speed)
    {
      ms += 1000;
      final double[] p = FollowTestRoutes.point(east, north);
      guide.update(p[0], p[1], course, speed, ms, distM, ms);
    }
  }
}
