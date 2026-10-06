package app.trackzero.follow;

import androidx.annotation.NonNull;
import app.trackzero.TzConfig;
import app.trackzero.geo.Geo;
import java.util.List;

/**
 * Turn-by-turn along an imported track (plan §10.2): where the rider is on the route, the next maneuvers, off-route
 * and arrival, the ETA, and when to announce what. Pure; driven on one thread with every fix.
 */
public final class FollowEngine
{
  /** The rider is this far from the first point before the ride has reached the route: head to the start. */
  static final double HEAD_TO_START_M = 60;
  /** The route counts as joined after this much of it, on it. */
  static final double JOINED_ALONG_M = 50;
  /** The route behind the last place still searched (GPS jitter, a few metres back). */
  static final double SNAP_BACK_M = 50;
  /** The route ahead always searched; more after a gap between fixes. */
  static final double SNAP_AHEAD_M = 500;
  static final double SNAP_AHEAD_MPS = 15;
  /** Speed under which the GPS course is noise; on foot, the lower one. */
  static final double COURSE_MIN_MPS = 2;
  static final double COURSE_MIN_FOOT_MPS = 1;
  /** The "then" row shows a maneuver at most this far after the next one. */
  static final double THEN_MAX_M = 1000;
  /** No "in 200 m" for a maneuver closer than this to the previous one (its "now" was just spoken). */
  static final double FAR_MIN_GAP_M = 250;
  /** No "in 200 m" this close to the maneuver: the "now" follows at once. */
  static final double FAR_MIN_M = TzConfig.VOICE_NEAR_M + 20;
  static final double ARRIVE_M = 30;
  /** The street after a maneuver is looked up this far after it, at two places: the leg must run along it. */
  static final double STREET_NEAR_M = 30;
  static final double STREET_FAR_M = 80;
  /**
   * Wrong way (BUG-002): for this long the course has been this far off the route's direction and the rider's place on
   * the route has gone back at least this much.
   */
  static final long WRONG_WAY_MS = 10_000;
  static final double WRONG_WAY_DEG = 120;
  static final double WRONG_WAY_BACK_M = 30;
  /** It clears when the course is within this of the route's direction for this long, or after this much forward. */
  static final double RIGHT_WAY_DEG = 90;
  static final long RIGHT_WAY_MS = 3_000;
  static final double RIGHT_WAY_AHEAD_M = 20;
  /** Not this close to a U-turn on the route: there the rider turns round with it (an out-and-back). */
  static final double WRONG_WAY_UTURN_M = 50;
  /** The route's direction at the rider's place is taken over this much of it on either side. */
  static final double ROUTE_BEARING_M = 10;
  static final long ETA_MIN_DATA_MS = 2 * 60_000;
  static final long ETA_SAMPLE_MS = 10_000;

  public enum Phase
  {
    HEAD_TO_START,
    ON_ROUTE,
    /** On the route, riding it backwards. */
    WRONG_WAY,
    OFF_ROUTE,
    ARRIVED
  }

  /** What to say or signal. Called during {@link #update}; all optional. */
  public interface Events
  {
    /** The maneuver is about {@code distanceM} ahead (≈ 200 m). */
    default void onTurnSoon(@NonNull RouteManeuver m, double distanceM) {}

    /** The maneuver is now (≤ 50 m). */
    default void onTurnNow(@NonNull RouteManeuver m) {}

    default void onOffRoute() {}

    /** Riding the route backwards; once per episode. */
    default void onWrongWay() {}

    default void onBackOnRoute() {}

    default void onArrived() {}

    /**
     * The street after {@code m} is wanted: the one at all the given points along the next leg. Set
     * {@link RouteManeuver#street} (on the engine's thread) when known.
     */
    default void onStreetWanted(@NonNull RouteManeuver m, @NonNull double[] lat, @NonNull double[] lon) {}
  }

  @NonNull
  private final FollowRoute mRoute;
  @NonNull
  private final List<RouteManeuver> mManeuvers;
  @NonNull
  private final Events mEvents;

