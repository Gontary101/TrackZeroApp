package app.trackzero.screens;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzConfig;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzMap;
import app.trackzero.base.TzRouteLayer;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.Guide;
import app.trackzero.follow.Stop;
import app.trackzero.follow.Stops;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.Geo;
import app.trackzero.ride.RideSession;
import app.trackzero.ride.TzFix;
import app.trackzero.ride.TzRideService;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzPinLayer;
import app.trackzero.ui.TzStopKinds;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

/**
 * S2 · Add a stop (FEAT-007, Phase 30): the place over the map with dots from the route to it, how far ahead, the
 * detour and the time it costs (estimates, no routing), and ADD STOP. Adding works the path out ("Recalculating…")
 * and goes back to the ride; a place that cannot be reached says so and changes nothing. An added stop offers to be
 * removed instead. On a free ride there is no route to add it to: the place and its distance only.
 */
public class TzStopScreen extends TzScreen implements TzMap.TouchObserver
{
  static final String ARG_NAME = "stopName";
  static final String ARG_KIND = "stopKind";
  static final String ARG_LAT = "stopLat";
  static final String ARG_LON = "stopLon";
  static final String ARG_ALONG = "stopAlong";
  static final String ARG_OFF_ROUTE = "stopOffRoute";
  static final String ARG_DISTANCE = "stopDistance";
  /** The place is framed with at least this much around it. */
  private static final double MIN_FRAME_M = 300;

