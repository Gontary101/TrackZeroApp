package app.trackzero.follow;

/** What the rider has to do next. Glyphs and phrases are mapped in the UI ({@code TzManeuvers}). */
public enum Maneuver
{
  STRAIGHT,
  TURN_RIGHT,
  TURN_LEFT,
  SLIGHT_RIGHT,
  SLIGHT_LEFT,
  UTURN,
  /** Leave a roundabout by an exit ({@link RouteManeuver#exit}). Only from the router (Phase 15). */
  ROUNDABOUT,
  ARRIVE,
  /** The end of a routed leg: the rider reaches the track (to its start, or back onto it). */
  JOIN,
  REROUTE,
  HEAD_TO_START
}