  @NonNull
  private Phase mPhase = Phase.HEAD_TO_START;
  private boolean mJoined;
  private boolean mHasSnap;
  private double mAlong;
  private double mOffRouteM;
  private long mLastSnapMs;
  /** When the rider first went more than OFF_ROUTE_M from the route, or -1. */
  private long mFarSinceMs = -1;
  private double mToStartM;
  private double mBearingToStartDeg;
  private double mCourseDeg = Double.NaN;
  /** Index of the next maneuver ahead. */
  private int mNext;
  /** Since when the course has been against the route (-1: it is not), and the furthest place on it since then. */
  private long mAgainstSinceMs = -1;
  private double mAgainstFromAlong;
  /** While wrong way: since when the course has been with the route (-1: it is not); the place furthest back. */
  private long mWithSinceMs = -1;
  private double mWrongMinAlong;
  /** While wrong way: the remaining distance when the rider turned. */
  private double mWrongRemainingM;
  private final Snapper.Result mSnap = new Snapper.Result();
  private final Snapper.Result mWide = new Snapper.Result();

  // ETA: (moving time, ridden distance) every 10 s of moving time, over the last 15 min.
  private final long[] mEtaTime = new long[TzConfig.ETA_WINDOW_MIN * 60_000 / (int) ETA_SAMPLE_MS + 2];
  private final double[] mEtaDist = new double[mEtaTime.length];
  private int mEtaStart;
  private int mEtaCount;
  private double mSpeedMps = TzConfig.ETA_FALLBACK_KMH / 3.6;
  /** On foot (Phase 25): a slower ETA until enough data, and the course read at walking speeds. */
  private double mFallbackMps = TzConfig.ETA_FALLBACK_KMH / 3.6;
  private double mCourseMinMps = COURSE_MIN_MPS;
  /** Where the bike is left on a path to a place bikes cannot reach (Phase 27); -1: no walking. */
  private double mWalkFromM = -1;

  /** @param maneuvers from {@link ManeuverBuilder#build} for this route */
  public FollowEngine(@NonNull FollowRoute route, @NonNull List<RouteManeuver> maneuvers, @NonNull Events events)
  {
    mRoute = route;
    mManeuvers = maneuvers;
    mEvents = events;
  }

  /** On foot (Walk, Hike): call before the first fix. */
  public void setFoot(boolean foot)
  {
    mFallbackMps = (foot ? TzConfig.ETA_FALLBACK_FOOT_KMH : TzConfig.ETA_FALLBACK_KMH) / 3.6;
    mCourseMinMps = foot ? COURSE_MIN_FOOT_MPS : COURSE_MIN_MPS;
    mSpeedMps = mFallbackMps;
  }

  /** The route is walked from {@code m} on (the bike is left there): the ETA walks that part. */
  public void setWalkFrom(double m)
  {
    mWalkFromM = m;
  }

  /** Maneuvers from the track's shape alone. */
  public FollowEngine(@NonNull FollowRoute route, @NonNull Events events)
  {
    this(route, ManeuverBuilder.build(route, null), events);
  }

  /**
   * One fix.
   *
   * @param courseDeg  GPS course, NaN when unknown
   * @param rideDistM  ridden distance and moving time of the ride so far, for the ETA speed
   */
  public void update(double lat, double lon, double courseDeg, double speedMps, long elapsedMs, double rideDistM,
                     long rideMovingMs)
  {
    if (!Double.isNaN(courseDeg) && speedMps >= mCourseMinMps)
      mCourseDeg = courseDeg;
    final double course = speedMps >= mCourseMinMps ? courseDeg : Double.NaN;
    sampleSpeed(rideMovingMs, rideDistM);
    snap(lat, lon, course, elapsedMs);

    mToStartM = Geo.distance(lat, lon, mRoute.startLat(), mRoute.startLon());
    mBearingToStartDeg = Geo.bearing(lat, lon, mRoute.startLat(), mRoute.startLon());
    if (!mJoined && mAlong > JOINED_ALONG_M && mOffRouteM < TzConfig.OFF_ROUTE_M)
      mJoined = true;
    // From the start each time: a rider rejoining further back gets the turns again. Frozen while riding backwards.
    if (mPhase != Phase.WRONG_WAY)
    {
      mNext = 0;
      while (mNext < mManeuvers.size() - 1 && mManeuvers.get(mNext).at <= mAlong)
        mNext++;
    }

    final Phase before = mPhase;
    mPhase = phase(elapsedMs, course);
    if (mPhase != before)
    {
      if (mPhase == Phase.WRONG_WAY)
      {
        mWrongMinAlong = mAlong;
        mWithSinceMs = -1;
        mWrongRemainingM = Math.max(0, mRoute.totalM() - mAgainstFromAlong);
        mEvents.onWrongWay();
      }
      if (mPhase != Phase.ON_ROUTE)
        mAgainstSinceMs = -1;
      if (mPhase == Phase.OFF_ROUTE)
        mEvents.onOffRoute();
      else if (before == Phase.OFF_ROUTE && mPhase == Phase.ON_ROUTE)
        mEvents.onBackOnRoute();
      else if (mPhase == Phase.ARRIVED)
        mEvents.onArrived();
    }
    if (mPhase == Phase.ON_ROUTE)
      announce();
    requestStreets();
  }

