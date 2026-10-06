package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.geo.Geo;

/**
 * Turn-by-turn for a ride (Phase 15): the route followed, and the routed legs that get the rider onto it. On a track
 * ride a leg leads to the start when it is more than 200 m away, and back onto the track after leaving it (to its
 * closest point 200 m to 2 km further on). On a ride to a place the route itself is a routed path, worked out again
 * from wherever the rider leaves it. Paths come from the bicycle router through {@link Paths}; until one arrives the
 * engine's own guidance stands (head to start, off route). Pure; one thread.
 */
public final class Guide
{
  /** A leg to the start of the track when it is further than this. */
  static final double ROUTE_TO_START_M = 200;
  /** Back onto the track this far ahead of where the rider left it, at least... */
  static final double REJOIN_AHEAD_M = 200;
  /** ...and at most this far: further on, the rider would skip part of the route. */
  static final double REJOIN_WINDOW_M = 2000;
  static final double REJOIN_STEP_M = 25;
  /** No new path sooner than this after the last one; after failures, twice as long each time up to the max. */
  static final long RETRY_MIN_MS = 10_000;
  static final long RETRY_MAX_MS = 5 * 60_000;

  /** The bicycle router. A request replaces the one before; the answer comes back through {@link #onPath}. */
  public interface Paths
  {
    /**
     * @param bearingDeg the rider's heading, NaN when unknown
     * @param onFoot     a walking path, whatever the ride's profile (after the bike was left, Phase 27)
     */
    void request(double fromLat, double fromLon, double toLat, double toLon, double bearingDeg, boolean onFoot);

    void cancel();
  }

  private enum Want
  {
    NONE,
    START,
    REJOIN,
    DESTINATION
  }

  @NonNull
  private final Paths mPaths;
  @NonNull
  private final FollowEngine.Events mEvents;
  /** Only turns are announced on a leg: reaching its end is not arriving, and leaving it is handled here. */
  @NonNull
  private final FollowEngine.Events mLegEvents;
  private final boolean mToPlace;
  private final double mPlaceLat;
  private final double mPlaceLon;
  /** On foot (Walk, Hike): the engines it makes follow at walking speeds (Phase 25). */
  private final boolean mFoot;

  @Nullable
  private FollowEngine mMain;
  /** The path of a ride to a place (its altitudes make the climbs). */
  @Nullable
  private RoutedPath mMainPath;
  @Nullable
  private FollowEngine mLeg;
  private double mLegEndAlong;
  @NonNull
  private Want mPending = Want.NONE;
  private double mPendingEndAlong;
  /** The pending path is the ride to where the bike is left, to be joined to {@link #mWalkTail} (Phase 27). */
  private boolean mPendingToBike;
  /** A path to a place bikes cannot reach: the walked part, kept for a new ride to where the bike is left. */
  @Nullable
  private RoutedPath mWalkTail;
  private long mNextRequestMs;
  private long mBackoffMs = RETRY_MIN_MS;
  private int mLines;

  // The last fix, to place a new engine at once.
  private boolean mHasFix;
  private double mLat;
  private double mLon;
  private double mCourseDeg = Double.NaN;
  private double mSpeedMps;
  private long mElapsedMs;
  private double mRideDistM;
  private long mRideMovingMs;

  private Guide(@NonNull Paths paths, @NonNull FollowEngine.Events events, boolean toPlace, double placeLat,
                double placeLon, boolean foot)
  {
    mFoot = foot;
    mPaths = paths;
    mEvents = events;
    mToPlace = toPlace;
    mPlaceLat = placeLat;
    mPlaceLon = placeLon;
    mLegEvents = new FollowEngine.Events() {
      @Override
      public void onTurnSoon(@NonNull RouteManeuver m, double distanceM)
      {
        mEvents.onTurnSoon(m, distanceM);
      }

      @Override
      public void onTurnNow(@NonNull RouteManeuver m)
      {
        if (m.type != Maneuver.JOIN)
          mEvents.onTurnNow(m);
      }

      @Override
      public void onWrongWay()
      {
        mEvents.onWrongWay();
      }
    };
  }

  /** Following a track with {@code engine}, built with the same events. */
  @NonNull
  public static Guide forTrack(@NonNull FollowEngine engine, @NonNull Paths paths, @NonNull FollowEngine.Events events)
  {
    return forTrack(engine, paths, events, false);
  }

  /** As above; {@code foot}: on foot, set on {@code engine} too. */
  @NonNull
  public static Guide forTrack(@NonNull FollowEngine engine, @NonNull Paths paths, @NonNull FollowEngine.Events events,
                               boolean foot)
  {
    final Guide g = new Guide(paths, events, false, Double.NaN, Double.NaN, foot);
    engine.setFoot(foot);
    g.mMain = engine;
    return g;
  }

