package app.trackzero.ride;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import app.organicmaps.R;
import app.trackzero.TzConfig;
import app.trackzero.TzIo;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzActivity;
import app.trackzero.format.TzFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the ride in the foreground (plan §8.2): owns the {@link RideSession}, feeds it fixes, keeps the
 * notification and the crash journal up to date. Screens bind to it to read the session and send commands.
 * Everything runs on the main thread; files are written on the {@link TzIo} thread.
 */
public final class TzRideService extends Service
{
  private static final String TAG = "TzRide";
  private static final String CHANNEL_ID = "tz_ride";
  private static final int NOTIFICATION_ID = 0x7a01;
  private static final String ACTION_START = "app.trackzero.ride.START";
  private static final String ACTION_TOGGLE = "app.trackzero.ride.TOGGLE";
  private static final String ACTION_RESTORE = "app.trackzero.ride.RESTORE";
  private static final String EXTRA_ROUTE_ID = "routeId";
  private static final String EXTRA_NAME = "name";
  private static final long JOURNAL_FLUSH_MS = 30_000;
  private static final long NOTIFICATION_UPDATE_MS = 10_000;

  /** Called on the main thread after every fix and state change. */
  public interface Listener
  {
    void onRideChanged(@NonNull RideSession session);
  }

  public interface SavedCallback
  {
    /** {@code ride} is null if the ride could not be written (its journal is kept). */
    void onSaved(@Nullable Ride ride);
  }

  public final class LocalBinder extends Binder
  {
    @NonNull
    public TzRideService service()
    {
      return TzRideService.this;
    }
  }

  /** A ride is in progress (recording, paused or being saved) in this process. */
  private static boolean sRiding;

  private final LocalBinder mBinder = new LocalBinder();
  private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
  @Nullable
  private RideSession mSession;
  @Nullable
  private TzLocationSource mLocation;
  @Nullable
  private BarometerSource mBarometer;
  private long mLastFlushMs;
  private long mLastNotificationMs;
  @Nullable
  private RideSession.State mNotifiedState;

  public static boolean isRiding()
  {
    return sRiding;
  }

  /** Starts recording a ride; {@code routeId} is null for a free ride. */
  public static void start(@NonNull Context context, @Nullable String routeId, @NonNull String name)
  {
    final Intent i = new Intent(context, TzRideService.class).setAction(ACTION_START)
                         .putExtra(EXTRA_ROUTE_ID, routeId)
                         .putExtra(EXTRA_NAME, name);
    ContextCompat.startForegroundService(context, i);
  }

  /** Brings back a ride the process lost (crash, reboot), paused. Call when the app comes up. */
  public static void restoreIfNeeded(@NonNull Context context)
  {
    if (RideStore.get(context).hasJournal())
      ContextCompat.startForegroundService(context,
                                           new Intent(context, TzRideService.class).setAction(ACTION_RESTORE));
  }

