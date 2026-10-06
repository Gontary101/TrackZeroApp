package app.organicmaps.sdk.routing;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * TrackZero: a path through two or more points from CoMaps' router, without a routing session: nothing is drawn and
 * the map is left alone (the app follows the path itself). Immutable.
 */
@Keep
public final class BicyclePath
{
  /** {@code RouterResultCode::NoError}. */
  public static final int OK = 0;
  /** {@code RouterResultCode::Cancelled}. */
  public static final int CANCELLED = 1;
  /** {@code RouterResultCode::NeedMoreMaps}: a map on the way is not downloaded. */
  public static final int NEED_MORE_MAPS = 9;
  /** {@code RouterResultCode::RouteFileNotExist}: the start or the end has no map. */
  public static final int NO_MAP = 4;
  /** {@code RouterResultCode::StartPointNotFound}: no road near the start. */
  public static final int START_NOT_FOUND = 5;
  /** {@code RouterResultCode::EndPointNotFound}: no road near the end. */
  public static final int END_NOT_FOUND = 6;
  /** {@code RouterResultCode::RouteNotFound}: no way between the points. */
  public static final int ROUTE_NOT_FOUND = 8;
  /** {@code RouterResultCode::FileTooOld}: a map is too old to route on. */
  public static final int FILE_TOO_OLD = 11;
  /** {@code RouterResultCode::IntermediatePointNotFound}: no road near a waypoint. */
  public static final int INTERMEDIATE_NOT_FOUND = 12;
  /** {@code RouterResultCode::RouteNotFoundRedressRouteError}: no way between the points. */
  public static final int ROUTE_NOT_FOUND_REDRESS = 15;

  /** A {@code RouterResultCode}; the other fields are empty unless it is {@link #OK}. */
  public final int code;
  @NonNull
  public final double[] lat;
  @NonNull
  public final double[] lon;
  /** Metres for each point; null when the path has no altitudes (or some are unknown). */
  @Nullable
  public final double[] ele;
  /**
   * Each turn: the index of its point, a {@link CarDirection} ordinal, a {@link PedestrianTurnDirection} ordinal (a
   * path on foot; 0 otherwise), the roundabout exit (0: none), the street.
   */
  @NonNull
  public final int[] turnPoint;
  @NonNull
  public final int[] turnDirection;
  @NonNull
  public final int[] turnPedestrian;
  @NonNull
  public final int[] turnExit;
  @NonNull
  public final String[] turnStreet;

  // Called from JNI.
  @Keep
  private BicyclePath(int code, @NonNull double[] lat, @NonNull double[] lon, @Nullable double[] ele,
                      @NonNull int[] turnPoint, @NonNull int[] turnDirection, @NonNull int[] turnPedestrian,
                      @NonNull int[] turnExit, @NonNull String[] turnStreet)
  {
    this.turnPedestrian = turnPedestrian;
    this.code = code;
    this.lat = lat;
    this.lon = lon;
    this.ele = ele;
    this.turnPoint = turnPoint;
    this.turnDirection = turnDirection;
    this.turnExit = turnExit;
    this.turnStreet = turnStreet;
  }

  /**
   * Builds a path (blocking, seconds on a long one; the first one also loads the road graphs, kept for the next path
   * of the same profile). Null when the map engine isn't running or there are fewer than two points.
   *
   * @param lat        the start, any waypoints, the end
   * @param profile    TrackZero's profile ordinal (Road, Gravel, Walk, Hike); another one than the last frees the
   *                   router kept and builds this one's
   * @param bearingDeg the rider's heading, when {@code hasBearing}
   */
  @WorkerThread
  @Nullable
  public static native BicyclePath nativeBuild(@NonNull double[] lat, @NonNull double[] lon, int profile,
                                               boolean hasBearing, double bearingDeg);

  /** The profile of the router kept between paths, -1 when none is (checks that nothing is loaded while idle). */
  public static native int nativeLoadedProfile();

  /**
   * Frees the router kept between paths (its loaded road graphs). Call on the thread that builds paths, when no more
   * are coming for a while.
   */
  @WorkerThread
  public static native void nativeRelease();

  /** Stops the path being built; it returns {@link #CANCELLED}. */
  public static native void nativeCancel();
}
