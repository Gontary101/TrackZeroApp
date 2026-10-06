package app.trackzero.ride;

import androidx.annotation.NonNull;
import app.trackzero.routes.FootTime;
import app.trackzero.routes.Profile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * How long a route will take this rider (FEAT-009, Phase 33), from their own last rides of the same kind. Bicycle:
 * effort distance (km + 0.8 per 100 m climbed) at the median of their effort-km per moving hour; on foot: the walking
 * or hiking formula times the median of their moving-time-to-formula ratio. Stops: the median share of time not
 * moving, 0–35 %. Fewer than 3 usable rides: a typical pace, said so. Pure.
 */
public final class RideEstimator
{
  static final int LAST_RIDES = 12;
  static final int MIN_RIDES = 3;
  static final double MIN_BIKE_M = 5000;
  static final double MIN_FOOT_M = 2000;
  /** Effort-km for every 100 m climbed. */
  static final double EFFORT_PER_100M = 0.8;
  static final double MAX_STOPS = 0.35;
  static final double TYPICAL_STOPS = 0.1;
  static final double TYPICAL_ROAD_KMH = 20;
  static final double TYPICAL_GRAVEL_KMH = 16;

  /** The estimate: total time with stops, and how many rides it comes from (0: a typical pace). */
  public static final class Estimate
  {
    public final long totalS;
    public final int basis;

    public Estimate(long totalS, int basis)
    {
      this.totalS = totalS;
      this.basis = basis;
    }
  }

  private RideEstimator() {}

  /**
   * @param upM   the route's climbing, NaN without altitudes (effort is then the distance)
   * @param downM its descent, NaN when unknown (hiking only)
   */
  @NonNull
  public static Estimate estimate(@NonNull Profile profile, double distanceM, double upM, double downM,
                                  @NonNull List<Ride> rides)
  {
    final List<Ride> used = usable(profile, rides);
    final boolean typical = used.size() < MIN_RIDES;
    final double stops = typical ? TYPICAL_STOPS : stops(used);
    final double movingH;
    if (profile.foot)
    {
      final double factor = typical ? 1 : footFactor(used);
      movingH = formulaS(profile, distanceM, upM, downM) / 3600.0 * factor;
    }
    else
    {
      final double pace = typical ? (profile == Profile.GRAVEL ? TYPICAL_GRAVEL_KMH : TYPICAL_ROAD_KMH) : pace(used);
      movingH = effortKm(distanceM, upM) / pace;
    }
    return new Estimate(Math.round(movingH * (1 + stops) * 3600), typical ? 0 : used.size());
  }

  /** km + 0.8 per 100 m climbed; the distance alone without altitudes. */
  static double effortKm(double distanceM, double upM)
  {
    return distanceM / 1000 + (Double.isNaN(upM) ? 0 : EFFORT_PER_100M * Math.max(0, upM) / 100);
  }

  /** The last 12 rides of the same kind, long enough, with moving time; only the profile's own if 3 or more. */
  @NonNull
  static List<Ride> usable(@NonNull Profile profile, @NonNull List<Ride> rides)
  {
    final List<Ride> sorted = new ArrayList<>(rides);
    sorted.sort(Comparator.comparingLong((Ride r) -> r.startedAt).reversed());
    final List<Ride> same = new ArrayList<>();
    for (Ride r : sorted)
    {
      if (r.profile.foot != profile.foot || r.movingS <= 0
          || r.distanceM < (profile.foot ? MIN_FOOT_M : MIN_BIKE_M))
        continue;
      same.add(r);
      if (same.size() == LAST_RIDES)
        break;
    }
    final List<Ride> own = new ArrayList<>();
    for (Ride r : same)
      if (r.profile == profile)
        own.add(r);
    return own.size() >= MIN_RIDES ? own : same;
  }

  /** Median effort-km per moving hour. */
  static double pace(@NonNull List<Ride> rides)
  {
    final double[] v = new double[rides.size()];
    for (int i = 0; i < v.length; i++)
      v[i] = effortKm(rides.get(i).distanceM, rides.get(i).gainM) / (rides.get(i).movingS / 3600.0);
    return median(v);
  }

  /** Median of moving time over the formula's time for each foot ride. */
  static double footFactor(@NonNull List<Ride> rides)
  {
    final double[] v = new double[rides.size()];
    for (int i = 0; i < v.length; i++)
    {
      final Ride r = rides.get(i);
      // A past ride keeps its climbing only: its descent is taken as the same (most go out and back, or round).
      v[i] = r.movingS / (double) Math.max(1, formulaS(r.profile, r.distanceM, r.gainM, r.gainM));
    }
    return median(v);
  }

  /** Median share of time stopped (wall clock over moving time, less one), within 0–35 %. */
  static double stops(@NonNull List<Ride> rides)
  {
    final double[] v = new double[rides.size()];
    for (int i = 0; i < v.length; i++)
    {
      final Ride r = rides.get(i);
      final double elapsedS = (r.endedAt - r.startedAt) / 1000.0;
      v[i] = elapsedS / r.movingS - 1;
    }
    return Math.max(0, Math.min(MAX_STOPS, median(v)));
  }

  static long formulaS(@NonNull Profile p, double distanceM, double upM, double downM)
  {
    return p == Profile.HIKE ? FootTime.hikeSeconds(distanceM, upM, downM) : FootTime.walkSeconds(distanceM, upM);
  }

  static double median(@NonNull double[] v)
  {
    final double[] s = v.clone();
    Arrays.sort(s);
    final int n = s.length;
    return n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2;
  }
}
