package app.trackzero.ride;

import androidx.annotation.NonNull;

/**
 * The ride altitude (plan §8.4). With a barometer: barometric altitude plus the mean GPS − baro offset measured over
 * the first 60 s of accurate fixes. Without one (or until calibrated): GPS altitude smoothed with an EMA.
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

  /** Pressure altitude against the standard atmosphere. */
  void onBarometer(double altM)
  {
    mBaroAltM = altM;
  }

  /** The altitude after this fix, NaN while unknown. */
  double onFix(@NonNull TzFix f)
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
          mOffsetSum += f.altM - mBaroAltM;
          mOffsetCount++;
        }
      }
    }
    if (mOffsetCount > 0 && !Double.isNaN(mBaroAltM))
      return mBaroAltM + mOffsetSum / mOffsetCount;
    return mGpsEma;
  }
}
