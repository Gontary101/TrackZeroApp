package app.trackzero.screens;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import app.organicmaps.R;
import app.trackzero.TzConfig;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzMap;
import app.trackzero.base.TzRouteLayer;
import app.trackzero.follow.FollowEngine;
import app.trackzero.format.TzFormat;
import app.trackzero.ride.RideSession;
import app.trackzero.ride.RideStats;
import app.trackzero.ride.TzRideService;
import app.trackzero.routes.Route;
import app.trackzero.routes.RouteStore;
import app.trackzero.ui.TzChip;
import app.trackzero.ui.TzElevationProfileView;
import app.trackzero.ui.TzHoldButton;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzPageHandle;
import app.trackzero.ui.TzStatsIsland;
import app.trackzero.ui.TzTextSize;
import app.trackzero.ui.TzToggleRow;
import app.trackzero.ui.TzTurnCard;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;
import java.util.TimeZone;

/**
 * RIDE: page 0 map (R1 free ride / R2 route), page 1 speed (R4), page 2 elevation (R5); the controls sheet (R6)
 * whose collapsed state is the page handle; paused states (R7). Reads the ride from {@link TzRideService}.
 */
public class TzRideScreen extends TzScreen implements TzRideService.Listener
{
  private static final int PAGE_MAP = 0;
  private static final int PAGE_SPEED = 1;
  private static final int PAGE_CLIMB = 2;
  private static final long REFRESH_MS = 1000;
  private static final long ZOOM_STABLE_MS = 5000;
  /** The rider arrow sits at 67 % of the screen height (R1 note). */
  private static final float RIDER_Y = 0.67f;
  private static final float PAUSED_HERO_ALPHA = 0.35f;
  private static final float PAUSED_GRID_ALPHA = 0.55f;
  private static final float HERO_DP = 208;
  /** R4: line-height 0.85 keeps KM/H 10dp under the digits. */
  private static final float HERO_LINE_HEIGHT = 0.85f;

  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final Runnable mTick = this::tick;
  @Nullable
  private TzRideService mService;
  private boolean mBound;
  private int mPage = PAGE_MAP;
  @Nullable
  private Route mRoute;
  private boolean mRouteRequested;
  /** The follow engine said off route at the last render (R3). */
  private boolean mOffRoute;
  private boolean mEnding;
  private int mZoom = -1;
  private int mZoomCandidate = -1;
  private long mZoomCandidateSince;
  private int mTraceSize = -1;
  private int mTopInset;
  private int mBottomInset;
  /** Sheet slide offset: 0 collapsed (page handle), 1 open (R6). */
  private float mSlide;

  // Shared views
  private ViewPager2 mPager;
  private TzPageHandle mHandle;
  private View mScrim;
  private View mSheet;
  private View mControls;
  private BottomSheetBehavior<View> mSheetBehavior;
  private MaterialButton mResume;
  private MaterialButton mPauseButton;
  private TzHoldButton mEnd;
  private TextView mSheetSummary;
  private TzToggleRow mVoice;
  // Page 0
  @Nullable
  private View mMapTop;
  @Nullable
  private TextView mPill;
  @Nullable
  private View mLocate;
  @Nullable
  private TzTurnCard mTurn;
  @Nullable
  private TzStatsIsland mStats;
  // Page 1
  @Nullable
  private View mSpeedHeader;
  @Nullable
  private TzChip mSpeedChip;
  @Nullable
  private TextView mHero;
  @Nullable
  private TextView mHeroUnit;
  @Nullable
  private View mGrid;
  @Nullable
  private TzMetricView[] mCells;
  // Page 2
  @Nullable
  private View mClimbHeader;
  @Nullable
  private TzChip mClimbChip;
  @Nullable
  private TzElevationProfileView mProfile;

