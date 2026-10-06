package app.trackzero.follow;

import app.trackzero.TzConfig;

/**
 * The ride map's zoom (plan §9, BUG-004): z17 below 15 km/h, z16 up to 30, z15 above; a new band must hold for 5 s.
 * On foot (Phase 25) always z17.
 * Zooms are levels, not factors (the engine's follow command), so asking again cannot add up; the first one goes with
 * the ride camera. A zoom the map has not reached 3 s after it was asked (a follow update cut its animation short) is
 * asked again.
 */
public final class RideZoom
{
  public static final long STABLE_MS = 5000;
  public static final long SETTLE_MS = 3000;
  public static final int NONE = -1;

  /** The zoom wanted; NONE until the ride camera was set. */
  private int mZoom = NONE;
  private long mAskedAtMs;
  private int mCandidate = NONE;
  private long mCandidateSince;
  private boolean mFoot;

  /** On foot: one zoom whatever the speed. */
  public void setFoot(boolean foot)
  {
    mFoot = foot;
  }

  private int bandFor(double kmh)
  {
    return mFoot ? TzConfig.RIDE_ZOOM_FOOT : band(kmh);
  }

  public static int band(double kmh)
  {
    return kmh < 15 ? 17 : kmh <= 30 ? 16 : 15;
  }

  /** The zoom for the ride camera at this speed; from now on {@link #update} keeps it. */
  public int start(double kmh, long nowMs)
  {
    mZoom = bandFor(kmh);
    mAskedAtMs = nowMs;
    mCandidate = NONE;
    return mZoom;
  }

  public boolean started()
  {
    return mZoom != NONE;
  }

  /**
   * Call about once a second while the map follows the rider.
   * @param seenZoom the map's zoom now
   * @return the zoom level to ask for now, or NONE
   */
  public int update(double kmh, int seenZoom, long nowMs)
  {
    if (mZoom == NONE)
      return NONE;
    final int band = bandFor(kmh);
    if (band == mZoom)
    {
      mCandidate = NONE;
      return seenZoom != mZoom && nowMs - mAskedAtMs >= SETTLE_MS ? ask(nowMs) : NONE;
    }
    if (band != mCandidate)
    {
      mCandidate = band;
      mCandidateSince = nowMs;
      return NONE;
    }
    if (nowMs - mCandidateSince < STABLE_MS)
      return NONE;
    mZoom = band;
    mCandidate = NONE;
    return ask(nowMs);
  }

  private int ask(long nowMs)
  {
    mAskedAtMs = nowMs;
    return mZoom;
  }

  public int zoom()
  {
    return mZoom;
  }
}
