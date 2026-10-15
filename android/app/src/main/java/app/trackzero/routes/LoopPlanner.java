package app.trackzero.routes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.base.TzRouter;
import app.trackzero.follow.BikeWalkPlanner;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.RoutedPath;
import app.trackzero.geo.Geo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Round trips near a distance and a climb, for a profile (FEAT-004, plan v2 Phase 29). Eight candidate triangles
 * (start → A → B → start, A and B 60° apart, in eight directions), or with a point to pass through four candidates
 * start → via → C → start; each routed once, moved closer once if a point has no road, resized once if too long or
 * short; scored on size, climb and how much of it rides the same road twice; three picked in different directions.
 * Pure: the router is an interface; answers come one at a time on one thread.
 */
public final class LoopPlanner
{
  /** The climb presets (L1), in metres up per kilometre; any other target is a number ({@link #plan}). */
  public enum Climb
  {
    ANY(0),
    FLAT(5),
    ROLLING(13),
    HILLY(25);

    public final double mPerKm;

    Climb(double mPerKm)
    {
      this.mPerKm = mPerKm;
    }
  }

  /** One request: a path through the points. */
  public interface Router
  {
    void route(@NonNull double[] lat, @NonNull double[] lon, @NonNull Profile profile,
               @NonNull BikeWalkPlanner.Answer answer);
  }

  public interface Listener
  {
    /** {@code done} of {@code of} candidates tried. */
    void onProgress(int done, int of);

    /**
     * Up to three loops, best first; empty: none fits. {@code mapsMissing}: some candidate ran into a map that is
     * not on the phone (a big loop reaches beyond the downloaded region).
     */
    void onDone(@NonNull List<Loop> loops, boolean mapsMissing);
  }

  /** A proposed loop. */
  public static final class Loop
  {
    @NonNull
    public final RoutedPath path;
    /** The points it was asked through (the start, the corners or the via point, the start). */
    @NonNull
    public final double[] lat;
    @NonNull
    public final double[] lon;
    /** Its direction from the start, compass degrees. */
    public final double directionDeg;
    public final double upM;
    /** The share of it that rides the same road again later, 0…1. */
    public final double retrace;
    final double score;

    Loop(@NonNull RoutedPath path, @NonNull double[] lat, @NonNull double[] lon, double directionDeg, double upM,
         double retrace, double score)
    {
      this.path = path;
      this.lat = lat;
      this.lon = lon;
      this.directionDeg = directionDeg;
      this.upM = upM;
      this.retrace = retrace;
      this.score = score;
    }

    public double lengthM()
    {
      return path.line.totalM();
    }

    /** The point of it furthest from the start (its far side, named on the card). */
    @NonNull
    public double[] farthest()
    {
      final double[] la = path.line.lats();
      final double[] lo = path.line.lons();
      int best = 0;
      double bestM = -1;
      for (int i = 0; i < la.length; i++)
      {
        final double d = Geo.distance(la[0], lo[0], la[i], lo[i]);
        if (d > bestM)
        {
          bestM = d;
          best = i;
        }
      }
      return new double[] {la[best], lo[best]};
    }
  }

  /** Roads run about this much longer than straight lines. */
  static final double DETOUR = 1.3;
  static final int DIRECTIONS = 8;
  static final double CORNER_SPREAD_DEG = 30;
  static final int MAX_RETRIES = 4;
  static final double MOVE_CLOSER = 0.85;
  /** Resized when the first answer is outside this share of the distance, kept when inside the wider one. */
  static final double RESIZE_TOLERANCE = 0.25;
  static final double KEEP_TOLERANCE = 0.3;
  static final double MAX_RETRACE = 0.25;
  static final double RETRACE_SAMPLE_M = 25;
  static final double RETRACE_NEAR_M = 25;
  static final double RETRACE_LATER_M = 300;
  static final double MIN_VIA_LEG_M = 500;
  static final double PICK_APART_DEG = 90;
  static final int PICK = 3;

  /** One candidate loop, its points rebuilt from {@link #r} when it is moved or resized. */
  private final class Candidate
  {
    final double directionDeg;
    final boolean reversed;
    /** Bearing of the corner C for a loop through a via point; NaN for a triangle. */
    final double cornerDeg;
    double r;
    boolean moved;
    boolean resized;

    Candidate(double directionDeg, boolean reversed, double cornerDeg, double r)
    {
      this.directionDeg = directionDeg;
      this.reversed = reversed;
      this.cornerDeg = cornerDeg;
      this.r = r;
    }

