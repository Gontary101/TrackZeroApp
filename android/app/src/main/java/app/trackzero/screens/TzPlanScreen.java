package app.trackzero.screens;

import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.TzTrace;
import app.trackzero.base.TzLocation;
import app.trackzero.base.TzMap;
import app.trackzero.base.TzRoads;
import app.trackzero.base.TzRouteLayer;
import app.trackzero.base.TzRouter;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.RoutedPath;
import app.trackzero.format.TzFormat;
import app.trackzero.ride.Ride;
import app.trackzero.ride.RideEstimator;
import app.trackzero.ride.RideStore;
import app.trackzero.routes.GpxTrack;
import app.trackzero.routes.PlanDraft;
import app.trackzero.routes.Profile;
import app.trackzero.routes.RouteStats;
import app.trackzero.routes.RouteStore;
import app.trackzero.ui.TzMapTap;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzPinLayer;
import app.trackzero.ui.TzProfilePill;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * P1 · Plan a route and P2 · planning (FEAT-002, Phase 28): tap the map for a destination and points on the way, drag
 * pins, reorder the list, undo; the route is worked out 400 ms after each change for the profile chosen. PREVIEW makes
 * it a temporary route and opens the route preview, where it can be ridden or saved.
 */
public class TzPlanScreen extends TzScreen
    implements TzMap.TouchObserver, TzLocation.Listener, TzProfileSheet.Listener, TzPinLayer.Listener
{
  /** A destination to start from (B1's long-press). */
  public static final String ARG_LAT = "lat";
  public static final String ARG_LON = "lon";
  /** The route is worked out this long after the last change. */
  static final long ROUTE_DELAY_MS = 400;

  @NonNull
  private final PlanDraft mDraft = new PlanDraft();
  @NonNull
  private Profile mProfile = Profile.ROAD;
  @Nullable
  private RoutedPath mPath;
  /** The next tap sets the start (CHANGE). */
  private boolean mPickingStart;
  /** Routes asked for; an answer for an older one is dropped. */
  private int mRequest;
  private boolean mPreviewing;

  @Nullable
  private View mTop;
  @Nullable
  private FrameLayout mCard;
  @Nullable
  private TzPinLayer mPins;
  @Nullable
  private TzProfilePill mPill;
  @Nullable
  private ImageButton mUndo;
  @Nullable
  private TzMapTap mTap;
  @NonNull
  private final Points mPointsAdapter = new Points();
  @Nullable
  private ItemTouchHelper mReorder;
  private final Runnable mRoute = this::route;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_plan, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    // The past rides for TIME (Phase 33): read once; until then a typical pace.
    RideStore.get(requireContext()).list(rides -> {
      mRides = rides;
      if (getView() != null && mPath != null)
        showMetrics(mPath);
    });
    mTop = view.findViewById(R.id.tz_plan_top);
    mCard = view.findViewById(R.id.tz_plan_card);
    mPins = view.findViewById(R.id.tz_plan_pins);
    mPins.setListener(this);
    mPill = view.findViewById(R.id.tz_plan_profile_pill);
    mProfile = TzPrefs.profile(requireContext());
    mPill.setProfile(mProfile);
    mPill.setListener(v -> TzProfileSheet.show(this));
    mUndo = view.findViewById(R.id.tz_plan_undo);
    mUndo.setOnClickListener(v -> {
      if (!mDraft.canUndo())
        return;
      mDraft.undo();
      changed();
    });
    view.findViewById(R.id.tz_plan_back).setOnClickListener(v -> back());
    view.findViewById(R.id.tz_plan_start_change).setOnClickListener(v -> {
      mPickingStart = true;
      ((TextView) requireView().findViewById(R.id.tz_plan_empty_hint)).setText(R.string.tz_plan_pick_start);
    });
    view.findViewById(R.id.tz_plan_preview).setOnClickListener(v -> preview());

    final RecyclerView list = view.findViewById(R.id.tz_plan_points);
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    list.setAdapter(mPointsAdapter);
    mReorder = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
      @Override
      public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                            @NonNull RecyclerView.ViewHolder to)
      {
        mPointsAdapter.notifyItemMoved(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
        return true;
      }

      @Override
      public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {}

      @Override
      public boolean isLongPressDragEnabled()
      {
        return false; // From the handle only.
      }

      private int mFrom = -1;

      @Override
      public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int state)
      {
        super.onSelectedChanged(vh, state);
        if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null)
          mFrom = vh.getBindingAdapterPosition();
      }

      @Override
      public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh)
      {
        super.clearView(rv, vh);
        final int to = vh.getBindingAdapterPosition();
        if (mFrom >= 0 && to >= 0 && to != mFrom)
        {
          mDraft.reorder(mFrom, to);
          changed();
        }
        mFrom = -1;
      }
    });
    mReorder.attachToRecyclerView(list);

    mTap = new TzMapTap(requireContext(), new TzMapTap.Listener() {
      @Override
      public void onTap(float x, float y)
      {
        onMapTap(x, y);
      }
    }, this::onChrome);

    final Bundle args = getArguments();
    if (args != null && args.containsKey(ARG_LAT))
      mDraft.tap(args.getDouble(ARG_LAT), args.getDouble(ARG_LON));
    changed();
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    TzMap.addTouchObserver(this);
    if (mTap != null)
      TzMap.addTouchObserver(mTap);
    // The route starts where the rider is: a first position may still be coming.
    TzLocation.addListener(requireContext(), this);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    TzMap.removeTouchObserver(this);
    if (mTap != null)
    {
      TzMap.removeTouchObserver(mTap);
      mTap.cancel();
    }
    TzLocation.removeListener(requireContext(), this);
    if (mCard != null)
      mCard.removeCallbacks(mRoute);
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    TzRouteLayer.hide(this);
    // Leaving the planner (unless for its preview, which keeps nothing of the router either): free it.
    TzRouter.release();
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    ((FrameLayout.LayoutParams) mTop.getLayoutParams()).topMargin =
        top + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    mTop.requestLayout();
    ((FrameLayout.LayoutParams) mCard.getLayoutParams()).bottomMargin =
        bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra);
    mCard.requestLayout();
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

  /** Back to the routes; with points placed, after "Discard this plan?". */
  private void back()
  {
    if (mDraft.isEmpty())
    {
      TzNavigator.show(TzNavigator.Screen.ROUTES, null);
      return;
    }
    new MaterialAlertDialogBuilder(requireContext())
        .setMessage(R.string.tz_plan_discard)
        .setNegativeButton(R.string.tz_cancel, null)
        .setPositiveButton(R.string.tz_plan_discard_ok, (d, w) -> TzNavigator.show(TzNavigator.Screen.ROUTES, null))
        .show();
  }

  // ---- The map ----

  @Override
  public void onTouch(@NonNull MotionEvent e)
  {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN && mPins != null)
      mPins.onMapTouched();
  }

  /** A touch on the screen's own views (top row, card, pins) is not a tap on the map. */
  private boolean onChrome(@NonNull MotionEvent e)
  {
    return hit(mTop, e) || hit(mCard, e) || (mPins != null && mPins.hit(e));
  }

  private static boolean hit(@Nullable View v, @NonNull MotionEvent e)
  {
    if (v == null || v.getVisibility() != View.VISIBLE)
      return false;
    final Rect r = new Rect();
    v.getGlobalVisibleRect(r);
    return r.contains((int) e.getRawX(), (int) e.getRawY());
  }

  private void onMapTap(float x, float y)
  {
    final double[] ll = TzMap.screenToLatLon(x, y);
    if (TzTrace.on())
      TzTrace.log("plan", "tap " + TzTrace.ll(ll[0], ll[1]) + (mPickingStart ? " start" : ""));
    if (mPickingStart)
    {
      mPickingStart = false;
      mDraft.setStart(new PlanDraft.Point(ll[0], ll[1], null));
      ((TextView) requireView().findViewById(R.id.tz_plan_empty_hint)).setText(R.string.tz_plan_empty_hint);
    }
    else if (!mDraft.tap(ll[0], ll[1]))
    {
      ((TextView) requireView().findViewById(R.id.tz_plan_hint)).setText(R.string.tz_plan_full);
      return;
    }
    changed();
  }

  @Override
  public void onPinTapped(int index)
  {
    mDraft.remove(index);
    changed();
  }

  @Override
  public void onPinMoved(int index, double lat, double lon)
  {
    mDraft.move(index, lat, lon);
    changed();
  }

  @Override
  public void onLocation(@NonNull Location here)
  {
    // The first position, if the plan waited for one.
    if (mPath == null && mDraft.start() == null && !mDraft.isEmpty())
      scheduleRoute();
    TzLocation.removeListener(requireContext(), this);
  }

  @Override
  public void onProfileChosen(@NonNull Profile profile)
  {
    if (profile == mProfile)
      return;
    mProfile = profile;
    if (mPill != null)
      mPill.setProfile(profile);
    scheduleRoute();
  }

  // ---- The plan ----

  /** After every change: the card, the pins, the names, and the route a moment later. */
  private void changed()
  {
    final View v = getView();
    if (v == null)
      return;
    final boolean empty = mDraft.isEmpty();
    v.findViewById(R.id.tz_plan_empty).setVisibility(empty ? View.VISIBLE : View.GONE);
    v.findViewById(R.id.tz_plan_full).setVisibility(empty ? View.GONE : View.VISIBLE);
    final boolean canUndo = mDraft.canUndo();
    ImageViewCompat.setImageTintList(mUndo, ContextCompat.getColorStateList(requireContext(),
                                                                            canUndo ? R.color.tz_text1
                                                                                    : R.color.tz_text3));
    mUndo.setEnabled(canUndo);
    final PlanDraft.Point start = mDraft.start();
    ((TextView) v.findViewById(R.id.tz_plan_start_text)).setText(
        start == null ? getString(R.string.tz_plan_from_here)
                      : start.name != null && !start.name.isEmpty() ? start.name : getString(R.string.tz_plan_picked_point));
    ((TextView) v.findViewById(R.id.tz_plan_hint)).setText(R.string.tz_plan_hint);
    mPins.setPoints(mDraft.points(), start);
    mPointsAdapter.notifyDataSetChanged();
    lookUpNames();
    mPath = null;
    showMetrics(null);
    if (empty)
      TzRouteLayer.hide(this);
    scheduleRoute();
  }

  /** Street names for points (and a picked start) that have none yet. */
  private void lookUpNames()
  {
    final List<PlanDraft.Point> points = mDraft.points();
    for (int i = 0; i < points.size(); i++)
    {
      final PlanDraft.Point p = points.get(i);
      if (p.name != null)
        continue;
      final int index = i;
      TzRoads.nameAt(requireContext(), new double[] {p.lat}, new double[] {p.lon}, name -> {
        if (getView() == null || name == null || name.isEmpty())
          return;
        mDraft.name(index, p.lat, p.lon, name);
        mPointsAdapter.notifyDataSetChanged();
      });
    }
    final PlanDraft.Point start = mDraft.start();
    if (start != null && start.name == null)
      TzRoads.nameAt(requireContext(), new double[] {start.lat}, new double[] {start.lon}, name -> {
        if (getView() == null || name == null || name.isEmpty())
          return;
        mDraft.nameStart(start.lat, start.lon, name);
        ((TextView) requireView().findViewById(R.id.tz_plan_start_text)).setText(name);
      });
  }

  private void scheduleRoute()
  {
    if (mCard == null)
      return;
    mCard.removeCallbacks(mRoute);
    mRequest++;
    TzRouter.cancel();
    if (!mDraft.isEmpty())
      mCard.postDelayed(mRoute, ROUTE_DELAY_MS);
  }

  /** One path through the start, the points on the way and the destination, for the profile. */
  private void route()
  {
    if (getView() == null || mDraft.isEmpty())
      return;
    final PlanDraft.Point start = mDraft.start();
    final Location here = start == null ? TzLocation.last(requireContext()) : null;
    if (start == null && here == null)
    {
      ((TextView) requireView().findViewById(R.id.tz_plan_hint)).setText(R.string.tz_plan_no_gps);
      return;
    }
    final List<PlanDraft.Point> points = mDraft.points();
    final double[] lat = new double[points.size() + 1];
    final double[] lon = new double[points.size() + 1];
    lat[0] = start != null ? start.lat : here.getLatitude();
    lon[0] = start != null ? start.lon : here.getLongitude();
    for (int i = 0; i < points.size(); i++)
    {
      lat[i + 1] = points.get(i).lat;
      lon[i + 1] = points.get(i).lon;
    }
    final int request = ++mRequest;
    TzRouter.route(lat, lon, mProfile, Double.NaN, (path, reason) -> {
      if (request != mRequest || getView() == null)
        return;
      if (path == null)
      {
        TzRouteLayer.hide(this);
        ((TextView) requireView().findViewById(R.id.tz_plan_hint)).setText(
            reason == TzRouter.Reason.NO_MAP ? R.string.tz_plan_no_map : R.string.tz_plan_no_way);
        return;
      }
      mPath = path;
      // The pins mark the points; the line has no ring of its own.
      TzRouteLayer.showRide(this, path.line.lats(), path.line.lons(), false);
      showMetrics(path);
    });
  }

  @NonNull
  private List<Ride> mRides = new ArrayList<>();

  /** DISTANCE, GAIN, TIME of the path; dashes while there is none. */
  private void showMetrics(@Nullable RoutedPath path)
  {
    final View v = requireView();
    final TzMetricView distance = v.findViewById(R.id.tz_plan_stat0);
    final TzMetricView gain = v.findViewById(R.id.tz_plan_stat1);
    final TzMetricView time = v.findViewById(R.id.tz_plan_stat2);
    v.findViewById(R.id.tz_plan_preview).setEnabled(path != null);
    if (path == null)
    {
      distance.setValue(TzFormat.UNKNOWN, "");
      gain.setValue(TzFormat.UNKNOWN, "");
      time.setValue(TzFormat.UNKNOWN, "");
      return;
    }
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final FollowRoute line = path.line;
    distance.setValue(TzFormat.distance(line.totalM(), units, locale));
    final double[] upDown = path.ele == null ? null : RouteStats.ascentDescent(path.ele);
    if (upDown == null)
      gain.setValue(TzFormat.UNKNOWN, "");
    else
      gain.setValue(TzFormat.elevation(upDown[0], units, locale));
    time.setValue(TzFormat.hoursMinutes(timeSeconds(line.totalM(), upDown)), "");
  }

  /** The rider's own estimate (Phase 33, the same as a ride's), from their past rides once those are read. */
  private long timeSeconds(double distanceM, @Nullable double[] upDown)
  {
    final double up = upDown == null ? Double.NaN : upDown[0];
    final double down = upDown == null ? Double.NaN : upDown[1];
    return RideEstimator.estimate(mProfile, distanceM, up, down, mRides).totalS;
  }

  /** The plan as the temporary route, then the route preview on it. */
  private void preview()
  {
    final RoutedPath path = mPath;
    if (path == null || mPreviewing)
      return;
    mPreviewing = true;
    final PlanDraft.Point destination = mDraft.destination();
    final String name = destination != null && destination.name != null && !destination.name.isEmpty()
                        ? getString(R.string.tz_plan_name_to, destination.name)
                        : getString(R.string.tz_plan_name_date, DateFormat.getMediumDateFormat(requireContext())
                                                                    .format(new Date()));
    final List<GpxTrack.Waypoint> points = new ArrayList<>();
    for (PlanDraft.Point p : mDraft.points())
      points.add(new GpxTrack.Waypoint(p.lat, p.lon, p.name));
    RouteStore.get(requireContext()).putTemp(name, path, mProfile, points, route -> {
      mPreviewing = false;
      if (route == null || getView() == null)
        return;
      final Bundle args = new Bundle();
      args.putString(TzRoutesScreen.ARG_ROUTE_ID, route.id);
      TzNavigator.show(TzNavigator.Screen.PREVIEW, args);
    });
  }

  // ---- The point rows ----

  private final class Points extends RecyclerView.Adapter<Points.Row>
  {
    final class Row extends RecyclerView.ViewHolder
    {
      final TextView number;
      final ImageView flag;
      final TextView name;

      Row(@NonNull View v)
      {
        super(v);
        number = v.findViewById(R.id.tz_plan_row_number);
        flag = v.findViewById(R.id.tz_plan_row_flag);
        name = v.findViewById(R.id.tz_plan_row_name);
        v.findViewById(R.id.tz_plan_row_remove).setOnClickListener(x -> {
          final int i = getBindingAdapterPosition();
          if (i < 0)
            return;
          mDraft.remove(i);
          changed();
        });
        v.findViewById(R.id.tz_plan_row_drag).setOnTouchListener((x, e) -> {
          if (e.getActionMasked() == MotionEvent.ACTION_DOWN && mReorder != null)
            mReorder.startDrag(this);
          return false;
        });
      }
    }

    @NonNull
    @Override
    public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.tz_plan_point_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Row row, int i)
    {
      final List<PlanDraft.Point> points = mDraft.points();
      final boolean destination = i == points.size() - 1;
      row.number.setVisibility(destination ? View.GONE : View.VISIBLE);
      row.flag.setVisibility(destination ? View.VISIBLE : View.GONE);
      row.number.setText(String.valueOf(i + 1));
      final PlanDraft.Point p = points.get(i);
      row.name.setText(p.name != null && !p.name.isEmpty() ? p.name : getString(R.string.tz_plan_point, i + 1));
    }

    @Override
    public int getItemCount()
    {
      return mDraft.points().size();
    }
  }
}
