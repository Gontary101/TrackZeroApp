package app.trackzero.ride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.TzConfig;

/**
 * One ride (plan §8.3): IDLE → RECORDING ⇄ PAUSED / AUTO_PAUSED → SAVED. Pure; driven on one thread by
 * {@link TzRideService}. Times passed in are monotonic ({@code elapsedRealtime}) except {@code startedAt} (UTC).
 *
 * Everything worth restoring after a crash is also appended to a journal buffer that the service drains to disk.
 */
public final class RideSession
{
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
  private long mStartedAt;
  @Nullable
  private TzFix mLastFix;
  /** When the speed last dropped below the auto-pause threshold while recording, or -1. */
  private long mSlowSinceMs = -1;
  @NonNull
  private final StringBuilder mJournal = new StringBuilder();

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
    s.mTrack = c.track;
    s.mTrack.breakSegment();
    s.mStats.restore(c.totals);
    return s;
  }

  public void start(long startedAtUtc, long elapsedMs, @Nullable String routeId, @NonNull String name)
  {
    if (mState != State.IDLE)
      throw new IllegalStateException("Already started: " + mState);
    mStartedAt = startedAtUtc;
    mRouteId = routeId;
    mName = name;
    mState = State.RECORDING;
    mStats.startRecording(elapsedMs);
    RideJournal.header(mJournal, startedAtUtc, routeId, name);
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
    }
    mStats.advance(f, recording);
  }

  private void autoPause(@NonNull TzFix f)
  {
    final double kmh = mStats.speedKmh();
    if (mState == State.RECORDING)
    {
      if (kmh >= TzConfig.AUTO_PAUSE_KMH)
      {
        mSlowSinceMs = -1;
      }
      else if (mSlowSinceMs < 0)
      {
        mSlowSinceMs = f.elapsedMs;
      }
      else if (f.elapsedMs - mSlowSinceMs >= TzConfig.AUTO_PAUSE_SECONDS * 1000L)
      {
        // The stop began when the rider slowed down, not when we became sure of it.
        mStats.stopRecording(f.elapsedMs);
        mStats.removeMovingTime(f.elapsedMs - mSlowSinceMs);
        mTrack.breakSegment();
        mSlowSinceMs = -1;
        mState = State.AUTO_PAUSED;
      }
    }
    else if (mState == State.AUTO_PAUSED && kmh > TzConfig.AUTO_RESUME_KMH)
    {
      startRecording(f.elapsedMs);
    }
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

  public long startedAt()
  {
    return mStartedAt;
  }

  /** The latest position, also while paused. */
  @Nullable
  public TzFix lastFix()
  {
    return mLastFix;
  }
}
