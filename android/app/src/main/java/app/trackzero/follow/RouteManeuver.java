package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A maneuver at a fixed place on the route. Only the street and the announcement flags change, on one thread. */
public final class RouteManeuver
{
  @NonNull
  public final Maneuver type;
  /** Distance along the route, metres. */
  public final double at;
  /** Signed direction change, degrees, right turns positive. */
  public final double angleDeg;

  /** Street after the maneuver; null until looked up, "" when it has none. */
  @Nullable
  public String street;
  boolean streetRequested;
  boolean farAnnounced;
  boolean nearAnnounced;

  public RouteManeuver(@NonNull Maneuver type, double at, double angleDeg)
  {
    this.type = type;
    this.at = at;
    this.angleDeg = angleDeg;
  }

  @NonNull
  @Override
  public String toString()
  {
    return type + "@" + Math.round(at) + "m(" + Math.round(angleDeg) + "°)";
  }
}
