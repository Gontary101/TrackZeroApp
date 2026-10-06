package app.trackzero.screens;

import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzLocation;
import app.trackzero.base.TzMap;
import app.trackzero.base.TzSearch;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.Geo;
import app.trackzero.ui.TzChip;
import app.trackzero.ui.TzMapControls;
import app.trackzero.ui.TzMapTap;
import java.util.Locale;

/**
 * B1 · Map: GPS chip, map controls, ruler and attribution over the browsing map; a search result pinned on it gets
 * a card with NAVIGATE HERE (Phase 15).
 */
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
  private View mPlaceCard;
  /** Bottom inset, from {@link #onInsets}. */
  private int mBottom;
  /** A long-press on the map plans a route to there (Phase 28). Nothing is drawn on B1 for it. */
  @Nullable
  private TzMapTap mTap;

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
    mPlaceCard = view.findViewById(R.id.tz_map_place);
    view.findViewById(R.id.tz_map_place_go).setOnClickListener(v -> navigateToPlace());
    // The ruler and the attribution stay above the card.
    mPlaceCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (b - t != ob - ot)
        layoutFooter();
    });
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
    mTap = new TzMapTap(requireContext(), new TzMapTap.Listener() {
      @Override
      public void onLongPress(float x, float y)
      {
        final double[] ll = TzMap.screenToLatLon(x, y);
        final Bundle args = new Bundle();
        args.putDouble(TzPlanScreen.ARG_LAT, ll[0]);
        args.putDouble(TzPlanScreen.ARG_LON, ll[1]);
        TzNavigator.show(TzNavigator.Screen.PLAN, args);
      }
    }, this::onChrome);
    super.onViewCreated(view, savedInstanceState);
  }

  /** A touch on B1's own views or the island is not the map's. */
  private boolean onChrome(@NonNull MotionEvent e)
  {
    final View island = requireActivity().findViewById(R.id.tz_island_map);
    return hit(mGps, e) || hit(mControls, e) || hit(mPlaceCard, e)
           || (island != null && hit((View) island.getParent(), e));
  }

  private static boolean hit(@Nullable View v, @NonNull MotionEvent e)
  {
    if (v == null || v.getVisibility() != View.VISIBLE)
      return false;
    final Rect r = new Rect();
    v.getGlobalVisibleRect(r);
    return r.contains((int) e.getRawX(), (int) e.getRawY());
  }

  @Override
  public void onStart()
  {
    super.onStart();
    if (mTap != null)
      TzMap.addTouchObserver(mTap);
    TzMap.addFollowListener(this);
    TzLocation.addListener(requireContext(), this);
    TzSearch.setShownListener(this::bindPlace);
    bindPlace();
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
    if (mTap != null)
    {
      TzMap.removeTouchObserver(mTap);
      mTap.cancel();
    }
    TzMap.removeFollowListener(this);
    TzLocation.removeListener(requireContext(), this);
    TzSearch.setShownListener(null);
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    final int extraTop = getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    setTopMargin(mGps, top + extraTop);
    setTopMargin(mControls, top + extraTop);

    mBottom = bottom;
    // The place card sits where the footer does, 16dp above the island.
    final FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) mPlaceCard.getLayoutParams();
    cp.bottomMargin = bottom + getResources().getDimensionPixelSize(R.dimen.tz_map_footer_bottom);
    mPlaceCard.setLayoutParams(cp);
    layoutFooter();

    // Centre the rider between the status bar and the island.
    final int islandTop = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                        + getResources().getDimensionPixelSize(R.dimen.tz_island_h);
    TzMap.setVisibleArea(this, top, islandTop);
  }

  /** Ruler (bottom-left) and attribution (bottom-right): 16dp above the island, or above the place card. */
  private void layoutFooter()
  {
    if (mAttribution == null || mPlaceCard == null)
      return;
    int footer = mBottom + getResources().getDimensionPixelSize(R.dimen.tz_map_footer_bottom);
    if (mPlaceCard.getVisibility() == View.VISIBLE)
      footer += mPlaceCard.getHeight() + getResources().getDimensionPixelSize(R.dimen.tz_space_16);
    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mAttribution.getLayoutParams();
    if (lp.bottomMargin != footer)
    {
      lp.bottomMargin = footer;
      mAttribution.setLayoutParams(lp);
    }
    TzMap.setRulerPosition(getResources().getDimensionPixelSize(R.dimen.tz_map_footer_side), footer);
  }

  /** The card of the search result pinned on the map: what and how far, NAVIGATE HERE. */
  private void bindPlace()
  {
    final View v = getView();
    if (v == null || mPlaceCard == null)
      return;
    final TzSearch.Place p = TzSearch.shown();
    final boolean show = p != null;
    if ((mPlaceCard.getVisibility() == View.VISIBLE) != show)
    {
      mPlaceCard.setVisibility(show ? View.VISIBLE : View.GONE);
      layoutFooter();
    }
    TzNavigator.updateBack();
    if (p == null)
      return;
    String label = p.subtitle;
    final Location here = TzLocation.last(requireContext());
    if (here != null)
    {
      final String d = TzFormat.distance(Geo.distance(here.getLatitude(), here.getLongitude(), p.lat, p.lon),
                                         TzPrefs.units(requireContext()), Locale.getDefault()).toString();
      label = label.isEmpty() ? d : label + " · " + d;
    }
    ((TextView) v.findViewById(R.id.tz_map_place_label)).setText(label);
    ((TextView) v.findViewById(R.id.tz_map_place_title)).setText(p.name);
  }

  @Override
  public boolean handlesBack()
  {
    return TzSearch.shown() != null;
  }

  /** Back closes the place card (and its pin) before it leaves the app. */
  @Override
  public boolean onBackPressed()
  {
    if (TzSearch.shown() == null)
      return false;
    TzSearch.clearShown();
    bindPlace();
    return true;
  }

  private void navigateToPlace()
  {
    final TzSearch.Place p = TzSearch.shown();
    if (p == null)
      return;
    final Bundle args = new Bundle();
    args.putString(TzPreviewScreen.ARG_PLACE_NAME, p.name);
    args.putDouble(TzPreviewScreen.ARG_PLACE_LAT, p.lat);
    args.putDouble(TzPreviewScreen.ARG_PLACE_LON, p.lon);
    TzNavigator.show(TzNavigator.Screen.PREVIEW, args);
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
