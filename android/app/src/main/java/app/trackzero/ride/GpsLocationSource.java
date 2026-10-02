package app.trackzero.ride;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;

/** GPS fixes once a second straight from the GPS provider (no Play Services). */
final class GpsLocationSource implements TzLocationSource, LocationListenerCompat
{
  private static final String TAG = "TzGps";
  private static final long INTERVAL_MS = 1000;

  @NonNull
  private final LocationManager mManager;
  @Nullable
  private Listener mListener;
  private long mMaxDelayMs;

  GpsLocationSource(@NonNull Context context)
  {
    mManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
  }

  // The service only starts a ride with the location permission granted.
  @SuppressLint("MissingPermission")
  @Override
  public void start(@NonNull Listener listener)
  {
    mListener = listener;
    request();
  }

  @SuppressLint("MissingPermission")
  private void request()
  {
    final LocationRequestCompat request = new LocationRequestCompat.Builder(INTERVAL_MS)
                                              .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                                              .setMinUpdateDistanceMeters(0)
                                              .setMaxUpdateDelayMillis(mMaxDelayMs)
                                              .build();
    try
    {
      LocationManagerCompat.requestLocationUpdates(mManager, LocationManager.GPS_PROVIDER, request, this,
                                                   Looper.getMainLooper());
    }
    catch (SecurityException | IllegalArgumentException e)
    {
      Log.e(TAG, "No GPS updates", e);
    }
  }

  @SuppressLint("MissingPermission")
  @Override
  public void setMaxDelay(long maxDelayMs)
  {
    if (maxDelayMs == mMaxDelayMs)
      return;
    // Going back to fixes at once (the screen came on): the ones batched so far first.
    if (maxDelayMs < mMaxDelayMs && mListener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    {
      try
      {
        mManager.requestFlush(LocationManager.GPS_PROVIDER, this, 0);
      }
      catch (IllegalArgumentException e)
      {
        Log.w(TAG, "No flush", e);
      }
    }
    mMaxDelayMs = maxDelayMs;
    if (mListener != null)
      request();
  }

  @Override
  public void stop()
  {
    LocationManagerCompat.removeUpdates(mManager, this);
    mListener = null;
  }

  @Override
  public void onLocationChanged(@NonNull Location l)
  {
    if (mListener != null)
      mListener.onFix(toFix(l));
  }

  @NonNull
  static TzFix toFix(@NonNull Location l)
  {
    final TzFix f = new TzFix();
    f.lat = l.getLatitude();
    f.lon = l.getLongitude();
    // Prefer height above sea level (as in GPX files); older devices only report height above the ellipsoid.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && l.hasMslAltitude())
    {
      f.altM = l.getMslAltitudeMeters();
      f.hasAlt = true;
    }
    else if (l.hasAltitude())
    {
      f.altM = l.getAltitude();
      f.hasAlt = true;
    }
    f.hasSpeed = l.hasSpeed();
    f.speedMps = l.getSpeed();
    f.accuracyM = l.hasAccuracy() ? l.getAccuracy() : 0;
    f.hasBearing = l.hasBearing();
    f.bearingDeg = l.getBearing();
    f.timeMs = l.getTime();
    f.elapsedMs = l.getElapsedRealtimeNanos() / 1_000_000;
    return f;
  }
}
