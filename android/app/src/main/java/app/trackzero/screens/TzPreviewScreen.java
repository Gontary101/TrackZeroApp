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
import app.trackzero.format.TzFormat;
import app.trackzero.geo.Geo;
import app.trackzero.ride.TzRide;
import app.trackzero.routes.Route;
import app.trackzero.routes.RouteGeometry;
import app.trackzero.routes.RouteStore;
import app.trackzero.ui.TzElevationProfileView;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzMotion;
import java.util.Locale;

/** B5 · Route preview and B6 · panning: the route on the map, its card, START RIDE. */
public class TzPreviewScreen extends TzScreen implements TzMap.TouchObserver
{
  @Nullable
  private Route mRoute;
  @Nullable
  private RouteGeometry mGeometry;

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
    view.findViewById(R.id.tz_preview_back).setOnClickListener(v -> TzNavigator.show(TzNavigator.Screen.ROUTES, null));
    mPeek.setOnClickListener(v -> setCollapsed(false));
    mStar.setOnClickListener(v -> {
      if (mRoute == null)
        return;
      mFavorite = !mFavorite;
      RouteStore.get(requireContext()).setFavorite(mRoute.id, mFavorite);
      bindStar(mFavorite);
    });
    view.findViewById(R.id.tz_preview_start).setOnClickListener(v -> {
      if (mRoute != null)
        TzRide.start(requireActivity(), mRoute);
    });
    // Frame once the card has its final height; later size changes (collapse/expand) keep the rider's view.
    mCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (!mFramed)
        frameRoute();
    });

    final String id = requireArguments().getString(TzRoutesScreen.ARG_ROUTE_ID);
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
  }

  @Override
  public void onStop()
  {
    super.onStop();
    TzMap.removeTouchObserver(this);
    mCard.removeCallbacks(mExpand);
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    if (mAnimator != null)
      mAnimator.cancel();
    TzRouteLayer.hide();
    TzMap.setVisibleArea(0, 0);
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
    mGeometry = g;
    if (g.profileDist != null)
      ((TzElevationProfileView) requireView().findViewById(R.id.tz_preview_profile))
          .setProfile(g.profileDist, g.profileAlt);
    TzRouteLayer.show(g.lineLat, g.lineLon);
    frameRoute();
  }

  /** Fits the route between the top buttons and the card, 24dp clear of both and of the screen sides. */
  private void frameRoute()
  {
    if (mGeometry == null || mCard.getHeight() == 0)
      return;
    // The preview shows the route, not the rider; following would also pull the camera back mid-animation.
    TzMap.stopFollowing();
    TzMap.setVisibleArea(mTop.getBottom(), mCard.getHeight() + mBottomMargin);
    TzMap.showRect(mGeometry.minLat, mGeometry.minLon, mGeometry.maxLat, mGeometry.maxLon,
                   getResources().getDimensionPixelSize(R.dimen.tz_space_24));
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
