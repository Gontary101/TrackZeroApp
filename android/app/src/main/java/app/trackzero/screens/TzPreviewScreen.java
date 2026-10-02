package app.trackzero.screens;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
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
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.RoutedPath;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.Geo;
import app.trackzero.ride.TzRide;
import app.trackzero.routes.Route;
import app.trackzero.routes.RouteGeometry;
import app.trackzero.routes.RouteStats;
import app.trackzero.routes.RouteStore;
import app.trackzero.ui.TzElevationProfileView;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzMotion;
import java.util.Locale;

/**
 * B5 · Route preview and B6 · panning: the route on the map, its card, START RIDE. A route of the library, or the
 * bike route to a place found in search (Phase 15: worked out from the rider's position).
 */
public class TzPreviewScreen extends TzScreen implements TzMap.TouchObserver, TzLocation.Listener
{
  /** A place instead of a route of the library: its name and where it is. */
  public static final String ARG_PLACE_NAME = "placeName";
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
  private int mBottomMargin;
  private boolean mFavorite;

  // Pan detection: a gesture that starts on the map and moves (or pinches) collapses the card.
  private boolean mGestureOnMap;
  private float mDownX;
  private float mDownY;
  private boolean mCollapsed;
  private boolean mFramed;
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
    view.findViewById(R.id.tz_preview_back).setOnClickListener(v -> back());
    mPeek.setOnClickListener(v -> setCollapsed(false));
    mStar.setOnClickListener(v -> {
      if (mRoute == null)
        return;
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
        TzRide.startTo(requireActivity(), mPlaceName, mPlaceLat, mPlaceLon, mPath);
      }
      else if (mRoute != null)
      {
        TzRide.start(requireActivity(), mRoute);
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
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    if (mAnimator != null)
      mAnimator.cancel();
    TzRouteLayer.hide(this);
    TzMap.clearVisibleArea(this);
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
    TzRouter.route(here.getLatitude(), here.getLongitude(), mPlaceLat, mPlaceLon,
                   here.hasBearing() ? here.getBearing() : Double.NaN, this::onPlacePath);
  }

  private void onPlacePath(@Nullable RoutedPath path, boolean mapMissing)
  {
    mRouting = false;
    if (getView() == null)
      return;
    if (path == null)
    {
      bindPlace(getString(mapMissing ? R.string.tz_preview_no_map : R.string.tz_preview_no_route));
      return;
    }
    mPath = path;
    bindPlace(getString(R.string.tz_preview_to_place));
    final FollowRoute line = path.line;
    TzRouteLayer.showPath(this, line.lats(), line.lons());
    mBox = extent(line.lats(), line.lons());
    frameRoute();
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
      stat2.setValue(TzFormat.elevation(stats.highM, units, locale));
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
    mBottomMargin = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra);
    ((FrameLayout.LayoutParams) mCard.getLayoutParams()).bottomMargin = mBottomMargin;
    mCard.requestLayout();
  }

  private void bindCard(@NonNull Route r)
  {
    final View v = requireView();
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final String kind = getString(TzRoutesScreen.kindLabel(r.kind));
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
    bindStar(r.favorite);
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
    if (g.profileDist != null)
      ((TzElevationProfileView) requireView().findViewById(R.id.tz_preview_profile))
          .setProfile(g.profileDist, g.profileAlt);
    TzRouteLayer.show(this, g.lineLat, g.lineLon);
    frameRoute();
  }

  /** Fits the route between the top buttons and the card, 24dp clear of both and of the screen sides. */
  private void frameRoute()
  {
    if (mBox == null || mCard.getHeight() == 0)
      return;
    // The preview shows the route, not the rider; following would also pull the camera back mid-animation.
    TzMap.stopFollowing();
    TzMap.setVisibleArea(this, mTop.getBottom(), mCard.getHeight() + mBottomMargin);
    TzMap.showRect(mBox[0], mBox[1], mBox[2], mBox[3], getResources().getDimensionPixelSize(R.dimen.tz_space_24));
    mFramed = true;
  }

  // B6: collapse while the rider pans / pinches the map, come back 1.5 s after the gesture ends.

  @Override
  public void onTouch(@NonNull MotionEvent e)
  {
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
        setCollapsed(true);
    }
    case MotionEvent.ACTION_MOVE ->
    {
      final float slop = getResources().getDimension(R.dimen.tz_space_8);
      if (mGestureOnMap && Math.hypot(e.getRawX() - mDownX, e.getRawY() - mDownY) > slop)
        setCollapsed(true);
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
