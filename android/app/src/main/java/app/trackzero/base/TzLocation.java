package app.trackzero.base;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import app.organicmaps.MwmApplication;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.util.LocationUtils;
import app.trackzero.ride.TzFix;
import java.util.HashMap;
import java.util.Map;

/** The map's location feed (CoMaps LocationHelper) for browse screens: NO GPS chip, first camera, "starts in". */
public final class TzLocation
{
  public interface Listener
  {
    void onLocation(@NonNull Location location);

    /** GPS switched off or no fix for a long time. */
    default void onLocationLost() {}
  }

  private static final Map<Listener, LocationListener> sAdapters = new HashMap<>();
  /**
   * Location switched back on while a browse screen listens: CoMaps stopped at "off" and would wait for the next
   * start of the app or a tap on the locate button (the chip said NO GPS meanwhile).
   */
  @Nullable
  private static BroadcastReceiver sProviders;

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
    if (sProviders == null)
    {
      final Context app = c.getApplicationContext();
      sProviders = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")  // checked
        @Override
        public void onReceive(Context context, Intent intent)
        {
          final LocationHelper helper = MwmApplication.from(app).getLocationHelper();
          if (!helper.isActive() && hasPermission(app) && LocationUtils.areLocationServicesTurnedOn(app))
            helper.start();
        }
      };
      ContextCompat.registerReceiver(app, sProviders, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
                                     ContextCompat.RECEIVER_NOT_EXPORTED);
    }
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
    if (sAdapters.isEmpty() && sProviders != null)
    {
      c.getApplicationContext().unregisterReceiver(sProviders);
      sProviders = null;
    }
  }
}
