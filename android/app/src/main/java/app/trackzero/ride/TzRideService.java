package app.trackzero.ride;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
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
import app.trackzero.TzTrace;
import app.trackzero.base.TzActivity;
import app.trackzero.base.TzLocation;
import app.trackzero.base.TzRoads;
import app.trackzero.base.TzRouter;
import app.trackzero.follow.Climb;
import app.trackzero.follow.ClimbDetector;
import app.trackzero.follow.ClimbNames;
import app.trackzero.follow.ClimbTracker;
import app.trackzero.follow.FollowEngine;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.Guide;
import app.trackzero.follow.ManeuverBuilder;
import app.trackzero.follow.RouteManeuver;
import app.trackzero.follow.RoutedPath;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.Geo;
import app.trackzero.routes.GpxTrack;
import app.trackzero.routes.Profile;
import app.trackzero.routes.RouteGeometry;
import app.trackzero.routes.RouteStore;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the ride in the foreground (plan §8.2): owns the {@link RideSession}, feeds it fixes, keeps the
 * notification and the crash journal up to date, and on a route ride follows the route and speaks the turns.
 * Screens bind to it to read the session and send commands.
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
  private static final String EXTRA_PLACE_LAT = "placeLat";
  private static final String EXTRA_PLACE_LON = "placeLon";
  private static final String EXTRA_PROFILE = "profile";
  private static final long JOURNAL_FLUSH_MS = 30_000;
  private static final long NOTIFICATION_UPDATE_MS = 10_000;
  /** Screen off and nothing to say: fixes come in batches this far apart, so the phone sleeps between them. */
  private static final long SCREEN_OFF_BATCH_MS = 15_000;
  /** A GPX waypoint names a climb when it is this close to its summit (plan §11.1). */
  private static final double SUMMIT_WAYPOINT_M = 300;

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
  /** The path previewed for a ride to a place, handed to the ride it starts (paths don't fit in an Intent). */
  @Nullable
  private static RoutedPath sPreviewedPath;

  private final LocalBinder mBinder = new LocalBinder();
  private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
  @Nullable
  private RideSession mSession;
  @Nullable
  private TzLocationSource mLocation;
  @Nullable
  private BarometerSource mBarometer;
  /** Turn-by-turn on a route ride (once the route is loaded) or a ride to a place; null on a free ride. */
  @Nullable
  private Guide mGuide;
  /** {@link Guide#linesVersion()} the climbs of a ride to a place were made for. */
  private int mClimbsLines = -1;
  /** The route's climbs (R5); null on a free ride or a route without elevation. */
  @Nullable
  private ClimbTracker mClimbs;
  @Nullable
  private TzVoice mVoice;
  /** The last fix good enough to follow with: a new engine starts from it, not from nowhere. */
  @Nullable
  private TzFix mFollowFix;
  private long mLastFlushMs;
  private long mLastNotificationMs;
  @Nullable
  private RideSession.State mNotifiedState;
  /** The screen is on (someone may look at the notification or the app). */
  private boolean mInteractive = true;

  private final BroadcastReceiver mScreen = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent)
    {
      mInteractive = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
      updateSources();
      // Shown again: up to date at once (it wasn't refreshed while the screen was off).
      if (mInteractive)
        updateNotification();
    }
  };

  public static boolean isRiding()
  {
    return sRiding;
  }

  /** Starts recording a ride; {@code routeId} is null for a free ride. */
  public static void start(@NonNull Context context, @Nullable String routeId, @NonNull String name,
                           @NonNull Profile profile)
  {
    final Intent i = new Intent(context, TzRideService.class).setAction(ACTION_START)
                         .putExtra(EXTRA_ROUTE_ID, routeId)
                         .putExtra(EXTRA_NAME, name)
                         .putExtra(EXTRA_PROFILE, profile.id);
    ContextCompat.startForegroundService(context, i);
  }

  /** Starts a ride to a place along {@code path} (the one previewed), worked out again if the rider leaves it. */
  public static void startTo(@NonNull Context context, @NonNull String name, double lat, double lon,
                             @NonNull RoutedPath path, @NonNull Profile profile)
  {
    sPreviewedPath = path;
    final Intent i = new Intent(context, TzRideService.class).setAction(ACTION_START)
                         .putExtra(EXTRA_NAME, name)
                         .putExtra(EXTRA_PROFILE, profile.id)
                         .putExtra(EXTRA_PLACE_LAT, lat)
                         .putExtra(EXTRA_PLACE_LON, lon);
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
    TzTrace.init(this);
    NotificationManagerCompat.from(this).createNotificationChannel(
        new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.tz_ride_channel))
            .setShowBadge(false)
            .build());
    final PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
    mInteractive = power == null || power.isInteractive();
    final IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_ON);
    screen.addAction(Intent.ACTION_SCREEN_OFF);
    ContextCompat.registerReceiver(this, mScreen, screen, ContextCompat.RECEIVER_NOT_EXPORTED);
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
        startSession(intent.getStringExtra(EXTRA_ROUTE_ID), intent.getStringExtra(EXTRA_NAME),
                     intent.getDoubleExtra(EXTRA_PLACE_LAT, Double.NaN),
                     intent.getDoubleExtra(EXTRA_PLACE_LON, Double.NaN),
                     Profile.of(intent.getStringExtra(EXTRA_PROFILE)));
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
        updateSources();
        startFollowing();
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
    unregisterReceiver(mScreen);
    stopSources();
    stopFollowing();
    super.onDestroy();
  }

  // ---- Commands (main thread) ----

  /** The ride in progress, or null. */
  @Nullable
  public RideSession session()
  {
    return mSession;
  }

  /** Turn-by-turn of a route ride or a ride to a place; null on a free ride or while the route loads. */
  @Nullable
  public Guide guide()
  {
    return mGuide;
  }

  /** Climbs of a route ride with elevation; null otherwise or while the route loads. */
  @Nullable
  public ClimbTracker climbs()
  {
    return mClimbs;
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
    updateSources();
    changed();
  }

  public void resume()
  {
    if (mSession == null)
      return;
    mSession.resume(SystemClock.elapsedRealtime());
    updateSources();
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
    stopFollowing();
    changed();
    final RideStore.Finished finished = new RideStore.Finished(s.routeId(), s.name(), s.startedAt(),
                                                               System.currentTimeMillis(), s.track().segments(),
                                                               totals, s.profile());
    RideStore.get(this).save(finished, ride -> {
      if (ride == null)
        Log.e(TAG, "Ride not saved; its journal is kept");
      mSession = null;
      stopRide();
      callback.onSaved(ride);
    });
  }

  // ---- Internals ----

  private void startSession(@Nullable String routeId, @Nullable String name, double placeLat, double placeLon,
                            @NonNull Profile profile)
  {
    final long now = SystemClock.elapsedRealtime();
    if (TzTrace.on())
      TzTrace.log("ride", "startSession route=" + routeId + " profile=" + profile.id
                          + (Double.isNaN(placeLat) ? "" : " place=" + TzTrace.ll(placeLat, placeLon)));
    mSession = new RideSession(TzPrefs.autoPause(this));
    sRiding = true;
    if (Double.isNaN(placeLat))
      mSession.start(System.currentTimeMillis(), now, routeId, name == null ? "" : name, profile);
    else
      mSession.startTo(System.currentTimeMillis(), now, name == null ? "" : name, placeLat, placeLon, profile);
    RideStore.get(this).startJournal(mSession.drainJournal(now));
    mLastFlushMs = now;
    updateSources();
    startFollowing();
    changed();
  }

  /**
   * GPS and barometer while they serve: not during a pause the rider ends themselves with the screen off (a café
   * stop: nothing to record, nobody looking). Screen off with nothing to announce, fixes come in batches.
   */
  private void updateSources()
  {
    final RideSession s = mSession;
    final boolean needed = s != null && s.state() != RideSession.State.SAVED
                           && (s.state() != RideSession.State.PAUSED || mInteractive);
    if (needed && mLocation == null)
      startSources();
    else if (!needed && mLocation != null)
      stopSources();
    if (mLocation != null)
      mLocation.setMaxDelay(mInteractive || speaks() ? 0 : SCREEN_OFF_BATCH_MS);
  }

  /** Turns are announced: fixes must come as they happen. */
  private boolean speaks()
  {
    return mGuide != null && TzPrefs.voice(this);
  }

  private void startSources()
  {
    // The map shows the fixes the ride records, not a position of its own (BUG-004).
    TzLocation.setRideSource(this, true);
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
    TzLocation.setRideSource(this, false);
    if (mLocation != null)
      mLocation.stop();
    mLocation = null;
    if (mBarometer != null)
      mBarometer.stop();
    mBarometer = null;
  }

  /** Loads the route of a route ride and starts turn-by-turn on it; a ride to a place starts on its path. */
  private void startFollowing()
  {
    final RideSession s = mSession;
    if (s != null && s.toPlace())
    {
      // After a restore there is no previewed path: one is worked out from the first fix.
      final RoutedPath path = sPreviewedPath;
      sPreviewedPath = null;
      mGuide = Guide.forPlace(s.placeLat(), s.placeLon(), path, mPaths, mFollowEvents, s.profile().foot);
      if (TzTrace.on())
        TzTrace.log("ride", "guide created (place, path " + (path == null ? "none" : Math.round(path.line.totalM()) + " m") + ")");
      mVoice = new TzVoice(this);
      updateSources();
      placeClimbs();
      if (mFollowFix != null)
        follow(s, mFollowFix);
      return;
    }
    final String routeId = s == null ? null : s.routeId();
    if (routeId == null)
      return;
    final RouteStore routes = RouteStore.get(this);
    routes.find(routeId, route -> {
      if (route == null || mSession == null)
        return;
      routes.loadGeometry(route, g -> {
        if (g == null || g.lineLat.length < 2 || mSession == null)
          return;
        // Junctions and summit names are read from the maps: off the main thread.
        final boolean maps = TzRoads.ready(this);
        TzIo.submit(() -> prepare(g, maps), prepared -> {
          if (mSession == null || mGuide != null)
            return;
          mGuide = Guide.forTrack(prepared.engine, mPaths, mFollowEvents, mSession.profile().foot);
          if (TzTrace.on())
            TzTrace.log("ride", "guide created (track " + Math.round(prepared.engine.totalM()) + " m)");
          mClimbs = prepared.climbs;
          mVoice = new TzVoice(this);
          updateSources();
          // Built while the rider moved on: place them now, before anything shows the guide's state.
          if (mFollowFix != null)
          {
            follow(mSession, mFollowFix);
            changed();
          }
        });
      });
    });
  }

  private static final class Prepared
  {
    @NonNull
    final FollowEngine engine;
    @Nullable
    final ClimbTracker climbs;

    Prepared(@NonNull FollowEngine engine, @Nullable ClimbTracker climbs)
    {
      this.engine = engine;
      this.climbs = climbs;
    }
  }

  /** Turn-by-turn and climbs for a route (blocking: maps and geometry work). */
  @NonNull
  private Prepared prepare(@NonNull RouteGeometry g, boolean maps)
  {
    final FollowRoute line = new FollowRoute(g.lineLat, g.lineLon);
    final List<RouteManeuver> maneuvers = ManeuverBuilder.build(line, maps ? TzRoads::junctions : null);
    ClimbTracker climbs = null;
    if (g.ele != null)
    {
      final List<Climb> found = ClimbDetector.detect(g.ele, RouteGeometry.ELE_STEP_M, line.totalM());
      for (Climb c : found)
        c.name = summitName(line.latAt(c.summitAt), line.lonAt(c.summitAt), g.waypoints, maps);
      climbs = new ClimbTracker(found, g.ele, RouteGeometry.ELE_STEP_M);
    }
    Log.i(TAG, "Following " + Math.round(line.totalM()) + " m, " + maneuvers.size() + " maneuvers"
               + (maps ? "" : " (maps not loaded: every bend)")
               + (climbs == null ? ", no elevation" : ", " + climbs.climbs().size() + " climbs"));
    return new Prepared(new FollowEngine(line, maneuvers, mFollowEvents), climbs);
  }

  /**
   * The map's peak or pass at the summit (it names the place, as the design asks), else a GPX waypoint there; null
   * if neither.
   */
  @Nullable
  private static String summitName(double lat, double lon, @NonNull List<GpxTrack.Waypoint> waypoints, boolean maps)
  {
    final String map = maps ? TzRoads.summitName(lat, lon) : "";
    if (!map.isEmpty())
      return map;
    String best = null;
    double bestM = SUMMIT_WAYPOINT_M;
    for (GpxTrack.Waypoint w : waypoints)
    {
      final double d = Geo.distance(lat, lon, w.lat, w.lon);
      final String name = ClimbNames.fromWaypoint(w.name);
      if (d <= bestM && name != null)
      {
        best = name;
        bestM = d;
      }
    }
    return best;
  }

  /** The climbs of a ride to a place: those of its path, worked out again whenever the path is. */
  private void placeClimbs()
  {
    final Guide g = mGuide;
    if (g == null || !g.toPlace() || g.linesVersion() == mClimbsLines)
      return;
    final int lines = g.linesVersion();
    mClimbsLines = lines;
    final RoutedPath path = g.placePath();
    if (path == null || path.ele == null)
    {
      mClimbs = null;
      return;
    }
    final boolean maps = TzRoads.ready(this);
    TzIo.submit(() -> climbsOf(path, maps), climbs -> {
      if (mGuide == g && g.linesVersion() == lines)
        mClimbs = climbs;
    });
  }

  /** Climbs along a routed path, named from the map (blocking). */
  @Nullable
  private static ClimbTracker climbsOf(@NonNull RoutedPath path, boolean maps)
  {
    final FollowRoute line = path.line;
    final float[] ele = RouteGeometry.elevationProfile(line.distances(), path.ele);
    if (ele == null)
      return null;
    final List<Climb> found = ClimbDetector.detect(ele, RouteGeometry.ELE_STEP_M, line.totalM());
    for (Climb c : found)
      c.name = maps ? emptyToNull(TzRoads.summitName(line.latAt(c.summitAt), line.lonAt(c.summitAt))) : null;
    return new ClimbTracker(found, ele, RouteGeometry.ELE_STEP_M);
  }

  @Nullable
  private static String emptyToNull(@NonNull String s)
  {
    return s.isEmpty() ? null : s;
  }

  /** Paths for the guide, from CoMaps' router for the ride's profile; answers for a guide since replaced are dropped. */
  private final Guide.Paths mPaths = new Guide.Paths() {
    @Override
    public void request(double fromLat, double fromLon, double toLat, double toLon, double bearingDeg)
    {
      final Guide g = mGuide;
      final RideSession s = mSession;
      final Profile profile = s == null ? Profile.ROAD : s.profile();
      TzRouter.route(fromLat, fromLon, toLat, toLon, profile, bearingDeg, (path, reason) -> {
        if (g == null || mGuide != g)
          return;
        g.onPath(path);
        placeClimbs();
        changed();
      });
    }

    @Override
    public void cancel()
    {
      TzRouter.cancel();
    }
  };

  private void stopFollowing()
  {
    if (mGuide != null)
      TzRouter.release();
    mGuide = null;
    mClimbsLines = -1;
    mClimbs = null;
    mFollowFix = null;
    if (mVoice != null)
      mVoice.shutdown();
    mVoice = null;
  }

  private final FollowEngine.Events mFollowEvents = new FollowEngine.Events() {
    @Override
    public void onTurnSoon(@NonNull RouteManeuver m, double distanceM)
    {
      if (voiceOn())
        mVoice.turnSoon(m, distanceM);
    }

    @Override
    public void onTurnNow(@NonNull RouteManeuver m)
    {
      if (voiceOn())
        mVoice.turnNow(m);
    }

    @Override
    public void onOffRoute()
    {
      if (voiceOn())
        mVoice.say(R.string.tz_voice_off_route);
    }

    @Override
    public void onWrongWay()
    {
      if (voiceOn())
        mVoice.say(R.string.tz_voice_wrong_way);
    }

    @Override
    public void onBackOnRoute()
    {
      if (voiceOn())
        mVoice.say(R.string.tz_voice_back_on_route);
    }

    @Override
    public void onArrived()
    {
      if (voiceOn())
        mVoice.say(R.string.tz_voice_arrived);
    }

    @Override
    public void onStreetWanted(@NonNull RouteManeuver m, @NonNull double[] lat, @NonNull double[] lon)
    {
      TzRoads.nameAt(TzRideService.this, lat, lon, name -> m.street = name);
    }

    /** Silent while the rider has paused the ride (walking the bike, a café stop). */
    private boolean voiceOn()
    {
      return mVoice != null && mSession != null && mSession.state() != RideSession.State.PAUSED;
    }
  };

  private void onFix(@NonNull TzFix f)
  {
    final RideSession s = mSession;
    if (s == null)
      return;
    if (TzTrace.on() && mFollowFix == null)
      TzTrace.log("ride", "first fix acc=" + Math.round(f.accuracyM) + " " + TzTrace.ll(f.lat, f.lon));
    final RideSession.State before = s.state();
    s.onFix(f);
    if (f.accuracyM <= TzConfig.IGNORE_FIX_ACCURACY_M)
    {
      TzLocation.pushRideFix(this, f);
      mFollowFix = f;
      follow(s, f);
    }
    if (s.state() != before || f.elapsedMs - mLastFlushMs >= JOURNAL_FLUSH_MS)
      flushJournal();
    changed();
  }

  private void follow(@NonNull RideSession s, @NonNull TzFix f)
  {
    final Guide g = mGuide;
    if (g == null)
      return;
    final double speedMps = f.hasSpeed ? f.speedMps : s.stats().speedKmh() / 3.6;
    final boolean placed = g.placed();
    g.update(f.lat, f.lon, f.hasBearing ? f.bearingDeg : Double.NaN, speedMps, f.elapsedMs, s.stats().distanceM(),
             s.stats().movingMs(f.elapsedMs));
    if (TzTrace.on() && !placed && g.placed())
      TzTrace.log("ride", "follow placed along=" + Math.round(g.along()));
    if (TzTrace.on() && g.placed())
      TzTrace.log("ride", "follow along=" + Math.round(g.along()) + " course="
                          + (f.hasBearing ? Math.round(f.bearingDeg) : "none") + " v=" + Math.round(speedMps * 10) / 10.0
                          + (g.turnState().wrongWay ? " WRONG WAY" : g.offRoute() ? " OFF ROUTE" : ""));
    placeClimbs();
    if (mClimbs != null)
      mClimbs.update(g.along(), s.stats().movingMs(f.elapsedMs));
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
    // Nobody sees it with the screen off: refreshed when the screen comes on (state changes still go out at once).
    if (stateChanged || (mInteractive && now - mLastNotificationMs >= NOTIFICATION_UPDATE_MS))
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
    stopFollowing();
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    stopSelf();
  }
}
