package app.trackzero.base;

import android.location.Location;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.MwmApplication;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.location.LocationState;
import app.trackzero.TzTrace;
import java.util.ArrayList;
import java.util.List;

/**
 * The only map API the rest of TrackZero uses. All calls must happen on the UI thread while {@link TzActivity}
 * is alive.
 */
public final class TzMap
{
  public interface FollowListener
  {
    void onFollowChanged(boolean following);
  }

  /** Sees every touch on the screen before normal dispatch (e.g. to notice the rider panning the map). */
  public interface TouchObserver
  {
    void onTouch(@NonNull MotionEvent e);
  }

  /** A NOT_FOLLOW this soon after the finger left the screen is still the rider's gesture (a fling). */
  private static final long FLING_MS = 300;

  @Nullable
  private static TzActivity sHost;
  private static final List<FollowListener> sFollowListeners = new ArrayList<>();
  private static final List<TouchObserver> sTouchObservers = new ArrayList<>();

  private static final FollowRequest sRequest = new FollowRequest();
  /** The zoom of a request made before the engine existed. */
  private static int sRequestedZoom = -1;
  private static boolean sFollowing;
  private static boolean sTouchDown;
  private static long sTouchUpAtMs;

  private TzMap() {}

  static void attach(@NonNull TzActivity host)
  {
    sHost = host;
  }

  static void detach(@NonNull TzActivity host)
  {
    if (sHost == host)
      sHost = null;
  }

  // Screens may call in before the map engine exists (e.g. the app opening straight on a ride): camera calls are
  // dropped until then, and a follow request is kept and applied by onEngineReady().

  public static void zoomIn()
  {
    if (Map.isEngineCreated())
      Map.zoomIn();
  }

  public static void zoomOut()
  {
    if (Map.isEngineCreated())
      Map.zoomOut();
  }

  /**
   * Zooms the ride map to a level while it follows the rider (follow mode stays on). A level, not a factor: asking
   * again before the engine has applied it cannot add up (BUG-004, 13 -> 19 -> 13). Changes of up to two levels are
   * animated. Returns false (nothing done) when the map is not following.
   */
  public static boolean setZoom(int level)
  {
    final int current = zoom();
    if (TzTrace.on())
      TzTrace.log("map", "setZoom current=" + current + " target=" + level);
    if (current <= 0 || !sFollowing)
      return false;
    Framework.nativeFollowMyPosition(LocationState.getMode() == LocationState.FOLLOW_AND_ROTATE, level,
                                     Math.abs(level - current) <= 2);
    return true;
  }

  /** The current zoom level, 0 until the map is ready. */
  public static int zoom()
  {
    return Map.isEngineCreated() ? Framework.nativeGetDrawScale() : 0;
  }

  /** Centres the map on a point at a zoom level (no animation). */
  public static void centerOn(double lat, double lon, int zoom)
  {
    if (TzTrace.on())
      TzTrace.log("map", "centerOn " + TzTrace.ll(lat, lon) + " z" + zoom);
    if (Map.isEngineCreated())
      Framework.nativeSetViewportCenter(lat, lon, zoom);
  }

  /** Follows the rider, heading-up or north-up, keeping the zoom. */
  public static void followRider(boolean headingUp)
  {
    follow(headingUp, -1);
  }

  /**
   * The first camera of a ride, in one command: the map jumps to the rider at {@code zoom} (no animation) and follows,
   * heading-up or north-up. Without a position yet, the first one is shown that way.
   */
  public static void startRideCamera(int zoom, boolean headingUp)
  {
    follow(headingUp, zoom);
  }

  private static void follow(boolean headingUp, int zoom)
  {
    sRequest.request(headingUp);
    final boolean engine = Map.isEngineCreated();
    if (TzTrace.on())
      TzTrace.log("map", (zoom < 0 ? "followRider" : "startRideCamera z" + zoom) + " headingUp=" + headingUp + " mode="
                         + modeName(currentMode()) + (engine ? "" : " (before the engine)"));
    if (engine)
      Framework.nativeFollowMyPosition(headingUp, zoom, false);
    else
      sRequestedZoom = zoom;
  }