    @NonNull
    double[][] points()
    {
      if (!Double.isNaN(cornerDeg))
      {
        final double[] c = offset(mStartLat, mStartLon, cornerDeg, r);
        return new double[][] {{mStartLat, mViaLat, c[0], mStartLat}, {mStartLon, mViaLon, c[1], mStartLon}};
      }
      final double[] a = offset(mStartLat, mStartLon, directionDeg - CORNER_SPREAD_DEG, r);
      final double[] b = offset(mStartLat, mStartLon, directionDeg + CORNER_SPREAD_DEG, r);
      final double[] first = reversed ? b : a;
      final double[] second = reversed ? a : b;
      return new double[][] {{mStartLat, first[0], second[0], mStartLat}, {mStartLon, first[1], second[1], mStartLon}};
    }
  }

  @NonNull
  private final Router mRouter;
  private boolean mCancelled;

  private double mStartLat;
  private double mStartLon;
  private double mViaLat = Double.NaN;
  private double mViaLon = Double.NaN;
  private double mDistanceM;
  @NonNull
  /** Metres up per km asked for; NaN: any. */
  private double mClimbPerKm = Double.NaN;
  @NonNull
  private Profile mProfile = Profile.ROAD;
  @Nullable
  private Listener mListener;
  private final List<Candidate> mCandidates = new ArrayList<>();
  private final List<Loop> mFound = new ArrayList<>();
  private boolean mMapsMissing;
  private int mIndex;
  private int mRetries;

  public LoopPlanner(@NonNull Router router)
  {
    mRouter = router;
  }

  /** No more requests and no answer after this. */
  public void cancel()
  {
    mCancelled = true;
  }

  /**
   * @param viaLat     a point every loop passes through, NaN for none
   * @param climbPerKm metres up per kilometre to aim for, NaN when climbing does not count
   * @param shiftDeg   0, or 22.5 for "other loops" (directions between the first ones)
   */
  public void plan(double startLat, double startLon, double distanceM, double climbPerKm, @NonNull Profile profile,
                   double viaLat, double viaLon, double shiftDeg, @NonNull Listener listener)
  {
    mStartLat = startLat;
    mStartLon = startLon;
    mViaLat = viaLat;
    mViaLon = viaLon;
    mDistanceM = distanceM;
    mClimbPerKm = climbPerKm;
    mProfile = profile;
    mListener = listener;
    mCandidates.clear();
    mFound.clear();
    mMapsMissing = false;
    mIndex = 0;
    mRetries = 0;
    if (Double.isNaN(viaLat))
    {
      // A triangle start → A → B → start: perimeter about 3r in a straight line, the roads longer.
      final double r = distanceM / (3 * DETOUR);
      for (int k = 0; k < DIRECTIONS; k++)
        mCandidates.add(new Candidate(shiftDeg + k * 360.0 / DIRECTIONS, k % 2 == 1, Double.NaN, r));
    }
    else
    {
      // start → via → C → start, C beside the way to the via point.
      final double toVia = Geo.distance(startLat, startLon, viaLat, viaLon);
      final double viaDeg = Geo.bearing(startLat, startLon, viaLat, viaLon);
      final double r = Math.max(MIN_VIA_LEG_M, distanceM / DETOUR - 2 * toVia);
      for (double turn : new double[] {-60, 60, -120, 120})
      {
        final double c = Geo.normalizeAngle(viaDeg + turn + shiftDeg);
        mCandidates.add(new Candidate((c + 360) % 360, false, c, r));
      }
    }
    next();
  }

  private void next()
  {
    if (mCancelled)
      return;
    if (mIndex >= mCandidates.size())
    {
      mListener.onDone(pick(mFound), mMapsMissing);
      return;
    }
    request(mCandidates.get(mIndex));
  }

  private void request(@NonNull Candidate c)
  {
    final double[][] p = c.points();
    mRouter.route(p[0], p[1], mProfile, (path, reason) -> {
      if (mCancelled)
        return;
      if (path == null)
      {
        if (reason == TzRouter.Reason.NO_MAP)
          mMapsMissing = true;
        final boolean noRoad = reason == TzRouter.Reason.NO_START || reason == TzRouter.Reason.NO_END
                               || reason == TzRouter.Reason.NO_WAY;
        if (noRoad && !c.moved && mRetries < MAX_RETRIES)
        {
          // A point without a road near it (a lake, a forest): once, a little closer to the start.
          c.moved = true;
          mRetries++;
          c.r *= MOVE_CLOSER;
          request(c);
          return;
        }
        done();
        return;
      }
      final double length = path.line.totalM();
      if (Math.abs(length - mDistanceM) > RESIZE_TOLERANCE * mDistanceM && !c.resized && mRetries < MAX_RETRIES)
      {
        c.resized = true;
        mRetries++;
        c.r *= mDistanceM / length;
        request(c);
        return;
      }
      if (Math.abs(length - mDistanceM) <= KEEP_TOLERANCE * mDistanceM)
        mFound.add(measure(path, p, c.directionDeg));
      done();
    });
  }