  private void snap(double lat, double lon, double course, long elapsedMs)
  {
    if (!mHasSnap)
    {
      Snapper.snap(mRoute, lat, lon, course, 0, mRoute.totalM(), true, mSnap);
      take(mSnap);
    }
    else
    {
      final double gapS = Math.max(1, (elapsedMs - mLastSnapMs) / 1000.0);
      final double ahead = Math.max(SNAP_AHEAD_M, SNAP_AHEAD_MPS * gapS);
      Snapper.snap(mRoute, lat, lon, course, mAlong - SNAP_BACK_M, mAlong + ahead, false, mSnap);
      if (mSnap.distanceM <= TzConfig.OFF_ROUTE_M)
      {
        take(mSnap);
      }
      else
      {
        // Away from the route: back on it somewhere else (a shortcut)? Else the rider's place on it stays where
        // they left it, so coming back there finds it, and only the distance to the route changes.
        Snapper.snap(mRoute, lat, lon, course, 0, mRoute.totalM(), true, mWide);
        if (mWide.distanceM <= TzConfig.BACK_ON_ROUTE_M)
          take(mWide);
        else
          mOffRouteM = Math.min(mSnap.distanceM, mWide.distanceM);
      }
    }
    mHasSnap = true;
    mLastSnapMs = elapsedMs;
  }

  private void take(@NonNull Snapper.Result r)
  {
    mAlong = r.along;
    mOffRouteM = r.distanceM;
  }

  @NonNull
  private Phase phase(long elapsedMs, double course)
  {
    if (mPhase == Phase.ARRIVED)
      return Phase.ARRIVED;
    if (!mJoined)
      return mToStartM > HEAD_TO_START_M && mOffRouteM > TzConfig.BACK_ON_ROUTE_M ? Phase.HEAD_TO_START
                                                                                : Phase.ON_ROUTE;
    if (mOffRouteM > TzConfig.OFF_ROUTE_M)
    {
      if (mFarSinceMs < 0)
        mFarSinceMs = elapsedMs;
    }
    else
    {
      mFarSinceMs = -1;
    }
    if (mPhase == Phase.OFF_ROUTE)
      return mOffRouteM < TzConfig.BACK_ON_ROUTE_M ? Phase.ON_ROUTE : Phase.OFF_ROUTE;
    if (mFarSinceMs >= 0 && elapsedMs - mFarSinceMs >= TzConfig.OFF_ROUTE_SECONDS * 1000L)
      return Phase.OFF_ROUTE;
    if (wrongWay(elapsedMs, course))
      return Phase.WRONG_WAY;
    if (mAlong > mRoute.totalM() - ARRIVE_M)
      return Phase.ARRIVED;
    return Phase.ON_ROUTE;
  }