  /** The screen that asked to follow goes away: the map keeps following, but nothing asks for it any more. */
  public static void forgetFollowRequest()
  {
    sRequest.forget();
  }

  public static void stopFollowing()
  {
    if (TzTrace.on())
      TzTrace.log("map", "stopFollowing");
    sRequest.forget();
    if (Map.isEngineCreated())
      Framework.nativeStopLocationFollow();
  }

  static void onEngineReady()
  {
    if (TzTrace.on())
      TzTrace.log("map", "onEngineReady mode=" + modeName(LocationState.getMode()) + " requested="
                         + modeName(sRequest.mode()));
    if (sRequest.mode() != FollowRequest.NONE)
      Framework.nativeFollowMyPosition(sRequest.mode() == LocationState.FOLLOW_AND_ROTATE, sRequestedZoom, false);
    sRequestedZoom = -1;
    TzRouteLayer.onEngineReady();
  }

  public static boolean isFollowing()
  {
    return sFollowing;
  }

  public static void addFollowListener(@NonNull FollowListener l)
  {
    sFollowListeners.add(l);
    l.onFollowChanged(sFollowing);
  }

  public static void removeFollowListener(@NonNull FollowListener l)
  {
    sFollowListeners.remove(l);
  }

  /** The screen whose visible area is set, for {@link #clearVisibleArea}. */
  @Nullable
  private static Object sAreaOwner;

  /**
   * Declares which part of the map is not covered by TrackZero UI (pixels from the top and bottom of the map).
   * The rider arrow and {@link #showRect} are centred inside this area. {@code owner} is the screen setting it.
   */
  public static void setVisibleArea(@NonNull Object owner, int topPx, int bottomPx)
  {
    if (TzTrace.on())
      TzTrace.log("map", "setVisibleArea " + owner.getClass().getSimpleName() + " top=" + topPx + " bottom=" + bottomPx);
    sAreaOwner = owner;
    if (sHost != null)
      sHost.setVisibleMapArea(topPx, bottomPx);
  }

  /**
   * The whole map again, unless another screen has set its area since: a screen going away comes after the next
   * one has set up (fragment transactions reorder).
   */
  public static void clearVisibleArea(@NonNull Object owner)
  {
    if (TzTrace.on())
      TzTrace.log("map", "clearVisibleArea " + owner.getClass().getSimpleName() + (sAreaOwner == owner ? "" : " (not owner)"));
    if (sAreaOwner != owner)
      return;
    sAreaOwner = null;
    if (sHost != null)
      sHost.setVisibleMapArea(0, 0);
  }

  /** Frames the rect inside the area set by {@link #setVisibleArea}, keeping {@code paddingPx} clear on every side. */
  public static void showRect(double minLat, double minLon, double maxLat, double maxLon, int paddingPx)
  {
    if (TzTrace.on())
      TzTrace.log("map", "showRect " + TzTrace.ll(minLat, minLon) + " " + TzTrace.ll(maxLat, maxLon) + " pad=" + paddingPx);
    if (sHost == null || !Map.isEngineCreated())
      return;
    final int w = sHost.visibleMapWidth();
    final int h = sHost.visibleMapHeight();
    if (w <= 2 * paddingPx || h <= 2 * paddingPx)
      return;
    // The engine fits a rect edge to edge, so grow the rect (in mercator, where the fit is linear) until the
    // original one sits inside the padding.
    final double minY = mercatorY(minLat);
    final double maxY = mercatorY(maxLat);
    final double scale = Math.max((maxLon - minLon) / (w - 2 * paddingPx), (maxY - minY) / (h - 2 * paddingPx));
    final double cx = (minLon + maxLon) / 2;
    final double cy = (minY + maxY) / 2;
    final double hx = scale * w / 2;
    final double hy = scale * h / 2;
    Framework.nativeShowRect(latitude(cy - hy), cx - hx, latitude(cy + hy), cx + hx, true);
  }

