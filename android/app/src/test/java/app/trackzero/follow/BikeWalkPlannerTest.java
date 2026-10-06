package app.trackzero.follow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.trackzero.base.TzRouter;
import app.trackzero.routes.Profile;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class BikeWalkPlannerTest
{
  /** The rider at the origin; the place 2 km north, at the top of a footpath. */
  private static final double[] FROM = FollowTestRoutes.point(0, 0);
  private static final double[] PLACE = FollowTestRoutes.point(50, 2000);

  /** The walk: north 1800 m, right 50 m, north 200 m to the place. 2050 m. */
  private static RoutedPath walkPath()
  {
    final FollowRoute line = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 1800},
                                                       new double[] {50, 1800}, new double[] {50, 2000});
    final List<RouteManeuver> m = new ArrayList<>();
    m.add(new RouteManeuver(Maneuver.TURN_RIGHT, 1800, 0, "Chemin"));
    m.add(new RouteManeuver(Maneuver.TURN_LEFT, 1850, 0, "Sentier"));
    m.add(new RouteManeuver(Maneuver.ARRIVE, line.totalM(), 0, ""));
    return new RoutedPath(line, m, null);
  }

  /** A straight bicycle path to anywhere. */
  private static RoutedPath straight(double fromLat, double fromLon, double toLat, double toLon)
  {
    final FollowRoute line = new FollowRoute(new double[] {fromLat, toLat}, new double[] {fromLon, toLon});
    final List<RouteManeuver> m = new ArrayList<>();
    m.add(new RouteManeuver(Maneuver.ARRIVE, line.totalM(), 0, ""));
    return new RoutedPath(line, m, null);
  }

  /** Answers at once; the bike reaches only points up to {@code bikeNorthM} north, unless {@code bikeAll}. */
  private static final class FakeRouter implements BikeWalkPlanner.Router
  {
    final List<String> asked = new ArrayList<>();
    double bikeNorthM = 1700;
    boolean bikeAll;
    boolean walkWorks = true;
    boolean hikeWorks;
    TzRouter.Reason bikeFailure = TzRouter.Reason.NO_WAY;

    @Override
    public void route(double fromLat, double fromLon, double toLat, double toLon, Profile profile, double bearingDeg,
                      int timeoutSec, BikeWalkPlanner.Answer answer)
    {
      asked.add(profile.id + (timeoutSec > 0 ? "/" + timeoutSec + "s" : ""));
      if (profile == Profile.WALK || profile == Profile.HIKE)
      {
        if ((profile == Profile.WALK && walkWorks) || (profile == Profile.HIKE && hikeWorks))
          answer.onPath(walkPath(), TzRouter.Reason.OK);
        else
          answer.onPath(null, TzRouter.Reason.NO_WAY);
        return;
      }
      final double north = (toLat - FROM[0]) * app.trackzero.geo.Geo.M_PER_DEG;
      if (bikeAll || north <= bikeNorthM)
        answer.onPath(straight(fromLat, fromLon, toLat, toLon), TzRouter.Reason.OK);
      else
        answer.onPath(null, bikeFailure);
    }
  }

  private static BikeWalkPlanner.Result plan(FakeRouter router)
  {
    final BikeWalkPlanner.Result[] out = new BikeWalkPlanner.Result[1];
    new BikeWalkPlanner(router).plan(FROM[0], FROM[1], PLACE[0], PLACE[1], Profile.ROAD, Double.NaN,
                                     r -> out[0] = r);
    assertNotNull("answered", out[0]);
    return out[0];
  }

  @Test
  public void aPlaceTheBikeReachesIsUnchanged()
  {
    final FakeRouter router = new FakeRouter();
    router.bikeAll = true;
    final BikeWalkPlanner.Result r = plan(router);
    assertEquals(BikeWalkPlanner.Result.Kind.BIKE, r.kind);
    assertEquals(-1, r.path.walkFromM, 0);
    assertEquals(List.of("road"), router.asked);
  }

  @Test
  public void bikeToTheNearestReachablePointThenWalk()
  {
    final FakeRouter router = new FakeRouter();
    final BikeWalkPlanner.Result r = plan(router);
    assertEquals(BikeWalkPlanner.Result.Kind.BIKE_AND_WALK, r.kind);
    // Tried 50 m and 150 m before the end (too far up), then 400 m: 1650 m along the walk.
    assertEquals(List.of("road", "walk", "road/8s", "road/8s", "road/8s"), router.asked);
    final RoutedPath p = r.path;
    assertTrue(p.bikeAndWalk());
    assertEquals(1650, p.walkFromM, 2); // The bike's straight line to the 1650 m point.
    assertEquals(1650 + 400, p.line.totalM(), 3);
    // Turns: leave the bike at P, then the walk's turns after it, shifted, then arrival.
    final List<RouteManeuver> m = p.maneuvers;
    assertEquals(Maneuver.LEAVE_BIKE, m.get(0).type);
    assertEquals(p.walkFromM, m.get(0).at, 0.01);
    assertEquals(Maneuver.TURN_RIGHT, m.get(1).type);
    assertEquals(p.walkFromM + 150, m.get(1).at, 2);
    assertEquals("Chemin", m.get(1).street);
    assertEquals(Maneuver.TURN_LEFT, m.get(2).type);
    assertEquals(Maneuver.ARRIVE, m.get(3).type);
    assertEquals(p.line.totalM(), m.get(3).at, 0.01);
    assertNull(p.ele);
  }

  @Test
  public void noBikeAccessOffersTheWalk()
  {
    final FakeRouter router = new FakeRouter();
    router.bikeNorthM = -1;
    final BikeWalkPlanner.Result r = plan(router);
    assertEquals(BikeWalkPlanner.Result.Kind.WALK_ONLY, r.kind);
    assertEquals(2050, r.path.line.totalM(), 3);
    // The bike, the walk, then the bike to every candidate before the end (6 km and beyond are skipped).
    assertEquals(List.of("road", "walk", "road/8s", "road/8s", "road/8s", "road/8s"), router.asked);
  }

  @Test
  public void noWayOnFootEitherIsNoRoute()
  {
    final FakeRouter router = new FakeRouter();
    router.walkWorks = false;
    final BikeWalkPlanner.Result r = plan(router);
    assertEquals(BikeWalkPlanner.Result.Kind.NONE, r.kind);
    assertEquals(TzRouter.Reason.NO_WAY, r.reason);
    assertEquals(List.of("road", "walk", "hike"), router.asked);
  }

  @Test
  public void aTrailOnlyAHikeFindsStillWorks()
  {
    final FakeRouter router = new FakeRouter();
    router.walkWorks = false;
    router.hikeWorks = true;
    assertEquals(BikeWalkPlanner.Result.Kind.BIKE_AND_WALK, plan(router).kind);
  }

  @Test
  public void aMissingMapIsPassedThroughUntouched()
  {
    final FakeRouter router = new FakeRouter();
    router.bikeFailure = TzRouter.Reason.NO_MAP;
    final BikeWalkPlanner.Result r = plan(router);
    assertEquals(BikeWalkPlanner.Result.Kind.NONE, r.kind);
    assertEquals(TzRouter.Reason.NO_MAP, r.reason);
    assertEquals(List.of("road"), router.asked);
  }

  @Test
  public void cancelledGivesNoAnswer()
  {
    final FakeRouter router = new FakeRouter();
    final BikeWalkPlanner planner = new BikeWalkPlanner(router);
    planner.cancel();
    final boolean[] answered = {false};
    planner.plan(FROM[0], FROM[1], PLACE[0], PLACE[1], Profile.ROAD, Double.NaN, r -> answered[0] = true);
    assertTrue(!answered[0]);
  }
}