  /** Riding to a place; {@code path} is the one previewed, or null to work one out from the first fix. */
  @NonNull
  public static Guide forPlace(double lat, double lon, @Nullable RoutedPath path, @NonNull Paths paths,
                               @NonNull FollowEngine.Events events)
  {
    return forPlace(lat, lon, path, paths, events, false);
  }

  /** As above, {@code foot}: on foot. */
  @NonNull
  public static Guide forPlace(double lat, double lon, @Nullable RoutedPath path, @NonNull Paths paths,
                               @NonNull FollowEngine.Events events, boolean foot)
  {
    final Guide g = new Guide(paths, events, true, lat, lon, foot);
    if (path != null)
      g.setMain(path);
    return g;
  }

  /** One fix; same arguments as {@link FollowEngine#update}. */
  public void update(double lat, double lon, double courseDeg, double speedMps, long elapsedMs, double rideDistM,
                     long rideMovingMs)
  {
    mHasFix = true;
    mLat = lat;
    mLon = lon;
    if (!Double.isNaN(courseDeg)
        && speedMps >= (mFoot ? FollowEngine.COURSE_MIN_FOOT_MPS : FollowEngine.COURSE_MIN_MPS))
      mCourseDeg = courseDeg;
    mSpeedMps = speedMps;
    mElapsedMs = elapsedMs;
    mRideDistM = rideDistM;
    mRideMovingMs = rideMovingMs;
    place(mMain);
    place(mLeg);
    plan();
  }

  private void place(@Nullable FollowEngine e)
  {
    if (e != null && mHasFix)
      e.update(mLat, mLon, mCourseDeg, mSpeedMps, mElapsedMs, mRideDistM, mRideMovingMs);
  }

  private void plan()
  {
    final FollowEngine main = mMain;
    if (main == null)
    {
      request(Want.DESTINATION);
      return;
    }
    if (mToPlace)
    {
      if (main.phase() == FollowEngine.Phase.OFF_ROUTE)
        request(Want.DESTINATION);
      return;
    }
    final FollowEngine leg = mLeg;
    if (leg != null)
    {
      // On the track (it has the turns now), or at the leg's end without being on the track: done either way.
      if (onIt(main) || leg.phase() == FollowEngine.Phase.ARRIVED)
        dropLeg();
      else if (leg.phase() == FollowEngine.Phase.OFF_ROUTE)
        request(main.phase() == FollowEngine.Phase.HEAD_TO_START ? Want.START : Want.REJOIN);
      return;
    }
    if (main.phase() == FollowEngine.Phase.HEAD_TO_START && main.toStartM() > ROUTE_TO_START_M)
      request(Want.START);
    else if (main.phase() == FollowEngine.Phase.OFF_ROUTE)
      request(Want.REJOIN);
    else if (mPending != Want.NONE && onIt(main))
      cancel();
  }

  /** On the route, either way along it. */
  private static boolean onIt(@NonNull FollowEngine e)
  {
    return e.phase() == FollowEngine.Phase.ON_ROUTE || e.phase() == FollowEngine.Phase.WRONG_WAY;
  }

  private void request(@NonNull Want want)
  {
    if (!mHasFix || mPending != Want.NONE || mElapsedMs < mNextRequestMs)
      return;
    final double toLat, toLon;
    boolean onFoot = false;
    mPendingToBike = false;
    final RoutedPath main = mMainPath;
    if (want == Want.DESTINATION && main != null && main.bikeAndWalk() && mWalkTail != null
        && mMain.along() < main.walkFromM)
    {
      // Left a bike + walk path before the bike was left: by bike back to that point, then the same walk.
      toLat = mWalkTail.line.startLat();
      toLon = mWalkTail.line.startLon();
      mPendingToBike = true;
      mPendingEndAlong = 0;
    }
    else if (want == Want.DESTINATION)
    {
      toLat = mPlaceLat;
      toLon = mPlaceLon;
      // After the bike was left, the way to the place is on foot.
      onFoot = main != null && main.bikeAndWalk();
      mPendingEndAlong = 0;
    }
    else
    {
      final FollowRoute track = mMain.route();
      mPendingEndAlong = want == Want.START ? 0 : rejoinAlong(track, mMain.along());
      toLat = track.latAt(mPendingEndAlong);
      toLon = track.lonAt(mPendingEndAlong);
    }
    mPending = want;
    mPaths.request(mLat, mLon, toLat, toLon, mCourseDeg, onFoot);
  }

  /** The track's closest point to the rider between 200 m and 2 km past {@code along} (its end if nearer). */
  private double rejoinAlong(@NonNull FollowRoute track, double along)
  {
    final double from = Math.min(along + REJOIN_AHEAD_M, track.totalM());
    final double to = Math.min(along + REJOIN_WINDOW_M, track.totalM());
    double best = from;
    double bestM = Double.MAX_VALUE;
    for (double a = from; a <= to; a += REJOIN_STEP_M)
    {
      final double d = Geo.distance(mLat, mLon, track.latAt(a), track.lonAt(a));
      if (d < bestM)
      {
        bestM = d;
        best = a;
      }
    }
    return best;
  }

