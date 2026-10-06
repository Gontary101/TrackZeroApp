package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.base.TzRouter;
import app.trackzero.routes.Profile;
import java.util.ArrayList;
import java.util.List;

/**
 * A path to a place a bike cannot reach (BUG-001, plan v2 Phase 27): ride as far as a bike can go, then walk. Asked
 * for a bicycle path that has no way (or no road at the end), it gets the walking path to the place and tries the
 * bicycle to points on it before the end, nearest the place first; the first that works is where the bike is left.
 * Pure: the router is an interface, its answers come back one at a time on one thread.
 */
public final class BikeWalkPlanner
{
  /** Where the bike may be left: this far before the end of the walking path, nearest the place first. */
  static final double[] CANDIDATES_M = {50, 150, 400, 1000, 2500, 6000};
  /** Each try to a candidate gives up after this long. */
  static final int TRY_TIMEOUT_S = 8;

  public interface Router
  {
    /** One path; {@code timeoutSec} 0 for the default. The answer comes through {@code answer}. */
    void route(double fromLat, double fromLon, double toLat, double toLon, @NonNull Profile profile, double bearingDeg,
               int timeoutSec, @NonNull Answer answer);
  }

  public interface Answer
  {
    void onPath(@Nullable RoutedPath path, @NonNull TzRouter.Reason reason);
  }

  /** What the rider is offered. */
  public static final class Result
  {
    public enum Kind
    {
      /** A bicycle path all the way, as before. */
      BIKE,
      /** Ride to where a bike can go, then walk ({@link RoutedPath#walkFromM}). */
      BIKE_AND_WALK,
      /** No bike access at all: the walking path, offered as a walk. */
      WALK_ONLY,
      /** Nothing: {@link #reason} says why (no map, no route at all, cancelled…). */
      NONE
    }

    @NonNull
    public final Kind kind;
    @Nullable
    public final RoutedPath path;
    @NonNull
    public final TzRouter.Reason reason;

    Result(@NonNull Kind kind, @Nullable RoutedPath path, @NonNull TzRouter.Reason reason)
    {
      this.kind = kind;
      this.path = path;
      this.reason = reason;
    }
  }

  public interface Listener
  {
    void onResult(@NonNull Result result);
  }

  @NonNull
  private final Router mRouter;
  private boolean mCancelled;

  public BikeWalkPlanner(@NonNull Router router)
  {
    mRouter = router;
  }

  /** No answer after this. */
  public void cancel()
  {
    mCancelled = true;
  }

  /** A path to the place with {@code bike} (a bicycle profile), or the best the planner can offer instead. */
  public void plan(double fromLat, double fromLon, double toLat, double toLon, @NonNull Profile bike,
                   double bearingDeg, @NonNull Listener listener)
  {
    mRouter.route(fromLat, fromLon, toLat, toLon, bike, bearingDeg, 0, (path, reason) -> {
      if (mCancelled)
        return;
      if (path != null)
        listener.onResult(new Result(Result.Kind.BIKE, path, TzRouter.Reason.OK));
      else if (reason != TzRouter.Reason.NO_WAY && reason != TzRouter.Reason.NO_END)
        listener.onResult(new Result(Result.Kind.NONE, null, reason)); // No map, cancelled…: as before.
      else
        walk(fromLat, fromLon, toLat, toLon, bike, bearingDeg, Profile.WALK, listener);
    });
  }

  /** The walking path to the place (a hike's if walking finds none: the place may be on a trail). */
  private void walk(double fromLat, double fromLon, double toLat, double toLon, @NonNull Profile bike,
                    double bearingDeg, @NonNull Profile foot, @NonNull Listener listener)
  {
    mRouter.route(fromLat, fromLon, toLat, toLon, foot, bearingDeg, 0, (walk, reason) -> {
      if (mCancelled)
        return;
      if (walk == null)
      {
        if (foot == Profile.WALK && reason != TzRouter.Reason.NO_MAP && reason != TzRouter.Reason.CANCELLED)
          walk(fromLat, fromLon, toLat, toLon, bike, bearingDeg, Profile.HIKE, listener);
        else
          listener.onResult(new Result(Result.Kind.NONE, null,
                                       reason == TzRouter.Reason.NO_MAP ? reason : TzRouter.Reason.NO_WAY));
        return;
      }
      final List<Double> candidates = new ArrayList<>();
      for (double before : CANDIDATES_M)
        if (before < walk.line.totalM())
          candidates.add(walk.line.totalM() - before);
      tryCandidate(fromLat, fromLon, bike, bearingDeg, walk, candidates, 0, listener);
    });
  }

