package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.TzConfig;
import app.trackzero.geo.DouglasPeucker;
import app.trackzero.geo.Geo;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the turns of a GPX track (plan §10.1): bends of the simplified line, measured over 30 m on each side so GPS
 * noise and rounded corners do not matter.
 *
 * A track's shape cannot tell a turn at a crossroads from a bend in the road (a mountain road would say "keep left"
 * at every curve), so when the map knows the roads, a bend away from any junction is not a maneuver.
 */
public final class ManeuverBuilder
{
  static final double SLIGHT_DEG = 25;
  static final double TURN_DEG = 60;
  static final double UTURN_DEG = 155;
  /** A U-turn away from junctions is real only if the track comes back along itself (an out-and-back), not a hairpin. */
  static final double UTURN_SAME_ROAD_M = 12;

  /** Where roads meet, from the map. */
  public interface Junctions
  {
    /** Per point: 1 if roads meet there, 0 if not, -1 unknown. */
    @NonNull
    int[] at(@NonNull double[] lat, @NonNull double[] lon);
  }

  private ManeuverBuilder() {}

  /**
   * The route's maneuvers in order, ending with ARRIVE at the last point.
   *
   * @param junctions null to keep every bend (no map)
   */
  @NonNull
  public static List<RouteManeuver> build(@NonNull FollowRoute route, @Nullable Junctions junctions)
  {
    final double w = TzConfig.MANEUVER_BEARING_WINDOW_M;
    final double total = route.totalM();
    final boolean[] keep = DouglasPeucker.simplify(route.x, route.y, TzConfig.DP_EPSILON_M);

    // Bends worth a look, in order along the route.
    final List<double[]> bends = new ArrayList<>(); // {at, angle}
    for (int i = 1; i < keep.length - 1; i++)
    {
      if (!keep[i])
        continue;
      final double at = route.cum[i];
      final double a = angle(route, at, at, w);
      if (Math.abs(a) >= SLIGHT_DEG)
        bends.add(new double[] {at, a});
    }

    // Bends closer than 20 m are one maneuver (a corner drawn with two vertices, a jog across a junction): its
    // direction change is measured across the whole group, so a left-right jog cancels out.
    final List<RouteManeuver> out = new ArrayList<>();
    int g = 0;
    while (g < bends.size())
    {
      int end = g;
      while (end + 1 < bends.size() && bends.get(end + 1)[0] - bends.get(end)[0] < TzConfig.MANEUVER_MERGE_M)
        end++;
      double at = bends.get(g)[0];
      double strongest = 0;
      for (int k = g; k <= end; k++)
      {
        if (Math.abs(bends.get(k)[1]) > strongest)
        {
          strongest = Math.abs(bends.get(k)[1]);
          at = bends.get(k)[0];
        }
      }
      final double a = g == end ? bends.get(g)[1] : angle(route, bends.get(g)[0], bends.get(end)[0], w);
      final Maneuver type = classify(a);
      if (type != null && at < total)
        out.add(new RouteManeuver(type, at, a));
      g = end + 1;
    }
    if (junctions != null)
      dropBends(route, out, junctions);
    out.add(new RouteManeuver(Maneuver.ARRIVE, total, 0));
    return out;
  }

  private static void dropBends(@NonNull FollowRoute route, @NonNull List<RouteManeuver> maneuvers,
                                @NonNull Junctions junctions)
  {
    final double[] lat = new double[maneuvers.size()];
    final double[] lon = new double[maneuvers.size()];
    for (int i = 0; i < lat.length; i++)
    {
      lat[i] = route.latAt(maneuvers.get(i).at);
      lon[i] = route.lonAt(maneuvers.get(i).at);
    }
    final int[] junction = junctions.at(lat, lon);
    final double w = TzConfig.MANEUVER_BEARING_WINDOW_M;
    for (int i = maneuvers.size() - 1; i >= 0; i--)
    {
      if (junction[i] != 0)
        continue;
      final RouteManeuver m = maneuvers.get(i);
      final double before = Math.max(0, m.at - w);
      final double after = Math.min(route.totalM(), m.at + w);
      final boolean backAlongItself = Math.hypot(route.xAt(after) - route.xAt(before),
                                                 route.yAt(after) - route.yAt(before)) < UTURN_SAME_ROAD_M;
      if (m.type != Maneuver.UTURN || !backAlongItself)
        maneuvers.remove(i);
    }
  }

  /** Direction change from the {@code w} metres before {@code from} to the {@code w} metres after {@code to}. */
  private static double angle(@NonNull FollowRoute route, double from, double to, double w)
  {
    final double in = route.bearing(Math.max(0, from - w), from);
    final double out = route.bearing(to, Math.min(route.totalM(), to + w));
    return Geo.normalizeAngle(out - in);
  }

  /** Null below the slight-turn threshold. */
  static Maneuver classify(double angleDeg)
  {
    final double a = Math.abs(angleDeg);
    final boolean right = angleDeg > 0;
    if (a < SLIGHT_DEG)
      return null;
    if (a < TURN_DEG)
      return right ? Maneuver.SLIGHT_RIGHT : Maneuver.SLIGHT_LEFT;
    if (a < UTURN_DEG)
      return right ? Maneuver.TURN_RIGHT : Maneuver.TURN_LEFT;
    return Maneuver.UTURN;
  }
}
