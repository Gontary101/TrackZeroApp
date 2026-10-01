package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

/**
 * Where the rider is among the route's climbs (plan §11.2): the climb they are on, else the next one, with the
 * numbers R5 shows. Pure; updated with every fix.
 */
public final class ClimbTracker
{
  /** SUMMIT IN needs this much riding on the climb to know the climbing speed. */
  static final long SUMMIT_IN_MIN_MS = 60_000;

  @NonNull
  private final List<Climb> mClimbs;
  @NonNull
  private final float[] mEle;
  private final double mStep;
  private double mAlong;
  /** The climb shown: the one the rider is on, else the next; -1 after the last. */
  private int mShown = -1;
  private boolean mOnClimb;
  /** Where and when (moving time) the rider was first seen on the shown climb; NaN / -1 before. */
  private double mEntryAlong = Double.NaN;
  private long mEntryMovingMs = -1;
  private long mMovingMs;

  /**
   * @param ele  the route's elevation every {@code step} metres (the climbs' profile)
   */
  public ClimbTracker(@NonNull List<Climb> climbs, @NonNull float[] ele, double step)
  {
    mClimbs = climbs;
    mEle = ele;
    mStep = step;
  }

  public void update(double along, long movingMs)
  {
    mAlong = along;
    mMovingMs = movingMs;
    int shown = -1;
    for (int i = 0; i < mClimbs.size(); i++)
    {
      if (mClimbs.get(i).summitAt > along)
      {
        shown = i;
        break;
      }
    }
    final boolean on = shown >= 0 && mClimbs.get(shown).startAt <= along;
    if (shown != mShown || !on)
    {
      mEntryAlong = Double.NaN;
      mEntryMovingMs = -1;
    }
    if (on && mEntryMovingMs < 0)
    {
      mEntryAlong = along;
      mEntryMovingMs = movingMs;
    }
    mShown = shown;
    mOnClimb = on;
  }

  @NonNull
  public List<Climb> climbs()
  {
    return mClimbs;
  }

  /** The climb the rider is on, else the next one; null after the last (or with none). */
  @Nullable
  public Climb shown()
  {
    return mShown < 0 ? null : mClimbs.get(mShown);
  }

  /** The rider is between the start and the summit of {@link #shown()}. */
  public boolean onClimb()
  {
    return mOnClimb;
  }

  public double toSummitM()
  {
    final Climb c = shown();
    return c == null ? Double.NaN : Math.max(0, c.summitAt - mAlong);
  }

  /** Climbed since the start of the climb, from the route profile: 0 before it. */
  public double gainedM()
  {
    final Climb c = shown();
    return c == null || !mOnClimb ? 0 : Math.max(0, profileAlt(mAlong) - c.startAlt);
  }

  /** Seconds to the summit at the speed ridden on this climb so far; NaN until a minute of it. */
  public double summitInSeconds()
  {
    final Climb c = shown();
    final long ms = mMovingMs - mEntryMovingMs;
    if (c == null || !mOnClimb || mEntryMovingMs < 0 || ms < SUMMIT_IN_MIN_MS)
      return Double.NaN;
    final double mps = (mAlong - mEntryAlong) / (ms / 1000.0);
    return mps > 0 ? (c.summitAt - mAlong) / mps : Double.NaN;
  }

  /** The route's elevation every {@link #step()} metres. */
  @NonNull
  public float[] profile()
  {
    return mEle;
  }

  public double step()
  {
    return mStep;
  }

  /** The route profile's elevation at {@code along}. */
  public double profileAlt(double along)
  {
    final double x = Math.max(0, along / mStep);
    final int i = (int) Math.min(Math.floor(x), mEle.length - 1);
    if (i >= mEle.length - 1)
      return mEle[mEle.length - 1];
    return mEle[i] + (x - i) * (mEle[i + 1] - mEle[i]);
  }
}
