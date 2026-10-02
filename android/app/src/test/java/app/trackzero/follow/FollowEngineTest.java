package app.trackzero.follow;

import app.trackzero.TzConfig;
import app.trackzero.geo.Geo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FollowEngineTest
{
  @Test
  public void ridingLShapeShowsApproachingTurnThenArrivesOnce()
  {
    Ride ride = new Ride(lShape());
    double corner = ride.engine.maneuvers().get(0).at;
    double previousDistance = Double.POSITIVE_INFINITY;
    boolean sawImminent = false;
    boolean sawArrivalAhead = false;
    int fixes = (int) Math.ceil(ride.route.totalM() / Ride.SPEED_MPS);
    for (int i = 0; i <= fixes; i++)
    {
      ride.onRoute();
      TurnState state = ride.engine.turnState();
      if (ride.engine.along() < corner)
      {
        assertEquals(FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
        assertEquals(Maneuver.TURN_RIGHT, state.maneuver);
        assertEquals(Maneuver.ARRIVE, state.thenManeuver);
        assertTrue(state.distanceM < previousDistance);
        previousDistance = state.distanceM;
        assertEquals(state.distanceM <= TzConfig.IMMINENT_M, state.imminent);
        sawImminent |= state.imminent;
      }
      else
      {
        assertEquals(Maneuver.ARRIVE, state.maneuver);
        if (ride.engine.remainingM() >= FollowEngine.ARRIVE_M)
        {
          assertEquals(FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
          sawArrivalAhead = true;
        }
        else
        {
          assertEquals(FollowEngine.Phase.ARRIVED, ride.engine.phase());
          assertEquals(1, ride.events.arrived);
        }
      }
    }
    assertTrue("The turn becomes imminent before it is passed", sawImminent);
    assertTrue("Arrival is the next maneuver after the corner", sawArrivalAhead);
    ride.onRoute(); // A repeated fix at the end must not emit another arrival.
    assertEquals(FollowEngine.Phase.ARRIVED, ride.engine.phase());
    assertEquals(1, ride.events.arrived);
  }

  @Test
  public void lShapeAnnouncesCornerSoonAndNowExactlyOnce()
  {
    Ride ride = new Ride(lShape());
    ride.toEnd();
    RouteManeuver corner = ride.engine.maneuvers().get(0);
    assertEquals(Collections.singletonList(corner), ride.events.soon);
    assertEquals(Collections.singletonList(corner), ride.events.now);
    double soonDistance = ride.events.soonDistances.get(0);
    assertTrue(soonDistance > 70); // Above the 50 m "now" threshold plus its 20 m buffer.
    assertTrue(soonDistance <= TzConfig.VOICE_FAR_M);
    double nowDistance = ride.events.nowDistances.get(0);
    assertTrue(nowDistance > 0);
    assertTrue(nowDistance <= TzConfig.VOICE_NEAR_M);
  }

  @Test
  public void turnLessThanTwoHundredFiftyMetresAfterPreviousSkipsSoonAnnouncement()
  {
    // Two ordinary corners separated by 100 m, well outside the 20 m merge window.
    FollowRoute route = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 500},
        new double[] {100, 500}, new double[] {100, 1000});
    Ride ride = new Ride(route);
    List<RouteManeuver> maneuvers = ride.engine.maneuvers();
    assertEquals(3, maneuvers.size());
    assertEquals(Maneuver.TURN_RIGHT, maneuvers.get(0).type);
    assertEquals(Maneuver.TURN_LEFT, maneuvers.get(1).type);
    assertTrue(maneuvers.get(1).at - maneuvers.get(0).at < FollowEngine.FAR_MIN_GAP_M);
    ride.toEnd();
    assertEquals(Collections.singletonList(maneuvers.get(0)), ride.events.soon);
    assertEquals(Arrays.asList(maneuvers.get(0), maneuvers.get(1)), ride.events.now);
  }

  @Test
  public void firstFixMidRoutePlacesRiderOnRoute()
  {
    // The engine is built while the rider is already riding: its first fix is 500 m into the route.
    Ride ride = new Ride(FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 1000}));
    assertFalse(ride.engine.placed());
    ride.atOffset(0, 500, 0);
    assertTrue(ride.engine.placed());
    assertEquals(FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
    assertEquals(500, ride.engine.along(), 1);
  }

  @Test
  public void offRouteRequiresFiveSecondsAndUsesRejoinHysteresis()
  {
    Ride ride = new Ride(FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 1000}));
    while (ride.seconds < 50)
    {
      ride.onRoute(); // Fifty seconds at 6 m/s joins the route and covers 300 m.
    }
    assertEquals(FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
    ride.atOffset(60, 300, 0); // First off-line fix starts the timer at zero elapsed seconds.
    assertEquals(FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
    for (int secondsOff = 1; secondsOff < TzConfig.OFF_ROUTE_SECONDS; secondsOff++)
    {
      ride.atOffset(60, 300 + secondsOff * Ride.SPEED_MPS, 0);
      assertEquals("Still within the five-second grace period",
          FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
      assertEquals(0, ride.events.offRoute);
    }
    ride.atOffset(60, 300 + TzConfig.OFF_ROUTE_SECONDS * Ride.SPEED_MPS, 0);
    assertEquals(FollowEngine.Phase.OFF_ROUTE, ride.engine.phase());
    assertEquals(1, ride.events.offRoute);
    assertTrue(ride.engine.turnState().offRoute);
    assertEquals(60, ride.engine.turnState().distanceM, 0.5);
    double north = 300 + (TzConfig.OFF_ROUTE_SECONDS + 1) * Ride.SPEED_MPS;
    ride.atOffset(60, north, 0); // Continue off route; the event must not repeat.
    assertEquals(FollowEngine.Phase.OFF_ROUTE, ride.engine.phase());
    assertEquals(1, ride.events.offRoute);
    ride.atOffset(30, north, 270); // Under 40 m, but still outside the 20 m rejoin threshold.
    assertEquals(FollowEngine.Phase.OFF_ROUTE, ride.engine.phase());
    assertTrue(ride.engine.turnState().offRoute);
    assertEquals(30, ride.engine.turnState().distanceM, 0.5);
    assertEquals(0, ride.events.backOnRoute);
    ride.atOffset(10, north, 270);
    assertEquals(FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
    assertFalse(ride.engine.turnState().offRoute);
    assertEquals(1, ride.events.backOnRoute);
    ride.atOffset(10, north + Ride.SPEED_MPS, 0);
    assertEquals(FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
    assertEquals(1, ride.events.backOnRoute);
    assertEquals(1, ride.events.offRoute);
  }

  @Test
  public void comingBackWhereTheRiderLeftFindsThatPlace()
  {
    // Out 400 m north and back south on a road 30 m east: the way back passes 30 m from the way out.
    Ride ride = new Ride(FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 400},
        new double[] {30, 400}, new double[] {30, 0}));
    while (ride.seconds < 33)
    {
      ride.onRoute(); // To ~200 m.
    }
    // A detour 70 m west, parallel up to 380 m north, then back onto the way out at 250 m.
    for (double north = 206; north <= 380; north += Ride.SPEED_MPS)
    {
      ride.atOffset(-70, north, 0);
    }
    assertEquals(FollowEngine.Phase.OFF_ROUTE, ride.engine.phase());
    ride.atOffset(0, 250, 0);
    assertEquals(FollowEngine.Phase.ON_ROUTE, ride.engine.phase());
    assertEquals(250, ride.engine.along(), 2);
  }

  @Test
  public void firstFixFiveHundredMetresEastShowsHeadToStart()
  {
    Ride ride = new Ride(FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 1000}));
    ride.atOffset(500, 0, 270);
    assertEquals(FollowEngine.Phase.HEAD_TO_START, ride.engine.phase());
    assertEquals(Maneuver.HEAD_TO_START, ride.engine.turnState().maneuver);
    assertEquals(500, ride.engine.turnState().distanceM, 2); // Geographic and flat metres differ slightly.
    assertFalse(ride.engine.turnState().offRoute);
  }

  @Test
  public void etaUsesFallbackThroughoutFirstTwoMinutesOfMovingTime()
  {
    Ride ride = new Ride(longStraight());
    for (int second = 0; second < 120; second++)
    {
      ride.onRoute();
      assertEquals(ride.engine.remainingM() / (TzConfig.ETA_FALLBACK_KMH / 3.6),
          ride.engine.etaSeconds(), 1e-9);
    }
  }

  @Test
  public void etaUsesMeasuredSixMetresPerSecondAfterFiveMinutes()
  {
    Ride ride = new Ride(longStraight());
    for (int second = 0; second <= 300; second++)
    {
      ride.onRoute();
    }
    assertEquals(300_000L, ride.movingMs);
    assertEquals(1800, ride.distanceM, 0.01); // Five minutes at 6 m/s.
    double expectedSeconds = ride.engine.remainingM() / Ride.SPEED_MPS;
    assertEquals(expectedSeconds, ride.engine.etaSeconds(), expectedSeconds * 0.05);
  }

  @Test
  public void streetRequestsCoverNextTwoTurnsOnceEachAsRideProgresses()
  {
    // Three corners let the test distinguish the initial pair from a later request.
    FollowRoute route = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 500},
        new double[] {500, 500}, new double[] {500, 1000}, new double[] {1000, 1000});
    Ride ride = new Ride(route);
    List<RouteManeuver> maneuvers = ride.engine.maneuvers();
    assertEquals(4, maneuvers.size());
    List<RouteManeuver> firstTwo = Arrays.asList(maneuvers.get(0), maneuvers.get(1));
    ride.onRoute();
    assertEquals(firstTwo, ride.events.streets);
    while (ride.engine.along() + Ride.SPEED_MPS < maneuvers.get(0).at)
    {
      ride.onRoute();
      assertEquals(firstTwo, ride.events.streets);
    }
    ride.onRoute(); // Passing the first corner brings the third turn into the next-two window.
    assertEquals(Arrays.asList(maneuvers.get(0), maneuvers.get(1), maneuvers.get(2)), ride.events.streets);
    ride.toEnd();
    assertEquals(3, ride.events.streets.size());
    for (int i = 0; i < maneuvers.size() - 1; i++)
    {
      assertEquals(1, Collections.frequency(ride.events.streets, maneuvers.get(i)));
    }
    assertFalse(ride.events.streets.contains(maneuvers.get(3))); // ARRIVE has no street after it.
  }

  private static FollowRoute lShape()
  {
    return FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 500}, new double[] {500, 500});
  }

  private static FollowRoute longStraight()
  {
    // Long enough to keep riding for five minutes without reaching the arrival threshold.
    return FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 4000});
  }

  /** One-second fixes at 6 m/s; ride totals come from the supplied positions. */
  private static final class Ride
  {
    static final double SPEED_MPS = 6;
    final FollowRoute route;
    final RecordingEvents events = new RecordingEvents();
    final FollowEngine engine;
    int seconds = -1;
    long movingMs;
    double distanceM;
    private double[] previous;

    Ride(FollowRoute route)
    {
      this.route = route;
      engine = new FollowEngine(route, events);
      events.engine = engine;
    }

    void onRoute()
    {
      double along = Math.min(route.totalM(), (seconds + 1) * SPEED_MPS);
      double course = route.bearing(along, Math.min(route.totalM(), along + SPEED_MPS));
      fix(new double[] {route.latAt(along), route.lonAt(along)}, course);
    }

    void atOffset(double east, double north, double course)
    {
      fix(FollowTestRoutes.point(east, north), course);
    }

    void toEnd()
    {
      int lastSecond = (int) Math.ceil(route.totalM() / SPEED_MPS);
      while (seconds < lastSecond)
      {
        onRoute();
      }
    }

    private void fix(double[] point, double course)
    {
      seconds++;
      if (previous != null)
      {
        double stepM = Geo.distance(previous[0], previous[1], point[0], point[1]);
        distanceM += stepM;
        if (stepM > 0)
        {
          movingMs += 1000;
          course = Geo.bearing(previous[0], previous[1], point[0], point[1]);
        }
      }
      previous = point;
      engine.update(point[0], point[1], course, SPEED_MPS, seconds * 1000L, distanceM, movingMs);
    }
  }

  private static final class RecordingEvents implements FollowEngine.Events
  {
    final List<RouteManeuver> soon = new ArrayList<>();
    final List<Double> soonDistances = new ArrayList<>();
    final List<RouteManeuver> now = new ArrayList<>();
    final List<Double> nowDistances = new ArrayList<>();
    final List<RouteManeuver> streets = new ArrayList<>();
    FollowEngine engine;
    int offRoute;
    int backOnRoute;
    int arrived;

    @Override
    public void onTurnSoon(RouteManeuver maneuver, double distanceM)
    {
      soon.add(maneuver);
      soonDistances.add(distanceM);
    }

    @Override
    public void onTurnNow(RouteManeuver maneuver)
    {
      now.add(maneuver);
      nowDistances.add(engine.turnState().distanceM);
    }

    @Override
    public void onOffRoute()
    {
      offRoute++;
    }

    @Override
    public void onBackOnRoute()
    {
      backOnRoute++;
    }

    @Override
    public void onArrived()
    {
      arrived++;
    }

    @Override
    public void onStreetWanted(RouteManeuver maneuver, double[] lat, double[] lon)
    {
      streets.add(maneuver);
    }
  }
}
