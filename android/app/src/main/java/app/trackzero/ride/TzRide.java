package app.trackzero.ride;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzBase;
import app.trackzero.base.TzRouteLayer;
import app.trackzero.follow.RoutedPath;
import app.trackzero.routes.Profile;
import app.trackzero.routes.Route;
import java.text.DateFormat;
import java.util.Date;

/** Starting a ride from the browse screens (B1 RIDE, B5 START RIDE). */
public final class TzRide
{
  private TzRide() {}

  /**
   * Asks for location if needed, starts recording ({@code route} null = free ride) and shows the ride screen. The
   * ride's profile is the one chosen last ({@link TzPrefs#profile}): on the preview, or before for a free ride.
   */
  public static void start(@NonNull Activity activity, @Nullable Route route)
  {
    start(activity, route, null);
  }

  /** As {@link #start(Activity, Route)}; the route line {@code lineOwner} shows is handed to the ride screen. */
  public static void start(@NonNull Activity activity, @Nullable Route route, @Nullable Object lineOwner)
  {
    TzBase.requestRidePermissions(activity, () -> {
      if (lineOwner != null)
        TzRouteLayer.transfer(lineOwner, TzRouteLayer.RIDE_START);
      final Profile profile = TzPrefs.profile(activity);
      final String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date());
      final String name = route != null ? route.name
                          : activity.getString(profile == Profile.HIKE ? R.string.tz_free_hike_name
                                               : profile.foot ? R.string.tz_free_walk_name
                                                              : R.string.tz_free_ride_name,
                                               date);
      TzRideService.start(activity, route == null ? null : route.id, name, profile);
      TzNavigator.show(TzNavigator.Screen.RIDE, null);
    });
  }

  /**
   * Starts a ride to a place along the path previewed (Phase 15), with {@code profile} (the one chosen; Walk when a
   * place without bike access is walked instead, Phase 27).
   */
  public static void startTo(@NonNull Activity activity, @NonNull String name, double lat, double lon,
                             @NonNull RoutedPath path, @NonNull Object lineOwner, @NonNull Profile profile)
  {
    TzBase.requestRidePermissions(activity, () -> {
      TzRouteLayer.transfer(lineOwner, TzRouteLayer.RIDE_START);
      TzRideService.startTo(activity, name, lat, lon, path, profile);
      TzNavigator.show(TzNavigator.Screen.RIDE, null);
    });
  }

  /** A ride is running, or one was cut short and will be restored. */
  public static boolean inProgress(@NonNull Context context)
  {
    return TzRideService.isRiding() || RideStore.get(context).hasJournal();
  }
}
