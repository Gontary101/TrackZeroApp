package app.trackzero.ride;

import androidx.annotation.NonNull;
import app.trackzero.TzConfig;
import app.trackzero.geo.GainCounter;
import app.trackzero.geo.Geo;
import java.util.Arrays;

/**
 * Live ride measurements (plan §8.4). Pure: fed fixes by {@link RideSession}, which says when the ride is
 * recording. Durations use the fixes' monotonic clock.
 *
 * Per fix the session calls {@link #update} (speed, altitude: always) and then, while recording, {@link #record}
 * (distance, time, gain, grade).
 */
public final class RideStats
{
  static final int SPEED_WINDOW = 3;
  static final double MIN_DISPLAY_KMH = 1.5;
  static final double GRADE_EMA_ALPHA = 0.3;
  /** Faster than this between two fixes is a GPS jump (a cold start, a tunnel exit), not riding. */
  static final double MAX_PLAUSIBLE_KMH = 150;
  /** The speed shown drops to 0 when fixes stop coming for this long. */
  static final long SPEED_STALE_MS = 5_000;

  /** What survives a crash (plan §8.3): the totals, not the smoothing state. */
  public static final class Snapshot
  {
    public final double distanceM;
    public final long movingMs;
    public final double gainM;
    public final double maxKmh;
    public final double maxGradePct;

    public Snapshot(double distanceM, long movingMs, double gainM, double maxKmh, double maxGradePct)
    {
      this.distanceM = distanceM;
      this.movingMs = movingMs;
      this.gainM = gainM;
      this.maxKmh = maxKmh;
      this.maxGradePct = maxGradePct;
    }
  }

  private final AltitudeFilter mAltitude = new AltitudeFilter();
  private final GainCounter mGain = new GainCounter(TzConfig.ELE_HYSTERESIS_M);

  // Last accepted fix.
  private boolean mHasPrev;
  private double mPrevLat;
  private double mPrevLon;
  private long mPrevElapsedMs;

  private final double[] mSpeeds = new double[SPEED_WINDOW];
  private int mSpeedCount;
  private int mSpeedNext;
  private double mDisplayKmh;
  private double mAltitudeM = Double.NaN;
  /** Altitude for gain, grade and the profile ({@link AltitudeFilter#relativeM}). */
  private double mClimbAltM = Double.NaN;

  private double mDistanceM;
  private long mMovingMs;
  /** Start of the open moving-time interval, or -1 when not recording. */
  private long mRecordingSinceMs = -1;
  /** The previous accepted fix was recorded in the current stretch, so the distance to it counts. */
  private boolean mLinked;
  private double mMaxKmh;

  // Grade: (cumulative distance, altitude) of the current stretch, oldest first.
  private double[] mGradeDist = new double[64];
  private double[] mGradeAlt = new double[64];
  private int mGradeStart;
  private int mGradeEnd;
  private double mGradeEma = Double.NaN;
  private double mGradePct = Double.NaN;
  private double mMaxGradePct;

  void onBarometer(double altM)
  {
    mAltitude.onBarometer(altM);
  }

  /** Updates speed and altitude. Returns false if the fix is too inaccurate to measure with. */
  boolean update(@NonNull TzFix f)
  {
    if (f.accuracyM > TzConfig.IGNORE_FIX_ACCURACY_M)
      return false;

    double kmh = 0;
    if (f.hasSpeed)
    {
      kmh = f.speedMps * 3.6;
    }
    else if (mHasPrev && f.elapsedMs > mPrevElapsedMs)
    {
      kmh = Geo.distance(mPrevLat, mPrevLon, f.lat, f.lon) / ((f.elapsedMs - mPrevElapsedMs) / 1000.0) * 3.6;
    }
    mSpeeds[mSpeedNext] = kmh;
    mSpeedNext = (mSpeedNext + 1) % SPEED_WINDOW;
    mSpeedCount = Math.min(mSpeedCount + 1, SPEED_WINDOW);
    double sum = 0;
    for (int i = 0; i < mSpeedCount; i++)
      sum += mSpeeds[i];
    final double avg = sum / mSpeedCount;
    mDisplayKmh = avg < MIN_DISPLAY_KMH ? 0 : avg;

    mAltitude.onFix(f);
    mAltitudeM = mAltitude.absoluteM();
    mClimbAltM = mAltitude.relativeM();
    return true;
  }

  /** Accounts an accepted fix taken while recording. Call after {@link #update} with the same fix. */
  void record(@NonNull TzFix f)
  {
    if (mLinked)
    {
      final double d = Geo.distance(mPrevLat, mPrevLon, f.lat, f.lon);
      final double hours = (f.elapsedMs - mPrevElapsedMs) / 3_600_000.0;
      if (hours > 0 && d / 1000 / hours <= MAX_PLAUSIBLE_KMH)
        mDistanceM += d;
    }
    if (mRecordingSinceMs >= 0 && f.elapsedMs > mRecordingSinceMs)
    {
      mMovingMs += f.elapsedMs - mRecordingSinceMs;
      mRecordingSinceMs = f.elapsedMs;
    }
    mLinked = true;
    mMaxKmh = Math.max(mMaxKmh, mDisplayKmh);
    mGain.add(mClimbAltM);
    updateGrade();
  }

  /** Remembers an accepted fix as the reference for the next speed / distance. Call last for every fix. */
  void advance(@NonNull TzFix f, boolean recorded)
  {
    mHasPrev = true;
    mPrevLat = f.lat;
    mPrevLon = f.lon;
    mPrevElapsedMs = f.elapsedMs;
    if (!recorded)
      mLinked = false;
  }