  private final ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder)
    {
      mService = ((TzRideService.LocalBinder) binder).service();
      mService.addListener(TzRideScreen.this);
      render();
    }

    @Override
    public void onServiceDisconnected(ComponentName name)
    {
      mService = null;
    }
  };

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_ride, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mPager = view.findViewById(R.id.tz_ride_pager);
    mHandle = view.findViewById(R.id.tz_ride_handle);
    mScrim = view.findViewById(R.id.tz_ride_scrim);
    mSheet = view.findViewById(R.id.tz_ride_sheet);
    mControls = view.findViewById(R.id.tz_ride_controls);
    mResume = view.findViewById(R.id.tz_ride_resume);
    mPauseButton = view.findViewById(R.id.tz_ride_pause);
    mEnd = view.findViewById(R.id.tz_ride_end);
    mSheetSummary = view.findViewById(R.id.tz_ride_sheet_summary);
    mVoice = view.findViewById(R.id.tz_ride_voice);

    mPager.setAdapter(new Pages());
    // All three pages stay alive: they are cheap, and the screen updates them in place.
    mPager.setOffscreenPageLimit(2);
    mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
      @Override
      public void onPageSelected(int position)
      {
        setPage(position);
      }
    });

    setUpSheet();
    mResume.setOnClickListener(v -> {
      if (mService != null)
        mService.resume();
    });

    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    mVoice.setSubtitle(getString(R.string.tz_voice_subtitle,
                                 TzFormat.distance(TzConfig.VOICE_FAR_M, units, locale).toString(),
                                 TzFormat.distance(TzConfig.VOICE_NEAR_M, units, locale).toString()));
    mVoice.setChecked(TzPrefs.voice(requireContext()), false);
    mVoice.setListener(on -> TzPrefs.setVoice(requireContext(), on));
    final TzToggleRow awake = view.findViewById(R.id.tz_ride_awake);
    awake.setChecked(TzPrefs.keepAwake(requireContext()), false);
    awake.setListener(on -> {
      TzPrefs.setKeepAwake(requireContext(), on);
      applyKeepAwake();
    });
    mPauseButton.setOnClickListener(v -> {
      final RideSession s = session();
      if (mService == null || s == null)
        return;
      if (s.state() == RideSession.State.PAUSED)
        mService.resume();
      else
        mService.pause();
      mSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    });
    mEnd.setListener(this::endRide);

    // Keep the rider at 67 % of the height whatever the screen size.
    view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (b - t != ob - ot)
        applyVisibleArea();
    });
    super.onViewCreated(view, savedInstanceState);
  }

  private void setUpSheet()
  {
    mSheetBehavior = BottomSheetBehavior.from(mSheet);
    mSheetBehavior.setHideable(false);
    mSheetBehavior.setSkipCollapsed(false);
    mSheetBehavior.setFitToContents(true);
    mSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
      @Override
      public void onStateChanged(@NonNull View sheet, int state)
      {
        final boolean collapsed = state == BottomSheetBehavior.STATE_COLLAPSED;
        mScrim.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (collapsed)
          applySlide(0);
        else if (state == BottomSheetBehavior.STATE_EXPANDED)
          applySlide(1);
        render();
      }

      @Override
      public void onSlide(@NonNull View sheet, float offset)
      {
        mScrim.setVisibility(View.VISIBLE);
        applySlide(Math.max(0, offset));
      }
    });
    mScrim.setOnClickListener(v -> mSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED));
    mHandle.setOnClickListener(v -> mSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED));
    applySlide(0);
  }

  /** Collapsed: only the transparent page handle. Expanded: the R6 panel on surface1 over a scrim. */
  private void applySlide(float offset)
  {
    mSlide = offset;
    // The sheet has its own RESUME: the floating one gives way to it.
    mResume.setAlpha(1 - offset);
    mResume.setVisibility(isManuallyPaused() && offset < 1 ? View.VISIBLE : View.GONE);
    mScrim.setAlpha(offset);
    mSheet.getBackground().mutate().setAlpha(Math.round(offset * 255));
    mSheet.setElevation(offset * getResources().getDimension(R.dimen.tz_elev3));
    mControls.setAlpha(offset);
    mHandle.setAlpha(1 - offset);
    // Taps on the collapsed handle area must not reach the (invisible) controls under it.
    mControls.setVisibility(offset > 0 ? View.VISIBLE : View.INVISIBLE);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    mBound = requireContext().bindService(new Intent(requireContext(), TzRideService.class), mConnection, 0);
    applyKeepAwake();
    setPage(mPager.getCurrentItem());
    mHandler.post(mTick);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    mHandler.removeCallbacks(mTick);
    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (mService != null)
      mService.removeListener(this);
    if (mBound)
      requireContext().unbindService(mConnection);
    mBound = false;
    mService = null;
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    TzRouteLayer.hide();
    TzMap.setVisibleArea(0, 0);
  }

  @Override
  public boolean onBackPressed()
  {
    // Back never ends a ride: it opens (or closes) the controls sheet.
    mSheetBehavior.setState(mSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                                ? BottomSheetBehavior.STATE_COLLAPSED
                                : BottomSheetBehavior.STATE_EXPANDED);
    return true;
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    mTopInset = top;
    mBottomInset = bottom;
    final int handle = getResources().getDimensionPixelSize(R.dimen.tz_handle_h);
    mSheetBehavior.setPeekHeight(handle + bottom);
    mControls.setPadding(mControls.getPaddingLeft(), mControls.getPaddingTop(), mControls.getPaddingRight(),
                         bottom + getResources().getDimensionPixelSize(R.dimen.tz_space_16));
    final FrameLayout.LayoutParams rlp = (FrameLayout.LayoutParams) mResume.getLayoutParams();
    rlp.bottomMargin = bottom + handle + getResources().getDimensionPixelSize(R.dimen.tz_ride_resume_above_handle);
    mResume.setLayoutParams(rlp);
    layoutPages();
    applyVisibleArea();
  }

  /** Puts page content under the status bar and above the handle; pages may be created after the insets. */
  private void layoutPages()
  {
    final int handle = getResources().getDimensionPixelSize(R.dimen.tz_handle_h);
    final int top = mTopInset + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    if (mMapTop != null)
    {
      setTopMargin(mMapTop, top);
      setTopMargin(mTurn, top);
      final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mStats.getLayoutParams();
      lp.bottomMargin = mBottomInset + handle + getResources().getDimensionPixelSize(R.dimen.tz_ride_above_handle);
      mStats.setLayoutParams(lp);
      // The engine's ruler and its OSM attribution go above the island instead of under the handle.
      TzMap.setRulerPosition(getResources().getDimensionPixelSize(R.dimen.tz_gutter),
                             lp.bottomMargin + getResources().getDimensionPixelSize(R.dimen.tz_stats_island_h)
                                 + getResources().getDimensionPixelSize(R.dimen.tz_space_12));
    }
    if (mSpeedHeader != null)
    {
      setTopMargin(mSpeedHeader, top);
      final View page = (View) mSpeedHeader.getParent();
      page.setPadding(0, 0, 0, mBottomInset + handle);
    }
    if (mClimbHeader != null)
      setTopMargin(mClimbHeader, top);
  }

  private static void setTopMargin(@NonNull View v, int px)
  {
    final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
    lp.topMargin = px;
    v.setLayoutParams(lp);
  }

  private void applyVisibleArea()
  {
    final View root = getView();
    if (root == null || root.getHeight() == 0)
      return;
    // The engine centres the rider in the visible area: start it low enough to put the centre at 67 %.
    final int h = root.getHeight();
    TzMap.setVisibleArea(Math.round(h * (2 * RIDER_Y - 1)), 0);
  }

  private void setPage(int page)
  {
    mPage = page;
    mHandle.setPage(page, true);
    mHandle.setContentDescription(getString(R.string.tz_cd_page_handle, page + 1, TzPageHandle.PAGES));
    // Speed and elevation pages are opaque: no need to draw the map behind them.
    TzMap.setRenderingEnabled(page == PAGE_MAP);
    if (page == PAGE_MAP)
      TzMap.followRider(true);
    mTraceSize = -1;
    render();
  }

  private void applyKeepAwake()
  {
    if (TzPrefs.keepAwake(requireContext()))
      requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else
      requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  private void endRide()
  {
    if (mService == null || mEnding)
      return;
    mEnding = true;
    final Context app = requireContext().getApplicationContext();
    mService.end(ride -> {
      if (ride == null)
      {
        Toast.makeText(app, R.string.tz_ride_not_saved, Toast.LENGTH_LONG).show();
        TzNavigator.show(TzNavigator.Screen.MAP, null);
        return;
      }
      final Bundle args = new Bundle();
      args.putString(TzSummaryScreen.ARG_RIDE_ID, ride.id);
      TzNavigator.show(TzNavigator.Screen.SUMMARY, args);
    });
  }

  private boolean isManuallyPaused()
  {
    final RideSession s = session();
    return s != null && s.state() == RideSession.State.PAUSED;
  }

  @Nullable
  private RideSession session()
  {
    return mService == null ? null : mService.session();
  }

  @Override
  public void onRideChanged(@NonNull RideSession session)
  {
    render();
  }

  private void tick()
  {
    render();
    mHandler.postDelayed(mTick, REFRESH_MS);
  }

  // ---- Rendering: once a second and on every fix ----

  private void render()
  {
    final RideSession s = session();
    if (s == null || getView() == null || s.state() == RideSession.State.SAVED)
      return;
    loadRoute(s);

    final long now = SystemClock.elapsedRealtime();
    final RideStats st = s.stats();
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final RideSession.State state = s.state();
    final boolean paused = state != RideSession.State.RECORDING;

    mResume.setVisibility(state == RideSession.State.PAUSED && mSlide < 1 ? View.VISIBLE : View.GONE);
    // Turn announcements only exist on a route.
    mVoice.setVisibility(s.routeId() != null ? View.VISIBLE : View.GONE);
    renderPauseButton(state == RideSession.State.PAUSED);
    if (mSheetBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED)
      mSheetSummary.setText(getString(R.string.tz_ride_sheet_summary, TzFormat.duration(st.movingMs(now) / 1000),
                                      TzFormat.distance(st.distanceM(), units, locale).toString()));

    switch (mPage)
    {
    case PAGE_MAP -> renderMap(s, st, now, units, locale, state == RideSession.State.PAUSED);
    case PAGE_SPEED -> renderSpeed(s, st, now, units, locale, paused);
    case PAGE_CLIMB -> renderClimb(s, units, locale);
    }
  }

  private void renderPauseButton(boolean paused)
  {
    mPauseButton.setText(paused ? R.string.tz_resume_ride : R.string.tz_pause_ride);
    mPauseButton.setIconResource(paused ? R.drawable.ic_tz_play : R.drawable.ic_tz_pause);
    final Context c = requireContext();
    mPauseButton.setBackgroundTintList(ContextCompat.getColorStateList(c, paused ? R.color.tz_mint
                                                                                 : R.color.tz_surface3));
    final ColorStateList content = ContextCompat.getColorStateList(c, paused ? R.color.tz_on_mint : R.color.tz_text1);
    mPauseButton.setTextColor(content);
    mPauseButton.setIconTint(content);
  }

  private void renderChip(@Nullable TzChip chip, @NonNull RideSession.State state)
  {
    if (chip == null)
      return;
    switch (state)
    {
    case PAUSED -> chip.setState(getString(R.string.tz_paused), R.color.tz_amber, R.color.tz_amber);
    case AUTO_PAUSED -> chip.setState(getString(R.string.tz_auto_paused), R.color.tz_amber, R.color.tz_amber);
    default -> chip.setState(getString(R.string.tz_rec), R.color.tz_mint, R.color.tz_text1);
    }
    chip.setPulsing(state == RideSession.State.RECORDING);
  }

  private void renderMap(@NonNull RideSession s, @NonNull RideStats st, long now, @NonNull TzFormat.Units units,
                         @NonNull Locale locale, boolean manuallyPaused)
  {
    if (mStats == null)
      return;
    final boolean route = s.routeId() != null;
    mPill.setVisibility(route ? View.GONE : View.VISIBLE);
    mLocate.setVisibility(route ? View.GONE : View.VISIBLE);
    final FollowEngine follow = mService == null ? null : mService.follow();
    mTurn.setVisibility(route && follow != null ? View.VISIBLE : View.GONE);

    final TzFormat.Value speed = TzFormat.speed(st.speedKmh(now), units, locale);
    mStats.setCell(0, getString(R.string.tz_stat_speed), speed.number, speed.unit);
    if (route && follow != null)
    {
      final boolean off = follow.phase() == FollowEngine.Phase.OFF_ROUTE;
      if (off && !mOffRoute)
        mTurn.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.REJECT
                                                                                   : HapticFeedbackConstants.LONG_PRESS);
      if (off != mOffRoute)
        TzRouteLayer.setDimmed(off);
      mOffRoute = off;
      final TzFormat.Value rem = TzFormat.distance(follow.remainingM(), units, locale);
      if (off)
      {
        mStats.setCell(1, getString(R.string.tz_stat_eta), getString(R.string.tz_eta_unknown), "");
        mStats.setCell(2, getString(R.string.tz_stat_remaining), getString(R.string.tz_value_unknown), rem.unit);
      }
      else
      {
        final long eta = System.currentTimeMillis() + Math.round(follow.etaSeconds() * 1000);
        mStats.setCell(1, getString(R.string.tz_stat_eta),
                       TzFormat.clock(eta, TimeZone.getDefault(), DateFormat.is24HourFormat(requireContext())), "");
        mStats.setCell(2, getString(R.string.tz_stat_remaining), rem.number, rem.unit);
      }
      mTurn.setUnits(units);
      mTurn.bind(follow.turnState());
    }
    else
    {
      final TzFormat.Value dist = TzFormat.distance(st.distanceM(), units, locale);
      mStats.setCell(1, getString(R.string.tz_stat_distance), dist.number, dist.unit);
      mStats.setCell(2, getString(R.string.tz_stat_time), TzFormat.duration(st.movingMs(now) / 1000), "");
    }
    // R7 on the map: the RESUME button takes the island's place, the island moves up over it.
    final float lift = manuallyPaused ? -(mResume.getHeight() + getResources().getDimension(R.dimen.tz_space_12)) : 0;
    mStats.setTranslationY(lift);
    autoZoom(st.speedKmh(now), now);
  }

  /** z17 below 15 km/h, z16 up to 30, z15 above; a new band must hold for 5 s. */
  private void autoZoom(double kmh, long now)
  {
    if (!TzMap.isFollowing())
      return;
    final int band = kmh < 15 ? 17 : kmh <= 30 ? 16 : 15;
    if (mZoom < 0)
    {
      if (TzMap.setZoom(band))
        mZoom = band;
      return;
    }
    if (band == mZoom)
    {
      mZoomCandidate = -1;
      // A zoom animation can be cut short by follow updates: settle it on the next tick.
      if (TzMap.zoom() != mZoom)
        TzMap.setZoom(mZoom);
      return;
    }
    if (band != mZoomCandidate)
    {
      mZoomCandidate = band;
      mZoomCandidateSince = now;
      return;
    }
    if (now - mZoomCandidateSince >= ZOOM_STABLE_MS && TzMap.setZoom(band))
    {
      mZoom = band;
      mZoomCandidate = -1;
    }
  }

  private void renderSpeed(@NonNull RideSession s, @NonNull RideStats st, long now, @NonNull TzFormat.Units units,
                           @NonNull Locale locale, boolean paused)
  {
    if (mHero == null)
      return;
    renderChip(mSpeedChip, s.state());
    final TzFormat.Value speed = TzFormat.speed(paused ? 0 : st.speedKmh(now), units, locale);
    mHero.setText(speed.number);
    mHero.setAlpha(paused ? PAUSED_HERO_ALPHA : 1f);
    mHeroUnit.setText(units == TzFormat.Units.IMPERIAL ? R.string.tz_unit_mph_dot : R.string.tz_unit_kmh_dot);
    mGrid.setAlpha(paused ? PAUSED_GRID_ALPHA : 1f);

    mCells[0].setValue(TzFormat.distance(st.distanceM(), units, locale));
    mCells[1].setValue(TzFormat.duration(st.movingMs(now) / 1000), "");
    final double alt = st.altitudeM();
    if (Double.isNaN(alt))
      mCells[2].setValue(TzFormat.UNKNOWN, "");
    else
      mCells[2].setValue(TzFormat.elevation(alt, units, locale));
    mCells[3].setValue(TzFormat.grade(st.gradePct(), locale));
    mCells[4].setValue(TzFormat.speed(st.avgKmh(now), units, locale));
    mCells[5].setValue(TzFormat.speed(st.maxKmh(), units, locale));
  }

  private void renderClimb(@NonNull RideSession s, @NonNull TzFormat.Units units, @NonNull Locale locale)
  {
    if (mProfile == null)
      return;
    renderChip(mClimbChip, s.state());
    final float[] dist = s.traceDistances();
    // The trace grows every 10 m: redraw only when it did.
    if (dist.length == mTraceSize)
      return;
    mTraceSize = dist.length;
    if (dist.length < 2)
    {
      mProfile.setProfile(null, null);
      return;
    }
    mProfile.setProfile(dist, s.traceAltitudes());
    mProfile.setRiderDistance(dist[dist.length - 1]);
    final TzFormat.Value end = TzFormat.distance(dist[dist.length - 1], units, locale);
    // "0 KM" … "9.6 KM": the start uses the end's unit.
    mProfile.setAxisLabels(("0 " + end.unit).toUpperCase(locale), end.toString().toUpperCase(locale));
  }

  /** A route ride draws its line and shows the route's numbers; loaded once per screen. */
  private void loadRoute(@NonNull RideSession s)
  {
    if (mRouteRequested || s.routeId() == null)
      return;
    mRouteRequested = true;
    final RouteStore store = RouteStore.get(requireContext());
    store.find(s.routeId(), route -> {
      if (getView() == null || route == null)
        return;
      mRoute = route;
      store.loadGeometry(route, g -> {
        if (getView() != null && g != null)
          TzRouteLayer.show(g.lineLat, g.lineLon);
      });
      render();
    });
  }

  // ---- Pages ----

  private final class Pages extends RecyclerView.Adapter<RecyclerView.ViewHolder>
  {
    @Override
    public int getItemCount()
    {
      return TzPageHandle.PAGES;
    }

    @Override
    public int getItemViewType(int position)
    {
      return position;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int page)
    {
      final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      final View v = switch (page)
      {
        case PAGE_MAP -> inflater.inflate(R.layout.tz_ride_page_map, parent, false);
        case PAGE_SPEED -> inflater.inflate(R.layout.tz_ride_page_speed, parent, false);
        default -> inflater.inflate(R.layout.tz_ride_page_climb, parent, false);
      };
      bindPage(page, v);
      return new RecyclerView.ViewHolder(v) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
  }

  private void bindPage(int page, @NonNull View v)
  {
    switch (page)
    {
    case PAGE_MAP ->
    {
      mMapTop = v.findViewById(R.id.tz_ride_map_top);
      mPill = v.findViewById(R.id.tz_ride_pill);
      mLocate = v.findViewById(R.id.tz_ride_locate);
      mTurn = v.findViewById(R.id.tz_ride_turn);
      mStats = v.findViewById(R.id.tz_ride_stats);
      mLocate.setOnClickListener(x -> TzMap.followRider(true));
    }
    case PAGE_SPEED ->
    {
      mSpeedHeader = v.findViewById(R.id.tz_ride_speed_header);
      mSpeedChip = v.findViewById(R.id.tz_ride_speed_chip);
      mHero = v.findViewById(R.id.tz_ride_hero);
      // The hero already fills the width: it does not grow with the font scale (dp, not sp).
      mHero.setTextSize(TypedValue.COMPLEX_UNIT_DIP, HERO_DP);
      TzTextSize.applyLineHeight(mHero, HERO_LINE_HEIGHT);
      mHeroUnit = v.findViewById(R.id.tz_ride_hero_unit);
      mGrid = v.findViewById(R.id.tz_ride_grid);
      final int[] ids = {R.id.tz_ride_cell_distance, R.id.tz_ride_cell_time, R.id.tz_ride_cell_elevation,
                         R.id.tz_ride_cell_grade, R.id.tz_ride_cell_avg, R.id.tz_ride_cell_max};
      final int[] labels = {R.string.tz_stat_distance, R.string.tz_stat_time, R.string.tz_stat_elevation,
                            R.string.tz_stat_grade, R.string.tz_stat_avg_speed, R.string.tz_stat_max_speed};
      mCells = new TzMetricView[ids.length];
      for (int i = 0; i < ids.length; i++)
      {
        final View cell = v.findViewById(ids[i]);
        ((TextView) cell.findViewById(R.id.tz_cell_label)).setText(labels[i]);
        mCells[i] = cell.findViewById(R.id.tz_cell_value);
      }
    }
    default ->
    {
      mClimbHeader = v.findViewById(R.id.tz_ride_climb_header);
      mClimbChip = v.findViewById(R.id.tz_ride_climb_chip);
      mProfile = v.findViewById(R.id.tz_ride_profile);
    }
    }
    layoutPages();
    render();
  }
}
