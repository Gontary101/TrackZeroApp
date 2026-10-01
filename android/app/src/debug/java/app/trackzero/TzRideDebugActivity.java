package app.trackzero;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import app.organicmaps.R;
import app.trackzero.ride.RideSession;
import app.trackzero.ride.RideStats;
import app.trackzero.ride.TzRideService;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

/**
 * Debug-only ride controls until the ride screens exist:
 * adb shell am start -n app.trackzero.debug/app.trackzero.TzRideDebugActivity
 */
public class TzRideDebugActivity extends AppCompatActivity implements TzRideService.Listener
{
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final Runnable mTick = this::tick;
  @Nullable
  private TzRideService mService;
  private TextView mStatus;

  private final ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder)
    {
      mService = ((TzRideService.LocalBinder) binder).service();
      mService.addListener(TzRideDebugActivity.this);
      tick();
    }

    @Override
    public void onServiceDisconnected(ComponentName name)
    {
      mService = null;
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    final LinearLayout column = new LinearLayout(this);
    column.setOrientation(LinearLayout.VERTICAL);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_gutter);
    column.setPadding(pad, pad * 4, pad, pad);
    setContentView(column);

    mStatus = new TextView(this, null, 0, R.style.TzText_Body);
    column.addView(mStatus);
    button(column, "START FREE RIDE", () -> {
      TzRideService.start(this, null, "Free ride (debug)");
      bind();
    });
    button(column, "PAUSE", () -> {
      if (mService != null)
        mService.pause();
    });
    button(column, "RESUME", () -> {
      if (mService != null)
        mService.resume();
    });
    button(column, "END", () -> {
      if (mService != null)
        mService.end(ride -> mStatus.setText(ride == null ? "NOT SAVED"
                                                           : String.format(Locale.ROOT, "SAVED %s\n%.1f m, %d s, +%.0f m",
                                                                           ride.file, ride.distanceM, ride.movingS,
                                                                           ride.gainM)));
    });
  }

  private void button(@NonNull LinearLayout column, @NonNull String label, @NonNull Runnable action)
  {
    final MaterialButton b = new MaterialButton(new ContextThemeWrapper(this, R.style.TzButton_Secondary), null, 0);
    b.setText(label);
    b.setOnClickListener(v -> action.run());
    column.addView(b);
  }

  @Override
  protected void onStart()
  {
    super.onStart();
    bind();
  }

  private void bind()
  {
    bindService(new Intent(this, TzRideService.class), mConnection, 0);
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    mHandler.removeCallbacks(mTick);
    if (mService != null)
      mService.removeListener(this);
    unbindService(mConnection);
    mService = null;
  }

  @Override
  public void onRideChanged(@NonNull RideSession session)
  {
    tick();
  }

  private void tick()
  {
    mHandler.removeCallbacks(mTick);
    final RideSession s = mService == null ? null : mService.session();
    if (s == null)
      return;
    final RideStats st = s.stats();
    final long now = SystemClock.elapsedRealtime();
    mStatus.setText(String.format(Locale.ROOT,
                                  "%s  %s\n%.1f km/h  %.0f m  %d s\nalt %.1f  gain %.0f  grade %.1f  max %.1f km/h\n"
                                      + "points %d  segments %d",
                                  s.state(), s.name(), st.speedKmh(), st.distanceM(), st.movingMs(now) / 1000,
                                  st.altitudeM(), st.gainM(), st.gradePct(), st.maxKmh(), s.track().pointCount(),
                                  s.track().segments().size()));
    mHandler.postDelayed(mTick, 1000);
  }
}
