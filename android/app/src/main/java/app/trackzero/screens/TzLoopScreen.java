package app.trackzero.screens;

import android.content.DialogInterface;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.TzTrace;
import app.trackzero.base.TzLocation;
import app.trackzero.base.TzMap;
import app.trackzero.base.TzRouter;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.DouglasPeucker;
import app.trackzero.routes.GpxTrack;
import app.trackzero.routes.LoopChoice;
import app.trackzero.routes.LoopPlanner;
import app.trackzero.routes.Profile;
import app.trackzero.routes.RouteStore;
import app.trackzero.ui.TzListRow;
import app.trackzero.ui.TzMapTap;
import app.trackzero.ui.TzProfiles;
import app.trackzero.ui.TzRouteCard;
import app.trackzero.ui.TzSegmented;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * L1 · Loop setup and L2 · Loop results (FEAT-004, Phase 29): from a start, a distance, a climb and the profile (and a
 * point to pass through), three round trips in different directions; one becomes the temporary route and opens in
 * the preview. Leaving, picking a loop or starting a ride stops the search and frees the router.
 */
public class TzLoopScreen extends TzScreen implements TzProfileSheet.Listener
{
  /** Distances offered, in km (or miles): for a bike, and on foot. */
  private static final int[] BIKE_DISTANCES = {20, 30, 40, 60, 80};
  private static final int[] FOOT_DISTANCES = {5, 10, 15, 20, 30};
  private static final double M_PER_MI = 1609.344;
  private static final double M_PER_FT = 0.3048;
  /** The climbing control's last option: a total typed in. */
  private static final int CLIMB_CUSTOM = 4;

