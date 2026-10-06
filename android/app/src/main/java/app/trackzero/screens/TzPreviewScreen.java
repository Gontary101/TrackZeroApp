package app.trackzero.screens;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;
import app.trackzero.TzConfig;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzLocation;
import app.trackzero.base.TzMap;
import app.trackzero.base.TzRouteLayer;
import app.trackzero.base.TzRouter;
import app.trackzero.follow.BikeWalkPlanner;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.RoutedPath;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.Geo;
import app.trackzero.ride.TzRide;
import app.trackzero.routes.FootTime;
import app.trackzero.routes.Profile;
import app.trackzero.routes.Route;
import app.trackzero.routes.RouteGeometry;
import app.trackzero.routes.RouteStats;
import app.trackzero.routes.RouteStore;
import app.trackzero.routes.RouteSurfaces;
import app.trackzero.routes.SurfaceProfile;
import app.trackzero.ui.TzElevationProfileView;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzMotion;
import app.trackzero.ui.TzPinLayer;
import app.trackzero.ui.TzProfilePill;
import app.trackzero.ui.TzSurfaceBarView;
import java.util.Locale;

/**
 * B5 · Route preview and B6 · panning: the route on the map, its card, START RIDE. A route of the library, or the
 * bike route to a place found in search (Phase 15: worked out from the rider's position). The profile pill (Phase 22)
 * sets the ride's profile: the path to a place is worked out again for it.
 */
