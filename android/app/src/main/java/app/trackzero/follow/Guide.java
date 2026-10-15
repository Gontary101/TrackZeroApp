package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.geo.Geo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turn-by-turn for a ride (Phase 15): the route followed, and the routed legs that get the rider onto it. On a track
 * ride a leg leads to the start when it is more than 200 m away, and back onto the track after leaving it (to its
 * closest point 200 m to 2 km further on). On a ride to a place the route itself is a routed path, worked out again
 * from wherever the rider leaves it. Paths come from the bicycle router through {@link Paths}; until one arrives the
 * engine's own guidance stands (head to start, off route). Pure; one thread.
 *
 * <p>Stops (Phase 30): a place the rider adds is routed to at once, to know it can be reached, then kept in a list in
 * route order. When the rider is on the route within 2 km of the next one, a leg leads there; the route itself is
 * followed quietly meanwhile. Reaching it (30 m) ends the leg, and the usual rejoin (or a new path to the place)
 * takes the rider on. A stop passed by 500 m on the route is dropped.
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
  /** The leg to the next stop starts this far before it along the route... */
  static final double STOP_LEAD_M = 2000;
  /** ...and a stop this far behind the rider on the route was passed by. */
  static final double STOP_PASSED_M = 500;

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

  /** What happens to stops (Phase 30), for the journal and the voice. */
  public interface StopEvents
  {
    /** Routed to and kept. */
    default void onStopAdded(@NonNull Stop stop) {}

    /** No path to it: nothing changed. */
    default void onStopFailed(@NonNull Stop stop) {}

    /** The rider is there (within 30 m). */
    default void onStopReached(@NonNull Stop stop) {}

    /** Removed by the rider, or passed by on the route. */
    default void onStopDropped(@NonNull Stop stop) {}
  }

  /** A stop and where it is on the route (worked out again when the route changes). */
  private static final class Entry
  {
    @NonNull
    final Stop stop;
    double along;
    int alongFor = -1;

    Entry(@NonNull Stop stop)
    {
      this.stop = stop;
    }
  }

  private enum Want
  {
    NONE,
    START,
    REJOIN,
    DESTINATION,
    /** A path to a stop just added: can it be reached? */
    STOP_CHECK,
    /** The leg to the next stop. */
    STOP
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
  /** Stops in route order. */
  private final List<Entry> mStops = new ArrayList<>();
  /** The stop being checked (STOP_CHECK), or the one a leg is wanted or followed to. */
  @Nullable
  private Entry mChecking;
  @Nullable
  private Entry mLegStop;
  @NonNull
  private StopEvents mStopEvents = new StopEvents() {};
  /** False in the battery saver (Phase 35): no paths asked for; off the route only warns. */
  private boolean mRerouting = true;

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
        if (m.type != Maneuver.JOIN && m.type != Maneuver.STOP)
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
    if (mLegStop != null)
    {
      planStopLeg();
      return;
    }
    if (mPending == Want.STOP_CHECK)
      return;
    if (mLeg == null && onIt(main) && nextStopDue(main))
      return;
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

  /** On the way to a stop: there, or off the leg (a new one), or still riding it. */
  private void planStopLeg()
  {
    final FollowEngine leg = mLeg;
    final Entry stop = mLegStop;
    final FollowEngine main = mMain;
    if (main != null && onIt(main) && main.along() - alongOf(stop) > STOP_PASSED_M)
    {
      // Ridden on past it along the route: not wanted after all.
      mStops.remove(stop);
      endStopLeg();
      mStopEvents.onStopDropped(stop.stop);
      return;
    }
    if (leg == null)
    {
      // The leg is wanted but no path came yet (or the last one failed): ask again when allowed.
      request(Want.STOP);
      return;
    }
    if (leg.phase() == FollowEngine.Phase.ARRIVED)
    {
      mStops.remove(stop);
      endStopLeg();
      mStopEvents.onStopReached(stop.stop);
    }
    else if (leg.phase() == FollowEngine.Phase.OFF_ROUTE)
    {
      request(Want.STOP);
    }
  }

  /**
   * On the route with no leg: whether the next stop wants its leg now (true: asked for). Stops passed by are dropped
   * first.
   */
  private boolean nextStopDue(@NonNull FollowEngine main)
  {
    while (!mStops.isEmpty())
    {
      final Entry next = mStops.get(0);
      final double ahead = alongOf(next) - main.along();
      if (ahead < -STOP_PASSED_M)
      {
        mStops.remove(0);
        mStopEvents.onStopDropped(next.stop);
        continue;
      }
      if (ahead > STOP_LEAD_M)
        return false;
      mLegStop = next;
      main.setQuiet(true);
      cancel();
      request(Want.STOP);
      return true;
    }
    return false;
  }

  private double alongOf(@NonNull Entry e)
  {
    if (e.alongFor != mLines && mMain != null)
    {
      // From a little behind the rider: of an out-and-back's two legs, the one still to ride.
      e.along = Stops.alongOf(mMain.route(), e.stop.lat, e.stop.lon, mMain.along() - STOP_PASSED_M, mMain.totalM());
      e.alongFor = mLines;
    }
    return e.along;
  }

  /** The rider is back to following the route (or its path) by themselves. */
  private void endStopLeg()
  {
    mLegStop = null;
    if (mPending == Want.STOP)
      cancel();
    if (mLeg != null)
      dropLeg();
    if (mMain != null)
      mMain.setQuiet(false);
  }

  /** On the route, either way along it. */
  private static boolean onIt(@NonNull FollowEngine e)
  {
    return e.phase() == FollowEngine.Phase.ON_ROUTE || e.phase() == FollowEngine.Phase.WRONG_WAY;
  }

  private void request(@NonNull Want want)
  {
    if (!mRerouting || !mHasFix || mPending != Want.NONE || mElapsedMs < mNextRequestMs)
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
    else if (want == Want.STOP_CHECK || want == Want.STOP)
    {
      final Entry stop = want == Want.STOP ? mLegStop : mChecking;
      toLat = stop.stop.lat;
      toLon = stop.stop.lon;
      mPendingEndAlong = alongOf(stop);
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
    if (want == Want.STOP_CHECK)
    {
      onCheck(path);
      return;
    }
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
    if (want == Want.STOP)
    {
      if (mLegStop != null)
        startStopLeg(mLegStop, path);
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

  private void startStopLeg(@NonNull Entry stop, @NonNull RoutedPath path)
  {
    final List<RouteManeuver> maneuvers = path.maneuvers(Maneuver.STOP);
    maneuvers.get(maneuvers.size() - 1).street = stop.stop.name;
    mLeg = new FollowEngine(path.line, maneuvers, mLegEvents);
    mLeg.setFoot(mFoot);
    mLegStop = stop;
    mLegEndAlong = alongOf(stop);
    if (mMain != null)
      mMain.setQuiet(true);
    mLines++;
    place(mLeg);
  }

  /**
   * The battery saver turns rerouting off: no path is asked for (a leg already followed stays). Turning it back on
   * asks at once if the rider needs one (off the route, or a leg to a stop wanted).
   */
  public void setRerouting(boolean on)
  {
    if (on == mRerouting)
      return;
    mRerouting = on;
    if (!on)
    {
      if (mPending != Want.NONE && mPending != Want.STOP_CHECK)
        cancel();
      return;
    }
    mNextRequestMs = 0;
    mBackoffMs = RETRY_MIN_MS;
    if (mHasFix)
      plan();
  }

  public boolean rerouting()
  {
    return mRerouting;
  }

  // ---- Stops ----

  /** Receives what happens to stops. */
  public void setStopEvents(@NonNull StopEvents events)
  {
    mStopEvents = events;
  }

  /**
   * Adds a stop: a path to it is worked out first, and {@link StopEvents#onStopAdded} or
   * {@link StopEvents#onStopFailed} says how it went. Replaces a request in flight (it is asked again later).
   */
  public void addStop(@NonNull Stop stop)
  {
    if (!mHasFix || mMain == null || mChecking != null)
    {
      mStopEvents.onStopFailed(stop);
      return;
    }
    if (mPending != Want.NONE)
      cancel();
    mChecking = new Entry(stop);
    mPending = Want.STOP_CHECK;
    mPaths.request(mLat, mLon, stop.lat, stop.lon, mCourseDeg, false);
  }

  private void onCheck(@Nullable RoutedPath path)
  {
    final Entry entry = mChecking;
    mChecking = null;
    if (entry == null)
      return;
    if (path == null)
    {
      mStopEvents.onStopFailed(entry.stop);
      return;
    }
    insert(entry);
    mStopEvents.onStopAdded(entry.stop);
    // The next stop and close enough: this path is its leg. Otherwise the route stays as it is until then.
    final FollowEngine main = mMain;
    if (main != null && mLegStop == null && mStops.get(0) == entry
        && alongOf(entry) - main.along() <= STOP_LEAD_M)
      startStopLeg(entry, path);
  }

  private void insert(@NonNull Entry entry)
  {
    int i = 0;
    while (i < mStops.size() && alongOf(mStops.get(i)) <= alongOf(entry))
      i++;
    mStops.add(i, entry);
  }

  /** Stops kept from before a restore: already checked, added as they are. */
  public void restoreStops(@NonNull List<Stop> stops)
  {
    for (Stop s : stops)
      insert(new Entry(s));
  }

  /** Removes a stop (the rider changed their mind); a leg to it ends. */
  public void removeStop(@NonNull Stop stop)
  {
    for (int i = 0; i < mStops.size(); i++)
    {
      final Entry e = mStops.get(i);
      if (!e.stop.same(stop))
        continue;
      mStops.remove(i);
      if (mLegStop == e)
        endStopLeg();
      mStopEvents.onStopDropped(e.stop);
      return;
    }
  }

  /** The stops still to come, in route order. */
  @NonNull
  public List<Stop> stops()
  {
    final List<Stop> out = new ArrayList<>(mStops.size());
    for (Entry e : mStops)
      out.add(e.stop);
    return Collections.unmodifiableList(out);
  }

  /** A stop being checked (S2 waits for it). */
  public boolean checkingStop()
  {
    return mChecking != null;
  }

  /** The stop the current leg leads to, or null. */
  @Nullable
  public Stop legStop()
  {
    return mLegStop == null || mLeg == null ? null : mLegStop.stop;
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
        return leg.turnState().asHeadToPath();
      // Off a stop's leg but on the route itself (past the turn-off): the route guides while a new leg is asked
      // for; the rider is not lost (Phase 38).
      if (mLegStop != null && main != null && onIt(main))
        return main.turnState();
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
