package app.trackzero.follow;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ManeuverBuilderTest
{
  @Test
  public void northThenEastProducesOneRightTurnAndArrival()
  {
    FollowRoute route = FollowTestRoutes.polyline(
        new double[] {0, 0}, new double[] {0, 500}, new double[] {500, 500});
    List<RouteManeuver> maneuvers = assertSingleTurn(route, Maneuver.TURN_RIGHT);
    assertEquals(500, maneuvers.get(0).at, 1);
  }

  @Test
  public void northThenWestProducesOneLeftTurnAndArrival()
  {
    FollowRoute route = FollowTestRoutes.polyline(
        new double[] {0, 0}, new double[] {0, 500}, new double[] {-500, 500});
    assertSingleTurn(route, Maneuver.TURN_LEFT);
  }

  @Test
  public void gentleFifteenDegreeBendProducesOnlyArrival()
  {
    assertOnlyArrival(bend(15));
  }

  @Test
  public void fortyDegreeRightBendProducesSlightRight()
  {
    assertSingleTurn(bend(40), Maneuver.SLIGHT_RIGHT);
  }

  @Test
  public void fortyDegreeLeftBendProducesSlightLeft()
  {
    assertSingleTurn(bend(-40), Maneuver.SLIGHT_LEFT);
  }

  @Test
  public void hundredSeventyDegreeReversalProducesUturn()
  {
    assertSingleTurn(bend(170), Maneuver.UTURN);
  }

  @Test
  public void nearReversalWithTenMetreOffsetProducesUturn()
  {
    // A 500 m return leg offset only 10 m east is approximately a 179-degree reversal.
    FollowRoute route = FollowTestRoutes.polyline(
        new double[] {0, 0}, new double[] {0, 500}, new double[] {10, 0});
    assertSingleTurn(route, Maneuver.UTURN);
  }

  @Test
  public void rightCornersTenMetresApartMergeIntoOneUturn()
  {
    FollowRoute route = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 500},
        new double[] {10, 500}, new double[] {10, 0});
    assertSingleTurn(route, Maneuver.UTURN);
  }

  @Test
  public void leftThenRightJogTenMetresApartProducesOnlyArrival()
  {
    FollowRoute route = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 500},
        new double[] {-10, 500}, new double[] {-10, 1000});
    assertOnlyArrival(route);
  }

  @Test
  public void denseStraightLineWithSeededTwoMetreJitterProducesOnlyArrival()
  {
    Random random = new Random(20261001L);
    double[][] points = new double[201][2]; // A 1 km track with fixes every 5 m.
    for (int i = 0; i < points.length; i++)
    {
      points[i][0] = 4 * random.nextDouble() - 2; // Lateral jitter in [-2, 2) m.
      points[i][1] = i * 5;
    }
    assertOnlyArrival(FollowTestRoutes.polyline(points));
  }

  @Test
  public void bendAwayFromAnyJunctionIsDroppedWhenTheMapKnowsTheRoads()
  {
    List<RouteManeuver> maneuvers = ManeuverBuilder.build(bend(90), junctions(0));
    assertEquals(1, maneuvers.size());
    assertEquals(Maneuver.ARRIVE, maneuvers.get(0).type);
  }

  @Test
  public void turnAtAJunctionIsKept()
  {
    List<RouteManeuver> maneuvers = ManeuverBuilder.build(bend(90), junctions(1));
    assertEquals(Maneuver.TURN_RIGHT, maneuvers.get(0).type);
  }

  @Test
  public void turnIsKeptWhereTheMapIsMissing()
  {
    List<RouteManeuver> maneuvers = ManeuverBuilder.build(bend(90), junctions(-1));
    assertEquals(Maneuver.TURN_RIGHT, maneuvers.get(0).type);
  }

  @Test
  public void outAndBackTurnaroundAwayFromJunctionsIsStillAUturn()
  {
    FollowRoute route = FollowTestRoutes.polyline(
        new double[] {0, 0}, new double[] {0, 500}, new double[] {0, 0});
    List<RouteManeuver> maneuvers = ManeuverBuilder.build(route, junctions(0));
    assertEquals(Maneuver.UTURN, maneuvers.get(0).type);
  }

  @Test
  public void hairpinBendAwayFromJunctionsIsNotAUturn()
  {
    // A switchback: the legs are 40 m apart, so the road bends; nobody turns around.
    FollowRoute route = FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 500},
        new double[] {20, 520}, new double[] {40, 500}, new double[] {40, 0});
    List<RouteManeuver> maneuvers = ManeuverBuilder.build(route, junctions(0));
    assertEquals(1, maneuvers.size());
  }

  /** Answers {@code value} for every point. */
  private static ManeuverBuilder.Junctions junctions(int value)
  {
    return (lat, lon) -> {
      int[] out = new int[lat.length];
      Arrays.fill(out, value);
      return out;
    };
  }

  private static FollowRoute bend(double degrees)
  {
    double angle = Math.toRadians(degrees);
    return FollowTestRoutes.polyline(new double[] {0, 0}, new double[] {0, 500},
        new double[] {500 * Math.sin(angle), 500 + 500 * Math.cos(angle)});
  }

  private static List<RouteManeuver> assertSingleTurn(FollowRoute route, Maneuver expected)
  {
    List<RouteManeuver> maneuvers = ManeuverBuilder.build(route, null);
    assertEquals("One turn followed by arrival", 2, maneuvers.size());
    assertEquals(expected, maneuvers.get(0).type);
    assertArrival(route, maneuvers);
    return maneuvers;
  }

  private static void assertOnlyArrival(FollowRoute route)
  {
    List<RouteManeuver> maneuvers = ManeuverBuilder.build(route, null);
    assertEquals(1, maneuvers.size());
    assertArrival(route, maneuvers);
  }

  private static void assertArrival(FollowRoute route, List<RouteManeuver> maneuvers)
  {
    RouteManeuver last = maneuvers.get(maneuvers.size() - 1);
    assertEquals(Maneuver.ARRIVE, last.type);
    assertEquals(route.totalM(), last.at, 0);
  }
}
