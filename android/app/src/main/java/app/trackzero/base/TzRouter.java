package app.trackzero.base;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.sdk.routing.BicyclePath;
import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.PedestrianTurnDirection;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.Maneuver;
import app.trackzero.follow.RouteManeuver;
import app.trackzero.follow.RoutedPath;
import app.trackzero.routes.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Paths from CoMaps' router (Phase 15) for a profile (Phase 22): to the start of a track, back onto it, or to a place,
 * through waypoints if any. One at a time on their own thread, at background priority; a new request cancels the one
 * before. Answers arrive on the main thread.
 */
public final class TzRouter
{
  private static final String TAG = "TzRouter";

  /** Why there is a path or not. */
  public enum Reason
  {
    OK,
    /** A map on the way is not on the phone (or too old to route on). */
    NO_MAP,
    /** No way between the points (or to a waypoint). */
    NO_WAY,
    /** No road near the start. */
    NO_START,
    /** No road near the end. */
    NO_END,
    CANCELLED,
    /** Anything else, or the map engine not running. */
    ERROR;

    /** CoMaps' {@code RouterResultCode} as a reason. */
    @NonNull
    static Reason of(int code)
    {
      return switch (code)
      {
        case BicyclePath.OK -> OK;
        case BicyclePath.CANCELLED -> CANCELLED;
        case BicyclePath.NO_MAP, BicyclePath.NEED_MORE_MAPS, BicyclePath.FILE_TOO_OLD -> NO_MAP;
        case BicyclePath.ROUTE_NOT_FOUND, BicyclePath.ROUTE_NOT_FOUND_REDRESS, BicyclePath.INTERMEDIATE_NOT_FOUND ->
          NO_WAY;
        case BicyclePath.START_NOT_FOUND -> NO_START;
        case BicyclePath.END_NOT_FOUND -> NO_END;
        default -> ERROR;
      };
    }
  }

  public interface Callback
  {
    /** The path, or null with the reason there is none. */
    void onPath(@Nullable RoutedPath path, @NonNull Reason reason);
  }

