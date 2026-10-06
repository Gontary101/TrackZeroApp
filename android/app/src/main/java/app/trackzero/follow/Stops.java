package app.trackzero.follow;

import androidx.annotation.NonNull;
import app.trackzero.geo.Geo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * S1's list (FEAT-007, Phase 30): places from the map placed on the route, kept within 1 km of it and ordered by how
 * far ahead they are; on a free ride, within 5 km of the rider by straight distance. Pure.
 */
public final class Stops
{
  public static final int WATER = 1;
  public static final int REPAIR = 2;

  /** Searched this far either side of the route. */
  public static final double MAX_OFF_ROUTE_M = 1000;
  /** In a ride: this far ahead of the rider. */
  public static final double RIDE_AHEAD_M = 40_000;
  /** On a free ride: this far around the rider. */
  public static final double FREE_RADIUS_M = 5000;
  /** "On the route" this close to it. */
  public static final double ON_ROUTE_M = 30;
  /** A detour on roads is about this much longer than out and back in a straight line. */
  static final double DETOUR_FACTOR = 1.3;

  /** A place in the list. */
  public static final class Row
  {
    @NonNull
    public final Stop stop;
    /** Where it is along the route, and how far from it; NaN on a free ride. */
    public final double alongM;
    public final double offRouteM;
    /** How far ahead along the route (from the rider or the start); the straight distance on a free ride. */
    public final double distanceM;

    Row(@NonNull Stop stop, double alongM, double offRouteM, double distanceM)
    {
      this.stop = stop;
      this.alongM = alongM;
      this.offRouteM = offRouteM;
      this.distanceM = distanceM;
    }

    public boolean onRoute()
    {
      return offRouteM <= ON_ROUTE_M;
    }
  }

  private Stops() {}

  /** The places within 1 km of the route between {@code fromAlong} and {@code toAlong}, nearest ahead first. */
  @NonNull
  public static List<Row> along(@NonNull FollowRoute route, @NonNull List<Stop> places, double fromAlong,
                                double toAlong)
  {
    final List<Row> out = new ArrayList<>();
    final double from = Math.max(0, fromAlong);
    final double to = Math.min(route.totalM(), toAlong);
    if (route.size() < 2 || to < from)
      return out;
    final Snapper.Result r = new Snapper.Result();
    final Snapper.Result back = new Snapper.Result();
    for (Stop s : places)
    {
      // Within the window only: of an out-and-back's two legs, the one still ahead.
      Snapper.snap(route, s.lat, s.lon, Double.NaN, from, to, true, r);
      if (r.distanceM > MAX_OFF_ROUTE_M)
        continue;
      // Closer to the route just behind the window's start: already passed.
      Snapper.snap(route, s.lat, s.lon, Double.NaN, Math.max(0, from - MAX_OFF_ROUTE_M), to, false, back);
      if (back.along < from - 1 && back.distanceM < r.distanceM - 1)
        continue;
      out.add(new Row(s, r.along, r.distanceM, r.along - from));
    }
    out.sort(Comparator.comparingDouble(row -> row.distanceM));
    return out;
  }

  /** The places within 5 km of the rider, nearest first. */
  @NonNull
  public static List<Row> near(double lat, double lon, @NonNull List<Stop> places)
  {
    final List<Row> out = new ArrayList<>();
    for (Stop s : places)
    {
      final double d = Geo.distance(lat, lon, s.lat, s.lon);
      if (d <= FREE_RADIUS_M)
        out.add(new Row(s, Double.NaN, Double.NaN, d));
    }
    out.sort(Comparator.comparingDouble(row -> row.distanceM));
    return out;
  }

  /** Where a point is along the route between the two marks (the earliest of equally close places). */
  static double alongOf(@NonNull FollowRoute route, double lat, double lon, double fromAlong, double toAlong)
  {
    final Snapper.Result r = new Snapper.Result();
    Snapper.snap(route, lat, lon, Double.NaN, Math.max(0, fromAlong), Math.min(route.totalM(), toAlong), true, r);
    return r.along;
  }

  /** S2's DETOUR: there and back in a straight line, about 1.3 times longer on roads; rounded to 10 m. */
  public static double detourM(double offRouteM)
  {
    return Math.round(2 * offRouteM * DETOUR_FACTOR / 10) * 10.0;
  }
}