  private void cancel()
  {
    mPending = Want.NONE;
    mPaths.cancel();
  }

  private void dropLeg()
  {
    mLeg = null;
    mLines++;
  }

  private void setMain(@NonNull RoutedPath path)
  {
    final FollowEngine e = new FollowEngine(path.line, path.maneuvers(Maneuver.ARRIVE), mEvents);
    e.setFoot(mFoot);
    if (mMain != null)
      e.inheritSpeed(mMain);
    mMain = e;
    mMainPath = path;
    if (path.bikeAndWalk())
      e.setWalkFrom(path.walkFromM);
    mWalkTail = path.walkTail();
    mLeg = null;
    mLines++;
  }

  /** The answer to the last request: a path, or null when there is none (no map, no way, cancelled). */
  public void onPath(@Nullable RoutedPath path)
  {
    final Want want = mPending;
    mPending = Want.NONE;
    if (want == Want.NONE)
      return;
    if (path == null)
    {
      mNextRequestMs = mElapsedMs + mBackoffMs;
      mBackoffMs = Math.min(2 * mBackoffMs, RETRY_MAX_MS);
      return;
    }
    mBackoffMs = RETRY_MIN_MS;
    mNextRequestMs = mElapsedMs + RETRY_MIN_MS;
    if (want == Want.DESTINATION)
    {
      final RoutedPath was = mMainPath;
      if (mPendingToBike && mWalkTail != null)
        setMain(BikeWalkPlanner.join(path, mWalkTail, 0));
      else if (was != null && was.bikeAndWalk())
        setMain(new RoutedPath(path.line, path.maneuvers, path.ele, 0)); // All on foot from here.
      else
        setMain(path);
      place(mMain);
      return;
    }
    // Back on the track by themselves while the path was worked out: no leg.
    if (mMain == null || onIt(mMain))
      return;
    mLeg = new FollowEngine(path.line, path.maneuvers(Maneuver.JOIN), mLegEvents);
    mLeg.setFoot(mFoot);
    mLegEndAlong = mPendingEndAlong;
    mLines++;
    place(mLeg);
  }

  // ---- Readings ----

  /** True once a fix has placed the rider on a route; before that there is nothing to show. */
  public boolean placed()
  {
    return mMain != null && mMain.placed();
  }

  /** What the turn card shows; call when {@link #placed()}. */
  @NonNull
  public TurnState turnState()
  {
    final FollowEngine main = mMain;
    final boolean working = mPending != Want.NONE;
    final FollowEngine leg = mLeg;
    if (leg != null && leg.placed())
    {
      if (leg.phase() != FollowEngine.Phase.OFF_ROUTE)
        return leg.turnState();
      return working ? TurnState.recalculating(leg.offRouteM()) : TurnState.offRoute(leg.offRouteM());
    }
    if (main.phase() == FollowEngine.Phase.OFF_ROUTE && working)
      return TurnState.recalculating(main.offRouteM());
    return main.turnState();
  }

  /** R3: off the route (or the leg) shown. */
  public boolean offRoute()
  {
    return placed() && turnState().offRoute;
  }

  /** Along the leg (if any), then the rest of the route. */
  public double remainingM()
  {
    final FollowEngine main = mMain;
    if (main == null)
      return 0;
    final FollowEngine leg = mLeg;
    if (leg != null)
      return leg.remainingM() + Math.max(0, main.totalM() - mLegEndAlong);
    return main.remainingM();
  }

  public double etaSeconds()
  {
    if (mMain == null)
      return 0;
    // On the route itself its engine knows (a walked part goes at walking speed, Phase 27).
    return mLeg == null ? mMain.etaSeconds() : remainingM() / mMain.speedMps();
  }

  /** The rider's place on the route (where they left it while on a leg), metres. */
  public double along()
  {
    return mMain == null ? 0 : mMain.along();
  }

  /** The route followed: the track, or the current path to the place; null until there is one. */
  @Nullable
  public FollowRoute line()
  {
    return mMain == null ? null : mMain.route();
  }

  /** The routed path of a ride to a place, for its altitudes; null on a track ride. */
  @Nullable
  public RoutedPath placePath()
  {
    return mMainPath;
  }

  /** The leg being followed to the track, or null. */
  @Nullable
  public FollowRoute legLine()
  {
    return mLeg == null ? null : mLeg.route();
  }

  /** Changes whenever {@link #line()} or {@link #legLine()} does. */
  public int linesVersion()
  {
    return mLines;
  }

  public boolean toPlace()
  {
    return mToPlace;
  }
}
