package app.trackzero.ride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.TzConfig;
import app.trackzero.geo.Geo;
import app.trackzero.routes.Profile;
import java.util.Arrays;
import java.util.List;

/**
 * One ride (plan §8.3): IDLE → RECORDING ⇄ PAUSED / AUTO_PAUSED → SAVED. Pure; driven on one thread by
 * {@link TzRideService}. Times passed in are monotonic ({@code elapsedRealtime}) except {@code startedAt} (UTC).
 *
 * Everything worth restoring after a crash is also appended to a journal buffer that the service drains to disk.
 */
public final class RideSession
{
  private static final double TRACE_STEP_M = 10;

  public enum State
  {
    IDLE,
    RECORDING,
    PAUSED,
    AUTO_PAUSED,
    SAVED
  }

  @NonNull
  private State mState = State.IDLE;
  private boolean mAutoPause;
  @NonNull
  private final RideStats mStats = new RideStats();
  @NonNull
  private RideTrack mTrack = new RideTrack();
  @Nullable
  private String mRouteId;
  @NonNull
  private String mName = "";
  /** A ride to a place: where to; NaN otherwise. */
  private double mPlaceLat = Double.NaN;
  private double mPlaceLon = Double.NaN;
  /** Fixed at START (Phase 22). */
  @NonNull
  private Profile mProfile = Profile.ROAD;
  private long mStartedAt;
  @Nullable
  private TzFix mLastFix;
  /** When the speed last dropped below the auto-pause threshold while recording, or -1. */
  private long mSlowSinceMs = -1;
  @NonNull
  private final StringBuilder mJournal = new StringBuilder();
  /** Altitude every 10 m ridden, for the elevation page. */
  private float[] mTraceDist = new float[256];
  private float[] mTraceAlt = new float[256];
  private int mTraceSize;

  public RideSession(boolean autoPause)
  {
    mAutoPause = autoPause;
  }

  /** A ride restored from its journal after the process died: paused, so the rider decides when to go on. */
  @NonNull
  static RideSession restore(@NonNull RideJournal.Content c, boolean autoPause)
  {
    final RideSession s = new RideSession(autoPause);
    s.mState = State.PAUSED;
    s.mStartedAt = c.startedAt;
    s.mRouteId = c.routeId;
    s.mName = c.name;
    s.mPlaceLat = c.placeLat;
    s.mPlaceLon = c.placeLon;
    s.mProfile = c.profile;
    s.mTrack = c.track;
    s.mTrack.breakSegment();
    s.mStats.restore(c.totals);
    double dist = 0;
    for (List<RideTrack.Point> seg : c.track.segments())
    {
      for (int i = 0; i < seg.size(); i++)
      {
        final RideTrack.Point p = seg.get(i);
        if (i > 0)
          dist += Geo.distance(seg.get(i - 1).lat, seg.get(i - 1).lon, p.lat, p.lon);
        s.trace(dist, p.altM);
      }
    }
    return s;
  }

  public void start(long startedAtUtc, long elapsedMs, @Nullable String routeId, @NonNull String name,
                    @NonNull Profile profile)
  {
    if (mState != State.IDLE)
      throw new IllegalStateException("Already started: " + mState);
    mStartedAt = startedAtUtc;
    mRouteId = routeId;
    mName = name;
    mProfile = profile;
    mState = State.RECORDING;
    mStats.startRecording(elapsedMs);
    RideJournal.header(mJournal, startedAtUtc, routeId, name);
    RideJournal.profile(mJournal, profile);
  }

  /** Starts a ride to a place (no route of the library: a path is worked out to it). */
  public void startTo(long startedAtUtc, long elapsedMs, @NonNull String name, double lat, double lon,
                      @NonNull Profile profile)
  {
    start(startedAtUtc, elapsedMs, null, name, profile);
    mPlaceLat = lat;
    mPlaceLon = lon;
    RideJournal.place(mJournal, lat, lon);
  }

  public void onFix(@NonNull TzFix f)
  {
    if (mState == State.IDLE || mState == State.SAVED)
      return;
    mLastFix = f;
    if (!mStats.update(f))
      return;

    if (mAutoPause)
      autoPause(f);

    final boolean recording = mState == State.RECORDING;
    if (recording)
    {
      mStats.record(f);
      final RideTrack.Point p = new RideTrack.Point(f.timeMs, f.lat, f.lon, mStats.altitudeM());
      if (mTrack.add(p))
        RideJournal.segment(mJournal);
      RideJournal.point(mJournal, p);
      trace(mStats.distanceM(), mStats.climbAltitudeM());
    }
    mStats.advance(f, recording);
  }

