package app.trackzero.ride;

import androidx.annotation.NonNull;

/**
 * The ride altitude (plan §8.4). With a barometer: barometric altitude plus the mean GPS − baro offset measured over
 * the first 60 s of accurate fixes. Without one (or until calibrated): GPS altitude smoothed with an EMA.
 *
 * Climbing (gain, grade, profile) needs only changes, which the barometer measures well from the start; the offset
 * moving while it calibrates is not climbing. So there are two readings: {@link #absoluteM} for the altitude shown
 * and saved, {@link #relativeM} with the offset held at its first value for everything measured from changes.
 */
final class AltitudeFilter
{
  static final long CALIBRATION_MS = 60_000;
  static final float CALIBRATION_MAX_ACCURACY_M = 15;
  static final double GPS_EMA_ALPHA = 0.2;

  private double mBaroAltM = Double.NaN;
  private long mCalibrationStartMs = -1;
  private double mOffsetSum;
  private int mOffsetCount;
  private double mGpsEma = Double.NaN;
  private double mFirstOffset = Double.NaN;
  private double mAbsoluteM = Double.NaN;
  private double mRelativeM = Double.NaN;

  /** Pressure altitude against the standard atmosphere. */
  void onBarometer(double altM)
  {
    mBaroAltM = altM;
  }

  void onFix(@NonNull TzFix f)
  {
    if (f.hasAlt)
    {
      mGpsEma = Double.isNaN(mGpsEma) ? f.altM : mGpsEma + GPS_EMA_ALPHA * (f.altM - mGpsEma);
      if (!Double.isNaN(mBaroAltM) && f.accuracyM > 0 && f.accuracyM <= CALIBRATION_MAX_ACCURACY_M)
      {
        if (mCalibrationStartMs < 0)
          mCalibrationStartMs = f.elapsedMs;
        if (f.elapsedMs - mCalibrationStartMs <= CALIBRATION_MS)
        {
          if (mOffsetCount == 0)
            mFirstOffset = f.altM - mBaroAltM;
          mOffsetSum += f.altM - mBaroAltM;
          mOffsetCount++;
        }
      }
    }
    final boolean baro = mOffsetCount > 0 && !Double.isNaN(mBaroAltM);
    mAbsoluteM = baro ? mBaroAltM + mOffsetSum / mOffsetCount : mGpsEma;
    mRelativeM = baro ? mBaroAltM + mFirstOffset : mGpsEma;
  }

  /** Best estimate of the altitude above sea level, NaN while unknown. */
  double absoluteM()
  {
    return mAbsoluteM;
  }

  /** Altitude for measuring changes: a constant away from {@link #absoluteM} once calibrated. NaN while unknown. */
  double relativeM()
  {
    return mRelativeM;
  }
}