public class TzPreviewScreen extends TzScreen
    implements TzMap.TouchObserver, TzLocation.Listener, TzProfileSheet.Listener
{
  /** A place instead of a route of the library: its name and where it is. */
  public static final String ARG_PLACE_NAME = "placeName";
  /** A place from S1 is shown with at least this much around it and its closest point on the route. */
  private static final double FOCUS_PAD_M = 300;
  public static final String ARG_PLACE_LAT = "placeLat";
  public static final String ARG_PLACE_LON = "placeLon";

  @Nullable
  private Route mRoute;
  /** What is framed: the route's or the path's extent; null until known. */
  @Nullable
  private double[] mBox;

  // A ride to a place.
  @Nullable
  private String mPlaceName;
  private double mPlaceLat;
  private double mPlaceLon;
  @Nullable
  private RoutedPath mPath;
  private boolean mRouting;
  private boolean mStarted;
  /** A bicycle path being worked out, with a walk when the bike cannot go all the way (Phase 27). */
  @Nullable
  private BikeWalkPlanner mPlanner;
  /** No bike access: the walking path is offered, and START begins a walk. */
  private boolean mWalkOnly;

  @Nullable
  private View mTop;
  @Nullable
  private FrameLayout mCard;
  @Nullable
  private View mFull;
  @Nullable
  private View mPeek;
  @Nullable
  private ImageButton mStar;
  /** S1 for this route (Phase 30), and the place picked there. */
  private ImageButton mStopsButton;
  private TzPinLayer mPins;
  @Nullable
  private double[] mFocus;
  /** Where the place is along the route (its closest point there), NaN when not known. */
  private double mFocusAlong = Double.NaN;
  /** The place's closest point on the route, once the route is read. */
  @Nullable
  private double[] mFocusOnRoute;
  @Nullable
  private TzProfilePill mPill;
  /** The profile of the path shown (or being worked out). */
  @NonNull
  private Profile mProfile = Profile.ROAD;
  private int mBottomMargin;
  private boolean mFavorite;

  // Pan detection: a gesture that starts on the map and moves (or pinches) collapses the card.
  private boolean mGestureOnMap;
  private float mDownX;
  private float mDownY;
  private boolean mCollapsed;
  private boolean mFramed;
  /** The rider has moved the map since it was framed: a taller card no longer reframes it. */
  private boolean mPanned;
  private final Runnable mReframe = () -> {
    // Posted when the surfaces came; the screen may have gone since (Back during the read).
    if (!mPanned && isAdded() && getView() != null)
      frameRoute();
  };
  @Nullable
  private ValueAnimator mAnimator;
  private final Runnable mExpand = () -> setCollapsed(false);

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_preview, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mTop = view.findViewById(R.id.tz_preview_top);
    mCard = view.findViewById(R.id.tz_preview_card);
    mFull = view.findViewById(R.id.tz_preview_full);
    mPeek = view.findViewById(R.id.tz_preview_peek);
    mStar = view.findViewById(R.id.tz_preview_star);
    mPins = view.findViewById(R.id.tz_preview_pins);
    mStopsButton = view.findViewById(R.id.tz_preview_stops);
    mStopsButton.setOnClickListener(v -> {
      if (mRoute == null)
        return;
      final Bundle a = new Bundle();
      a.putString(TzStopsScreen.ARG_ROUTE_ID, mRoute.id);
      TzNavigator.show(TzNavigator.Screen.STOPS, a);
    });
    mPill = view.findViewById(R.id.tz_preview_profile_pill);
    mProfile = TzPrefs.profile(requireContext());
    mPill.setProfile(mProfile);
    mPill.setListener(v -> TzProfileSheet.show(this));
    view.findViewById(R.id.tz_preview_back).setOnClickListener(v -> back());
    mPeek.setOnClickListener(v -> setCollapsed(false));
    mStar.setOnClickListener(v -> {
      if (mRoute == null)
        return;
      if (mRoute.temp)
      {
        // A planned route not saved yet (Phase 28): Save keeps it in the library; the button becomes the star.
        RouteStore.get(requireContext()).saveTemp(mRoute.id, mRoute.name, saved -> {
          if (saved == null || getView() == null)
            return;
          mRoute = saved;
          bindCard(saved);
          Toast.makeText(requireContext(), R.string.tz_plan_saved, Toast.LENGTH_SHORT).show();
        });
        return;
      }
      mFavorite = !mFavorite;
      RouteStore.get(requireContext()).setFavorite(mRoute.id, mFavorite);
      bindStar(mFavorite);
    });
    view.findViewById(R.id.tz_preview_start).setOnClickListener(v -> {
      if (mPlaceName != null)
      {
        if (mPath == null)
          return;
        mStarted = true;
        TzRide.startTo(requireActivity(), mPlaceName, mPlaceLat, mPlaceLon, mPath, this,
                       mWalkOnly ? Profile.WALK : TzPrefs.profile(requireContext()));
      }
      else if (mRoute != null)
      {
        TzRide.start(requireActivity(), mRoute, this);
      }
    });
    // Frame once the card has its final height; later size changes (collapse/expand) keep the rider's view.
    mCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (!mFramed)
        frameRoute();
    });

    final Bundle args = requireArguments();
    mPlaceName = args.getString(ARG_PLACE_NAME);
    if (mPlaceName != null)
    {
      mPlaceLat = args.getDouble(ARG_PLACE_LAT);
      mPlaceLon = args.getDouble(ARG_PLACE_LON);
      bindPlace(getString(R.string.tz_preview_finding));
      super.onViewCreated(view, savedInstanceState);
      return;
    }
    final String id = args.getString(TzRoutesScreen.ARG_ROUTE_ID);
    if (args.containsKey(TzStopScreen.ARG_LAT))
    {
      mFocus = new double[] {args.getDouble(TzStopScreen.ARG_LAT), args.getDouble(TzStopScreen.ARG_LON)};
      mFocusAlong = args.getDouble(TzStopScreen.ARG_ALONG, Double.NaN);
    }
    final RouteStore store = RouteStore.get(requireContext());
    store.find(id, route -> {
      if (getView() == null || route == null)
        return;
      mRoute = route;
      bindCard(route);
      store.loadGeometry(route, this::onGeometry);
    });
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    TzMap.addTouchObserver(this);
    if (mPlaceName != null && mPath == null)
    {
      // The path starts where the rider is: the first fix starts it.
      TzLocation.addListener(requireContext(), this);
      final Location here = TzLocation.last(requireContext());
      if (here != null)
        onLocation(here);
      else
        bindPlace(getString(R.string.tz_preview_no_gps));
    }
  }

  @Override
  public void onStop()
  {
    super.onStop();
    TzMap.removeTouchObserver(this);
    TzLocation.removeListener(requireContext(), this);
    mCard.removeCallbacks(mExpand);
    mCard.removeCallbacks(mReframe);
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    if (mAnimator != null)
      mAnimator.cancel();
    mCard.removeCallbacks(mReframe);
    mCard.removeCallbacks(mExpand);
    TzRouteLayer.hide(this);
    TzMap.clearVisibleArea(this);
    if (mPlanner != null)
      mPlanner.cancel();
    // Leaving without riding: the router's road graphs are not needed any more (a ride keeps them).
    if (mPlaceName != null && !mStarted)
      TzRouter.release();
  }

  @Override
  public boolean onBackPressed()
  {
    back();
    return true;
  }

  /** Back to where the preview came from: the routes, or the map with the place found. */
  private void back()
  {
    TzNavigator.show(mPlaceName != null ? TzNavigator.Screen.MAP : TzNavigator.Screen.ROUTES, null);
  }

  // ---- A ride to a place ----

  @Override
  public void onLocation(@NonNull Location here)
  {
    if (mRouting || mPath != null)
      return;
    mRouting = true;
    TzLocation.removeListener(requireContext(), this);
    bindPlace(getString(R.string.tz_preview_finding));
    final double bearing = here.hasBearing() ? here.getBearing() : Double.NaN;
    if (mProfile.foot)
    {
      TzRouter.route(here.getLatitude(), here.getLongitude(), mPlaceLat, mPlaceLon, mProfile, bearing,
                     this::onPlacePath);
      return;
    }
    // By bike: and if a bike cannot go all the way, as far as it can and walk the rest (BUG-001).
    mPlanner = new BikeWalkPlanner(
        (fromLat, fromLon, toLat, toLon, profile, bearingDeg, timeoutSec, answer)
            -> TzRouter.route(new double[] {fromLat, toLat}, new double[] {fromLon, toLon}, profile, bearingDeg,
                              timeoutSec, answer::onPath));
    final BikeWalkPlanner planner = mPlanner;
    planner.plan(here.getLatitude(), here.getLongitude(), mPlaceLat, mPlaceLon, mProfile, bearing, r -> {
      if (planner != mPlanner)
        return;
      mWalkOnly = r.kind == BikeWalkPlanner.Result.Kind.WALK_ONLY;
      onPlacePath(r.path, r.reason);
    });
  }

  private void onPlacePath(@Nullable RoutedPath path, @NonNull TzRouter.Reason reason)
  {
    mRouting = false;
    if (getView() == null)
      return;
    if (path == null)
    {
      bindPlace(getString(reason == TzRouter.Reason.NO_MAP ? R.string.tz_preview_no_map
                                                           : R.string.tz_preview_no_place_route));
      return;
    }
    mPath = path;
    bindPlace(placeLabel(path));
    final FollowRoute line = path.line;
    if (path.bikeAndWalk())
    {
      // B5W: the ride to where the bike is left, then dots to the place.
      final double[][] ride = path.ridePart();
      final double[][] walk = path.walkPart();
      TzRouteLayer.showRide(this, ride[0], ride[1], false);
      TzRouteLayer.showWalk(this, walk[0], walk[1]);
    }
    else
    {
      TzRouteLayer.hideWalk();
      TzRouteLayer.showPath(this, line.lats(), line.lons(), false);
    }
    mBox = extent(line.lats(), line.lons());
    frameRoute();
    RouteSurfaces.forPath(requireContext(), line, p -> {
      if (path == mPath)
        onSurfaces(p);
    });
    if (mProfile == Profile.HIKE)
      RouteSurfaces.demanding(requireContext(), line, demanding -> {
        if (path == mPath && getView() != null && demanding)
        {
          TransitionManager.beginDelayedTransition(mCard, new ChangeBounds().setDuration(TzMotion.GROW_MS));
          requireView().findViewById(R.id.tz_preview_demanding).setVisibility(View.VISIBLE);
        }
      });
  }

  /** "Route by bike", or on foot with the walking (Phase 25) or hiking (Phase 26) time. */
  @NonNull
  private String placeLabel(@NonNull RoutedPath path)
  {
    if (mWalkOnly)
      return getString(R.string.tz_preview_walk_only, TzFormat.distance(path.line.totalM(), TzPrefs.units(
          requireContext()), Locale.getDefault()).toString());
    if (path.bikeAndWalk())
      return getString(R.string.tz_preview_bike_walk);
    if (!mProfile.foot)
      return getString(R.string.tz_preview_to_place);
    final FollowRoute line = path.line;
    if (mProfile == Profile.HIKE)
    {
      final double[] upDown = path.ele == null ? null : RouteStats.ascentDescent(path.ele);
      return getString(R.string.tz_preview_hike, TzFormat.hoursMinutes(
          FootTime.hikeSeconds(line.totalM(), upDown == null ? Double.NaN : upDown[0],
                               upDown == null ? Double.NaN : upDown[1])));
    }
    final double ascent = path.ele == null ? Double.NaN : RouteStats.compute(line.lats(), line.lons(), path.ele).gainM;
    return getString(R.string.tz_preview_on_foot,
                     TzFormat.hoursMinutes(FootTime.walkSeconds(line.totalM(), ascent)));
  }

  /** Surfaces known (Phase 23): the bar and legend, or the caption without a map; the card grows to them. */
  private void onSurfaces(@Nullable SurfaceProfile p)
  {
    final View v = getView();
    if (v == null || p == null || (!p.noMap && p.runs.isEmpty()))
      return;
    final TzSurfaceBarView bar = v.findViewById(R.id.tz_preview_surfaces);
    final View none = v.findViewById(R.id.tz_preview_surfaces_none);
    TransitionManager.beginDelayedTransition(mCard, new ChangeBounds().setDuration(TzMotion.GROW_MS));
    bar.setProfile(p.noMap ? null : p);
    bar.setVisibility(p.noMap ? View.GONE : View.VISIBLE);
    none.setVisibility(p.noMap ? View.VISIBLE : View.GONE);
    // The card is taller now: keep the route clear of it, unless the rider has moved the map.
    mCard.removeCallbacks(mReframe);
    mCard.postDelayed(mReframe, TzMotion.GROW_MS);
  }

  /** The surfaces go with the path they were read on. */
  private void hideSurfaces()
  {
    final View v = requireView();
    v.findViewById(R.id.tz_preview_surfaces).setVisibility(View.GONE);
    v.findViewById(R.id.tz_preview_surfaces_none).setVisibility(View.GONE);
    v.findViewById(R.id.tz_preview_demanding).setVisibility(View.GONE);
  }

  @Override
  public void onProfileChosen(@NonNull Profile profile)
  {
    if (profile == mProfile || getView() == null)
      return;
    mProfile = profile;
    if (mPill != null)
      mPill.setProfile(profile);
    // A track is followed whatever the profile (it decides the legs and detours of the ride). The path to a place is
    // the profile's own: work it out again, dropping the one in flight.
    if (mPlaceName == null)
      return;
    TzRouter.cancel();
    if (mPlanner != null)
      mPlanner.cancel();
    mPlanner = null;
    mWalkOnly = false;
    mRouting = false;
    mPath = null;
    TzRouteLayer.hide(this);
    hideSurfaces();
    bindPlace(getString(R.string.tz_preview_finding));
    final Location here = TzLocation.last(requireContext());
    if (here != null)
      onLocation(here);
    else
      TzLocation.addListener(requireContext(), this);
  }

  /** The card of a ride to a place: what is happening (or the way there), the place, the path's numbers. */
  private void bindPlace(@NonNull String label)
  {
    final View v = requireView();
    mStar.setVisibility(View.GONE);
    ((TextView) v.findViewById(R.id.tz_preview_label)).setText(label);
    ((TextView) v.findViewById(R.id.tz_preview_title)).setText(mPlaceName);
    ((TextView) v.findViewById(R.id.tz_preview_peek_title)).setText(mPlaceName);
    v.findViewById(R.id.tz_preview_start).setEnabled(mPath != null);
    final TzMetricView stat0 = v.findViewById(R.id.tz_preview_stat0);
    final TzMetricView stat1 = v.findViewById(R.id.tz_preview_stat1);
    final TzMetricView stat2 = v.findViewById(R.id.tz_preview_stat2);
    final TzElevationProfileView profile = v.findViewById(R.id.tz_preview_profile);
    final RoutedPath path = mPath;
    // A hike shows what it climbs and descends (D+, D−); bike + walk (B5W) the two parts and the gain; the others
    // distance, gain and high point.
    final boolean hike = mProfile == Profile.HIKE;
    final boolean bikeWalk = path != null && path.bikeAndWalk();
    ((TextView) v.findViewById(R.id.tz_preview_stat0_label)).setText(bikeWalk ? R.string.tz_preview_by_bike
                                                                               : R.string.tz_preview_distance);
    ((TextView) v.findViewById(R.id.tz_preview_stat1_label)).setText(bikeWalk ? R.string.tz_preview_on_foot_stat
                                                                     : hike   ? R.string.tz_preview_climb
                                                                              : R.string.tz_preview_gain);
    ((TextView) v.findViewById(R.id.tz_preview_stat2_label)).setText(bikeWalk ? R.string.tz_preview_gain
                                                                     : hike   ? R.string.tz_preview_descent
                                                                              : R.string.tz_preview_high);
    v.findViewById(R.id.tz_preview_walk_note).setVisibility(bikeWalk ? View.VISIBLE : View.GONE);
    v.findViewById(R.id.tz_preview_ele_note).setVisibility(hike && path != null && path.ele == null ? View.VISIBLE
                                                                                                     : View.GONE);
    if (path == null)
    {
      stat0.setValue(TzFormat.UNKNOWN, "");
      stat1.setValue(TzFormat.UNKNOWN, "");
      stat2.setValue(TzFormat.UNKNOWN, "");
      profile.setVisibility(View.INVISIBLE);
      ((TextView) v.findViewById(R.id.tz_preview_peek_stats)).setText(label);
      return;
    }
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final FollowRoute line = path.line;
    final TzFormat.Value dist = TzFormat.distance(line.totalM(), units, locale);
    stat0.setValue(dist);
    String peek = dist.toString();
    if (path.ele != null)
    {
      final RouteStats stats = RouteStats.compute(line.lats(), line.lons(), path.ele);
      final TzFormat.Value gain = TzFormat.elevation(stats.gainM, units, locale);
      stat1.setValue(gain);
      if (hike)
      {
        final double[] upDown = RouteStats.ascentDescent(path.ele);
        if (upDown == null)
          stat2.setValue(TzFormat.UNKNOWN, "");
        else
          stat2.setValue(TzFormat.elevation(upDown[1], units, locale));
      }
      else
      {
        stat2.setValue(TzFormat.elevation(stats.highM, units, locale));
      }
      peek += " · " + gain;
      final double[] d = line.distances();
      final float[] pd = new float[d.length];
      final float[] pa = new float[d.length];
      for (int i = 0; i < d.length; i++)
      {
        pd[i] = (float) d[i];
        pa[i] = (float) path.ele[i];
      }
      profile.setProfile(pd, pa);
      profile.setVisibility(View.VISIBLE);
    }
    else
    {
      stat1.setValue(TzFormat.UNKNOWN, "");
      stat2.setValue(TzFormat.UNKNOWN, "");
      profile.setVisibility(View.GONE);
    }
    if (bikeWalk)
    {
      final TzFormat.Value onFoot = TzFormat.distance(line.totalM() - path.walkFromM, units, locale);
      stat0.setValue(TzFormat.distance(path.walkFromM, units, locale));
      stat1.setValue(onFoot);
      if (path.ele == null)
        stat2.setValue(TzFormat.UNKNOWN, "");
      else
        stat2.setValue(TzFormat.elevation(RouteStats.compute(line.lats(), line.lons(), path.ele).gainM, units, locale));
      ((TextView) v.findViewById(R.id.tz_preview_walk_note_text))
          .setText(getString(R.string.tz_preview_walk_note, onFoot.toString()));
    }
    ((TextView) v.findViewById(R.id.tz_preview_peek_stats)).setText(peek);
  }

  /** {minLat, minLon, maxLat, maxLon}. */
  @NonNull
  private static double[] extent(@NonNull double[] lat, @NonNull double[] lon)
  {
    final double[] box = {lat[0], lon[0], lat[0], lon[0]};
    for (int i = 1; i < lat.length; i++)
    {
      box[0] = Math.min(box[0], lat[i]);
      box[1] = Math.min(box[1], lon[i]);
      box[2] = Math.max(box[2], lat[i]);
      box[3] = Math.max(box[3], lon[i]);
    }
    return box;
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    ((FrameLayout.LayoutParams) mTop.getLayoutParams()).topMargin =
        top + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    mTop.requestLayout();
    ((FrameLayout.LayoutParams) mStopsButton.getLayoutParams()).topMargin =
        top + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra)
        + getResources().getDimensionPixelSize(R.dimen.tz_map_btn) + getResources().getDimensionPixelSize(R.dimen.tz_space_12);
    mStopsButton.requestLayout();
    mBottomMargin = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra);
    ((FrameLayout.LayoutParams) mCard.getLayoutParams()).bottomMargin = mBottomMargin;
    mCard.requestLayout();
  }

  private void bindCard(@NonNull Route r)
  {
    final View v = requireView();
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final String kind = TzRoutesScreen.kindText(requireContext(), r);
    mStopsButton.setVisibility(View.VISIBLE);
    final Location here = TzLocation.last(requireContext());
    final String label;
    if (here != null && r.silLat.length > 0)
    {
      final double toStart = Geo.distance(here.getLatitude(), here.getLongitude(), r.silLat[0], r.silLon[0]);
      label = getString(R.string.tz_preview_starts_away, kind, TzFormat.distance(toStart, units, locale).toString());
    }
    else
    {
      label = kind;
    }
    ((TextView) v.findViewById(R.id.tz_preview_label)).setText(label);
    ((TextView) v.findViewById(R.id.tz_preview_title)).setText(r.name);
    ((TextView) v.findViewById(R.id.tz_preview_peek_title)).setText(r.name);

    final TzFormat.Value dist = TzFormat.distance(r.distanceM, units, locale);
    ((TzMetricView) v.findViewById(R.id.tz_preview_stat0)).setValue(dist);
    String peek = dist.toString();
    if (r.hasElevation())
    {
      final TzFormat.Value gain = TzFormat.elevation(r.gainM, units, locale);
      ((TzMetricView) v.findViewById(R.id.tz_preview_stat1)).setValue(gain);
      ((TzMetricView) v.findViewById(R.id.tz_preview_stat2)).setValue(TzFormat.elevation(r.highM, units, locale));
      peek += " · " + gain;
    }
    else
    {
      ((TzMetricView) v.findViewById(R.id.tz_preview_stat1)).setValue(TzFormat.UNKNOWN, "");
      ((TzMetricView) v.findViewById(R.id.tz_preview_stat2)).setValue(TzFormat.UNKNOWN, "");
      v.findViewById(R.id.tz_preview_profile).setVisibility(View.GONE);
    }
    ((TextView) v.findViewById(R.id.tz_preview_peek_stats)).setText(peek);
    mFavorite = r.favorite;
    if (r.temp)
      bindSave();
    else
      bindStar(r.favorite);
  }

  /** A planned route not saved yet: the bookmark (Save) instead of the star. */
  private void bindSave()
  {
    mStar.setImageResource(R.drawable.ic_tz_save);
    ImageViewCompat.setImageTintList(mStar, ContextCompat.getColorStateList(requireContext(), R.color.tz_text1));
    mStar.setContentDescription(getString(R.string.tz_plan_save));
  }

  private void bindStar(boolean favorite)
  {
    mStar.setImageResource(favorite ? R.drawable.ic_tz_star_filled : R.drawable.ic_tz_star);
    ImageViewCompat.setImageTintList(
        mStar, ContextCompat.getColorStateList(requireContext(), favorite ? R.color.tz_mint : R.color.tz_text1));
    mStar.setContentDescription(getString(favorite ? R.string.tz_cd_remove_favorite : R.string.tz_cd_add_favorite));
  }

  private void onGeometry(@Nullable RouteGeometry g)
  {
    if (getView() == null)
      return;
    if (g == null)
    {
      Toast.makeText(requireContext(), R.string.tz_route_unreadable, Toast.LENGTH_LONG).show();
      return;
    }
    mBox = new double[] {g.minLat, g.minLon, g.maxLat, g.maxLon};
    if (mFocus != null && !Double.isNaN(mFocusAlong) && g.lineLat.length >= 2)
    {
      final FollowRoute line = new FollowRoute(g.lineLat, g.lineLon);
      mFocusOnRoute = new double[] {line.latAt(mFocusAlong), line.lonAt(mFocusAlong)};
    }
    if (g.profileDist != null)
      ((TzElevationProfileView) requireView().findViewById(R.id.tz_preview_profile))
          .setProfile(g.profileDist, g.profileAlt);
    TzRouteLayer.show(this, g.lineLat, g.lineLon, false);
    frameRoute();
    if (mRoute != null)
      RouteStore.get(requireContext()).loadSurfaces(mRoute, g, this::onSurfaces);
  }

  /** Fits the route between the top buttons and the card, 24dp clear of both and of the screen sides. */
  private void frameRoute()
  {
    if (mBox == null || mCard.getHeight() == 0)
      return;
    // The preview shows the route, not the rider; following would also pull the camera back mid-animation.
    TzMap.stopFollowing();
    TzMap.setVisibleArea(this, mTop.getBottom(), mCard.getHeight() + mBottomMargin);
    mFramed = true;
    if (mFocus != null)
    {
      // Back from S1 with a place: that place and the route next to it, close up.
      final double[] near = mFocusOnRoute != null ? mFocusOnRoute : mFocus;
      final double pad = FOCUS_PAD_M / Geo.M_PER_DEG;
      final double padLon = pad / Math.cos(Math.toRadians(mFocus[0]));
      TzMap.showRect(Math.min(mFocus[0], near[0]) - pad, Math.min(mFocus[1], near[1]) - padLon,
                     Math.max(mFocus[0], near[0]) + pad, Math.max(mFocus[1], near[1]) + padLon,
                     getResources().getDimensionPixelSize(R.dimen.tz_space_24));
      mPins.setPlace(mFocus);
      mPins.follow(1500);
      return;
    }
    TzMap.showRect(mBox[0], mBox[1], mBox[2], mBox[3], getResources().getDimensionPixelSize(R.dimen.tz_space_24));
  }

  // B6: collapse while the rider pans / pinches the map, come back 1.5 s after the gesture ends.

  @Override
  public void onTouch(@NonNull MotionEvent e)
  {
    mPins.onMapTouched();
    switch (e.getActionMasked())
    {
    case MotionEvent.ACTION_DOWN ->
    {
      mGestureOnMap = !hit(mCard, e) && !hit(mTop, e);
      mDownX = e.getRawX();
      mDownY = e.getRawY();
      mCard.removeCallbacks(mExpand);
    }
    case MotionEvent.ACTION_POINTER_DOWN ->
    {
      if (mGestureOnMap)
      {
        mPanned = true;
        setCollapsed(true);
      }
    }
    case MotionEvent.ACTION_MOVE ->
    {
      final float slop = getResources().getDimension(R.dimen.tz_space_8);
      if (mGestureOnMap && Math.hypot(e.getRawX() - mDownX, e.getRawY() - mDownY) > slop)
      {
        mPanned = true;
        setCollapsed(true);
      }
    }
    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
    {
      if (mCollapsed)
        mCard.postDelayed(mExpand, TzConfig.PREVIEW_REEXPAND_MS);
    }
    default -> {}
    }
  }

  private static boolean hit(@NonNull View v, @NonNull MotionEvent e)
  {
    final Rect r = new Rect();
    return v.getGlobalVisibleRect(r) && r.contains((int) e.getRawX(), (int) e.getRawY());
  }

  private void setCollapsed(boolean collapsed)
  {
    if (collapsed == mCollapsed)
      return;
    mCollapsed = collapsed;
    if (mAnimator != null)
      mAnimator.cancel();

    final int from = mCard.getHeight();
    final int peekH = getResources().getDimensionPixelSize(R.dimen.tz_peek_h);
    final int to;
    if (collapsed)
    {
      to = peekH;
    }
    else
    {
      mFull.measure(View.MeasureSpec.makeMeasureSpec(mCard.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      to = mFull.getMeasuredHeight();
    }
    mFull.setVisibility(View.VISIBLE);
    mPeek.setVisibility(View.VISIBLE);
    final float fullFrom = mFull.getAlpha();
    final float peekFrom = mPeek.getAlpha();
    final float fullTo = collapsed ? 0f : 1f;

    mAnimator = ValueAnimator.ofFloat(0f, 1f);
    mAnimator.setDuration(collapsed ? TzMotion.COLLAPSE_MS : TzMotion.EXPAND_MS);
    mAnimator.setInterpolator(TzMotion.EASE);
    mAnimator.addUpdateListener(a -> {
      final float t = (float) a.getAnimatedValue();
      mCard.getLayoutParams().height = Math.round(from + (to - from) * t);
      mCard.requestLayout();
      mFull.setAlpha(fullFrom + (fullTo - fullFrom) * t);
      mPeek.setAlpha(peekFrom + ((1f - fullTo) - peekFrom) * t);
    });
    mAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation)
      {
        if (mAnimator != animation)
          return;
        mFull.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        mPeek.setVisibility(collapsed ? View.VISIBLE : View.GONE);
        mCard.setBackgroundResource(collapsed ? R.drawable.tz_bg_surface2_r28 : R.drawable.tz_bg_surface2_r32);
        if (!collapsed)
          mCard.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mCard.requestLayout();
      }
    });
    mAnimator.start();
  }
}