  private void autoPause(@NonNull TzFix f)
  {
    final double kmh = mStats.speedKmh();
    // On foot a stop is slower and shorter to tell from walking (Phase 25).
    final boolean foot = mProfile.foot;
    final double pauseKmh = foot ? TzConfig.AUTO_PAUSE_FOOT_KMH : TzConfig.AUTO_PAUSE_KMH;
    final double resumeKmh = foot ? TzConfig.AUTO_RESUME_FOOT_KMH : TzConfig.AUTO_RESUME_KMH;
    final int pauseS = foot ? TzConfig.AUTO_PAUSE_FOOT_SECONDS : TzConfig.AUTO_PAUSE_SECONDS;
    if (mState == State.RECORDING)
    {
      if (kmh >= pauseKmh)
      {
        mSlowSinceMs = -1;
      }
      else if (mSlowSinceMs < 0)
      {
        mSlowSinceMs = f.elapsedMs;
      }
      else if (f.elapsedMs - mSlowSinceMs >= pauseS * 1000L)
      {
        // The stop began when the rider slowed down, not when we became sure of it.
        mStats.stopRecording(f.elapsedMs);
        mStats.removeMovingTime(f.elapsedMs - mSlowSinceMs);
        mTrack.breakSegment();
        mSlowSinceMs = -1;
        mState = State.AUTO_PAUSED;
      }
    }
    else if (mState == State.AUTO_PAUSED && kmh > resumeKmh)
    {
      startRecording(f.elapsedMs);
    }
  }

  private void trace(double distM, double altM)
  {
    if (Double.isNaN(altM) || (mTraceSize > 0 && distM - mTraceDist[mTraceSize - 1] < TRACE_STEP_M))
      return;
    if (mTraceSize == mTraceDist.length)
    {
      mTraceDist = Arrays.copyOf(mTraceDist, mTraceSize * 2);
      mTraceAlt = Arrays.copyOf(mTraceAlt, mTraceSize * 2);
    }
    mTraceDist[mTraceSize] = (float) distM;
    mTraceAlt[mTraceSize] = (float) altM;
    mTraceSize++;
  }

  /** Manual pause; never resumes by itself. */
  public void pause(long elapsedMs)
  {
    if (mState == State.RECORDING)
    {
      mStats.stopRecording(elapsedMs);
      mTrack.breakSegment();
    }
    if (mState == State.RECORDING || mState == State.AUTO_PAUSED)
      mState = State.PAUSED;
    mSlowSinceMs = -1;
  }

  public void resume(long elapsedMs)
  {
    if (mState == State.PAUSED || mState == State.AUTO_PAUSED)
      startRecording(elapsedMs);
  }

  private void startRecording(long elapsedMs)
  {
    mState = State.RECORDING;
    mStats.startRecording(elapsedMs);
    mTrack.breakSegment();
    mSlowSinceMs = -1;
  }

  /** Ends the ride; the caller saves {@link #track()} and the totals. */
  public void end(long elapsedMs)
  {
    if (mState == State.IDLE || mState == State.SAVED)
      return;
    if (mState == State.RECORDING)
      mStats.stopRecording(elapsedMs);
    mState = State.SAVED;
  }

  public void setAutoPause(boolean on, long elapsedMs)
  {
    mAutoPause = on;
    if (!on && mState == State.AUTO_PAUSED)
      startRecording(elapsedMs);
  }

  /** Journal records since the last call, followed by the current totals; empty if nothing was recorded. */
  @NonNull
  String drainJournal(long elapsedMs)
  {
    if (mState == State.IDLE)
      return "";
    mJournal.append(RideJournal.totals(mStats.snapshot(elapsedMs)));
    final String text = mJournal.toString();
    mJournal.setLength(0);
    return text;
  }

  // ---- Readings ----

  @NonNull
  public State state()
  {
    return mState;
  }

  @NonNull
  public RideStats stats()
  {
    return mStats;
  }

  @NonNull
  public RideTrack track()
  {
    return mTrack;
  }

  @Nullable
  public String routeId()
  {
    return mRouteId;
  }

  @NonNull
  public String name()
  {
    return mName;
  }

  /** How the rider gets about on this ride, fixed at START. */
  @NonNull
  public Profile profile()
  {
    return mProfile;
  }

  /** A ride to a place ({@link #placeLat()}, {@link #placeLon()}). */
  public boolean toPlace()
  {
    return !Double.isNaN(mPlaceLat);
  }

  public double placeLat()
  {
    return mPlaceLat;
  }

  public double placeLon()
  {
    return mPlaceLon;
  }

  public long startedAt()
  {
    return mStartedAt;
  }

  /** Ridden distance (m) of each altitude sample, a copy. */
  @NonNull
  public float[] traceDistances()
  {
    return Arrays.copyOf(mTraceDist, mTraceSize);
  }

  /** Altitude (m, for its shape: {@link RideStats#climbAltitudeM}) every 10 m ridden, a copy. */
  @NonNull
  public float[] traceAltitudes()
  {
    return Arrays.copyOf(mTraceAlt, mTraceSize);
  }

  /** The latest position, also while paused. */
  @Nullable
  public TzFix lastFix()
  {
    return mLastFix;
  }
}
