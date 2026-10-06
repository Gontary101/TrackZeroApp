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

  /** Bike north 1 km to where it is left, then walk 300 m further north to the place. */
  private static RoutedPath bikeAndWalk()
  {
    return BikeWalkPlanner.join(path(new double[] {0, 0}, new double[] {0, 1000}),
                                path(new double[] {0, 1000}, new double[] {0, 1300}), 0);
  }

  @Test
  public void leavingABikeAndWalkPathBeforeTheBikeIsLeftRidesBackToThatPoint()
  {
    final Paths paths = new Paths();
    final FollowEngine.Events events = new FollowEngine.Events() {};
    final double[] place = FollowTestRoutes.point(0, 1300);
    final Rider r = new Rider(Guide.forPlace(place[0], place[1], bikeAndWalk(), paths, events), paths);
    r.at(0, 0);
    r.rideTo(0, 300);
    r.rideTo(150, 300); // Off to the east.
    r.hold(6);
    assertEquals(1, paths.requests.size());
    assertFalse("By bike", paths.onFoot.get(0));
    final double[] p = FollowTestRoutes.point(0, 1000);
    assertEquals(0, Geo.distance(paths.requests.get(0)[2], paths.requests.get(0)[3], p[0], p[1]), 1);
    // A new ride to P is joined to the same walk.
    r.guide.onPath(path(new double[] {150, 300}, new double[] {150, 1000}, new double[] {0, 1000}));
    final RoutedPath now = r.guide.placePath();
    assertNotNull(now);
    assertTrue(now.bikeAndWalk());
    assertEquals(850, now.walkFromM, 3);
    assertEquals(850 + 300, now.line.totalM(), 3);
  }

  @Test
  public void leavingItAfterTheBikeIsLeftWalksToThePlace()
  {
    final Paths paths = new Paths();
    final FollowEngine.Events events = new FollowEngine.Events() {};
    final double[] place = FollowTestRoutes.point(0, 1300);
    final Rider r = new Rider(Guide.forPlace(place[0], place[1], bikeAndWalk(), paths, events), paths);
    r.at(0, 0);
    r.rideTo(0, 1100); // Past the bike's end: walking.
    r.rideTo(150, 1100);
    r.hold(6);
    assertEquals(1, paths.requests.size());
    assertTrue("On foot", paths.onFoot.get(0));
    assertEquals(0, Geo.distance(paths.requests.get(0)[2], paths.requests.get(0)[3], place[0], place[1]), 1);
    r.guide.onPath(path(new double[] {150, 1100}, new double[] {0, 1300}));
    assertEquals(0, r.guide.placePath().walkFromM, 0); // All on foot from here.
  }

  // ---- Stops (Phase 30) ----

  /** 5 km due north. */
  private static final FollowRoute LONG = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 5000});

  private static final class StopLog implements Guide.StopEvents, FollowEngine.Events
  {
    final List<String> log = new ArrayList<>();
    int offRoutes;

    @Override
    public void onStopAdded(Stop stop)
    {
      log.add("added " + stop.name);
    }

    @Override
    public void onStopFailed(Stop stop)
    {
      log.add("failed " + stop.name);
    }

    @Override
    public void onStopReached(Stop stop)
    {
      log.add("reached " + stop.name);
    }

    @Override
    public void onStopDropped(Stop stop)
    {
      log.add("dropped " + stop.name);
    }

    @Override
    public void onOffRoute()
    {
      offRoutes++;
    }
  }

  private static Rider onLong(StopLog log)
  {
    final Paths paths = new Paths();
    final Guide g = Guide.forTrack(new FollowEngine(LONG, log), paths, log);
    g.setStopEvents(log);
    return new Rider(g, paths);
  }

  private static Stop stop(String name, double east, double north)
  {
    final double[] p = FollowTestRoutes.point(east, north);
    return new Stop(name, Stop.DRINKING_WATER, p[0], p[1]);
  }

  @Test
  public void aStopFarAheadKeepsTheRouteUntilNearThenALegLeadsThereAndBack()
  {
    final StopLog log = new StopLog();
    final Rider r = onLong(log);
    r.at(0, 0);
    r.rideTo(0, 300);
    r.guide.addStop(stop("Fontaine", 120, 4000));
    assertEquals(1, r.paths.requests.size());
    assertTrue(r.guide.checkingStop());
    // Reachable: kept, but 3.7 km ahead the route stays as it is.
    r.guide.onPath(path(new double[] {0, 300}, new double[] {0, 4000}, new double[] {120, 4000}));
    assertEquals(List.of("added Fontaine"), log.log);
    assertNull(r.guide.legLine());
    assertEquals(1, r.guide.stops().size());

    r.rideTo(0, 1900);
    assertEquals(1, r.paths.requests.size());
    r.rideTo(0, 2100);
    // 2 km before it: the leg is asked for from the rider.
    assertEquals(2, r.paths.requests.size());
    r.guide.onPath(path(new double[] {0, 2100}, new double[] {0, 4000}, new double[] {120, 4000}));
    assertNotNull(r.guide.legLine());
    assertEquals(Maneuver.TURN_RIGHT, r.guide.turnState().maneuver);

    r.rideTo(0, 4000);
    final TurnState last = r.guide.turnState();
    assertEquals(Maneuver.STOP, last.maneuver);
    assertEquals("Fontaine", last.street);
    r.rideTo(110, 4000);
    assertEquals(List.of("added Fontaine", "reached Fontaine"), log.log);
    assertNull(r.guide.legLine());
    assertTrue(r.guide.stops().isEmpty());
    // Off the route at the stop: the usual way back, at least 200 m on. The route was quiet on the way.
    r.hold(6);
    assertEquals(3, r.paths.requests.size());
    final double[] req = r.paths.requests.get(2);
    assertTrue(Stops.alongOf(LONG, req[2], req[3], 0, 5000) >= 4150);
    assertEquals(0, log.offRoutes);
  }

  @Test
  public void aStopThatCannotBeReachedChangesNothing()
  {
    final StopLog log = new StopLog();
    final Rider r = onLong(log);
    r.at(0, 0);
    r.rideTo(0, 300);
    final TurnState before = r.guide.turnState();
    r.guide.addStop(stop("Source", 900, 1000));
    r.guide.onPath(null);
    assertEquals(List.of("failed Source"), log.log);
    assertTrue(r.guide.stops().isEmpty());
    assertNull(r.guide.legLine());
    assertEquals(before.maneuver, r.guide.turnState().maneuver);
    // No backoff from it: a rejoin later is asked for as usual.
    r.rideTo(100, 300);
    r.hold(6);
    assertEquals(2, r.paths.requests.size());
  }

  @Test
  public void aNearStopUsesItsCheckPathAsTheLeg()
  {
    final StopLog log = new StopLog();
    final Rider r = onLong(log);
    r.at(0, 0);
    r.rideTo(0, 300);
    r.guide.addStop(stop("Atelier", 100, 1500));
    r.guide.onPath(path(new double[] {0, 300}, new double[] {0, 1500}, new double[] {100, 1500}));
    assertNotNull(r.guide.legLine());
    assertEquals(1, r.paths.requests.size());
    assertEquals(1200 + 100 + 5000 - 1500, r.guide.remainingM(), 20);
  }

  @Test
  public void twoStopsAreKeptInRouteOrder()
  {
    final StopLog log = new StopLog();
    final Rider r = onLong(log);
    r.at(0, 0);
    r.rideTo(0, 300);
    r.guide.addStop(stop("B", 100, 4500));
    r.guide.onPath(path(new double[] {0, 300}, new double[] {0, 4500}, new double[] {100, 4500}));
    r.guide.addStop(stop("A", -100, 3000));
    r.guide.onPath(path(new double[] {0, 300}, new double[] {0, 3000}, new double[] {-100, 3000}));
    assertEquals("A", r.guide.stops().get(0).name);
    assertEquals("B", r.guide.stops().get(1).name);
  }

  @Test
  public void aStopRiddenPastIsDroppedAndOneRemovedEndsItsLeg()
  {
    final StopLog log = new StopLog();
    final Rider r = onLong(log);
    r.at(0, 0);
    r.rideTo(0, 300);
    r.guide.addStop(stop("Atelier", 100, 1500));
    r.guide.onPath(path(new double[] {0, 300}, new double[] {0, 1500}, new double[] {100, 1500}));
    r.rideTo(0, 2100);
    assertEquals("dropped Atelier", log.log.get(log.log.size() - 1));
    assertNull(r.guide.legLine());
    assertTrue(r.guide.stops().isEmpty());

    r.guide.addStop(stop("Fontaine", 100, 2800));
    r.guide.onPath(path(new double[] {0, 2100}, new double[] {0, 2800}, new double[] {100, 2800}));
    assertNotNull(r.guide.legLine());
    r.guide.removeStop(stop("Fontaine", 100, 2800));
    assertNull(r.guide.legLine());
    assertEquals("dropped Fontaine", log.log.get(log.log.size() - 1));
    r.rideTo(0, 2400);
    assertFalse(r.guide.offRoute());
  }

  // ---- Battery saver (Phase 35) ----

  @Test
  public void inTheSaverOffRouteOnlyWarnsAndLeavingItAsksAtOnce()
  {
    final Rider r = Rider.onTrack();
    r.guide.setRerouting(false);
    r.at(0, 0);
    r.rideTo(0, 300);
    r.rideTo(100, 300);
    r.hold(30);
    assertTrue(r.paths.requests.isEmpty());
    assertTrue(r.guide.offRoute());
    r.guide.setRerouting(true);
    assertEquals(1, r.paths.requests.size());
  }

  @Test
  public void turningTheSaverOnDropsARequestInFlight()
  {
    final Rider r = Rider.onTrack();
    r.at(0, 0);
    r.rideTo(0, 300);
    r.rideTo(100, 300);
    r.hold(6);
    assertEquals(1, r.paths.requests.size());
    r.guide.setRerouting(false);
    assertEquals(1, r.paths.cancels);
    r.guide.onPath(path(new double[] {100, 300}, new double[] {0, 500}));
    assertNull(r.guide.legLine());
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
    final List<Boolean> onFoot = new ArrayList<>();
    int cancels;

    @Override
    public void request(double fromLat, double fromLon, double toLat, double toLon, double bearingDeg, boolean onFoot)
    {
      requests.add(new double[] {fromLat, fromLon, toLat, toLon});
      this.onFoot.add(onFoot);
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