  @NonNull
  private Profile mProfile = Profile.ROAD;
  /** A chip's index, or the chips' count: the distance typed in ({@link #mCustomDistance}). */
  private int mDistanceIndex = 1;
  /** A typed distance, km or mi; NaN: none yet. */
  private double mCustomDistance = Double.NaN;
  /** A preset's ordinal, or {@link #CLIMB_CUSTOM}: the total climb typed in ({@link #mCustomClimb}). */
  private int mClimbIndex = LoopPlanner.Climb.ROLLING.ordinal();
  /** A typed total climb, m or ft; NaN: none yet. */
  private double mCustomClimb = Double.NaN;
  /** Null: the rider's location. */
  @Nullable
  private double[] mStart;
  @Nullable
  private double[] mVia;
  /** Which point the map picker sets: the start, or the point to pass through; null when not picking. */
  @Nullable
  private Boolean mPickingStart;
  @Nullable
  private LoopPlanner mPlanner;
  /** Directions shifted for "other loops": 0, 22.5. */
  private double mShift;
  @Nullable
  private TzMapTap mTap;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_loop, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mProfile = TzPrefs.profile(requireContext());
    view.findViewById(R.id.tz_loop_back).setOnClickListener(v -> leave());
    view.findViewById(R.id.tz_loop_results_back).setOnClickListener(v -> showSetup());
    view.findViewById(R.id.tz_loop_start).setOnClickListener(v -> pick(true));
    view.findViewById(R.id.tz_loop_via).setOnClickListener(v -> pick(false));
    view.findViewById(R.id.tz_loop_profile).setOnClickListener(v -> TzProfileSheet.show(this));
    view.findViewById(R.id.tz_loop_picker_cancel).setOnClickListener(v -> {
      // With a point to pass through already, the picker's button removes it.
      if (Boolean.FALSE.equals(mPickingStart) && mVia != null)
        mVia = null;
      endPick();
    });
    view.findViewById(R.id.tz_loop_propose).setOnClickListener(v -> {
      if (mPlanner != null)
        stopSearch();
      else
      {
        mShift = 0;
        propose();
      }
    });
    view.findViewById(R.id.tz_loop_other).setOnClickListener(v -> {
      mShift = mShift == 0 ? 22.5 : 0;
      showSetup();
      propose();
    });
    final TzSegmented climb = view.findViewById(R.id.tz_loop_climb);
    // Five climbs share the row equally, also with a large font.
    climb.setFill(true);
    climb.setOptions(getString(R.string.tz_loop_any), getString(R.string.tz_loop_flat),
                     getString(R.string.tz_loop_rolling), getString(R.string.tz_loop_hilly),
                     getString(R.string.tz_loop_climb_other));
    climb.select(mClimbIndex);
    climb.setListener(i -> {
      if (i == CLIMB_CUSTOM)
      {
        askClimb(climb);
        return;
      }
      mClimbIndex = i;
      bindSetup();
    });
    // "Other" again: change the typed climb.
    climb.setReselectListener(i -> {
      if (i == CLIMB_CUSTOM)
        askClimb(climb);
    });
    mTap = new TzMapTap(requireContext(), new TzMapTap.Listener() {
      @Override
      public void onTap(float x, float y)
      {
        onMapTap(x, y);
      }
    }, this::onChrome);
    bindSetup();
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    stopSearch();
    endPick();
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    // Leaving L1 / L2 (or opening a loop): nothing of the search stays.
    stopSearch();
    TzRouter.release();
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    final View v = requireView();
    final int topPad = top + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    final int bottomPad = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra);
    for (int id : new int[] {R.id.tz_loop_setup, R.id.tz_loop_results})
    {
      final View panel = v.findViewById(id);
      panel.setPadding(panel.getPaddingLeft(), topPad, panel.getPaddingRight(), bottomPad);
    }
    final ViewGroup.MarginLayoutParams lp =
        (ViewGroup.MarginLayoutParams) v.findViewById(R.id.tz_loop_picker).getLayoutParams();
    lp.topMargin = topPad;
    v.findViewById(R.id.tz_loop_picker).setLayoutParams(lp);
  }

  @Override
  public boolean handlesBack()
  {
    return true;
  }

  @Override
  public boolean onBackPressed()
  {
    if (mPickingStart != null)
      endPick();
    else if (requireView().findViewById(R.id.tz_loop_results).getVisibility() == View.VISIBLE)
      showSetup();
    else
      leave();
    return true;
  }

  private void leave()
  {
    TzNavigator.show(TzNavigator.Screen.ROUTES, null);
  }

  @Override
  public void onProfileChosen(@NonNull Profile profile)
  {
    if (profile.foot != mProfile.foot)
    {
      mDistanceIndex = 1;
      mCustomDistance = Double.NaN;
    }
    mProfile = profile;
    bindSetup();
  }

  // ---- L1 ----

  private void bindSetup()
  {
    final View v = getView();
    if (v == null)
      return;
    ((TzListRow) v.findViewById(R.id.tz_loop_start)).setDetail(
        getString(mStart == null ? R.string.tz_loop_my_location : R.string.tz_loop_picked));
    ((TzListRow) v.findViewById(R.id.tz_loop_via)).setDetail(
        getString(mVia == null ? R.string.tz_loop_optional : R.string.tz_loop_picked));
    final TzListRow profile = v.findViewById(R.id.tz_loop_profile);
    profile.setDetail(getString(TzProfiles.label(mProfile)));
    profile.setIcon(TzProfiles.icon(mProfile));
    bindDistances(v);
    final double distanceM = distanceM();
    final TextView climb = v.findViewById(R.id.tz_loop_climb_caption);
    if (mClimbIndex == LoopPlanner.Climb.ANY.ordinal())
    {
      climb.setText(R.string.tz_loop_climb_any);
    }
    else
    {
      final double up = climbPerKm() * distanceM / 1000;
      final double rounded = mClimbIndex == CLIMB_CUSTOM
                                 ? up
                                 : up < 1000 ? Math.max(50, Math.round(up / 50) * 50) : Math.round(up / 100) * 100;
      final Locale locale = Locale.getDefault();
      climb.setText(getString(R.string.tz_loop_climb_caption, climbName(),
                              TzFormat.elevation(rounded, TzPrefs.units(requireContext()), locale).toString(),
                              chosenDistance()));
    }
  }

  private void bindDistances(@NonNull View v)
  {
    final LinearLayout row = v.findViewById(R.id.tz_loop_distances);
    row.removeAllViews();
    final int[] options = mProfile.foot ? FOOT_DISTANCES : BIKE_DISTANCES;
    final int gap = getResources().getDimensionPixelSize(R.dimen.tz_loop_chip_gap);
    // The presets, then one more: a distance typed in ("…" until there is one).
    for (int i = 0; i <= options.length; i++)
    {
      final boolean custom = i == options.length;
      final TextView chip = new TextView(requireContext());
      chip.setGravity(Gravity.CENTER);
      chip.setText(!custom ? String.valueOf(options[i])
                           : Double.isNaN(mCustomDistance) ? getString(R.string.tz_loop_distance_other)
                                                           : LoopChoice.label(mCustomDistance));
      chip.setContentDescription(custom ? getString(R.string.tz_loop_cd_other_distance) : null);
      chip.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.barlow_condensed_semibold));
      chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.tz_text_22));
      final boolean selected = i == mDistanceIndex;
      chip.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.tz_text1 : R.color.tz_text2));
      chip.setBackgroundResource(selected ? R.drawable.tz_bg_surface3_r24 : R.drawable.tz_bg_surface1_r24);
      chip.setSelected(selected);
      final int index = i;
      chip.setOnClickListener(x -> {
        if (custom)
        {
          askDistance();
          return;
        }
        mDistanceIndex = index;
        bindSetup();
      });
      final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
      if (i > 0)
        lp.setMarginStart(gap);
      row.addView(chip, lp);
    }
    ((TextView) v.findViewById(R.id.tz_loop_distance_label)).setText(
        TzPrefs.units(requireContext()) == TzFormat.Units.IMPERIAL ? R.string.tz_loop_distance_mi
                                                                    : R.string.tz_loop_distance_km);
  }

  /** The distance chosen, in km or mi, as on its chip. */
  private double chosenValue()
  {
    final int[] options = mProfile.foot ? FOOT_DISTANCES : BIKE_DISTANCES;
    return mDistanceIndex >= options.length ? mCustomDistance : options[mDistanceIndex];
  }

  /** The distance chosen as on its chip: "30 km". */
  @NonNull
  private String chosenDistance()
  {
    return LoopChoice.label(chosenValue()) + " "
         + TzFormat.distance(distanceM(), TzPrefs.units(requireContext()), Locale.getDefault()).unit;
  }

  /** The distance chosen, metres. */
  private double distanceM()
  {
    final double value = chosenValue();
    return TzPrefs.units(requireContext()) == TzFormat.Units.IMPERIAL ? value * M_PER_MI : value * 1000.0;
  }

  /** Metres up per km asked for, NaN for any. */
  private double climbPerKm()
  {
    if (mClimbIndex == CLIMB_CUSTOM)
    {
      final double m = TzPrefs.units(requireContext()) == TzFormat.Units.IMPERIAL ? mCustomClimb * M_PER_FT
                                                                                   : mCustomClimb;
      return LoopChoice.perKm(m, distanceM());
    }
    final LoopPlanner.Climb c = LoopPlanner.Climb.values()[mClimbIndex];
    return c == LoopPlanner.Climb.ANY ? Double.NaN : c.mPerKm;
  }

  @NonNull
  private String climbName()
  {
    return getString(new int[] {R.string.tz_loop_any, R.string.tz_loop_flat, R.string.tz_loop_rolling,
                                R.string.tz_loop_hilly, R.string.tz_loop_climb_other}[mClimbIndex]);
  }

  /** A distance beyond the chips (400 km, say): typed in, checked, then chosen. */
  private void askDistance()
  {
    final boolean imperial = TzPrefs.units(requireContext()) == TzFormat.Units.IMPERIAL;
    final double max = mProfile.foot ? LoopChoice.MAX_DISTANCE_FOOT : LoopChoice.MAX_DISTANCE_BIKE;
    askNumber(getString(imperial ? R.string.tz_loop_ask_distance_mi : R.string.tz_loop_ask_distance_km),
              getString(R.string.tz_loop_ask_range, LoopChoice.label(LoopChoice.MIN_DISTANCE), LoopChoice.label(max)),
              Double.isNaN(mCustomDistance) ? chosenValue() : mCustomDistance, text -> {
                final Double v = LoopChoice.distance(text, mProfile.foot);
                if (v == null)
                  return false;
                mCustomDistance = v;
                mDistanceIndex = (mProfile.foot ? FOOT_DISTANCES : BIKE_DISTANCES).length;
                bindSetup();
                return true;
              }, null);
  }

  /** A total climb typed in; cancelled, the control goes back to the climb chosen before. */
  private void askClimb(@NonNull TzSegmented control)
  {
    final boolean imperial = TzPrefs.units(requireContext()) == TzFormat.Units.IMPERIAL;
    final double suggested = Double.isNaN(mCustomClimb)
                                 ? Math.round(LoopPlanner.Climb.ROLLING.mPerKm * distanceM() / 1000
                                              / (imperial ? M_PER_FT : 1) / 50) * 50
                                 : mCustomClimb;
    askNumber(getString(imperial ? R.string.tz_loop_ask_climb_ft : R.string.tz_loop_ask_climb_m),
              getString(R.string.tz_loop_ask_climb_caption), suggested, text -> {
                final Double v = LoopChoice.climb(text);
                if (v == null)
                  return false;
                mCustomClimb = v;
                mClimbIndex = CLIMB_CUSTOM;
                control.select(CLIMB_CUSTOM);
                bindSetup();
                return true;
              }, () -> control.select(mClimbIndex));
  }

  private interface Accept
  {
    /** False: not a valid value (the dialog stays, with its caption as the reason). */
    boolean accept(@NonNull String text);
  }

  private void askNumber(@NonNull String title, @NonNull String caption, double value, @NonNull Accept accept,
                         @Nullable Runnable cancelled)
  {
    final EditText input = new EditText(requireContext());
    input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    input.setText(Double.isNaN(value) ? "" : LoopChoice.label(value));
    input.setSelectAllOnFocus(true);
    input.setTextColor(ContextCompat.getColor(requireContext(), R.color.tz_text1));
    final TextView hint = new TextView(requireContext(), null, 0, R.style.TzText_Caption);
    hint.setText(caption);
    final LinearLayout box = new LinearLayout(requireContext());
    box.setOrientation(LinearLayout.VERTICAL);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_24);
    box.setPadding(pad, getResources().getDimensionPixelSize(R.dimen.tz_space_8), pad, 0);
    box.addView(input);
    box.addView(hint);
    final androidx.appcompat.app.AlertDialog dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(box)
            .setNegativeButton(R.string.tz_cancel, (d, w) -> {
              if (cancelled != null)
                cancelled.run();
            })
            .setOnCancelListener(d -> {
              if (cancelled != null)
                cancelled.run();
            })
            .setPositiveButton(R.string.tz_ok, null)
            .create();
    dialog.setOnShowListener(d -> {
      input.requestFocus();
      dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
        if (accept.accept(input.getText().toString()))
          dialog.dismiss();
        else
          hint.setTextColor(ContextCompat.getColor(requireContext(), R.color.tz_danger));
      });
    });
    dialog.show();
    if (dialog.getWindow() != null)
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
  }

  // ---- Picking a point on the map ----

  private void pick(boolean start)
  {
    mPickingStart = start;
    final View v = requireView();
    v.findViewById(R.id.tz_loop_setup).setVisibility(View.GONE);
    v.findViewById(R.id.tz_loop_root).setBackground(null);
    v.findViewById(R.id.tz_loop_picker).setVisibility(View.VISIBLE);
    ((TextView) v.findViewById(R.id.tz_loop_picker_text)).setText(start ? R.string.tz_loop_pick_start
                                                                        : R.string.tz_loop_pick_via);
    ((TextView) v.findViewById(R.id.tz_loop_picker_cancel)).setText(!start && mVia != null ? R.string.tz_plan_cd_remove
                                                                                          : R.string.tz_cancel);
    // The screen covers the map: it is drawn again only while a point is picked, around the start, the loop's size.
    TzMap.setRenderingEnabled(true);
    final double[] around = mStart != null ? mStart : here();
    if (around != null)
      TzMap.centerOn(around[0], around[1], pickZoom(distanceM()));
    if (mTap != null)
      TzMap.addTouchObserver(mTap);
  }

  /** A zoom that shows about a loop of this length around its start. */
  static int pickZoom(double distanceM)
  {
    if (distanceM <= 10_000)
      return 14;
    if (distanceM <= 20_000)
      return 13;
    if (distanceM <= 40_000)
      return 12;
    if (distanceM <= 100_000)
      return 11;
    if (distanceM <= 250_000)
      return 10;
    return distanceM <= 500_000 ? 9 : 8;
  }

  private void endPick()
  {
    if (mPickingStart == null)
      return;
    mPickingStart = null;
    if (mTap != null)
    {
      TzMap.removeTouchObserver(mTap);
      mTap.cancel();
    }
    final View v = getView();
    if (v == null)
      return;
    v.findViewById(R.id.tz_loop_picker).setVisibility(View.GONE);
    v.findViewById(R.id.tz_loop_root).setBackgroundResource(R.color.tz_bg);
    v.findViewById(R.id.tz_loop_setup).setVisibility(View.VISIBLE);
    TzMap.setRenderingEnabled(false);
    bindSetup();
  }

  private boolean onChrome(@NonNull MotionEvent e)
  {
    final View picker = getView() == null ? null : requireView().findViewById(R.id.tz_loop_picker);
    if (picker == null || picker.getVisibility() != View.VISIBLE)
      return false;
    final Rect r = new Rect();
    picker.getGlobalVisibleRect(r);
    return r.contains((int) e.getRawX(), (int) e.getRawY());
  }

  private void onMapTap(float x, float y)
  {
    if (mPickingStart == null)
      return;
    final double[] ll = TzMap.screenToLatLon(x, y);
    if (mPickingStart)
      mStart = ll;
    else
      mVia = ll;
    endPick();
  }

  // ---- The search ----

  private void propose()
  {
    final double[] start = mStart != null ? mStart : here();
    if (start == null)
    {
      Toast.makeText(requireContext(), R.string.tz_loop_no_gps, Toast.LENGTH_SHORT).show();
      return;
    }
    stopSearch();
    final LoopPlanner planner = new LoopPlanner(
        (lat, lon, profile, answer) -> TzRouter.route(lat, lon, profile, Double.NaN, answer::onPath));
    mPlanner = planner;
    final double distanceM = distanceM();
    final String chosen = chosenDistance();
    if (TzTrace.on())
      TzTrace.log("loop", "propose " + mProfile.id + " " + Math.round(distanceM) + " m climb " + climbPerKm()
                         + " m/km shift " + mShift);
    setLooking(0, mVia == null ? 8 : 4);
    planner.plan(start[0], start[1], distanceM, climbPerKm(), mProfile, mVia == null ? Double.NaN : mVia[0],
                 mVia == null ? Double.NaN : mVia[1], mShift, new LoopPlanner.Listener() {
                   @Override
                   public void onProgress(int done, int of)
                   {
                     if (planner == mPlanner)
                       setLooking(done, of);
                   }

                   @Override
                   public void onDone(@NonNull List<LoopPlanner.Loop> loops, boolean mapsMissing)
                   {
                     if (planner != mPlanner || getView() == null)
                       return;
                     mPlanner = null;
                     resetButton();
                     if (TzTrace.on())
                       TzTrace.log("loop", "done " + loops.size() + " loops");
                     showResults(loops, chosen, mapsMissing);
                   }
                 });
  }

  @Nullable
  private double[] here()
  {
    final Location l = TzLocation.last(requireContext());
    return l == null ? null : new double[] {l.getLatitude(), l.getLongitude()};
  }

  private void setLooking(int done, int of)
  {
    final View v = getView();
    if (v != null)
      ((MaterialButton) v.findViewById(R.id.tz_loop_propose)).setText(getString(R.string.tz_loop_looking, done, of));
  }

  private void resetButton()
  {
    final View v = getView();
    if (v != null)
      ((MaterialButton) v.findViewById(R.id.tz_loop_propose)).setText(R.string.tz_loop_propose);
  }

  private void stopSearch()
  {
    if (mPlanner == null)
      return;
    mPlanner.cancel();
    mPlanner = null;
    TzRouter.cancel();
    resetButton();
  }

  // ---- L2 ----

  private void showSetup()
  {
    final View v = requireView();
    v.findViewById(R.id.tz_loop_results).setVisibility(View.GONE);
    v.findViewById(R.id.tz_loop_setup).setVisibility(View.VISIBLE);
  }

  private void showResults(@NonNull List<LoopPlanner.Loop> loops, @NonNull String chosen, boolean mapsMissing)
  {
    final View v = requireView();
    v.findViewById(R.id.tz_loop_setup).setVisibility(View.GONE);
    v.findViewById(R.id.tz_loop_results).setVisibility(View.VISIBLE);
    ((TextView) v.findViewById(R.id.tz_loop_results_title)).setText(
        loops.isEmpty() ? getString(R.string.tz_loop_none_title)
                        : getResources().getQuantityString(R.plurals.tz_loop_found, loops.size(), loops.size()));
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final String climb = climbName().toLowerCase(locale);
    ((TextView) v.findViewById(R.id.tz_loop_request)).setText(getString(
        R.string.tz_loop_request, getString(mStart == null ? R.string.tz_loop_from_here : R.string.tz_loop_from_point),
        getString(TzProfiles.label(mProfile)), chosen, climb));
    ((TextView) v.findViewById(R.id.tz_loop_note)).setText(!loops.isEmpty() ? R.string.tz_loop_estimates
                                                           : mapsMissing    ? R.string.tz_loop_none_maps
                                                                            : R.string.tz_loop_none);
    final LinearLayout cards = v.findViewById(R.id.tz_loop_cards);
    cards.removeAllViews();
    final String[] directions = getResources().getStringArray(R.array.tz_loop_directions);
    for (LoopPlanner.Loop loop : loops)
    {
      final TzRouteCard card = new TzRouteCard(requireContext(), null);
      final String direction = directions[LoopPlanner.directionIndex(loop.directionDeg)];
      final double[] far = loop.farthest();
      final String place = Framework.nativeGetPlaceName(far[0], far[1]);
      final String name = place.isEmpty() ? direction : getString(R.string.tz_loop_card_name, direction, place);
      final double[] lat = loop.path.line.lats();
      final double[] lon = loop.path.line.lons();
      final int[] keep = DouglasPeucker.simplifyToMaxPoints(lat, lon, 200);
      final double[] sLat = new double[keep.length], sLon = new double[keep.length];
      for (int i = 0; i < keep.length; i++)
      {
        sLat[i] = lat[keep[i]];
        sLon[i] = lon[keep[i]];
      }
      card.bind(name, TzFormat.distance(loop.lengthM(), units, locale),
                Double.isNaN(loop.upM) ? null : TzFormat.elevation(loop.upM, units, locale),
                getString(R.string.tz_loop_new_roads, Math.round(100 * (1 - loop.retrace))), false, sLat, sLon);
      card.showChevron();
      card.setListener(new TzRouteCard.Listener() {
        @Override
        public void onOpen()
        {
          open(loop, name);
        }

        @Override
        public void onToggleFavorite()
        {
          open(loop, name);
        }

        @Override
        public void onLongPress() {}
      });
      final LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.topMargin = getResources().getDimensionPixelSize(R.dimen.tz_space_16);
      cards.addView(card, lp);
    }
  }

  /** The loop as the temporary route (Phase 28), then its preview. */
  private void open(@NonNull LoopPlanner.Loop loop, @NonNull String direction)
  {
    final String name = getString(R.string.tz_loop_name, direction,
                                  TzFormat.distance(loop.lengthM(), TzPrefs.units(requireContext()),
                                                    Locale.getDefault()).toString());
    RouteStore.get(requireContext()).putTemp(name, loop.path, mProfile, new ArrayList<GpxTrack.Waypoint>(), route -> {
      if (route == null || getView() == null)
        return;
      final Bundle args = new Bundle();
      args.putString(TzRoutesScreen.ARG_ROUTE_ID, route.id);
      TzNavigator.show(TzNavigator.Screen.PREVIEW, args);
    });
  }
}
