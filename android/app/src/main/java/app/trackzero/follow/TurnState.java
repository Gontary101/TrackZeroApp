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
  /** Off route while a way back is being worked out (R3 "Recalculating…"). */
  public final boolean recalculating;
  /** Riding the route backwards (R3W); the maneuver fields say nothing then. */
  public final boolean wrongWay;
  /** {@link Maneuver#ROUNDABOUT} exits, from 1; 0 otherwise. */
  public final int exit;
  public final int thenExit;

  public TurnState(@NonNull Maneuver maneuver, double distanceM, @NonNull String street, float glyphRotationDeg,
                   @Nullable Maneuver thenManeuver, double thenDistanceM, @NonNull String thenStreet,
                   boolean imminent, boolean offRoute)
  {
    this(maneuver, distanceM, street, glyphRotationDeg, thenManeuver, thenDistanceM, thenStreet, imminent, offRoute,
         false, 0, 0);
  }

  public TurnState(@NonNull Maneuver maneuver, double distanceM, @NonNull String street, float glyphRotationDeg,
                   @Nullable Maneuver thenManeuver, double thenDistanceM, @NonNull String thenStreet,
                   boolean imminent, boolean offRoute, boolean recalculating, int exit, int thenExit)
  {
    this(maneuver, distanceM, street, glyphRotationDeg, thenManeuver, thenDistanceM, thenStreet, imminent, offRoute,
         recalculating, exit, thenExit, false);
  }

  private TurnState(@NonNull Maneuver maneuver, double distanceM, @NonNull String street, float glyphRotationDeg,
                    @Nullable Maneuver thenManeuver, double thenDistanceM, @NonNull String thenStreet,
                    boolean imminent, boolean offRoute, boolean recalculating, int exit, int thenExit,
                    boolean wrongWay)
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
    this.recalculating = recalculating;
    this.exit = exit;
    this.thenExit = thenExit;
    this.wrongWay = wrongWay;
  }

  @NonNull
  public static TurnState offRoute(double distanceToRouteM)
  {
    return new TurnState(Maneuver.REROUTE, distanceToRouteM, "", 0, null, 0, "", false, true);
  }

  @NonNull
  public static TurnState recalculating(double distanceToRouteM)
  {
    return new TurnState(Maneuver.REROUTE, distanceToRouteM, "", 0, null, 0, "", false, true, true, 0, 0);
  }

  @NonNull
  public static TurnState wrongWay()
  {
    return new TurnState(Maneuver.UTURN, 0, "", 0, null, 0, "", false, false, false, 0, 0, true);
  }
}
