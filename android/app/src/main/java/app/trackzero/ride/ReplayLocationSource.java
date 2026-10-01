package app.trackzero.ride;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.TzConfig;
import app.trackzero.TzIo;
import app.trackzero.routes.GpxParser;
import app.trackzero.routes.GpxTrack;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Debug "GPS" (TzConfig.DEBUG_REPLAY): one fix per second moving along {@code files/tz/replay.gpx} at 25 km/h.
 * Unlike real GPS it is driven by a timer, so it holds a wake lock to keep going with the screen off.
 */
final class ReplayLocationSource implements TzLocationSource
{
  private static final String TAG = "TzReplay";
  private static final long INTERVAL_MS = 1000;
  private static final long MAX_REPLAY_MS = 12 * 3600 * 1000L;

  @NonNull
  private final File mFile;
  @NonNull
  private final PowerManager.WakeLock mWakeLock;
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final Runnable mTick = this::tick;
  @Nullable
  private Listener mListener;
  @Nullable
  private ReplayPath mPath;
  private long mStartMs;

  ReplayLocationSource(@NonNull Context context)
  {
    mFile = new File(context.getFilesDir(), "tz/replay.gpx");
    final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrackZero:replay");
    mWakeLock.setReferenceCounted(false);
  }

  @Override
  public void start(@NonNull Listener listener)
  {
    mListener = listener;
    TzIo.submit(this::load, path -> {
      if (path == null || mListener == null)
        return;
      mPath = path;
      mStartMs = SystemClock.elapsedRealtime();
      mWakeLock.acquire(MAX_REPLAY_MS);
      tick();
    });
  }

  @Override
  public void stop()
  {
    mListener = null;
    mHandler.removeCallbacks(mTick);
    if (mWakeLock.isHeld())
      mWakeLock.release();
  }

  @Nullable
  private ReplayPath load()
  {
    try (InputStream in = new FileInputStream(mFile))
    {
      final GpxTrack t = GpxParser.parse(in);
      if (t.size() >= 2)
        return new ReplayPath(t.lat, t.lon, t.ele, TzConfig.REPLAY_KMH / 3.6);
      Log.e(TAG, mFile + " has no track");
    }
    catch (IOException e)
    {
      Log.e(TAG, "Cannot replay " + mFile, e);
    }
    return null;
  }

  private void tick()
  {
    if (mListener == null || mPath == null)
      return;
    final long now = SystemClock.elapsedRealtime();
    final TzFix f = mPath.fixAt(now - mStartMs, System.currentTimeMillis());
    f.elapsedMs = now;
    mListener.onFix(f);
    mHandler.postDelayed(mTick, INTERVAL_MS);
  }
}