  private void tryCandidate(double fromLat, double fromLon, @NonNull Profile bike, double bearingDeg,
                            @NonNull RoutedPath walk, @NonNull List<Double> candidates, int i,
                            @NonNull Listener listener)
  {
    if (i >= candidates.size())
    {
      // No bike access (or the walk is too short to bother): the walk alone.
      listener.onResult(new Result(Result.Kind.WALK_ONLY, walk, TzRouter.Reason.OK));
      return;
    }
    final double at = candidates.get(i);
    mRouter.route(fromLat, fromLon, walk.line.latAt(at), walk.line.lonAt(at), bike, bearingDeg, TRY_TIMEOUT_S,
                  (ride, reason) -> {
                    if (mCancelled)
                      return;
                    if (ride == null)
                      tryCandidate(fromLat, fromLon, bike, bearingDeg, walk, candidates, i + 1, listener);
                    else
                      listener.onResult(new Result(Result.Kind.BIKE_AND_WALK, join(ride, walk, at),
                                                   TzRouter.Reason.OK));
                  });
  }

  /**
   * The bicycle path, then the walking path from {@code walkFromM} (along it) to its end: one line, the bike's turns
   * with its arrival replaced by LEAVE_BIKE, then the walk's turns after that point.
   */
  @NonNull
  static RoutedPath join(@NonNull RoutedPath ride, @NonNull RoutedPath walk, double walkFromM)
  {
    final FollowRoute r = ride.line;
    final FollowRoute w = walk.line;
    final double[] rLat = r.lats(), rLon = r.lons(), wLat = w.lats(), wLon = w.lons();
    final double[] wDist = w.distances();
    final int n = rLat.length + 1 + wLat.length;
    final double[] lat = new double[n], lon = new double[n];
    final boolean withEle = ride.ele != null && walk.ele != null;
    final double[] ele = withEle ? new double[n] : null;
    int k = 0;
    for (int i = 0; i < rLat.length; i++, k++)
    {
      lat[k] = rLat[i];
      lon[k] = rLon[i];
      if (withEle)
        ele[k] = ride.ele[i];
    }
    // Where the bike is left: the point on the walk, unless the bike path ends exactly there.
    final int segment = segmentAt(wDist, walkFromM);
    final double pLat = w.latAt(walkFromM), pLon = w.lonAt(walkFromM);
    if (pLat != lat[k - 1] || pLon != lon[k - 1])
    {
      lat[k] = pLat;
      lon[k] = pLon;
      if (withEle)
      {
        final double span = wDist[segment + 1] - wDist[segment];
        final double t = span > 0 ? (walkFromM - wDist[segment]) / span : 0;
        ele[k] = walk.ele[segment] + t * (walk.ele[segment + 1] - walk.ele[segment]);
      }
      k++;
    }
    final int walkStart = k - 1;
    for (int i = segment + 1; i < wLat.length; i++)
    {
      if (wDist[i] <= walkFromM || (wLat[i] == lat[k - 1] && wLon[i] == lon[k - 1]))
        continue;
      lat[k] = wLat[i];
      lon[k] = wLon[i];
      if (withEle)
        ele[k] = walk.ele[i];
      k++;
    }
    final FollowRoute line = new FollowRoute(trim(lat, k), trim(lon, k));
    final double walkFrom = line.distanceAt(walkStart);

    final List<RouteManeuver> maneuvers = new ArrayList<>();
    for (RouteManeuver m : ride.maneuvers)
    {
      if (m.type == Maneuver.ARRIVE)
        continue;
      maneuvers.add(new RouteManeuver(m.type, m.at, m.exit, m.street == null ? "" : m.street));
    }
    maneuvers.add(new RouteManeuver(Maneuver.LEAVE_BIKE, walkFrom, 0, ""));
    for (RouteManeuver m : walk.maneuvers)
    {
      if (m.at <= walkFromM)
        continue;
      final double at = m.type == Maneuver.ARRIVE ? line.totalM() : Math.min(line.totalM(), walkFrom + m.at - walkFromM);
      maneuvers.add(new RouteManeuver(m.type, at, m.exit, m.street == null ? "" : m.street));
    }
    if (maneuvers.get(maneuvers.size() - 1).type != Maneuver.ARRIVE)
      maneuvers.add(new RouteManeuver(Maneuver.ARRIVE, line.totalM(), 0, ""));
    return new RoutedPath(line, maneuvers, withEle ? trim(ele, k) : null, walkFrom);
  }

  private static int segmentAt(@NonNull double[] cum, double at)
  {
    int i = 0;
    while (i < cum.length - 2 && cum[i + 1] < at)
      i++;
    return i;
  }

  @NonNull
  private static double[] trim(@NonNull double[] a, int n)
  {
    final double[] out = new double[n];
    System.arraycopy(a, 0, out, 0, n);
    return out;
  }
}
