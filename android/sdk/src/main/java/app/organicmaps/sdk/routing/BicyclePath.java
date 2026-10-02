package app.organicmaps.sdk.routing;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * TrackZero: a bicycle path between two points from CoMaps' router, without a routing session: nothing is drawn and
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

  /** A {@code RouterResultCode}; the other fields are empty unless it is {@link #OK}. */
  public final int code;
  @NonNull
  public final double[] lat;
  @NonNull
  public final double[] lon;
  /** Metres for each point; null when the path has no altitudes (or some are unknown). */
  @Nullable
  public final double[] ele;
  /** Each turn: the index of its point, a {@link CarDirection} ordinal, the roundabout exit (0: none), the street. */
  @NonNull
  public final int[] turnPoint;
  @NonNull
  public final int[] turnDirection;
  @NonNull
  public final int[] turnExit;
  @NonNull
  public final String[] turnStreet;

  // Called from JNI.
  @Keep
  private BicyclePath(int code, @NonNull double[] lat, @NonNull double[] lon, @Nullable double[] ele,
                      @NonNull int[] turnPoint, @NonNull int[] turnDirection, @NonNull int[] turnExit,
                      @NonNull String[] turnStreet)
  {
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
   * Builds a path (blocking, seconds on a long one; the first one also loads the road graphs, kept for the next).
   * Null when the map engine isn't running.
   *
   * @param bearingDeg the rider's heading, when {@code hasBearing}
   */
  @WorkerThread
  @Nullable
  public static native BicyclePath nativeBuild(double fromLat, double fromLon, double toLat, double toLon,
                                               boolean hasBearing, double bearingDeg);

  /**
   * Frees the router kept between paths (its loaded road graphs). Call on the thread that builds paths, when no more
   * are coming for a while.
   */
  @WorkerThread
  public static native void nativeRelease();

  /** Stops the path being built; it returns {@link #CANCELLED}. */
  public static native void nativeCancel();
}
