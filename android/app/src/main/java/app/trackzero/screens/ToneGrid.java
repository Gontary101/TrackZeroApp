package app.trackzero.screens;

import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import app.organicmaps.R;
import app.trackzero.ride.TonePattern;
import app.trackzero.ride.TzTones;
import app.trackzero.ui.TzToneTile;

/**
 * The six ToneTiles of {@code tz_tone_grid} (M1, T3): a tap plays that tone once through the speaker; nothing plays
 * by itself. The tone player is made at the first tap.
 */
final class ToneGrid
{
  @Nullable
  private TzTones mTones;

  ToneGrid(@NonNull View root)
  {
    tile(root, R.id.tz_tone_left, R.drawable.ic_tz_turn_left_bold, R.string.tz_tone_left, TonePattern.LEFT, false);
    tile(root, R.id.tz_tone_straight, R.drawable.ic_tz_straight_bold, R.string.tz_tone_straight, TonePattern.STRAIGHT,
         false);
    tile(root, R.id.tz_tone_right, R.drawable.ic_tz_turn_right_bold, R.string.tz_tone_right, TonePattern.RIGHT, false);
    tile(root, R.id.tz_tone_arrived, R.drawable.ic_tz_flag_bold, R.string.tz_tone_arrived, TonePattern.ARRIVE, false);
    tile(root, R.id.tz_tone_off_route, R.drawable.ic_tz_reroute_bold, R.string.tz_tone_off_route,
         TonePattern.OFF_ROUTE, true);
    tile(root, R.id.tz_tone_gps, R.drawable.ic_tz_gps_off_bold, R.string.tz_tone_gps_lost, TonePattern.GPS_LOST, true);
  }

  private void tile(@NonNull View root, @IdRes int id, @DrawableRes int glyph, @StringRes int label,
                    @NonNull TonePattern pattern, boolean warning)
  {
    final TzToneTile tile = root.findViewById(id);
    tile.bind(glyph, label, warning);
    tile.setOnClickListener(v -> {
      if (mTones == null)
        mTones = new TzTones(v.getContext());
      mTones.play(pattern);
    });
  }
}
