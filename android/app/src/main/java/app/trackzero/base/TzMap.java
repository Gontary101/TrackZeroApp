package app.trackzero.base;

import android.location.Location;
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

  private static final int NO_REQUEST = -1;

  @Nullable
  private static TzActivity sHost;
  private static final List<FollowListener> sFollowListeners = new ArrayList<>();

  // The core only exposes "switch to next mode" and reports the result asynchronously, so a follow request is
  // driven one step per mode change until the requested mode is reached.
  private static int sRequestedMode = NO_REQUEST;
  private static boolean sSwitchPending;
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

  public static void zoomIn()
  {
    Map.zoomIn();
  }

  public static void zoomOut()
  {
    Map.zoomOut();
  }

  /** Zooms around the rider (keeps follow mode). No-op until a position is known. */
  public static void setZoom(int level)
  {
    final Location loc = riderLocation();
    if (loc != null)
      Framework.nativeZoomToPoint(loc.getLatitude(), loc.getLongitude(), level, true);
  }

  public static void followRider(boolean headingUp)
  {
    sRequestedMode = headingUp ? LocationState.FOLLOW_AND_ROTATE : LocationState.FOLLOW;
    // FOLLOW -> FOLLOW_AND_ROTATE is silently refused until a heading is known; a new request retries.
    sSwitchPending = false;
    step(LocationState.getMode());
  }

  public static void stopFollowing()
  {
    sRequestedMode = NO_REQUEST;
    Framework.nativeStopLocationFollow();
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

  /**
   * Declares which part of the map is not covered by TrackZero UI (pixels from the top and bottom of the map).
   * The rider arrow and {@link #showRect} are centred inside this area.
   */
  public static void setVisibleArea(int topPx, int bottomPx)
  {
    if (sHost != null)
      sHost.setVisibleMapArea(topPx, bottomPx);
  }

  /** Frames the rect inside the area set by {@link #setVisibleArea}. */
  public static void showRect(double minLat, double minLon, double maxLat, double maxLon)
  {
    Framework.nativeShowRect(minLat, minLon, maxLat, maxLon, true);
  }

  /** Positions the scale ruler: offsets in pixels from the left and bottom edge of the map. */
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
    LocationState.nativeSwitchToNextMode();
  }
}
