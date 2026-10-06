package app.trackzero.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.trackzero.routes.FootTime;
import app.trackzero.routes.Profile;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RideEstimatorTest
{
  private static final double[] NONE = new double[0];
  private static long sNext = 1_780_000_000_000L;

  /** A ride: km, gain, moving minutes, minutes stopped. Each later than the one before. */
  private static Ride ride(Profile p, double km, double gainM, double movingMin, double stoppedMin)
  {
    final long start = sNext;
    sNext += 86_400_000L;
    final long end = start + Math.round((movingMin + stoppedMin) * 60_000);
    return new Ride("r" + start, null, "", "", start, end, Math.round(movingMin * 60), km * 1000, gainM, 0, 0, 0,
                    NONE, NONE, p);
  }

  @Test
  public void medianPaceOfEffortDistance()
  {
    final List<Ride> rides = new ArrayList<>();
    // Effort 30 km in 1.5 h (20), 22 km (20 km + 250 m) in 1 h (22), 25 km in 1 h (25): median 22.
    rides.add(ride(Profile.ROAD, 30, 0, 90, 0));
    rides.add(ride(Profile.ROAD, 20, 250, 60, 0));
    rides.add(ride(Profile.ROAD, 25, 0, 60, 0));
    final RideEstimator.Estimate e = RideEstimator.estimate(Profile.ROAD, 44_000, 0, Double.NaN, rides);
    assertEquals(3, e.basis);
    assertEquals(2 * 3600, e.totalS, 1);
  }

  @Test
  public void ownProfileOnlyWhenThereAreThree()
  {
    final List<Ride> rides = new ArrayList<>();
    rides.add(ride(Profile.ROAD, 30, 0, 60, 0));
    rides.add(ride(Profile.ROAD, 30, 0, 60, 0));
    rides.add(ride(Profile.GRAVEL, 15, 0, 60, 0));
    rides.add(ride(Profile.GRAVEL, 15, 0, 60, 0));
    // Two gravel rides: pooled with road (median of 30, 30, 15, 15 = 22.5).
    assertEquals(4, RideEstimator.estimate(Profile.GRAVEL, 22_500, 0, 0, rides).basis);
    assertEquals(3600, RideEstimator.estimate(Profile.GRAVEL, 22_500, 0, 0, rides).totalS, 1);
    rides.add(ride(Profile.GRAVEL, 15, 0, 60, 0));
    // Three: gravel alone.
    final RideEstimator.Estimate e = RideEstimator.estimate(Profile.GRAVEL, 15_000, 0, 0, rides);
    assertEquals(3, e.basis);
    assertEquals(3600, e.totalS, 1);
  }

  @Test
  public void stopsAreTheMedianShareClamped()
  {
    final List<Ride> rides = new ArrayList<>();
    rides.add(ride(Profile.ROAD, 20, 0, 60, 6));
    rides.add(ride(Profile.ROAD, 20, 0, 60, 12));
    rides.add(ride(Profile.ROAD, 20, 0, 60, 120));
    // Median 20 % stopped.
    assertEquals(Math.round(3600 * 1.2), RideEstimator.estimate(Profile.ROAD, 20_000, 0, 0, rides).totalS, 2);
    rides.add(ride(Profile.ROAD, 20, 0, 60, 120));
    rides.add(ride(Profile.ROAD, 20, 0, 60, 120));
    assertEquals(Math.round(3600 * 1.35), RideEstimator.estimate(Profile.ROAD, 20_000, 0, 0, rides).totalS, 2);
  }

  @Test
  public void fewRidesUseATypicalPace()
  {
    final List<Ride> rides = new ArrayList<>();
    rides.add(ride(Profile.ROAD, 40, 0, 60, 0));
    rides.add(ride(Profile.ROAD, 3, 0, 10, 0)); // Too short to count.
    final RideEstimator.Estimate road = RideEstimator.estimate(Profile.ROAD, 20_000, 0, 0, rides);
    assertEquals(0, road.basis);
    assertEquals(Math.round(3600 * 1.1), road.totalS, 1);
    assertEquals(Math.round(3600 * 1.1), RideEstimator.estimate(Profile.GRAVEL, 16_000, 0, 0, rides).totalS, 1);
  }

  @Test
  public void onFootTheFormulaTimesTheirFactor()
  {
    final List<Ride> rides = new ArrayList<>();
    // Each walk took 1.5 times the formula.
    for (int i = 0; i < 3; i++)
    {
      final long formula = FootTime.walkSeconds(6000, 100);
      rides.add(ride(Profile.WALK, 6, 100, formula * 1.5 / 60, 0));
    }
    rides.add(ride(Profile.ROAD, 30, 0, 60, 0)); // A bike ride does not count on foot.
    final RideEstimator.Estimate e = RideEstimator.estimate(Profile.WALK, 10_000, 0, Double.NaN, rides);
    assertEquals(3, e.basis);
    assertEquals(FootTime.walkSeconds(10_000, 0) * 1.5, e.totalS, 2);
    assertEquals(0, RideEstimator.estimate(Profile.HIKE, 10_000, 500, 500, new ArrayList<>()).basis);
  }

  @Test
  public void aRouteWithoutAltitudesIsItsDistance()
  {
    assertEquals(42, RideEstimator.effortKm(42_000, Double.NaN), 1e-9);
    assertEquals(42 + 8, RideEstimator.effortKm(42_000, 1000), 1e-9);
  }

  @Test
  public void workedOutExactlyOnce()
  {
    final EstimateOnce once = new EstimateOnce();
    final int[] calls = {0};
    // The route known at the start, then reroutes and fixes over a ride.
    for (int i = 0; i < 100; i++)
      once.routeKnown(() -> calls[0]++);
    assertEquals(1, calls[0]);
    final EstimateOnce restored = new EstimateOnce();
    restored.restored();
    assertFalse(restored.routeKnown(() -> calls[0]++));
    assertEquals(1, calls[0]);
    assertTrue(new EstimateOnce().routeKnown(() -> {}));
  }
}
