package app.trackzero.base;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.MwmApplication;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.util.LocationUtils;
import app.trackzero.ride.TzFix;
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

  /**
   * While a ride records, the map shows the ride's own GPS fixes ({@link #pushRideFix}) and CoMaps' location provider
   * (fused: network positions before GPS has locked) is stopped: one position, one location request (BUG-004).
   */
  @SuppressLint("MissingPermission") // Rides only run with the location permission.
  public static void setRideSource(@NonNull Context c, boolean on)
  {
    MwmApplication.from(c).getLocationHelper().setExternalSource(on);
  }

  /** A fix the ride accepted, for the map (see {@link #setRideSource}). */
  public static void pushRideFix(@NonNull Context c, @NonNull TzFix f)
  {
    final Location l = new Location(LocationManager.GPS_PROVIDER);
    l.setLatitude(f.lat);
    l.setLongitude(f.lon);
    l.setAccuracy(f.accuracyM);
    if (f.hasAlt)
      l.setAltitude(f.altM);
    if (f.hasSpeed)
      l.setSpeed(f.speedMps);
    if (f.hasBearing)
      l.setBearing(f.bearingDeg);
    l.setTime(f.timeMs);
    l.setElapsedRealtimeNanos(f.elapsedMs * 1_000_000);
    MwmApplication.from(c).getLocationHelper().pushLocation(l);
  }

  public static void removeListener(@NonNull Context c, @NonNull Listener l)
  {
    final LocationListener adapter = sAdapters.remove(l);
    if (adapter != null)
      MwmApplication.from(c).getLocationHelper().removeListener(adapter);
  }
}
