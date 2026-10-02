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
  /** {@link Maneuver#ROUNDABOUT}: the exit to take, from 1; 0 otherwise. */
  public final int exit;

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
    this.exit = 0;
  }

  /** A maneuver whose street is already known (the router names them): it is never looked up. */
  public RouteManeuver(@NonNull Maneuver type, double at, int exit, @NonNull String street)
  {
    this.type = type;
    this.at = at;
    this.angleDeg = 0;
    this.exit = exit;
    this.street = street;
    this.streetRequested = true;
  }

  @NonNull
  @Override
  public String toString()
  {
    return type + "@" + Math.round(at) + "m(" + Math.round(angleDeg) + "°)";
  }
}