  private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> new Thread(() -> {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
    r.run();
  }, "TzRouter"));
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  /** The latest request (main thread); older answers are dropped. */
  private static int sRequest;

  private TzRouter() {}

  /** From one point to another; see {@link #route(double[], double[], Profile, double, Callback)}. */
  public static void route(double fromLat, double fromLon, double toLat, double toLon, @NonNull Profile profile,
                           double bearingDeg, @NonNull Callback callback)
  {
    route(new double[] {fromLat, toLat}, new double[] {fromLon, toLon}, profile, bearingDeg, callback);
  }

  /**
   * @param lat        the start, any waypoints, the end (two or more points)
   * @param bearingDeg the rider's heading (picks the road they are on), NaN when unknown
   */
  public static void route(@NonNull double[] lat, @NonNull double[] lon, @NonNull Profile profile, double bearingDeg,
                           @NonNull Callback callback)
  {
    final int request = ++sRequest;
    // Stops the path being worked out for the previous request, if it still is.
    BicyclePath.nativeCancel();
    final double[] la = lat.clone();
    final double[] lo = lon.clone();
    EXECUTOR.execute(() -> {
      final long start = System.nanoTime();
      final boolean hasBearing = !Double.isNaN(bearingDeg);
      final BicyclePath p = BicyclePath.nativeBuild(la, lo, profile.ordinal(), hasBearing, hasBearing ? bearingDeg : 0);
      final RoutedPath path = p == null || p.code != BicyclePath.OK ? null : toPath(p);
      final Reason reason = path != null ? Reason.OK : p == null ? Reason.ERROR : Reason.of(p.code);
      Log.i(TAG, (path == null ? "No path (" + (p == null ? "no engine" : p.code) + ")"
                               : "Path " + Math.round(path.line.totalM()) + " m")
                     + " " + profile.id + " in " + (System.nanoTime() - start) / 1_000_000 + " ms");
      MAIN.post(() -> {
        if (request == sRequest)
          callback.onPath(path, reason);
      });
    });
  }

  /** Drops the request in flight. */
  public static void cancel()
  {
    sRequest++;
    BicyclePath.nativeCancel();
  }

  /** No more paths for now (the ride ended): frees the router's loaded road graphs. */
  public static void release()
  {
    cancel();
    EXECUTOR.execute(BicyclePath::nativeRelease);
  }

  /** The profile whose router is kept (its road graphs loaded), or null: for the idle check (Phase 22). */
  @Nullable
  public static Profile loaded()
  {
    final int p = BicyclePath.nativeLoadedProfile();
    return p < 0 || p >= Profile.values().length ? null : Profile.values()[p];
  }

  @Nullable
  private static RoutedPath toPath(@NonNull BicyclePath p)
  {
    return toPath(p.lat, p.lon, p.ele, p.turnPoint, p.turnDirection, p.turnPedestrian, p.turnExit, p.turnStreet);
  }

  /** CoMaps' path in TrackZero's terms: repeated points dropped, its turns as maneuvers ending with ARRIVE. */
  @VisibleForTesting
  @Nullable
  static RoutedPath toPath(@NonNull double[] lat, @NonNull double[] lon, @Nullable double[] ele,
                           @NonNull int[] turnPoint, @NonNull int[] turnDirection, @NonNull int[] turnPedestrian,
                           @NonNull int[] turnExit, @NonNull String[] turnStreet)
  {
    final int n = lat.length;
    final int[] kept = new int[n];
    int count = 0;
    final double[] kLat = new double[n];
    final double[] kLon = new double[n];
    final double[] kEle = ele == null ? null : new double[n];
    for (int i = 0; i < n; i++)
    {
      if (count > 0 && lat[i] == kLat[count - 1] && lon[i] == kLon[count - 1])
      {
        kept[i] = count - 1;
        continue;
      }
      kLat[count] = lat[i];
      kLon[count] = lon[i];
      if (kEle != null)
        kEle[count] = ele[i];
      kept[i] = count++;
    }
    if (count < 2)
      return null;
    final FollowRoute line = new FollowRoute(trim(kLat, count), trim(kLon, count));

    final List<RouteManeuver> maneuvers = new ArrayList<>();
    for (int t = 0; t < turnPoint.length; t++)
    {
      final Maneuver m = turnPedestrian[t] != 0 ? Turns.pedestrian(turnPedestrian[t]) : Turns.car(turnDirection[t]);
      if (m == null || turnPoint[t] < 0 || turnPoint[t] >= n)
        continue;
      final double at = line.distanceAt(kept[turnPoint[t]]);
      maneuvers.add(new RouteManeuver(m, m == Maneuver.ARRIVE ? line.totalM() : at,
                                      m == Maneuver.ROUNDABOUT ? turnExit[t] : 0, turnStreet[t]));
    }
    if (maneuvers.isEmpty() || maneuvers.get(maneuvers.size() - 1).type != Maneuver.ARRIVE)
      maneuvers.add(new RouteManeuver(Maneuver.ARRIVE, line.totalM(), 0, ""));
    return new RoutedPath(line, maneuvers, kEle == null ? null : trim(kEle, count));
  }

  /** CoMaps' turns as TrackZero maneuvers (a class of its own: loadable without the router's main-thread Handler). */
  static final class Turns
  {
    private Turns() {}

    /** CoMaps' turn as a TrackZero maneuver; null for the ones not announced (straight on, roundabout entry...). */
    @Nullable
    static Maneuver car(int direction)
    {
      final CarDirection[] all = CarDirection.values();
      if (direction < 0 || direction >= all.length)
        return null;
      return switch (all[direction])
      {
        case GO_STRAIGHT -> Maneuver.STRAIGHT;
        case TURN_RIGHT, TURN_SHARP_RIGHT -> Maneuver.TURN_RIGHT;
        case TURN_SLIGHT_RIGHT, EXIT_HIGHWAY_TO_RIGHT -> Maneuver.SLIGHT_RIGHT;
        case TURN_LEFT, TURN_SHARP_LEFT -> Maneuver.TURN_LEFT;
        case TURN_SLIGHT_LEFT, EXIT_HIGHWAY_TO_LEFT -> Maneuver.SLIGHT_LEFT;
        case U_TURN_LEFT, U_TURN_RIGHT -> Maneuver.UTURN;
        case LEAVE_ROUND_ABOUT -> Maneuver.ROUNDABOUT;
        case REACHED_YOUR_DESTINATION -> Maneuver.ARRIVE;
        case NO_TURN, ENTER_ROUND_ABOUT, STAY_ON_ROUND_ABOUT, START_AT_THE_END_OF_STREET -> null;
      };
    }

    /** A turn on foot (a walking path, Phase 25) as a TrackZero maneuver. */
    @Nullable
    static Maneuver pedestrian(int direction)
    {
      final PedestrianTurnDirection[] all = PedestrianTurnDirection.values();
      if (direction < 0 || direction >= all.length)
        return null;
      return switch (all[direction])
      {
        case GO_STRAIGHT -> Maneuver.STRAIGHT;
        case TURN_RIGHT -> Maneuver.TURN_RIGHT;
        case TURN_LEFT -> Maneuver.TURN_LEFT;
        case REACHED_YOUR_DESTINATION -> Maneuver.ARRIVE;
        case NO_TURN -> null;
      };
    }
  }

  @NonNull
  private static double[] trim(@NonNull double[] a, int n)
  {
    if (a.length == n)
      return a;
    final double[] out = new double[n];
    System.arraycopy(a, 0, out, 0, n);
    return out;
  }
}
