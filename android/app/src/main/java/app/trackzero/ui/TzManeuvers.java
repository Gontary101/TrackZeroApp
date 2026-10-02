package app.trackzero.ui;

import android.content.Context;
import android.widget.ImageView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import app.organicmaps.R;
import app.trackzero.follow.Maneuver;

/** Maneuver → glyph and phrase (Components board "Maneuver glyphs", plan Phase 4 ManeuverGlyph). */
public final class TzManeuvers
{
  private TzManeuvers() {}

  /** Sets the glyph on {@code view}; {@code bold} selects the 2.6 stroke used at 56dp. */
  public static void applyGlyph(@NonNull ImageView view, @NonNull Maneuver m, float rotationDeg, boolean bold)
  {
    view.setImageResource(glyph(m, bold));
    view.setScaleX(m == Maneuver.SLIGHT_LEFT ? -1f : 1f);
    view.setRotation(m == Maneuver.HEAD_TO_START ? rotationDeg : 0f);
  }

  @DrawableRes
  static int glyph(@NonNull Maneuver m, boolean bold)
  {
    return switch (m)
    {
      case STRAIGHT, HEAD_TO_START, JOIN -> bold ? R.drawable.ic_tz_straight_bold : R.drawable.ic_tz_straight;
      case TURN_RIGHT -> bold ? R.drawable.ic_tz_turn_right_bold : R.drawable.ic_tz_turn_right;
      case TURN_LEFT, UTURN -> bold ? R.drawable.ic_tz_turn_left_bold : R.drawable.ic_tz_turn_left;
      case SLIGHT_RIGHT, SLIGHT_LEFT -> bold ? R.drawable.ic_tz_fork_right_bold : R.drawable.ic_tz_fork_right;
      case ROUNDABOUT -> bold ? R.drawable.ic_tz_roundabout_bold : R.drawable.ic_tz_roundabout;
      case ARRIVE -> bold ? R.drawable.ic_tz_flag_bold : R.drawable.ic_tz_flag;
      case REROUTE -> bold ? R.drawable.ic_tz_reroute_bold : R.drawable.ic_tz_reroute;
    };
  }

  /** The turn card's instruction: "Turn right", "Take exit 2". */
  @NonNull
  public static String instruction(@NonNull Context c, @NonNull Maneuver m, int exit)
  {
    if (m == Maneuver.ROUNDABOUT)
      return exit > 0 ? c.getString(R.string.tz_mnv_roundabout, exit) : c.getString(R.string.tz_mnv_roundabout_any);
    return c.getString(phrase(m));
  }

  /** What the voice says: the instruction, with the roundabout named. */
  @NonNull
  public static String spoken(@NonNull Context c, @NonNull Maneuver m, int exit)
  {
    if (m == Maneuver.ROUNDABOUT && exit > 0)
      return c.getString(R.string.tz_voice_roundabout, exit);
    return instruction(c, m, exit);
  }

  @StringRes
  private static int phrase(@NonNull Maneuver m)
  {
    return switch (m)
    {
      case STRAIGHT -> R.string.tz_mnv_straight;
      case TURN_RIGHT -> R.string.tz_mnv_turn_right;
      case TURN_LEFT -> R.string.tz_mnv_turn_left;
      case SLIGHT_RIGHT -> R.string.tz_mnv_slight_right;
      case SLIGHT_LEFT -> R.string.tz_mnv_slight_left;
      case UTURN -> R.string.tz_mnv_uturn;
      case ROUNDABOUT -> R.string.tz_mnv_roundabout_any;
      case ARRIVE -> R.string.tz_mnv_arrive;
      case JOIN -> R.string.tz_mnv_join;
      case REROUTE -> R.string.tz_mnv_reroute;
      case HEAD_TO_START -> R.string.tz_mnv_head_to_start;
    };
  }
}
