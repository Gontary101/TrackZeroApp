package app.trackzero.follow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Everything the turn card shows for one fix. Immutable. */
public final class TurnState
{
  @NonNull
  public final Maneuver maneuver;
  /** Distance to the maneuver (or to the start / off-route distance). */
  public final double distanceM;
  /** Street after the maneuver, "" when unknown. */
  @NonNull
  public final String street;
  /** HEAD_TO_START: glyph rotation relative to the rider's heading, degrees clockwise. */
  public final float glyphRotationDeg;
  /** Following maneuver when within 1 km of the next one, else null. */
  @Nullable
  public final Maneuver thenManeuver;
  public final double thenDistanceM;
  @NonNull
  public final String thenStreet;
  /** Within {@code IMMINENT_M} of the maneuver. */
  public final boolean imminent;
  /** Off route: {@link #distanceM} is the distance to the route. */
  public final boolean offRoute;

  public TurnState(@NonNull Maneuver maneuver, double distanceM, @NonNull String street, float glyphRotationDeg,
                   @Nullable Maneuver thenManeuver, double thenDistanceM, @NonNull String thenStreet,
                   boolean imminent, boolean offRoute)
  {
    this.maneuver = maneuver;
    this.distanceM = distanceM;
    this.street = street;
    this.glyphRotationDeg = glyphRotationDeg;
    this.thenManeuver = thenManeuver;
    this.thenDistanceM = thenDistanceM;
    this.thenStreet = thenStreet;
    this.imminent = imminent;
    this.offRoute = offRoute;
  }

  @NonNull
  public static TurnState offRoute(double distanceToRouteM)
  {
    return new TurnState(Maneuver.REROUTE, distanceToRouteM, "", 0, null, 0, "", false, true);
  }
}