  void startRecording(long elapsedMs)
  {
    mRecordingSinceMs = elapsedMs;
    mLinked = false;
    // Whatever happened while paused (a walk up a hill, a lift) is not ridden gain or grade.
    mGain.restart();
    mGradeStart = mGradeEnd = 0;
    mGradeEma = Double.NaN;
    mGradePct = Double.NaN;
  }

  void stopRecording(long elapsedMs)
  {
    if (mRecordingSinceMs >= 0 && elapsedMs > mRecordingSinceMs)
      mMovingMs += elapsedMs - mRecordingSinceMs;
    mRecordingSinceMs = -1;
    mLinked = false;
    mGradePct = Double.NaN;
  }

  /** Takes back moving time already counted (auto-pause dates the stop from when the rider slowed down). */
  void removeMovingTime(long ms)
  {
    mMovingMs = Math.max(0, mMovingMs - ms);
  }

  private void updateGrade()
  {
    if (Double.isNaN(mClimbAltM))
      return;
    if (mGradeEnd == mGradeDist.length)
      compactGrade();
    mGradeDist[mGradeEnd] = mDistanceM;
    mGradeAlt[mGradeEnd] = mClimbAltM;
    mGradeEnd++;

    final double window = TzConfig.GRADE_WINDOW_M;
    final double target = mDistanceM - window;
    // Keep exactly one sample at or before the window start.
    while (mGradeEnd - mGradeStart > 2 && mGradeDist[mGradeStart + 1] <= target)
      mGradeStart++;
    if (mGradeDist[mGradeStart] > target)
    {
      mGradePct = Double.NaN;
      return;
    }
    final int i = mGradeStart;
    final double span = mGradeDist[i + 1] - mGradeDist[i];
    final double t = span > 0 ? (target - mGradeDist[i]) / span : 1;
    final double altBack = mGradeAlt[i] + t * (mGradeAlt[i + 1] - mGradeAlt[i]);
    final double raw = (mClimbAltM - altBack) / window * 100;
    mGradeEma = Double.isNaN(mGradeEma) ? raw : mGradeEma + GRADE_EMA_ALPHA * (raw - mGradeEma);
    mGradePct = mGradeEma;
    if (mDisplayKmh >= TzConfig.GRADE_MIN_KMH)
      mMaxGradePct = Math.max(mMaxGradePct, mGradeEma);
  }

  private void compactGrade()
  {
    final int n = mGradeEnd - mGradeStart;
    if (n > mGradeDist.length / 2)
    {
      mGradeDist = Arrays.copyOf(mGradeDist, mGradeDist.length * 2);
      mGradeAlt = Arrays.copyOf(mGradeAlt, mGradeAlt.length * 2);
    }
    System.arraycopy(mGradeDist, mGradeStart, mGradeDist, 0, n);
    System.arraycopy(mGradeAlt, mGradeStart, mGradeAlt, 0, n);
    mGradeStart = 0;
    mGradeEnd = n;
  }

  @NonNull
  Snapshot snapshot(long elapsedMs)
  {
    return new Snapshot(mDistanceM, movingMs(elapsedMs), mGain.gainM(), mMaxKmh, mMaxGradePct);
  }

  void restore(@NonNull Snapshot s)
  {
    mDistanceM = s.distanceM;
    mMovingMs = s.movingMs;
    mGain.setGainM(s.gainM);
    mMaxKmh = s.maxKmh;
    mMaxGradePct = s.maxGradePct;
  }

  // ---- Readings ----

  /** Smoothed speed (average of the last 3 fixes), 0 below 1.5 km/h. */
  public double speedKmh()
  {
    return mDisplayKmh;
  }

  /** {@link #speedKmh()} as of {@code elapsedMs}: 0 once the fixes have stopped (no signal, standing indoors). */
  public double speedKmh(long elapsedMs)
  {
    return mHasPrev && elapsedMs - mPrevElapsedMs > SPEED_STALE_MS ? 0 : mDisplayKmh;
  }

  public double distanceM()
  {
    return mDistanceM;
  }

  /** Recording time up to {@code elapsedMs} (keeps running between fixes while recording). */
  public long movingMs(long elapsedMs)
  {
    final long open = mRecordingSinceMs >= 0 ? Math.max(0, elapsedMs - mRecordingSinceMs) : 0;
    return mMovingMs + open;
  }

  public double avgKmh(long elapsedMs)
  {
    final long ms = movingMs(elapsedMs);
    return ms > 0 ? mDistanceM / (ms / 1000.0) * 3.6 : 0;
  }

  public double maxKmh()
  {
    return mMaxKmh;
  }

  /** Above sea level, NaN while unknown. */
  public double altitudeM()
  {
    return mAltitudeM;
  }

  /** Altitude to measure climbing with: changes are right from the first fix, the level may be off. NaN unknown. */
  public double climbAltitudeM()
  {
    return mClimbAltM;
  }

  public double gainM()
  {
    return mGain.gainM();
  }

  /** NaN when not shown: below 4 km/h, or less than 50 m into the current stretch. */
  public double gradePct()
  {
    return mDisplayKmh >= TzConfig.GRADE_MIN_KMH ? mGradePct : Double.NaN;
  }

  public double maxGradePct()
  {
    return mMaxGradePct;
  }
}
