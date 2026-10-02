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

  private static final int NO_REQUEST = -1;
  /** A switch with no mode change after this long was refused. */
  private static final long SWITCH_ANSWER_MS = 1000;

  @Nullable
  private static TzActivity sHost;
  private static final List<FollowListener> sFollowListeners = new ArrayList<>();
  private static final List<TouchObserver> sTouchObservers = new ArrayList<>();

  // The core only exposes "switch to next mode" and reports the result asynchronously, so a follow request is
  // driven one step per mode change until the requested mode is reached.
  private static int sRequestedMode = NO_REQUEST;
  private static boolean sSwitchPending;
  private static long sSwitchAtMs;
  private static boolean sFollowing;

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
   * Zooms to a level like a pinch: around the rider while following (follow mode stays on), else around the
   * visible centre. Returns false (nothing done) until the map is ready.
   */
  public static boolean setZoom(int level)
  {
    final int current = zoom();
    if (current <= 0)
      return false;
    // Band changes animate; a long way (first zoom of a ride) jumps, as an animation would be cut by follow updates.
    if (current != level)
      Framework.nativeScale(Math.pow(2, level - current), Math.abs(level - current) <= 2);
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
    if (Map.isEngineCreated())
      Framework.nativeSetViewportCenter(lat, lon, zoom);
  }

  public static void followRider(boolean headingUp)
  {
    sRequestedMode = headingUp ? LocationState.FOLLOW_AND_ROTATE : LocationState.FOLLOW;
    // FOLLOW -> FOLLOW_AND_ROTATE is silently refused until a heading is known; a new request retries.
    sSwitchPending = false;
    if (Map.isEngineCreated())
      step(LocationState.getMode());
  }

  /**
   * A new heading (compass reading, or a fix with a bearing). The core refuses FOLLOW → FOLLOW_AND_ROTATE without
   * one and reports nothing, so a heading-up request still waiting asks again, at most once a second.
   */
  static void onHeading()
  {
    if (sRequestedMode == NO_REQUEST || !Map.isEngineCreated()
        || SystemClock.elapsedRealtime() - sSwitchAtMs < SWITCH_ANSWER_MS)
      return;
    sSwitchPending = false;
    step(LocationState.getMode());
  }

  public static void stopFollowing()
  {
    sRequestedMode = NO_REQUEST;
    if (Map.isEngineCreated())
      Framework.nativeStopLocationFollow();
  }

  static void onEngineReady()
  {
    step(LocationState.getMode());
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
    if (sAreaOwner != owner)
      return;
    sAreaOwner = null;
    if (sHost != null)
      sHost.setVisibleMapArea(0, 0);
  }

  /** Frames the rect inside the area set by {@link #setVisibleArea}, keeping {@code paddingPx} clear on every side. */
  public static void showRect(double minLat, double minLon, double maxLat, double maxLon, int paddingPx)
  {
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
    final boolean causedByUs = sSwitchPending;
    sSwitchPending = false;
    // The rider panned the map away: drop the request instead of fighting the gesture.
    if (!causedByUs && mode == LocationState.NOT_FOLLOW)
      sRequestedMode = NO_REQUEST;
    step(mode);

    final boolean following = mode == LocationState.FOLLOW || mode == LocationState.FOLLOW_AND_ROTATE;
    if (following == sFollowing)
      return;
    sFollowing = following;
    for (FollowListener l : new ArrayList<>(sFollowListeners))
      l.onFollowChanged(following);
  }

  private static void step(int mode)
  {
    if (sRequestedMode == NO_REQUEST || sSwitchPending)
      return;
    if (mode == sRequestedMode)
    {
      sRequestedMode = NO_REQUEST;
      return;
    }
    // Waiting for the first fix: the core enters FOLLOW by itself once it arrives.
    if (mode == LocationState.PENDING_POSITION)
      return;
    sSwitchPending = true;
    sSwitchAtMs = SystemClock.elapsedRealtime();
    LocationState.nativeSwitchToNextMode();
  }
}