  private void done()
  {
    mIndex++;
    mListener.onProgress(mIndex, mCandidates.size());
    next();
  }

  @NonNull
  private Loop measure(@NonNull RoutedPath path, @NonNull double[][] points, double directionDeg)
  {
    final double length = path.line.totalM();
    final double[] upDown = path.ele == null ? null : RouteStats.ascentDescent(path.ele);
    final double up = upDown == null ? Double.NaN : upDown[0];
    final double retrace = retrace(path.line);
    double climbMiss = 0;
    if (!Double.isNaN(mClimbPerKm))
    {
      final double target = mClimbPerKm * length / 1000;
      climbMiss = Double.isNaN(up) ? 0.5 : Math.min(1, Math.abs(up - target) / Math.max(target, 100));
    }
    final double score = Math.abs(length - mDistanceM) / mDistanceM + 0.5 * climbMiss + 2 * retrace;
    return new Loop(path, points[0], points[1], directionDeg, up, retrace, score);
  }

  /**
   * The share of a line that runs within 25 m of a part of it at least 300 m further on (an out-and-back is about
   * half): samples every 25 m, hashed in 25 m cells.
   */
  static double retrace(@NonNull FollowRoute line)
  {
    final double total = line.totalM();
    final int n = (int) (total / RETRACE_SAMPLE_M) + 1;
    if (n < 2)
      return 0;
    final double lat0 = line.startLat();
    final double cos = Math.cos(Math.toRadians(lat0));
    final double[] x = new double[n], y = new double[n], at = new double[n];
    final Map<Long, List<Integer>> cells = new HashMap<>();
    for (int i = 0; i < n; i++)
    {
      at[i] = Math.min(total, i * RETRACE_SAMPLE_M);
      x[i] = (line.lonAt(at[i]) - line.startLon()) * Geo.M_PER_DEG * cos;
      y[i] = (line.latAt(at[i]) - lat0) * Geo.M_PER_DEG;
      cells.computeIfAbsent(cell(x[i], y[i]), k -> new ArrayList<>()).add(i);
    }
    int retraced = 0;
    for (int i = 0; i < n; i++)
    {
      final long cx = (long) Math.floor(x[i] / RETRACE_NEAR_M);
      final long cy = (long) Math.floor(y[i] / RETRACE_NEAR_M);
      boolean found = false;
      for (long dx = -1; dx <= 1 && !found; dx++)
        for (long dy = -1; dy <= 1 && !found; dy++)
        {
          final List<Integer> in = cells.get(key(cx + dx, cy + dy));
          if (in == null)
            continue;
          for (int j : in)
            if (at[j] >= at[i] + RETRACE_LATER_M && Math.hypot(x[j] - x[i], y[j] - y[i]) <= RETRACE_NEAR_M)
            {
              found = true;
              break;
            }
        }
      if (found)
        retraced++;
    }
    return (double) retraced / n;
  }

  private static long cell(double x, double y)
  {
    return key((long) Math.floor(x / RETRACE_NEAR_M), (long) Math.floor(y / RETRACE_NEAR_M));
  }

  private static long key(long cx, long cy)
  {
    return (cx << 32) ^ (cy & 0xffffffffL);
  }

  /** Best first; then the best heading at least 90° away from those picked; three at most. */
  @NonNull
  static List<Loop> pick(@NonNull List<Loop> found)
  {
    List<Loop> fit = new ArrayList<>();
    for (Loop l : found)
      if (l.retrace <= MAX_RETRACE)
        fit.add(l);
    // Riding some road twice beats no loop at all.
    if (fit.size() < 2)
      fit = new ArrayList<>(found);
    fit.sort(Comparator.comparingDouble(l -> l.score));
    final List<Loop> picked = new ArrayList<>();
    for (Loop l : fit)
    {
      boolean apart = true;
      for (Loop p : picked)
        if (Math.abs(Geo.normalizeAngle(l.directionDeg - p.directionDeg)) < PICK_APART_DEG)
          apart = false;
      if (apart)
        picked.add(l);
      if (picked.size() == PICK)
        break;
    }
    return picked;
  }

  /** The point {@code m} metres from ({@code lat}, {@code lon}) on bearing {@code deg} (flat, fine at loop sizes). */
  @NonNull
  static double[] offset(double lat, double lon, double deg, double m)
  {
    final double rad = Math.toRadians(deg);
    final double dLat = m * Math.cos(rad) / Geo.M_PER_DEG;
    final double dLon = m * Math.sin(rad) / (Geo.M_PER_DEG * Math.cos(Math.toRadians(lat)));
    return new double[] {lat + dLat, lon + dLon};
  }

  /** The compass direction's name index: 0 north, 1 north-east… 7 north-west. */
  public static int directionIndex(double deg)
  {
    return (int) Math.round(((deg % 360) + 360) % 360 / 45) % 8;
  }
}
