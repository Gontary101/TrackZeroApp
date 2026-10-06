package app.trackzero.screens;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.ride.TzRideService;

/** A screen's link to the ride in progress (S1, S2): bound while the screen is started. */
final class RideBinding
{
  interface Listener
  {
    void onRide(@NonNull TzRideService service);
  }

  @NonNull
  private final Listener mListener;
  @Nullable
  private TzRideService mService;
  private boolean mBound;
  private final ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder)
    {
      mService = ((TzRideService.LocalBinder) binder).service();
      mListener.onRide(mService);
    }

    @Override
    public void onServiceDisconnected(ComponentName name)
    {
      mService = null;
    }
  };

  RideBinding(@NonNull Listener listener)
  {
    mListener = listener;
  }

  void bind(@NonNull Context c)
  {
    if (!mBound)
      mBound = c.bindService(new Intent(c, TzRideService.class), mConnection, 0);
  }

  void unbind(@NonNull Context c)
  {
    if (mBound)
      c.unbindService(mConnection);
    mBound = false;
    mService = null;
  }

  @Nullable
  TzRideService service()
  {
    return mService;
  }
}
