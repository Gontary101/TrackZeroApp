package app.trackzero.screens;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzLocation;
import app.trackzero.base.TzMap;
import app.trackzero.format.TzFormat;
import app.trackzero.ui.TzChip;
import app.trackzero.ui.TzMapControls;
import java.util.Locale;

/** B1 · Map: GPS chip, map controls, ruler and attribution over the browsing map. */
public class TzMapScreen extends TzScreen implements TzLocation.Listener, TzMap.FollowListener
{
  private static final int FIRST_CAMERA_ZOOM = 15;
  private static final float GOOD_ACCURACY_M = 20;

  @Nullable
  private TzChip mGps;
  @Nullable
  private TzMapControls mControls;
  @Nullable
  private View mAttribution;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_map, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mGps = view.findViewById(R.id.tz_map_gps);
    mControls = view.findViewById(R.id.tz_map_controls);
    mAttribution = view.findViewById(R.id.tz_map_attribution);
    mControls.setListener(new TzMapControls.Listener() {
      @Override
      public void onZoomIn()
      {
        TzMap.zoomIn();
      }

      @Override
      public void onZoomOut()
      {
        TzMap.zoomOut();
      }

      @Override
      public void onLocate()
      {
        TzMap.followRider(false);
      }
    });
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    TzMap.addFollowListener(this);
    TzLocation.addListener(requireContext(), this);
    final Location last = TzLocation.last(requireContext());
    if (last != null)
      onLocation(last);
    else
      showNoGps();
  }

  @Override
  public void onStop()
  {
    super.onStop();
    TzMap.removeFollowListener(this);
    TzLocation.removeListener(requireContext(), this);
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    final int extraTop = getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    setTopMargin(mGps, top + extraTop);
    setTopMargin(mControls, top + extraTop);

    // Ruler (bottom-left) and attribution (bottom-right) sit 16dp above the island.
    final int footer = bottom + getResources().getDimensionPixelSize(R.dimen.tz_map_footer_bottom);
    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mAttribution.getLayoutParams();
    lp.bottomMargin = footer;
    mAttribution.setLayoutParams(lp);
    TzMap.setRulerPosition(getResources().getDimensionPixelSize(R.dimen.tz_map_footer_side), footer);

    // Centre the rider between the status bar and the island.
    final int islandTop = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                        + getResources().getDimensionPixelSize(R.dimen.tz_island_h);
    TzMap.setVisibleArea(top, islandTop);
  }

  private static void setTopMargin(@NonNull View v, int px)
  {
    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
    lp.topMargin = px;
    v.setLayoutParams(lp);
  }

  @Override
  public void onLocation(@NonNull Location location)
  {
    if (!TzPrefs.firstCameraDone(requireContext()))
    {
      TzPrefs.setFirstCameraDone(requireContext());
      TzMap.centerOn(location.getLatitude(), location.getLongitude(), FIRST_CAMERA_ZOOM);
    }
    if (mGps == null)
      return;
    final float acc = location.getAccuracy();
    final TzFormat.Value v =
        TzFormat.elevation(acc, TzPrefs.units(requireContext()), Locale.getDefault());
    final String text = getString(R.string.tz_gps_accuracy, (v.number + " " + v.unit).toUpperCase(Locale.ROOT));
    mGps.setState(text, acc <= GOOD_ACCURACY_M ? R.color.tz_mint : R.color.tz_amber, R.color.tz_text1);
  }

  @Override
  public void onLocationLost()
  {
    showNoGps();
  }

  private void showNoGps()
  {
    if (mGps != null)
      mGps.setState(getString(R.string.tz_gps_none), R.color.tz_amber, R.color.tz_text1);
  }

  @Override
  public void onFollowChanged(boolean following)
  {
    if (mControls != null)
      mControls.setFollowing(following);
  }
}
