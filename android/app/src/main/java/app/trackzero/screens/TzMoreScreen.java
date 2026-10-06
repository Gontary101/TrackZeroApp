package app.trackzero.screens;

import android.content.Context;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzConfig;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzBase;
import app.trackzero.base.TzMaps;
import app.trackzero.base.TzRoads;
import app.trackzero.format.TzFormat;
import app.trackzero.ride.RideStore;
import app.trackzero.ui.TzListRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** B7 · More: offline maps, ride history, settings, about. */
public class TzMoreScreen extends TzScreen
{
  @Nullable
  private View mContent;
  private TzListRow mOffline;
  private TzListRow mHistory;
  private TzListRow mUnits;
  private TzListRow mVoice;
  private TzListRow mDisplay;
  private TzListRow mAutoPause;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_more, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mContent = view.findViewById(R.id.tz_more_content);
    mOffline = view.findViewById(R.id.tz_more_offline);
    mHistory = view.findViewById(R.id.tz_more_history);
    mUnits = view.findViewById(R.id.tz_more_units);
    mVoice = view.findViewById(R.id.tz_more_voice);
    mDisplay = view.findViewById(R.id.tz_more_display);
    mAutoPause = view.findViewById(R.id.tz_more_auto_pause);
    final TzListRow about = view.findViewById(R.id.tz_more_about);

    mOffline.setOnClickListener(v -> TzNavigator.show(TzNavigator.Screen.MAPS, null));
    mHistory.setOnClickListener(v -> TzNavigator.show(TzNavigator.Screen.HISTORY, null));
    mUnits.setOnClickListener(v -> {
      final Context c = requireContext();
      TzPrefs.setUnits(c, TzPrefs.units(c) == TzFormat.Units.METRIC ? TzFormat.Units.IMPERIAL
                                                                    : TzFormat.Units.METRIC);
      // The map's scale bar follows.
      TzBase.syncUnits(c);
      refresh();
    });
    mVoice.setOnClickListener(v -> TzNavigator.show(TzNavigator.Screen.SOUNDS, null));
    mDisplay.setOnClickListener(v -> {
      TzPrefs.setKeepAwake(requireContext(), !TzPrefs.keepAwake(requireContext()));
      refresh();
    });
    mAutoPause.setOnClickListener(v -> {
      TzPrefs.setAutoPause(requireContext(), !TzPrefs.autoPause(requireContext()));
      refresh();
    });
    about.setDetail(getString(R.string.tz_version, TzBase.versionName()));
    about.setOnClickListener(v -> TzNavigator.show(TzNavigator.Screen.ABOUT, null));
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    // Settings also change from a ride's controls sheet, maps from the downloader.
    refresh();
  }

  private void refresh()
  {
    final Context c = requireContext();
    final Locale locale = Locale.getDefault();
    final List<TzMaps.Item> maps = TzRoads.ready(c) ? TzMaps.downloaded() : new ArrayList<>();
    if (maps.isEmpty())
    {
      mOffline.setDetail(getString(R.string.tz_more_manage));
    }
    else
    {
      long bytes = 0;
      for (TzMaps.Item m : maps)
        bytes += m.bytes;
      final String size = Formatter.formatShortFileSize(c, bytes);
      mOffline.setDetail(maps.size() == 1 ? getString(R.string.tz_more_maps_one, maps.get(0).name, size)
                                          : getString(R.string.tz_more_maps_many, maps.get(0).name, maps.size() - 1,
                                                      size));
    }
    RideStore.get(c).list(rides -> {
      if (getView() != null)
        mHistory.setDetail(getResources().getQuantityString(R.plurals.tz_more_rides, rides.size(), rides.size()));
    });
    final TzFormat.Units units = TzPrefs.units(c);
    mUnits.setDetail(getString(units == TzFormat.Units.METRIC ? R.string.tz_units_metric : R.string.tz_units_imperial));
    // Sounds (Phase 32): what plays on a ride.
    final boolean tones = TzPrefs.tones(c);
    final boolean voice = TzPrefs.voice(c);
    mVoice.setDetail(getString(tones && voice ? R.string.tz_sounds_tones_voice
                               : tones        ? R.string.tz_sounds_tones
                               : voice        ? R.string.tz_sounds_voice
                                              : R.string.tz_sounds_off));
    mDisplay.setDetail(getString(TzPrefs.keepAwake(c) ? R.string.tz_display_on : R.string.tz_display_off));
    mAutoPause.setDetail(TzPrefs.autoPause(c)
                             ? getString(R.string.tz_auto_pause_on,
                                         TzFormat.speedWhole(TzConfig.AUTO_PAUSE_KMH, units, locale).toString())
                             : getString(R.string.tz_auto_pause_off));
  }


  @Override
  public void onInsets(int top, int bottom)
  {
    // Page titles start 16dp under the status bar; the list clears the island with 32dp to spare.
    final int islandSpace = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                          + getResources().getDimensionPixelSize(R.dimen.tz_island_h)
                          + getResources().getDimensionPixelSize(R.dimen.tz_space_32);
    mContent.setPadding(mContent.getPaddingLeft(), top + getResources().getDimensionPixelSize(R.dimen.tz_space_16),
                        mContent.getPaddingRight(), islandSpace);
  }
}
