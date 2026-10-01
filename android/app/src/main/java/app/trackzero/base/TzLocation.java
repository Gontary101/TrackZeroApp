package app.trackzero.base;

import android.content.Context;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.MwmApplication;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.util.LocationUtils;
import java.util.HashMap;
import java.util.Map;

/** The map's location feed (CoMaps LocationHelper) for browse screens: GPS chip, first camera, "starts in". */
public final class TzLocation
{
  public interface Listener
  {
    void onLocation(@NonNull Location location);

    /** GPS switched off or no fix for a long time. */
    default void onLocationLost() {}
  }

  private static final Map<Listener, LocationListener> sAdapters = new HashMap<>();

  private TzLocation() {}

  public static boolean hasPermission(@NonNull Context c)
  {
    return LocationUtils.checkLocationPermission(c);
  }

  @Nullable
  public static Location last(@NonNull Context c)
  {
    return MwmApplication.from(c).getLocationHelper().getSavedLocation();
  }

  public static void addListener(@NonNull Context c, @NonNull Listener l)
  {
    final LocationListener adapter = new LocationListener() {
      @Override
      public void onLocationUpdated(@NonNull Location location)
      {
        l.onLocation(location);
      }

      @Override
      public void onLocationUpdateTimeout()
      {
        l.onLocationLost();
      }

      @Override
      public void onLocationDisabled()
      {
        l.onLocationLost();
      }
    };
    sAdapters.put(l, adapter);
    MwmApplication.from(c).getLocationHelper().addListener(adapter);
  }

  public static void removeListener(@NonNull Context c, @NonNull Listener l)
  {
    final LocationListener adapter = sAdapters.remove(l);
    if (adapter != null)
      MwmApplication.from(c).getLocationHelper().removeListener(adapter);
  }
}
