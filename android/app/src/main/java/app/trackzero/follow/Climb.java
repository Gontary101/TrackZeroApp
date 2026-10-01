package app.trackzero.follow;

import androidx.annotation.Nullable;

/** A climb of the route (plan §11.1). Distances are along the route line, altitudes from its profile. */
public final class Climb
{
  /** 1-based, in route order. */
  public final int index;
  public final double startAt;
  public final double summitAt;
  public final double startAlt;
  public final double summitAlt;
  public final double avgGradePct;
  /** From a GPX waypoint or the map at the summit; null until known (shown as "CLIMB {index}"). */
  @Nullable
  public String name;

  public Climb(int index, double startAt, double summitAt, double startAlt, double summitAlt)
  {
    this.index = index;
    this.startAt = startAt;
    this.summitAt = summitAt;
    this.startAlt = startAlt;
    this.summitAlt = summitAlt;
    this.avgGradePct = (summitAlt - startAlt) / (summitAt - startAt) * 100;
  }

  public double lengthM()
  {
    return summitAt - startAt;
  }
}
