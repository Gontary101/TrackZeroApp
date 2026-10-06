package app.trackzero.screens;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.ride.TonePattern;
import app.trackzero.ride.TzTones;
import app.trackzero.ui.TzToggleRow;
import app.trackzero.ui.TzToneTile;

/**
 * M1 · Sounds (FEAT-008, Phase 32): turn tones and spoken directions (independent, the same settings as R6's), the
 * off-route and GPS-lost warnings, and the tones to hear. Spoken directions' text opens the system voice settings.
 */
public class TzSoundsScreen extends TzScreen
{
  private static final String TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

  @Nullable
  private TzTones mTones;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_sounds, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    final Context c = requireContext();
    view.findViewById(R.id.tz_sounds_back).setOnClickListener(v -> back());
    toggle(view, R.id.tz_sounds_tones, TzPrefs.tones(c), on -> TzPrefs.setTones(c, on));
    final TzToggleRow voice = toggle(view, R.id.tz_sounds_voice, TzPrefs.voice(c), on -> TzPrefs.setVoice(c, on));
    voice.setTextAction(() -> openTtsSettings(c));
    toggle(view, R.id.tz_sounds_off_route, TzPrefs.warnOffRoute(c), on -> TzPrefs.setWarnOffRoute(c, on));
    toggle(view, R.id.tz_sounds_gps, TzPrefs.warnGps(c), on -> TzPrefs.setWarnGps(c, on));
    tile(view, R.id.tz_tone_left, R.drawable.ic_tz_turn_left_bold, R.string.tz_tone_left, TonePattern.LEFT, false);
    tile(view, R.id.tz_tone_straight, R.drawable.ic_tz_straight_bold, R.string.tz_tone_straight, TonePattern.STRAIGHT,
         false);
    tile(view, R.id.tz_tone_right, R.drawable.ic_tz_turn_right_bold, R.string.tz_tone_right, TonePattern.RIGHT, false);
    tile(view, R.id.tz_tone_arrived, R.drawable.ic_tz_flag_bold, R.string.tz_tone_arrived, TonePattern.ARRIVE, false);
    tile(view, R.id.tz_tone_off_route, R.drawable.ic_tz_reroute_bold, R.string.tz_tone_off_route,
         TonePattern.OFF_ROUTE, true);
    tile(view, R.id.tz_tone_gps, R.drawable.ic_tz_gps_off_bold, R.string.tz_tone_gps_lost, TonePattern.GPS_LOST, true);
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    // As More: the title 16dp under the status bar, the list clear of the island.
    final View v = requireView();
    final int islandSpace = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                          + getResources().getDimensionPixelSize(R.dimen.tz_island_h)
                          + getResources().getDimensionPixelSize(R.dimen.tz_space_32);
    v.setPadding(0, top + getResources().getDimensionPixelSize(R.dimen.tz_space_16), 0, islandSpace);
  }

  @Override
  public boolean handlesBack()
  {
    return true;
  }

  @Override
  public boolean onBackPressed()
  {
    back();
    return true;
  }

  private void back()
  {
    TzNavigator.show(TzNavigator.Screen.MORE, null);
  }

  @NonNull
  private static TzToggleRow toggle(@NonNull View root, @IdRes int id, boolean on, @NonNull TzToggleRow.Listener l)
  {
    final TzToggleRow row = root.findViewById(id);
    // Rows of a group: the group draws the card.
    row.setBackground(null);
    row.setChecked(on, false);
    row.setListener(l);
    return row;
  }

  private void tile(@NonNull View root, @IdRes int id, @DrawableRes int glyph, @StringRes int label,
                    @NonNull TonePattern pattern, boolean warning)
  {
    final TzToneTile tile = root.findViewById(id);
    tile.bind(glyph, label, warning);
    tile.setOnClickListener(v -> {
      if (mTones == null)
        mTones = new TzTones(requireContext());
      mTones.play(pattern);
    });
  }

  /** The system text-to-speech settings: the voice and its language are chosen there. */
  private static void openTtsSettings(@NonNull Context c)
  {
    try
    {
      c.startActivity(new Intent(TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    catch (ActivityNotFoundException e)
    {
      Toast.makeText(c, R.string.tz_no_tts_settings, Toast.LENGTH_SHORT).show();
    }
  }
}