  /** Riding the route backwards (plan v2 21.2); {@code course} NaN when unknown. */
  private boolean wrongWay(long elapsedMs, double course)
  {
    final double diff = Double.isNaN(course) ? Double.NaN : Math.abs(Geo.normalizeAngle(course - routeBearing()));
    if (mPhase == Phase.WRONG_WAY)
    {
      mWrongMinAlong = Math.min(mWrongMinAlong, mAlong);
      if (mAlong >= mWrongMinAlong + RIGHT_WAY_AHEAD_M)
        return false;
      if (Double.isNaN(diff) || diff > RIGHT_WAY_DEG)
      {
        mWithSinceMs = -1;
        return true;
      }
      if (mWithSinceMs < 0)
        mWithSinceMs = elapsedMs;
      return elapsedMs - mWithSinceMs < RIGHT_WAY_MS;
    }
    if (Double.isNaN(diff) || diff <= WRONG_WAY_DEG || nearUTurn())
    {
      mAgainstSinceMs = -1;
      return false;
    }
    if (mAgainstSinceMs < 0)
    {
      mAgainstSinceMs = elapsedMs;
      mAgainstFromAlong = mAlong;
    }
    // Back from the furthest place: going forward with a bad course (slow, noisy) must not count as going back.
    mAgainstFromAlong = Math.max(mAgainstFromAlong, mAlong);
    return elapsedMs - mAgainstSinceMs >= WRONG_WAY_MS && mAgainstFromAlong - mAlong >= WRONG_WAY_BACK_M;
  }

  /** The route's compass direction at the rider's place. */
  private double routeBearing()
  {
    return mRoute.bearing(Math.max(0, mAlong - ROUTE_BEARING_M), Math.min(mRoute.totalM(), mAlong + ROUTE_BEARING_M));
  }

  private boolean nearUTurn()
  {
    for (RouteManeuver m : mManeuvers)
      if (m.type == Maneuver.UTURN && Math.abs(m.at - mAlong) <= WRONG_WAY_UTURN_M)
        return true;
    return false;
  }

  private void announce()
  {
    final RouteManeuver m = mManeuvers.get(mNext);
    if (m.type == Maneuver.ARRIVE)
      return;
    final double dist = m.at - mAlong;
    if (!m.farAnnounced && dist <= TzConfig.VOICE_FAR_M)
    {
      m.farAnnounced = true;
      final double previousAt = mNext > 0 ? mManeuvers.get(mNext - 1).at : 0;
      if (dist > FAR_MIN_M && m.at - previousAt >= FAR_MIN_GAP_M)
        mEvents.onTurnSoon(m, dist);
    }
    if (!m.nearAnnounced && dist <= TzConfig.VOICE_NEAR_M)
    {
      m.nearAnnounced = true;
      mEvents.onTurnNow(m);
    }
  }

  /** Street names for the next two maneuvers; the probes stay before the maneuver after. */
  private void requestStreets()
  {
    for (int i = mNext; i < Math.min(mNext + 2, mManeuvers.size() - 1); i++)
    {
      final RouteManeuver m = mManeuvers.get(i);
      if (m.streetRequested)
        continue;
      m.streetRequested = true;
      final double gap = mManeuvers.get(i + 1).at - m.at;
      final double near = m.at + Math.min(STREET_NEAR_M, gap / 3);
      final double far = m.at + Math.min(STREET_FAR_M, gap * 2 / 3);
      mEvents.onStreetWanted(m, new double[] {mRoute.latAt(near), mRoute.latAt(far)},
                             new double[] {mRoute.lonAt(near), mRoute.lonAt(far)});
    }
  }

  private void sampleSpeed(long movingMs, double distM)
  {
    final int cap = mEtaTime.length;
    if (mEtaCount > 0 && movingMs - mEtaTime[(mEtaStart + mEtaCount - 1) % cap] < ETA_SAMPLE_MS)
      return;
    if (mEtaCount == cap)
    {
      mEtaStart = (mEtaStart + 1) % cap;
      mEtaCount--;
    }
    mEtaTime[(mEtaStart + mEtaCount) % cap] = movingMs;
    mEtaDist[(mEtaStart + mEtaCount) % cap] = distM;
    mEtaCount++;
    final long window = TzConfig.ETA_WINDOW_MIN * 60_000L;
    while (mEtaCount > 1 && movingMs - mEtaTime[mEtaStart] > window)
    {
      mEtaStart = (mEtaStart + 1) % cap;
      mEtaCount--;
    }
    final long spanMs = movingMs - mEtaTime[mEtaStart];
    final double v = spanMs > 0 ? (distM - mEtaDist[mEtaStart]) / (spanMs / 1000.0) : 0;
    mSpeedMps = spanMs >= ETA_MIN_DATA_MS && v > 0 ? v : mFallbackMps;
  }

