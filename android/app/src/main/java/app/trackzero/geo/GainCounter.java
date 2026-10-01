package app.trackzero.geo;

/**
 * Elevation gain with hysteresis: a rise counts once it exceeds the threshold above the lowest point since the last
 * counted rise, so altitude noise does not add up. Used for routes and for live rides.
 */
public final class GainCounter
{
  private final double mHysteresisM;
  private double mRef = Double.NaN;
  private double mGain;

  public GainCounter(double hysteresisM)
  {
    mHysteresisM = hysteresisM;
  }

  public void add(double altM)
  {
    if (Double.isNaN(altM))
      return;
    if (Double.isNaN(mRef) || altM < mRef)
    {
      mRef = altM;
    }
    else if (altM > mRef + mHysteresisM)
    {
      mGain += altM - mRef;
      mRef = altM;
    }
  }

  /** Forgets the reference altitude (e.g. after a pause) but keeps the gain counted so far. */
  public void restart()
  {
    mRef = Double.NaN;
  }

  public double gainM()
  {
    return mGain;
  }

  public void setGainM(double gainM)
  {
    mGain = gainM;
  }
}
