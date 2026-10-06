package app.trackzero.screens;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import app.trackzero.TzTrace;
import app.trackzero.base.TzMap;
import app.trackzero.base.TzRouteLayer;
import app.trackzero.follow.Climb;
import app.trackzero.follow.ClimbTracker;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.Guide;
import app.trackzero.follow.RideZoom;
import app.trackzero.follow.RoutedPath;
import app.trackzero.follow.TurnState;
import app.trackzero.format.TzFormat;
import app.trackzero.ride.RideSession;
import app.trackzero.ride.RideStats;
import app.trackzero.ride.TzRideService;
import app.trackzero.routes.Route;
import app.trackzero.routes.RouteStore;
import app.trackzero.ui.TzChip;
import app.trackzero.ui.RideBottomLayout;
import app.trackzero.ui.TzDisplay;
import app.trackzero.ui.TzElevationProfileView;
import app.trackzero.ui.TzHoldButton;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzPageHandle;
import app.trackzero.ui.TzSegmented;
import app.trackzero.ui.TzStatsIsland;
import app.trackzero.ui.TzTextSize;
import app.trackzero.ui.TzToggleRow;
import app.trackzero.ui.TzTurnCard;
import app.trackzero.ui.TzViews;
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
  /** An upward swipe starting this close to the bottom opens the controls... */
  private static final float SWIPE_UP_ZONE_DP = 120;
  /** ...when it is at least this fast (dp per second). */
  private static final float SWIPE_UP_MIN_DPS = 1000;
  /** A fix rendered this close to a tick makes the tick unnecessary. */
  private static final long TICK_SLACK_MS = 100;
  /** The rider arrow sits at 67 % of the screen height (R1 note). */
  private static final float RIDER_Y = 0.67f;
  private static final float PAUSED_HERO_ALPHA = 0.35f;
  private static final float PAUSED_GRID_ALPHA = 0.55f;
  private static final float HERO_DP = 208;
  /** Smallest hero on short screens (plan Phase 14). */
  private static final float HERO_MIN_DP = 160;
  private static final int GRID_ROWS = 3;
  /** R4 cell numbers (board: 50px). */
  private static final float CELL_NUMBER_SP = 50;
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
  /** The follow engine said off route (R3), wrong way (R3W), at the last render. */
  private boolean mOffRoute;
  private boolean mWrongWay;
  /** {@link Guide#linesVersion()} drawn; -1: none yet. */
  private int mLinesShown = -1;
  private boolean mEnding;
  private long mRenderedAtMs;
  private final RideZoom mZoom = new RideZoom();
  /** The trace has the first render with a session. */
  private boolean mTracedRender;
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
  private TzToggleRow mTones;
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
  private View mClimbContent;
  @Nullable
  private TextView mClimbTitle;
  @Nullable
  private TextView mClimbPill;
  @Nullable
  private View mClimbMetrics;
  @Nullable
  private TzMetricView[] mClimbCells;
  @Nullable
  private TzSegmented mClimbRange;
  @Nullable
  private TzElevationProfileView mProfile;
  @Nullable
  private TzStatsIsland mClimbStats;
  /** The data now in {@link #mProfile}: a route's climbs (their profile), else the ridden trace. */
  @Nullable
  private Object mProfileSource;
  /** The x range last asked of {@link #mProfile} (it animates there). */
  private double mRangeFrom = Double.NaN;
  private double mRangeTo = Double.NaN;

  private final ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder)
    {
      mService = ((TzRideService.LocalBinder) binder).service();
      if (TzTrace.on())
        TzTrace.log("ridescr", "onServiceConnected session=" + (mService.session() != null));
      mService.addListener(TzRideScreen.this);
      render();
    }

    @Override
    public void onServiceDisconnected(ComponentName name)
    {
      mService = null;
    }
  };

  /**
   * A quick upward swipe that starts near the bottom of a ride page opens the controls (Flow board), like dragging the
   * handle. Seen before normal dispatch, so the pager keeps its horizontal swipes and nothing is intercepted.
   */
  private final TzMap.TouchObserver mSwipeUp = new TzMap.TouchObserver() {
    @Nullable
    private GestureDetector mDetector;

    @Override
    public void onTouch(@NonNull MotionEvent e)
    {
      if (mDetector == null)
        mDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
          @Override
          public boolean onFling(@Nullable MotionEvent down, @NonNull MotionEvent up, float vx, float vy)
          {
            final View root = getView();
            if (down == null || root == null || mSheetBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED)
              return false;
            final float dp = getResources().getDisplayMetrics().density;
            final boolean fromBottom = down.getY() >= root.getHeight() - SWIPE_UP_ZONE_DP * dp;
            final boolean upward = vy < -SWIPE_UP_MIN_DPS * dp && Math.abs(vy) > 2 * Math.abs(vx);
            if (!fromBottom || !upward)
              return false;
            mSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            return true;
          }
        });
      mDetector.onTouchEvent(e);
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
    mTones = view.findViewById(R.id.tz_ride_tones);

    // The preview's line, handed over by START RIDE: shown until this screen has the route, and kept if it is the same.
    TzRouteLayer.transfer(TzRouteLayer.RIDE_START, this);
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
    mTones.setChecked(TzPrefs.tones(requireContext()), false);
    mTones.setListener(on -> TzPrefs.setTones(requireContext(), on));
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
      {
        applyBottomLayout();
        applyVisibleArea();
      }
    });
    super.onViewCreated(view, savedInstanceState);
  }

  private void setUpSheet()
  {
    mSheetBehavior = BottomSheetBehavior.from(mSheet);
    // The peek height is ours (applyBottomLayout): by default the behaviour raises it to the system gesture strip
    // plus 48dp, which put the handle into the stats island on gesture-navigation phones (BUG-003, D44).
    mSheetBehavior.setGestureInsetBottomIgnored(true);
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
    // S1 over the ride (Phase 30): the ride keeps running; Back comes here.
    mSheet.findViewById(R.id.tz_ride_stops).setOnClickListener(v -> {
      mSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
      TzNavigator.show(TzNavigator.Screen.STOPS, null);
    });
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
    if (TzTrace.on())
      TzTrace.log("ridescr", "onStart page=" + mPager.getCurrentItem());
    mBound = requireContext().bindService(new Intent(requireContext(), TzRideService.class), mConnection, 0);
    applyKeepAwake();
    TzDisplay.limitRefreshRate(requireActivity(), true);
    TzDisplay.setImmersive(requireActivity(), true);
    setPage(mPager.getCurrentItem());
    mHandler.post(mTick);
    TzMap.addTouchObserver(mSwipeUp);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    TzMap.removeTouchObserver(mSwipeUp);
    mHandler.removeCallbacks(mTick);
    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    TzDisplay.limitRefreshRate(requireActivity(), false);
    TzDisplay.setImmersive(requireActivity(), false);
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
    TzRouteLayer.hide(this);
    mLinesShown = -1;
    TzMap.clearVisibleArea(this);
    TzMap.forgetFollowRequest();
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
  public void onInsets(int top, int navigationBar)
  {
    // Immersive (FEAT-001): the map and the pages reach the bottom edge, and bars shown for a moment by a swipe go over
    // the ride, which does not move. What the rider touches (handle, RESUME, the sheet) stays above the system's
    // gesture strip, where touches are not reliably delivered with the bars hidden (D43). The top keeps the status
    // bar's height whether it shows or not: clear of a camera hole, and steady.
    mTopInset = top;
    mBottomInset = TzNavigator.gestureInset();
    applyBottomLayout();
    applyVisibleArea();
  }

  /**
   * The one place the ride's bottom is laid out (BUG-003): the handle, the islands, RESUME, the speed page and the
   * sheet's content, all from the same base. Runs on insets, when a page is bound and when the screen is resized.
   */
  private void applyBottomLayout()
  {
    final RideBottomLayout b = new RideBottomLayout(
        mBottomInset, getResources().getDimensionPixelSize(R.dimen.tz_handle_h),
        getResources().getDimensionPixelSize(R.dimen.tz_ride_above_handle),
        getResources().getDimensionPixelSize(R.dimen.tz_ride_resume_above_handle),
        getResources().getDimensionPixelSize(R.dimen.tz_space_16));
    mSheetBehavior.setPeekHeight(b.peek);
    mControls.setPadding(mControls.getPaddingLeft(), mControls.getPaddingTop(), mControls.getPaddingRight(),
                         b.controlsPadding);
    setBottomMargin(mResume, b.resumeMargin);
    final int top = mTopInset + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    if (mMapTop != null)
    {
      setTopMargin(mMapTop, top);
      setTopMargin(mTurn, top);
      setBottomMargin(mStats, b.islandMargin);
      // The engine's ruler and its OSM attribution go above the island instead of under the handle.
      TzMap.setRulerPosition(getResources().getDimensionPixelSize(R.dimen.tz_gutter),
                             b.islandMargin + getResources().getDimensionPixelSize(R.dimen.tz_stats_island_h)
                                 + getResources().getDimensionPixelSize(R.dimen.tz_space_12));
    }
    if (mSpeedHeader != null)
    {
      setTopMargin(mSpeedHeader, top);
      final View page = (View) mSpeedHeader.getParent();
      page.setPadding(0, 0, 0, b.pagePadding);
      page.post(this::fitGrid);
    }
    if (mClimbContent != null)
    {
      setTopMargin(mClimbContent, top);
      setBottomMargin(mClimbStats, b.islandMargin);
    }
  }

  private static void setBottomMargin(@NonNull View v, int px)
  {
    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
    if (lp.bottomMargin == px)
      return;
    lp.bottomMargin = px;
    v.setLayoutParams(lp);
  }

  /**
   * R4 on any screen (plan Phase 14: the grid shrinks, the hero may go down to 160, nothing is clipped):
   * 1. the grid's numbers scale with the cell width (the board's cells hold 166dp of text),
   * 2. the grid is 408dp when there is room (board: 3 rows of 136dp), else what is left down to its rows' natural
   *    height, and below that the hero gives way down to 160dp numerals,
   * 3. on the shortest screens the cells' vertical padding gives the rest.
   */
  private void fitGrid()
  {
    // Posted: the screen may have gone in between (a configuration change recreates it).
    if (!isAdded() || mGrid == null || mHero == null || mHeroUnit == null || mCells == null)
      return;
    final View page = (View) mGrid.getParent();
    final View heroBox = (View) mHero.getParent();
    final View cell = mGrid.findViewById(R.id.tz_ride_cell_distance);
    if (page.getHeight() == 0 || cell.getWidth() == 0)
      return;
    final float density = getResources().getDisplayMetrics().density;

    // 1. Numbers that fit the cell's width.
    final int padH = getResources().getDimensionPixelSize(R.dimen.tz_ride_cell_pad_h);
    final float widthScale = Math.min(1f, (cell.getWidth() - 2f * padH)
                                              / getResources().getDimension(R.dimen.tz_ride_cell_content_w));
    for (TzMetricView value : mCells)
      value.setNumberSize(CELL_NUMBER_SP * widthScale);
    final int padV = getResources().getDimensionPixelSize(R.dimen.tz_ride_cell_pad_v);
    setCellPadding(padH, padV);
    cell.measure(View.MeasureSpec.makeMeasureSpec(cell.getWidth(), View.MeasureSpec.EXACTLY),
                 View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    final int divider = getResources().getDimensionPixelSize(R.dimen.tz_divider);
    final int minGrid = GRID_ROWS * (cell.getMeasuredHeight() + divider);

    // 2. Grid first, then the hero.
    final int heroArea = getResources().getDimensionPixelSize(R.dimen.tz_ride_hero_area_h);
    final ViewGroup.MarginLayoutParams ulp = (ViewGroup.MarginLayoutParams) mHeroUnit.getLayoutParams();
    final int unitBlock = mHeroUnit.getHeight() + ulp.topMargin;
    final int heroMin = Math.round(HERO_MIN_DP * HERO_LINE_HEIGHT * density) + unitBlock;
    final int space = page.getHeight() - page.getPaddingBottom() - heroBox.getTop();
    final int boardGrid = getResources().getDimensionPixelSize(R.dimen.tz_ride_grid_h);
    final int hero = Math.max(heroMin, Math.min(heroArea, space - Math.min(boardGrid, Math.max(minGrid,
                                                                                             space - heroArea))));
    final int grid = Math.min(boardGrid, space - hero);
    final float heroDp = Math.max(HERO_MIN_DP, Math.min(HERO_DP, HERO_DP * (hero - unitBlock)
                                                                  / (heroArea - unitBlock)));

    // 3. Rows shorter than their content: less padding.
    if (grid < minGrid)
    {
      final int perCell = (int) Math.ceil((minGrid - grid) / (2.0 * GRID_ROWS));
      setCellPadding(padH, Math.max(getResources().getDimensionPixelSize(R.dimen.tz_space_4), padV - perCell));
    }

    final LinearLayout.LayoutParams hlp = (LinearLayout.LayoutParams) heroBox.getLayoutParams();
    final LinearLayout.LayoutParams glp = (LinearLayout.LayoutParams) mGrid.getLayoutParams();
    if (hlp.height != hero)
    {
      hlp.height = hero;
      heroBox.setLayoutParams(hlp);
    }
    if (glp.height != grid || glp.weight != 0)
    {
      glp.height = grid;
      glp.weight = 0;
      mGrid.setLayoutParams(glp);
    }
    if (Math.abs(mHero.getTextSize() - heroDp * density) > 1)
    {
      mHero.setTextSize(TypedValue.COMPLEX_UNIT_DIP, heroDp);
      TzTextSize.applyLineHeight(mHero, HERO_LINE_HEIGHT);
    }
  }

  /** R5: the profile is 230dp (board) unless that would run under the island on a short screen. */
  private void fitProfile()
  {
    if (!isAdded() || mProfile == null || mClimbContent == null || mClimbStats == null || mClimbStats.getTop() == 0)
      return;
    final int board = getResources().getDimensionPixelSize(R.dimen.tz_climb_profile_h);
    final int overlap = mClimbContent.getBottom() + getResources().getDimensionPixelSize(R.dimen.tz_space_16)
                      - mClimbStats.getTop();
    final ViewGroup.LayoutParams lp = mProfile.getLayoutParams();
    final int height = Math.max(getResources().getDimensionPixelSize(R.dimen.tz_climb_profile_min_h),
                                Math.min(board, lp.height - overlap));
    if (height == lp.height)
      return;
    lp.height = height;
    mProfile.setLayoutParams(lp);
  }

  private void setCellPadding(int horizontal, int vertical)
  {
    for (TzMetricView value : mCells)
    {
      final View cell = (View) value.getParent();
      cell.setPadding(horizontal, vertical, horizontal, vertical);
    }
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
    TzMap.setVisibleArea(this, Math.round(h * (2 * RIDER_Y - 1)), 0);
    // The ride camera places the rider in this area, so it goes out after it (BUG-004: it used the preview's area).
    if (mPage == PAGE_MAP && !mZoom.started())
    {
      final RideSession s = session();
      mZoom.setFoot(s != null ? s.profile().foot : TzPrefs.profile(requireContext()).foot);
      final Location here = TzMap.riderLocation();
      TzMap.startRideCamera(mZoom.start(here != null && here.hasSpeed() ? here.getSpeed() * 3.6 : 0,
                                        SystemClock.elapsedRealtime()), true);
    }
  }

  private void setPage(int page)
  {
    if (TzTrace.on())
      TzTrace.log("ridescr", "setPage " + page);
    mPage = page;
    mHandle.setPage(page, true);
    mHandle.setContentDescription(getString(R.string.tz_cd_page_handle, page + 1, TzPageHandle.PAGES));
    // Speed and elevation pages are opaque: no need to draw the map behind them.
    TzMap.setRenderingEnabled(page == PAGE_MAP);
    if (page == PAGE_MAP)
      showRider();
    mTraceSize = -1;
    // The REC dot pulses only where it is seen (the speed page stays attached beside the others).
    if (page != PAGE_SPEED && mSpeedChip != null)
      mSpeedChip.setPulsing(false);
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

  /** The clock runs on without fixes (no GPS, paused): render when no fix has for a second. */
  private void tick()
  {
    if (SystemClock.elapsedRealtime() - mRenderedAtMs >= REFRESH_MS - TICK_SLACK_MS)
      render();
    mHandler.postDelayed(mTick, REFRESH_MS);
  }

  // ---- Rendering: once a second and on every fix ----

  private void render()
  {
    final RideSession s = session();
    if (s == null || getView() == null || s.state() == RideSession.State.SAVED)
      return;
    if (TzTrace.on() && !mTracedRender)
    {
      mTracedRender = true;
      TzTrace.log("ridescr", "first render with a session, state=" + s.state() + " guide=" + (mService != null && mService.guide() != null));
    }
    loadRoute(s);
    mZoom.setFoot(s.profile().foot);

    final long now = SystemClock.elapsedRealtime();
    mRenderedAtMs = now;
    final RideStats st = s.stats();
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final RideSession.State state = s.state();
    final boolean paused = state != RideSession.State.RECORDING;

    mResume.setVisibility(state == RideSession.State.PAUSED && mSlide < 1 ? View.VISIBLE : View.GONE);
    // Turn announcements only exist on a route.
    mVoice.setVisibility(guided(s) ? View.VISIBLE : View.GONE);
    renderEstimate(s);
    // Tones are there on a free ride too: GPS lost.
    mTones.setSubtitle(getString(guided(s) ? R.string.tz_tones_subtitle : R.string.tz_tones_subtitle_free));
    renderPauseButton(state == RideSession.State.PAUSED);
    if (mSheetBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED)
      TzViews.setText(mSheetSummary,
                      getString(R.string.tz_ride_sheet_summary, TzFormat.duration(st.movingMs(now) / 1000),
                                TzFormat.distance(st.distanceM(), units, locale).toString()));

    switch (mPage)
    {
    case PAGE_MAP -> renderMap(s, st, now, units, locale, state == RideSession.State.PAUSED);
    case PAGE_SPEED -> renderSpeed(s, st, now, units, locale, paused);
    case PAGE_CLIMB -> renderClimb(s, st, units, locale);
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
    final boolean route = guided(s);
    // NO GPS (Phase 32): the warning card; on a free ride in place of the pill's row.
    final boolean noGps = mService != null && mService.gpsLost() && s.state() == RideSession.State.RECORDING;
    mPill.setVisibility(route || noGps ? View.GONE : View.VISIBLE);
    mLocate.setVisibility(route || noGps ? View.GONE : View.VISIBLE);
    final Guide guide = mService == null ? null : mService.guide();
    if (guide != null)
      showLines(s, guide);
    // Until the guide has placed the rider, the island shows the ride so far, as on a free ride.
    final boolean following = route && guide != null && guide.placed();
    mTurn.setVisibility(following || noGps ? View.VISIBLE : View.GONE);

    final TzFormat.Value speed = TzFormat.speed(st.speedKmh(now), units, locale);
    mStats.setCell(0, getString(R.string.tz_stat_speed), speed.number, speed.unit);
    if (following)
    {
      final TurnState turn = guide.turnState();
      final boolean off = turn.offRoute;
      // A warning (off route, wrong way) starts with a buzz; only off route dims the line (wrong way is still on it).
      if ((off && !mOffRoute) || (turn.wrongWay && !mWrongWay))
        mTurn.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.REJECT
                                                                                   : HapticFeedbackConstants.LONG_PRESS);
      if (off != mOffRoute)
        TzRouteLayer.setDimmed(off);
      mOffRoute = off;
      mWrongWay = turn.wrongWay;
      final TzFormat.Value rem = TzFormat.distance(guide.remainingM(), units, locale);
      if (off)
      {
        mStats.setCell(1, getString(R.string.tz_stat_eta), getString(R.string.tz_eta_unknown), "");
        mStats.setCell(2, getString(R.string.tz_stat_remaining), getString(R.string.tz_value_unknown), rem.unit);
      }
      else if (turn.wrongWay)
      {
        // R3W: no ETA while riding away from the end; the remaining distance as it was when the rider turned.
        mStats.setCell(1, getString(R.string.tz_stat_eta), getString(R.string.tz_eta_unknown), "");
        mStats.setCell(2, getString(R.string.tz_stat_remaining), rem.number, rem.unit);
      }
      else
      {
        final long eta = System.currentTimeMillis() + Math.round(guide.etaSeconds() * 1000);
        mStats.setCell(1, getString(R.string.tz_stat_eta),
                       TzFormat.clock(eta, TimeZone.getDefault(), DateFormat.is24HourFormat(requireContext())), "");
        mStats.setCell(2, getString(R.string.tz_stat_remaining), rem.number, rem.unit);
      }
      mTurn.setUnits(units);
      if (!noGps)
        mTurn.bind(turn);
    }
    else
    {
      final TzFormat.Value dist = TzFormat.distance(st.distanceM(), units, locale);
      mStats.setCell(1, getString(R.string.tz_stat_distance), dist.number, dist.unit);
      mStats.setCell(2, getString(R.string.tz_stat_time), TzFormat.duration(st.movingMs(now) / 1000), "");
    }
    if (noGps)
      mTurn.bindNoGps(Math.max(0, (now - mService.lastFixMs()) / 1000));
    // R7 on the map: the RESUME button takes the island's place, the island moves up over it.
    final float lift = manuallyPaused ? -(mResume.getHeight() + getResources().getDimension(R.dimen.tz_space_12)) : 0;
    mStats.setTranslationY(lift);
    autoZoom(st.speedKmh(now), now);
  }

  /**
   * Follows the rider heading-up. The first time on this screen, in one command at the zoom for the rider's speed, once
   * the screen has its size: no zoom of ours runs against the camera's move from wherever the map was (BUG-004).
   */
  private void showRider()
  {
    if (mZoom.started())
      TzMap.followRider(true);
    else
      applyVisibleArea();
  }

  private void autoZoom(double kmh, long now)
  {
    if (!TzMap.isFollowing())
      return;
    final int seen = TzMap.zoom();
    final int zoom = mZoom.update(kmh, seen, now);
    if (zoom == RideZoom.NONE)
      return;
    if (TzTrace.on())
      TzTrace.log("ridescr", "autoZoom " + seen + " -> " + zoom);
    TzMap.setZoom(zoom);
  }

  private void renderSpeed(@NonNull RideSession s, @NonNull RideStats st, long now, @NonNull TzFormat.Units units,
                           @NonNull Locale locale, boolean paused)
  {
    if (mHero == null)
      return;
    renderChip(mSpeedChip, s.state());
    final TzFormat.Value speed = TzFormat.speed(paused ? 0 : st.speedKmh(now), units, locale);
    TzViews.setText(mHero, speed.number);
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

  /** R5: the climb the rider is on or the next one; without climbs data, the elevation ridden. */
  private void renderClimb(@NonNull RideSession s, @NonNull RideStats st, @NonNull TzFormat.Units units,
                           @NonNull Locale locale)
  {
    if (mProfile == null)
      return;
    final ClimbTracker climbs = mService == null ? null : mService.climbs();
    final Guide guide = mService == null ? null : mService.guide();
    final FollowRoute line = guide == null ? null : guide.line();
    if (climbs == null || line == null || climbs.profile().length < 2)
    {
      renderElevation(s, st, units, locale);
      return;
    }
    final Climb c = climbs.shown();
    final int count = climbs.climbs().size();
    TzViews.setText(mClimbTitle, c == null ? getString(R.string.tz_no_climbs_ahead)
                                  : c.name != null ? c.name.toUpperCase(locale)
                                                   : getString(R.string.tz_climb_number, c.index));
    mClimbPill.setVisibility(c == null ? View.GONE : View.VISIBLE);
    if (c != null)
      TzViews.setText(mClimbPill, getString(R.string.tz_climb_pill, c.index, count));
    mClimbMetrics.setVisibility(View.VISIBLE);
    mClimbRange.setVisibility(c == null ? View.GONE : View.VISIBLE);

    // Metrics: live grade (mint: the one live reading), to the summit, climbed so far.
    mClimbCells[0].setValue(TzFormat.grade(st.gradePct(), locale));
    if (c == null)
    {
      mClimbCells[1].setValue(TzFormat.UNKNOWN, "");
      mClimbCells[2].setValue(TzFormat.UNKNOWN, "");
    }
    else
    {
      mClimbCells[1].setValue(TzFormat.distance(climbs.toSummitM(), units, locale));
      final TzFormat.Value gained = TzFormat.elevation(climbs.gainedM(), units, locale);
      mClimbCells[2].setValue("+" + gained.number, gained.unit);
    }

    // Profile: the route's, zoomed on the climb or the whole route.
    final float[] ele = climbs.profile();
    final double total = line.totalM();
    if (mProfileSource != climbs)
    {
      mProfileSource = climbs;
      final float[] dist = new float[ele.length];
      for (int i = 0; i < ele.length; i++)
        dist[i] = (float) Math.min(i * climbs.step(), total);
      mProfile.setProfile(dist, ele);
      mRangeFrom = mRangeTo = Double.NaN;
    }
    final boolean zoomed = c != null && mClimbRange.getSelected() == 0;
    final double from = zoomed ? c.startAt : 0;
    final double to = zoomed ? c.summitAt : total;
    if (from != mRangeFrom || to != mRangeTo)
    {
      // Animated when the rider switches the view or moves on to the next climb.
      mProfile.setRange((float) from, (float) to, !Double.isNaN(mRangeFrom));
      mRangeFrom = from;
      mRangeTo = to;
    }
    final TzFormat.Value length = TzFormat.distance(to - from, units, locale);
    mProfile.setAxisLabels(("0 " + length.unit).toUpperCase(locale), length.toString().toUpperCase(locale));
    mProfile.setRiderDistance((float) guide.along());
    if (c == null)
      mProfile.setSummit(Float.NaN, Float.NaN, null);
    else
      mProfile.setSummit((float) c.summitAt, (float) c.summitAlt,
                         TzFormat.elevation(c.summitAlt, units, locale).toString().toUpperCase(locale));

    // Island: altitude, time to the summit at the speed ridden on the climb, the climb's average grade.
    setAltitudeCell(st, units, locale);
    if (c == null)
    {
      final TzFormat.Value gain = TzFormat.elevation(st.gainM(), units, locale);
      mClimbStats.setCell(1, getString(R.string.tz_stat_gain), gain.number, gain.unit);
      final TzFormat.Value grade = TzFormat.grade(st.gradePct(), locale);
      mClimbStats.setCell(2, getString(R.string.tz_stat_grade), grade.number, grade.unit);
      return;
    }
    final double summitIn = climbs.summitInSeconds();
    mClimbStats.setCell(1, getString(R.string.tz_stat_summit_in),
                        Double.isNaN(summitIn) ? TzFormat.OFF_ROUTE : String.valueOf(Math.round(summitIn / 60)),
                        Double.isNaN(summitIn) ? "" : getString(R.string.tz_unit_min));
    final TzFormat.Value avg = TzFormat.grade(c.avgGradePct, locale);
    mClimbStats.setCell(2, getString(R.string.tz_stat_avg_grade), avg.number, avg.unit);
  }

  /** Free ride, or a route without elevation: the altitude ridden so far (plan §11.2, last case). */
  private void renderElevation(@NonNull RideSession s, @NonNull RideStats st, @NonNull TzFormat.Units units,
                               @NonNull Locale locale)
  {
    mClimbTitle.setText(R.string.tz_ride_elevation_title);
    mClimbPill.setVisibility(View.GONE);
    mClimbMetrics.setVisibility(View.GONE);
    mClimbRange.setVisibility(View.GONE);
    setAltitudeCell(st, units, locale);
    final TzFormat.Value gain = TzFormat.elevation(st.gainM(), units, locale);
    mClimbStats.setCell(1, getString(R.string.tz_stat_gain), gain.number, gain.unit);
    final TzFormat.Value grade = TzFormat.grade(st.gradePct(), locale);
    mClimbStats.setCell(2, getString(R.string.tz_stat_grade), grade.number, grade.unit);

    final float[] dist = s.traceDistances();
    // The trace grows every 10 m: redraw only when it did.
    if (mProfileSource == s && dist.length == mTraceSize)
      return;
    mProfileSource = s;
    mTraceSize = dist.length;
    mProfile.setSummit(Float.NaN, Float.NaN, null);
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

  private void setAltitudeCell(@NonNull RideStats st, @NonNull TzFormat.Units units, @NonNull Locale locale)
  {
    final double alt = st.altitudeM();
    if (Double.isNaN(alt))
    {
      mClimbStats.setCell(0, getString(R.string.tz_stat_altitude), TzFormat.UNKNOWN, "");
      return;
    }
    final TzFormat.Value v = TzFormat.elevation(alt, units, locale);
    mClimbStats.setCell(0, getString(R.string.tz_stat_altitude), v.number, v.unit);
  }

  /** R6's estimate (Phase 33): made once at the start, so it never changes during the ride. */
  private void renderEstimate(@NonNull RideSession s)
  {
    final View row = mSheet.findViewById(R.id.tz_ride_estimate);
    final boolean show = guided(s) && s.estimateS() >= 0;
    row.setVisibility(show ? View.VISIBLE : View.GONE);
    if (!show)
      return;
    final int basis = s.estimateBasis();
    TzViews.setText(row.findViewById(R.id.tz_ride_estimate_basis),
                    basis == 0 ? getString(R.string.tz_estimate_typical)
                               : getResources().getQuantityString(R.plurals.tz_estimate_from, basis, basis));
    TzViews.setText(row.findViewById(R.id.tz_ride_estimate_value), TzFormat.hoursMinutes(s.estimateS()));
  }

  /** A route ride or a ride to a place: turn-by-turn. */
  private static boolean guided(@NonNull RideSession s)
  {
    return s.routeId() != null || s.toPlace();
  }

  /**
   * The guide's lines when they change: the leg to the track (to its start, or back onto it), and the path of a ride
   * to a place (a track ride's own line comes from {@link #loadRoute}).
   */
  private void showLines(@NonNull RideSession s, @NonNull Guide guide)
  {
    if (guide.linesVersion() == mLinesShown)
      return;
    mLinesShown = guide.linesVersion();
    if (TzTrace.on())
      TzTrace.log("ridescr", "showLines v" + mLinesShown + " leg=" + (guide.legLine() != null) + " toPlace=" + s.toPlace());
    final FollowRoute leg = guide.legLine();
    if (leg == null)
      TzRouteLayer.hideLeg();
    else
      TzRouteLayer.showLeg(this, leg.lats(), leg.lons());
    final FollowRoute line = guide.line();
    if (!s.toPlace() || line == null)
      return;
    final RoutedPath path = guide.placePath();
    if (path != null && path.bikeAndWalk())
    {
      // Bike to where it is left, then dots to the place (Phase 27); all dots once walking.
      final double[][] ride = path.ridePart();
      final double[][] walk = path.walkPart();
      if (ride[0].length >= 2)
        TzRouteLayer.showRide(this, ride[0], ride[1], true);
      else
        TzRouteLayer.hide(this);
      TzRouteLayer.showWalk(this, walk[0], walk[1]);
    }
    else
    {
      TzRouteLayer.hideWalk();
      TzRouteLayer.showPath(this, line.lats(), line.lons(), true);
    }
  }

  /** A route ride draws its line and shows the route's numbers; loaded once per screen. */
  private void loadRoute(@NonNull RideSession s)
  {
    if (mRouteRequested || s.routeId() == null)
      return;
    mRouteRequested = true;
    if (TzTrace.on())
      TzTrace.log("ridescr", "loadRoute request " + s.routeId());
    final RouteStore store = RouteStore.get(requireContext());
    store.find(s.routeId(), route -> {
      if (TzTrace.on())
        TzTrace.log("ridescr", "loadRoute found=" + (route != null) + " view=" + (getView() != null));
      if (getView() == null || route == null)
        return;
      mRoute = route;
      store.loadGeometry(route, g -> {
        if (TzTrace.on())
          TzTrace.log("ridescr", "loadRoute geometry=" + (g != null) + " view=" + (getView() != null));
        if (getView() != null && g != null)
          TzRouteLayer.show(this, g.lineLat, g.lineLon, true);
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
      mClimbContent = v.findViewById(R.id.tz_ride_climb_content);
      mClimbTitle = v.findViewById(R.id.tz_ride_climb_title);
      mClimbPill = v.findViewById(R.id.tz_ride_climb_pill);
      mClimbMetrics = v.findViewById(R.id.tz_ride_climb_metrics);
      final int[] ids = {R.id.tz_ride_climb_grade, R.id.tz_ride_climb_to_summit, R.id.tz_ride_climb_gained};
      final int[] labels = {R.string.tz_stat_grade, R.string.tz_stat_to_summit, R.string.tz_stat_gained};
      mClimbCells = new TzMetricView[ids.length];
      for (int i = 0; i < ids.length; i++)
      {
        final View cell = v.findViewById(ids[i]);
        ((TextView) cell.findViewById(R.id.tz_cell_label)).setText(labels[i]);
        mClimbCells[i] = cell.findViewById(R.id.tz_cell_value);
      }
      mClimbCells[0].setNumberColor(ContextCompat.getColor(requireContext(), R.color.tz_mint));
      mClimbRange = v.findViewById(R.id.tz_ride_climb_range);
      mClimbRange.setOptions(getString(R.string.tz_climb_view_climb), getString(R.string.tz_climb_view_route));
      mClimbRange.setListener(i -> render());
      mProfile = v.findViewById(R.id.tz_ride_profile);
      mClimbStats = v.findViewById(R.id.tz_ride_climb_stats);
      // The content grows and shrinks with the mode (climb / elevation): the profile fits after each layout.
      mClimbContent.addOnLayoutChangeListener((x, l, t, r, b, ol, ot, or, ob) -> {
        if (b != ob)
          x.post(this::fitProfile);
      });
      mProfileSource = null;
      mRangeFrom = mRangeTo = Double.NaN;
    }
    }
    applyBottomLayout();
    render();
  }
}