  @NonNull
  private Stop mStop = new Stop("", 0, 0, 0);
  private double mAlong;
  private double mOffRoute;
  private double mDistance;
  private boolean mAdding;
  private boolean mShown;
  private boolean mFramed;
  private TzPinLayer mPins;
  private final RideBinding mRide = new RideBinding(service -> bind());

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_stop, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    final Bundle a = requireArguments();
    mStop = new Stop(a.getString(ARG_NAME, ""), a.getInt(ARG_KIND), a.getDouble(ARG_LAT), a.getDouble(ARG_LON));
    mAlong = a.getDouble(ARG_ALONG, Double.NaN);
    mOffRoute = a.getDouble(ARG_OFF_ROUTE, Double.NaN);
    mDistance = a.getDouble(ARG_DISTANCE, 0);
    mPins = view.findViewById(R.id.tz_stop_pins);
    view.findViewById(R.id.tz_stop_back).setOnClickListener(v -> back());
    ((TextView) view.findViewById(R.id.tz_stop_name)).setText(mStop.name);
    // Framed once the card has its height.
    view.findViewById(R.id.tz_stop_card).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> frame());
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    mRide.bind(requireContext());
    TzMap.addTouchObserver(this);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    TzMap.removeTouchObserver(this);
    mRide.unbind(requireContext());
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    TzRouteLayer.hide(this);
    TzMap.clearVisibleArea(this);
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    final View v = requireView();
    ((FrameLayout.LayoutParams) v.findViewById(R.id.tz_stop_back).getLayoutParams()).topMargin =
        top + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    ((FrameLayout.LayoutParams) v.findViewById(R.id.tz_stop_card).getLayoutParams()).bottomMargin =
        bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra);
    v.requestLayout();
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
    final Bundle args = new Bundle();
    args.putInt(TzStopsScreen.ARG_CATEGORY, requireArguments().getInt(TzStopsScreen.ARG_CATEGORY, Stops.WATER));
    TzNavigator.show(TzNavigator.Screen.STOPS, args);
  }

  @Override
  public void onTouch(@NonNull MotionEvent e)
  {
    mPins.onMapTouched();
  }

  /** The ride is known: lines, pin, numbers and the button. */
  private void bind()
  {
    final View v = getView();
    final TzRideService service = mRide.service();
    final RideSession session = service == null ? null : service.session();
    if (v == null || session == null)
      return;
    final Guide guide = service.guide();
    final FollowRoute route = guide == null ? null : guide.line();
    final boolean onRoute = route != null && !Double.isNaN(mAlong);
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final String kind = getString(TzStopKinds.label(mStop.kind));

    if (!mShown)
    {
      mShown = true;
      if (onRoute)
      {
        if (session.toPlace())
          TzRouteLayer.showPath(this, route.lats(), route.lons(), true);
        else
          TzRouteLayer.show(this, route.lats(), route.lons(), true);
        TzRouteLayer.showHint(this, new double[] {route.latAt(mAlong), mStop.lat},
                              new double[] {route.lonAt(mAlong), mStop.lon});
      }
      else
      {
        final TzFix fix = session.lastFix();
        if (fix != null)
          TzRouteLayer.showHint(this, new double[] {fix.lat, mStop.lat}, new double[] {fix.lon, mStop.lon});
      }
      mPins.setPlace(new double[] {mStop.lat, mStop.lon});
      frame();
    }

    final TextView label = v.findViewById(R.id.tz_stop_label);
    final MaterialButton add = v.findViewById(R.id.tz_stop_add);
    final TextView note = v.findViewById(R.id.tz_stop_note);
    ((TzMetricView) v.findViewById(R.id.tz_stop_ahead)).setValue(TzFormat.distance(mDistance, units, locale));
    if (!onRoute)
    {
      label.setText(getString(R.string.tz_stop_kind_where, kind,
                              getString(R.string.tz_stop_away, TzFormat.distance(mDistance, units, locale))));
      ((TextView) v.findViewById(R.id.tz_stop_ahead_label)).setText(R.string.tz_stop_distance);
      v.findViewById(R.id.tz_stop_detour_cell).setVisibility(View.INVISIBLE);
      v.findViewById(R.id.tz_stop_time_cell).setVisibility(View.INVISIBLE);
      add.setVisibility(View.GONE);
      note.setText(R.string.tz_stop_note_free);
      return;
    }
    label.setText(getString(R.string.tz_stop_kind_where, kind,
                            mOffRoute <= Stops.ON_ROUTE_M
                                ? getString(R.string.tz_stop_on_route)
                                : getString(R.string.tz_stop_off_route,
                                            TzFormat.distance(mOffRoute, units, locale).toString())));
    final double detour = Stops.detourM(mOffRoute);
    final TzFormat.Value d = TzFormat.distance(detour, units, locale);
    ((TzMetricView) v.findViewById(R.id.tz_stop_detour)).setValue(getString(R.string.tz_stop_plus, d.number), d.unit);
    ((TzMetricView) v.findViewById(R.id.tz_stop_time))
        .setValue(getString(R.string.tz_stop_plus, String.valueOf(extraMinutes(session, detour))),
                  getString(R.string.tz_unit_min));

    final boolean added = isAdded(guide);
    if (mAdding)
      return;
    add.setEnabled(true);
    add.setText(added ? R.string.tz_stop_remove : R.string.tz_stop_add);
    add.setIconResource(added ? R.drawable.ic_tz_close : R.drawable.ic_tz_plus);
    add.setOnClickListener(x -> {
      final TzRideService s = mRide.service();
      if (s == null)
        return;
      if (added)
      {
        s.removeStop(mStop);
        back();
        return;
      }
      mAdding = true;
      add.setEnabled(false);
      add.setText(R.string.tz_stop_recalculating);
      s.addStop(mStop, ok -> {
        mAdding = false;
        if (getView() == null)
          return;
        if (ok)
        {
          TzNavigator.show(TzNavigator.Screen.RIDE, null);
          return;
        }
        Toast.makeText(requireContext(), R.string.tz_stop_failed, Toast.LENGTH_LONG).show();
        bind();
      });
    });
  }

  private boolean isAdded(@Nullable Guide guide)
  {
    if (guide == null)
      return false;
    for (Stop s : guide.stops())
      if (s.same(mStop))
        return true;
    return false;
  }

  /** The detour at the ride's average moving speed so far (the profile's usual pace early on), whole minutes. */
  private static long extraMinutes(@NonNull RideSession s, double detourM)
  {
    double kmh = s.stats().avgKmh(SystemClock.elapsedRealtime());
    final double fallback = s.profile().foot ? TzConfig.ETA_FALLBACK_FOOT_KMH : TzConfig.ETA_FALLBACK_KMH;
    if (Double.isNaN(kmh) || kmh < fallback / 3)
      kmh = fallback;
    return Math.max(1, Math.round(detourM / (kmh / 3.6) / 60));
  }

  /** The place and where the dots start, between the back button and the card. */
  private void frame()
  {
    final View v = getView();
    if (v == null || !mShown || mFramed)
      return;
    final View card = v.findViewById(R.id.tz_stop_card);
    final View back = v.findViewById(R.id.tz_stop_back);
    if (card.getHeight() == 0)
      return;
    final TzRideService service = mRide.service();
    final Guide guide = service == null ? null : service.guide();
    final FollowRoute route = guide == null ? null : guide.line();
    double minLat = mStop.lat, maxLat = mStop.lat, minLon = mStop.lon, maxLon = mStop.lon;
    if (route != null && !Double.isNaN(mAlong))
    {
      minLat = Math.min(minLat, route.latAt(mAlong));
      maxLat = Math.max(maxLat, route.latAt(mAlong));
      minLon = Math.min(minLon, route.lonAt(mAlong));
      maxLon = Math.max(maxLon, route.lonAt(mAlong));
    }
    final double padLat = MIN_FRAME_M / Geo.M_PER_DEG;
    final double padLon = padLat / Math.cos(Math.toRadians(mStop.lat));
    mFramed = true;
    TzMap.stopFollowing();
    final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
    TzMap.setVisibleArea(this, back.getBottom(), card.getHeight() + lp.bottomMargin);
    TzMap.showRect(minLat - padLat, minLon - padLon, maxLat + padLat, maxLon + padLon,
                   getResources().getDimensionPixelSize(R.dimen.tz_space_24));
    mPins.follow(1500);
  }
}