  // ---- Readings ----

  @NonNull
  public Phase phase()
  {
    return mPhase;
  }

  /** True once a fix has placed the rider; before that the state says nothing about them. */
  public boolean placed()
  {
    return mHasSnap;
  }

  /** Distance along the route, metres. */
  public double along()
  {
    return mAlong;
  }

  public double offRouteM()
  {
    return mOffRouteM;
  }

  /** While riding backwards, what it was when the rider turned. */
  public double remainingM()
  {
    return mPhase == Phase.WRONG_WAY ? mWrongRemainingM : Math.max(0, mRoute.totalM() - mAlong);
  }

  /** Seconds to the end at the recent moving speed (plan §10.2). */
  public double etaSeconds()
  {
    if (mWalkFromM < 0 || mPhase == Phase.WRONG_WAY)
      return remainingM() / mSpeedMps;
    // Bike + walk: the ride at the recent speed, then the walk at walking speed (Phase 27).
    final double walkMps = TzConfig.ETA_FALLBACK_FOOT_KMH / 3.6;
    final double toBikeEnd = Math.max(0, mWalkFromM - mAlong);
    final double walked = remainingM() - toBikeEnd;
    return toBikeEnd / mSpeedMps + walked / walkMps;
  }

  @NonNull
  public FollowRoute route()
  {
    return mRoute;
  }

  public double totalM()
  {
    return mRoute.totalM();
  }

  /** Straight-line distance to the route's first point, metres. */
  public double toStartM()
  {
    return mToStartM;
  }

  /** The recent moving speed the ETA uses, m/s. */
  public double speedMps()
  {
    return mSpeedMps;
  }

  /**
   * Starts the ETA from another engine's recent speed (a route replaced by a new one mid-ride), instead of 2
   * minutes of the default speed.
   */
  public void inheritSpeed(@NonNull FollowEngine from)
  {
    System.arraycopy(from.mEtaTime, 0, mEtaTime, 0, mEtaTime.length);
    System.arraycopy(from.mEtaDist, 0, mEtaDist, 0, mEtaDist.length);
    mEtaStart = from.mEtaStart;
    mEtaCount = from.mEtaCount;
    mSpeedMps = from.mSpeedMps;
    mFallbackMps = from.mFallbackMps;
    mCourseMinMps = from.mCourseMinMps;
  }

  @NonNull
  public List<RouteManeuver> maneuvers()
  {
    return mManeuvers;
  }

  /** What the turn card shows. */
  @NonNull
  public TurnState turnState()
  {
    if (mPhase == Phase.OFF_ROUTE)
      return TurnState.offRoute(mOffRouteM);
    if (mPhase == Phase.WRONG_WAY)
      return TurnState.wrongWay();
    if (mPhase == Phase.ARRIVED)
      return new TurnState(Maneuver.ARRIVE, remainingM(), "", 0, null, 0, "", true, false);
    if (mPhase == Phase.HEAD_TO_START)
    {
      final double rotation = Double.isNaN(mCourseDeg) ? 0 : Geo.normalizeAngle(mBearingToStartDeg - mCourseDeg);
      return new TurnState(Maneuver.HEAD_TO_START, mToStartM, "", (float) rotation, null, 0, "", false, false);
    }
    final RouteManeuver m = mManeuvers.get(mNext);
    final double dist = Math.max(0, m.at - mAlong);
    final RouteManeuver then = mNext + 1 < mManeuvers.size() ? mManeuvers.get(mNext + 1) : null;
    final boolean imminent = dist <= TzConfig.IMMINENT_M;
    if (then == null || then.at - m.at > THEN_MAX_M)
      return new TurnState(m.type, dist, street(m), 0, null, 0, "", imminent, false, false, m.exit, 0);
    return new TurnState(m.type, dist, street(m), 0, then.type, then.at - m.at, street(then), imminent, false, false,
                         m.exit, then.exit);
  }

  @NonNull
  private static String street(@NonNull RouteManeuver m)
  {
    return m.street == null ? "" : m.street;
  }
}