  /** Mercator y in the core's units (degrees). */
  private static double mercatorY(double lat)
  {
    return Math.toDegrees(Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2)));
  }

  private static double latitude(double mercatorY)
  {
    return Math.toDegrees(2 * Math.atan(Math.exp(Math.toRadians(mercatorY))) - Math.PI / 2);
  }

  /** Positions the scale ruler: its left and bottom edges, in pixels from the left and bottom of the map. */
  public static void setRulerPosition(int xPx, int yPx)
  {
    if (sHost != null)
      sHost.setRulerOffset(xPx, yPx);
  }

  /** Pauses map drawing while an opaque TrackZero screen covers it. */
  public static void setRenderingEnabled(boolean on)
  {
    if (sHost != null)
      sHost.setMapRenderingEnabled(on);
  }

  /** A pixel of the map (activity coordinates: the map fills the window) as {lat, lon} (Phase 28). */
  @NonNull
  public static double[] screenToLatLon(float x, float y)
  {
    return Framework.nativeScreenToLatLon(x, y);
  }

  /** Where {@code lat, lon} is on the map, {x, y} pixels; off the screen when it is not shown. */
  @NonNull
  public static float[] latLonToScreen(double lat, double lon)
  {
    return Framework.nativeLatLonToScreen(lat, lon);
  }

  /** A finger is on the map, or was a moment ago (the map may still be moving). */
  public static boolean recentlyTouched(long withinMs)
  {
    return sTouchDown || SystemClock.uptimeMillis() - sTouchUpAtMs < withinMs;
  }

  public static void addTouchObserver(@NonNull TouchObserver o)
  {
    sTouchObservers.add(o);
  }

  public static void removeTouchObserver(@NonNull TouchObserver o)
  {
    sTouchObservers.remove(o);
  }

  static void dispatchTouch(@NonNull MotionEvent e)
  {
    switch (e.getActionMasked())
    {
    case MotionEvent.ACTION_DOWN -> sTouchDown = true;
    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
    {
      sTouchDown = false;
      sTouchUpAtMs = SystemClock.uptimeMillis();
    }
    default -> {}
    }
    for (int i = 0; i < sTouchObservers.size(); i++)
      sTouchObservers.get(i).onTouch(e);
  }

  @Nullable
  public static Location riderLocation()
  {
    if (sHost == null)
      return null;
    return MwmApplication.from(sHost).getLocationHelper().getSavedLocation();
  }

  static void onModeChanged(int mode)
  {
    final boolean touching = sTouchDown || SystemClock.uptimeMillis() - sTouchUpAtMs < FLING_MS;
    if (TzTrace.on())
      TzTrace.log("map", "onModeChanged " + modeName(mode) + " requested=" + modeName(sRequest.mode())
                         + (touching ? " touching" : ""));
    if (sRequest.onModeChanged(mode, touching, LocationState.getMode()) == FollowRequest.Action.ASK_AGAIN)
    {
      if (TzTrace.on())
        TzTrace.log("map", "follow asked again");
      Framework.nativeFollowMyPosition(sRequest.mode() == LocationState.FOLLOW_AND_ROTATE, -1, false);
    }

    final boolean following = mode == LocationState.FOLLOW || mode == LocationState.FOLLOW_AND_ROTATE;
    if (following == sFollowing)
      return;
    sFollowing = following;
    for (FollowListener l : new ArrayList<>(sFollowListeners))
      l.onFollowChanged(following);
  }

  private static int currentMode()
  {
    return Map.isEngineCreated() ? LocationState.getMode() : -2; // -2: no engine yet
  }

  /** For the trace. */
  @NonNull
  static String modeName(int mode)
  {
    return switch (mode)
    {
      case LocationState.PENDING_POSITION -> "PENDING_POSITION";
      case LocationState.NOT_FOLLOW_NO_POSITION -> "NOT_FOLLOW_NO_POSITION";
      case LocationState.NOT_FOLLOW -> "NOT_FOLLOW";
      case LocationState.FOLLOW -> "FOLLOW";
      case LocationState.FOLLOW_AND_ROTATE -> "FOLLOW_AND_ROTATE";
      case FollowRequest.NONE -> "none";
      default -> String.valueOf(mode);
    };
  }
}
