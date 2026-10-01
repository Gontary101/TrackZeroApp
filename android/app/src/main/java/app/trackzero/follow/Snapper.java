package app.trackzero.follow;

import androidx.annotation.NonNull;

/**
 * Finds where on the route the rider is (plan §10.2 "Snap").
 *
 * Both legs of an out-and-back lie on the same road, so distance alone cannot tell them apart. The search is limited
 * to a window around the last known place, and a segment running against the rider's course counts as farther away
 * than it is. When the place is ambiguous anyway (first fix of a loop that starts where it ends), the earliest
 * candidate wins.
 */
final class Snapper
{
  /** A segment this far off the rider's course runs the other way (the other leg of an out-and-back). */
  static final double HEADING_MISMATCH_DEG = 120;
  /**
   * How much farther such a segment counts: more than GPS noise between two legs of one road, less than the gap to
   * another road, so a clearly closer segment still wins (a rider coming back to the route from the side).
   */
  static final double HEADING_PENALTY_M = 15;
  /** Candidates this close to the best are ties, broken by the earliest along the route. */
  static final double TIE_M = 15;

  /** Result of one snap. Reused. */
  static final class Result
  {
    /** Distance along the route of the closest point, metres. */
    double along;
    /** Real distance from the rider to that point, metres. */
    double distanceM;
    /** Distance plus the heading penalty; what the choice was made on. */
    double score;
  }

  private Snapper() {}

  /**
   * Snaps to the part of the route between {@code fromAlong} and {@code toAlong}.
   *
   * @param courseDeg     rider's course, or NaN when unknown (standing, slow)
   * @param preferEarliest of several places on the route about as close (within {@link #TIE_M}), take the
   *                      earliest; for a first snap with no history. Otherwise simply the closest.
   */
  static void snap(@NonNull FollowRoute route, double lat, double lon, double courseDeg, double fromAlong,
                   double toAlong, boolean preferEarliest, @NonNull Result out)
  {
    final double px = route.projectX(lon);
    final double py = route.projectY(lat);
    final int first = route.segmentAt(Math.max(0, fromAlong));
    final int last = route.segmentAt(Math.min(route.totalM(), toAlong));

    out.score = Double.MAX_VALUE;
    double best = Double.MAX_VALUE;
    boolean inRun = false;
    // Pass 0 finds the best score; pass 1 (preferEarliest) the best point of the first stretch of segments
    // within TIE_M of it.
    for (int pass = preferEarliest ? 0 : 1; pass < 2; pass++)
    {
      for (int i = first; i <= last; i++)
      {
        final double ax = route.x[i], ay = route.y[i];
        final double dx = route.x[i + 1] - ax, dy = route.y[i + 1] - ay;
        final double len2 = dx * dx + dy * dy;
        final double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        final double qx = ax + t * dx - px, qy = ay + t * dy - py;
        final double d = Math.sqrt(qx * qx + qy * qy);
        double score = d;
        if (!Double.isNaN(courseDeg) && len2 > 0)
        {
          final double seg = FollowRoute.bearing(ax, ay, ax + dx, ay + dy);
          final double diff = Math.abs(((courseDeg - seg) % 360 + 540) % 360 - 180);
          if (diff > HEADING_MISMATCH_DEG)
            score += HEADING_PENALTY_M;
        }
        if (pass == 0)
        {
          best = Math.min(best, score);
          continue;
        }
        if (preferEarliest)
        {
          if (score > best + TIE_M)
          {
            if (inRun)
              return;
            continue;
          }
          inRun = true;
        }
        if (score < out.score)
        {
          out.score = score;
          out.distanceM = d;
          out.along = route.cum[i] + t * (route.cum[i + 1] - route.cum[i]);
        }
      }
    }
  }
}