  @Override
  public void onCreate()
  {
    super.onCreate();
    NotificationManagerCompat.from(this).createNotificationChannel(
        new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.tz_ride_channel))
            .setShowBadge(false)
            .build());
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId)
  {
    // Android requires the foreground promotion on every start, whatever we do next.
    goForeground();
    final String action = intent == null ? ACTION_RESTORE : intent.getAction();
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED)
    {
      Log.w(TAG, "No location permission, not recording");
      stopRide();
      return START_NOT_STICKY;
    }
    if (ACTION_START.equals(action))
    {
      if (mSession == null)
        startSession(intent.getStringExtra(EXTRA_ROUTE_ID), intent.getStringExtra(EXTRA_NAME));
    }
    else if (ACTION_TOGGLE.equals(action))
    {
      if (mSession != null)
      {
        if (mSession.state() == RideSession.State.RECORDING)
          pause();
        else
          resume();
      }
    }
    else if (mSession == null)
    {
      // ACTION_RESTORE, or the system restarting us after the process died.
      RideStore.get(this).readJournal(content -> {
        if (mSession != null)
          return;
        if (content == null)
        {
          stopRide();
          return;
        }
        mSession = RideSession.restore(content, TzPrefs.autoPause(this));
        sRiding = true;
        startSources();
        changed();
      });
    }
    return START_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent)
  {
    return mBinder;
  }

  @Override
  public void onDestroy()
  {
    stopSources();
    super.onDestroy();
  }

  // ---- Commands (main thread) ----

  /** The ride in progress, or null. */
  @Nullable
  public RideSession session()
  {
    return mSession;
  }

  public void addListener(@NonNull Listener l)
  {
    mListeners.add(l);
  }

  public void removeListener(@NonNull Listener l)
  {
    mListeners.remove(l);
  }

  public void pause()
  {
    if (mSession == null)
      return;
    mSession.pause(SystemClock.elapsedRealtime());
    flushJournal();
    changed();
  }

  public void resume()
  {
    if (mSession == null)
      return;
    mSession.resume(SystemClock.elapsedRealtime());
    changed();
  }

  public void setAutoPause(boolean on)
  {
    if (mSession != null)
      mSession.setAutoPause(on, SystemClock.elapsedRealtime());
  }

  /** Ends and saves the ride, then stops the service. */
  public void end(@NonNull SavedCallback callback)
  {
    final RideSession s = mSession;
    if (s == null)
      return;
    final long now = SystemClock.elapsedRealtime();
    final RideStats.Snapshot totals = s.stats().snapshot(now);
    s.end(now);
    stopSources();
    changed();
    final RideStore.Finished finished = new RideStore.Finished(s.routeId(), s.name(), s.startedAt(),
                                                               System.currentTimeMillis(), s.track().segments(),
                                                               totals);
    RideStore.get(this).save(finished, ride -> {
      if (ride == null)
        Log.e(TAG, "Ride not saved; its journal is kept");
      mSession = null;
      stopRide();
      callback.onSaved(ride);
    });
  }

  // ---- Internals ----

  private void startSession(@Nullable String routeId, @Nullable String name)
  {
    final long now = SystemClock.elapsedRealtime();
    mSession = new RideSession(TzPrefs.autoPause(this));
    sRiding = true;
    mSession.start(System.currentTimeMillis(), now, routeId, name == null ? "" : name);
    RideStore.get(this).startJournal(mSession.drainJournal(now));
    mLastFlushMs = now;
    startSources();
    changed();
  }

  private void startSources()
  {
    mLocation = TzConfig.DEBUG_REPLAY ? new ReplayLocationSource(this) : new GpsLocationSource(this);
    mLocation.start(this::onFix);
    // A replayed track carries its own elevation; the phone's barometer knows nothing about it.
    if (TzConfig.DEBUG_REPLAY)
      return;
    mBarometer = new BarometerSource(this);
    mBarometer.start(alt -> {
      if (mSession != null)
        mSession.stats().onBarometer(alt);
    });
  }

  private void stopSources()
  {
    if (mLocation != null)
      mLocation.stop();
    mLocation = null;
    if (mBarometer != null)
      mBarometer.stop();
    mBarometer = null;
  }

  private void onFix(@NonNull TzFix f)
  {
    final RideSession s = mSession;
    if (s == null)
      return;
    final RideSession.State before = s.state();
    s.onFix(f);
    if (s.state() != before || f.elapsedMs - mLastFlushMs >= JOURNAL_FLUSH_MS)
      flushJournal();
    changed();
  }

  private void flushJournal()
  {
    if (mSession == null)
      return;
    final long now = SystemClock.elapsedRealtime();
    RideStore.get(this).appendJournal(mSession.drainJournal(now));
    mLastFlushMs = now;
  }

  private void changed()
  {
    final RideSession s = mSession;
    if (s == null)
      return;
    final long now = SystemClock.elapsedRealtime();
    final boolean stateChanged = mNotifiedState != s.state();
    if (stateChanged || now - mLastNotificationMs >= NOTIFICATION_UPDATE_MS)
      updateNotification();
    for (Listener l : mListeners)
      l.onRideChanged(s);
  }

  private void goForeground()
  {
    final int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
    try
    {
      ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type);
    }
    catch (RuntimeException e)
    {
      // Not allowed from the background (Android 12+) or without the location permission (14+).
      Log.e(TAG, "Cannot go foreground", e);
    }
  }

  private void updateNotification()
  {
    mLastNotificationMs = SystemClock.elapsedRealtime();
    mNotifiedState = mSession == null ? null : mSession.state();
    try
    {
      NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification());
    }
    catch (SecurityException e)
    {
      // POST_NOTIFICATIONS refused: the ride still records.
    }
  }

  @NonNull
  private Notification notification()
  {
    final RideSession s = mSession;
    final boolean recording = s != null && s.state() == RideSession.State.RECORDING;
    final int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    final PendingIntent open = PendingIntent.getActivity(
        this, 0, new Intent(this, TzActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags);
    final PendingIntent toggle =
        PendingIntent.getService(this, 1, new Intent(this, TzRideService.class).setAction(ACTION_TOGGLE), flags);

    final NotificationCompat.Builder b =
        new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tz_routes)
            .setContentTitle(getString(recording || s == null ? R.string.tz_ride_notification_recording
                                                              : R.string.tz_ride_notification_paused))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
    if (s != null)
    {
      final long movingS = s.stats().movingMs(SystemClock.elapsedRealtime()) / 1000;
      final TzFormat.Value distance =
          TzFormat.distance(s.stats().distanceM(), TzPrefs.units(this), Locale.getDefault());
      b.setContentText(getString(R.string.tz_ride_notification_text, TzFormat.duration(movingS), distance.toString()));
      b.addAction(recording ? R.drawable.ic_tz_pause : R.drawable.ic_tz_play,
                  getString(recording ? R.string.tz_ride_pause : R.string.tz_ride_resume), toggle);
    }
    return b.build();
  }

  private void stopRide()
  {
    sRiding = false;
    stopSources();
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    stopSelf();
  }
}
