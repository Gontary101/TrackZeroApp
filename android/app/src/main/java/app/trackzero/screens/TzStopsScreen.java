package app.trackzero.screens;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.TzTrace;
import app.trackzero.base.TzPois;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.Guide;
import app.trackzero.follow.Stop;
import app.trackzero.follow.Stops;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.DouglasPeucker;
import app.trackzero.ride.RideSession;
import app.trackzero.ride.TzFix;
import app.trackzero.ride.TzRideService;
import app.trackzero.routes.RouteStore;
import app.trackzero.ui.TzCategoryChip;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzStopKinds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * S1 · Stops (FEAT-007, Phase 30): water or bicycle repair from the offline map. From the ride (R6 STOPS): within
 * 1 km of the next 40 km of the route, a row opens S2; on a free ride, within 5 km of the rider. From a route preview
 * (B5's pin): along the whole route, a row shows the place on the preview. Searched when the screen opens or the
 * chip changes; nothing runs otherwise.
 */
public class TzStopsScreen extends TzScreen
{
  /** Opened from B5 for this route; absent: from the ride. */
  public static final String ARG_ROUTE_ID = TzRoutesScreen.ARG_ROUTE_ID;
  static final String ARG_CATEGORY = "stopsCategory";

  private int mCategory = Stops.WATER;
  /** Answers to an older search are dropped. */
  private int mSearch;
  @Nullable
  private String mRouteId;
  @Nullable
  private FollowRoute mPreviewRoute;
  private final RideBinding mRide = new RideBinding(service -> search());

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_stops, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    final Bundle args = getArguments();
    mRouteId = args == null ? null : args.getString(ARG_ROUTE_ID);
    mCategory = args == null ? Stops.WATER : args.getInt(ARG_CATEGORY, Stops.WATER);
    view.findViewById(R.id.tz_stops_back).setOnClickListener(v -> back());
    final TzCategoryChip water = view.findViewById(R.id.tz_stops_chip_water);
    final TzCategoryChip repair = view.findViewById(R.id.tz_stops_chip_repair);
    final TzCategoryChip bivouac = view.findViewById(R.id.tz_stops_chip_bivouac);
    water.setIcon(R.drawable.ic_tz_water);
    repair.setIcon(R.drawable.ic_tz_wrench);
    bivouac.setIcon(R.drawable.ic_tz_tent);
    water.setOnClickListener(v -> choose(Stops.WATER));
    repair.setOnClickListener(v -> choose(Stops.REPAIR));
    bivouac.setOnClickListener(v -> choose(Stops.BIVOUAC));
    bindChips();
    if (mRouteId != null)
    {
      final RouteStore store = RouteStore.get(requireContext());
      store.find(mRouteId, route -> {
        if (route == null || getView() == null)
          return;
        store.loadGeometry(route, g -> {
          if (g == null || g.lineLat.length < 2 || getView() == null)
            return;
          mPreviewRoute = new FollowRoute(g.lineLat, g.lineLon);
          search();
        });
      });
    }
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    if (mRouteId == null)
      mRide.bind(requireContext());
  }

  @Override
  public void onStop()
  {
    super.onStop();
    mRide.unbind(requireContext());
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    final View v = requireView();
    v.setPadding(0, top + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra), 0, 0);
    final View scroll = v.findViewById(R.id.tz_stops_scroll);
    scroll.setPadding(0, 0, 0, bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra));
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
    if (mRouteId != null)
    {
      final Bundle args = new Bundle();
      args.putString(TzRoutesScreen.ARG_ROUTE_ID, mRouteId);
      TzNavigator.show(TzNavigator.Screen.PREVIEW, args);
    }
    else
    {
      TzNavigator.show(TzNavigator.Screen.RIDE, null);
    }
  }

  private void choose(int category)
  {
    if (category == mCategory)
      return;
    mCategory = category;
    bindChips();
    search();
  }

  private void bindChips()
  {
    final View v = requireView();
    ((TzCategoryChip) v.findViewById(R.id.tz_stops_chip_water)).setChecked(mCategory == Stops.WATER);
    ((TzCategoryChip) v.findViewById(R.id.tz_stops_chip_repair)).setChecked(mCategory == Stops.REPAIR);
    ((TzCategoryChip) v.findViewById(R.id.tz_stops_chip_bivouac)).setChecked(mCategory == Stops.BIVOUAC);
  }

  // ---- The search ----

  private void search()
  {
    final View v = getView();
    if (v == null)
      return;
    final int search = ++mSearch;
    final int category = mCategory;
    final TextView scope = v.findViewById(R.id.tz_stops_scope);
    ((LinearLayout) v.findViewById(R.id.tz_stops_rows)).removeAllViews();
    setNote(getString(R.string.tz_stops_searching));
    if (mRouteId != null)
    {
      final FollowRoute route = mPreviewRoute;
      if (route == null)
        return;
      scope.setText(R.string.tz_stops_scope_route);
      final int[] keep = DouglasPeucker.simplifyToMaxPoints(route.lats(), route.lons(), 2000);
      final double[] lat = new double[keep.length], lon = new double[keep.length];
      for (int i = 0; i < keep.length; i++)
      {
        lat[i] = route.lats()[keep[i]];
        lon[i] = route.lons()[keep[i]];
      }
      TzPois.along(requireContext(), lat, lon, Stops.MAX_OFF_ROUTE_M, category, places -> {
        if (search == mSearch && getView() != null)
          show(Stops.along(route, places, 0, route.totalM()), true);
      });
      return;
    }
    final TzRideService service = mRide.service();
    final RideSession session = service == null ? null : service.session();
    if (session == null)
      return;
    final Guide guide = service.guide();
    final FollowRoute route = guide == null ? null : guide.line();
    if (route != null)
    {
      scope.setText(R.string.tz_stops_scope_ride);
      final double from = guide.along();
      final double to = from + Stops.RIDE_AHEAD_M;
      final double[][] part = TzPois.slice(route, from, to);
      final long started = System.nanoTime();
      TzPois.along(requireContext(), part[0], part[1], Stops.MAX_OFF_ROUTE_M, category, places -> {
        if (TzTrace.on())
          TzTrace.log("stops", "ride search " + category + ": " + places.size() + " places in "
                               + (System.nanoTime() - started) / 1_000_000 + " ms");
        if (search == mSearch && getView() != null)
          show(Stops.along(route, places, from, to), true);
      });
      return;
    }
    scope.setText(R.string.tz_stops_scope_free);
    final TzFix fix = session.lastFix();
    if (fix == null)
    {
      setNote(getString(R.string.tz_stops_no_gps));
      return;
    }
    final double lat = fix.lat;
    final double lon = fix.lon;
    TzPois.along(requireContext(), new double[] {lat, lat}, new double[] {lon, lon}, Stops.FREE_RADIUS_M, category,
                 places -> {
                   if (search == mSearch && getView() != null)
                     show(Stops.near(lat, lon, places), false);
                 });
  }

  private void setNote(@NonNull String text)
  {
    ((TextView) requireView().findViewById(R.id.tz_stops_note)).setText(text);
  }

  private void show(@NonNull List<Stops.Row> rows, boolean onRoute)
  {
    final LinearLayout list = requireView().findViewById(R.id.tz_stops_rows);
    list.removeAllViews();
    if (rows.isEmpty())
      setNote(getString(onRoute ? R.string.tz_stops_empty_route : R.string.tz_stops_empty_free));
    else
      setNote(mCategory == Stops.BIVOUAC ? getString(R.string.tz_bivouac_footer) : "");
    final List<Stop> added = addedStops();
    final TzFormat.Units units = TzPrefs.units(requireContext());
    final Locale locale = Locale.getDefault();
    final LayoutInflater inflater = LayoutInflater.from(requireContext());
    for (Stops.Row row : rows)
    {
      final View v = inflater.inflate(R.layout.tz_stop_row, list, false);
      final boolean isAdded = contains(added, row.stop);
      final ImageView icon = v.findViewById(R.id.tz_stop_row_icon);
      icon.setImageResource(isAdded ? R.drawable.ic_tz_check : TzStopKinds.icon(row.stop.kind));
      ImageViewCompat.setImageTintList(
          icon, ContextCompat.getColorStateList(requireContext(), isAdded ? R.color.tz_mint : R.color.tz_text2));
      ((TextView) v.findViewById(R.id.tz_stop_row_name)).setText(TzStopKinds.name(requireContext(), row.stop));
      // An unnamed place is called by its kind already: the subtitle only says where it is.
      final String where = where(row, units, locale);
      ((TextView) v.findViewById(R.id.tz_stop_row_subtitle))
          .setText(row.stop.name.isEmpty()
                       ? where
                       : getString(R.string.tz_stop_kind_where, getString(TzStopKinds.label(row.stop.kind)), where));
      TzStopKinds.bindBadge(v.findViewById(R.id.tz_stop_row_badge), row.stop);
      final List<String> lines = new ArrayList<>();
      if (isAdded)
        lines.add(getString(R.string.tz_stop_added));
      if (row.stop.kind == Stop.SPRING)
        lines.add(getString(R.string.tz_stop_spring_note));
      final String bivouac = TzStopKinds.bivouacNote(requireContext(), row.stop);
      if (!bivouac.isEmpty())
        lines.add(bivouac);
      final TextView note = v.findViewById(R.id.tz_stop_row_note);
      note.setText(TextUtils.join("\n", lines));
      note.setVisibility(lines.isEmpty() ? View.GONE : View.VISIBLE);
      ((TzMetricView) v.findViewById(R.id.tz_stop_row_distance))
          .setValue(TzFormat.distance(row.distanceM, units, locale));
      v.setOnClickListener(x -> open(row));
      list.addView(v);
    }
  }

  @NonNull
  private String where(@NonNull Stops.Row row, @NonNull TzFormat.Units units, @NonNull Locale locale)
  {
    if (Double.isNaN(row.offRouteM))
      return getString(R.string.tz_stop_away, TzFormat.distance(row.distanceM, units, locale).toString());
    if (row.onRoute())
      return getString(R.string.tz_stop_on_route);
    return getString(R.string.tz_stop_off_route, TzFormat.distance(row.offRouteM, units, locale).toString());
  }

  @NonNull
  private List<Stop> addedStops()
  {
    final TzRideService service = mRide.service();
    final Guide guide = service == null ? null : service.guide();
    return guide == null ? new ArrayList<>() : guide.stops();
  }

  private static boolean contains(@NonNull List<Stop> stops, @NonNull Stop s)
  {
    for (Stop o : stops)
      if (o.same(s))
        return true;
    return false;
  }

  private void open(@NonNull Stops.Row row)
  {
    final Bundle args = new Bundle();
    args.putString(TzStopScreen.ARG_NAME, TzStopKinds.name(requireContext(), row.stop));
    args.putInt(TzStopScreen.ARG_KIND, row.stop.kind);
    args.putString(TzStopScreen.ARG_HOURS, row.stop.hours);
    args.putInt(TzStopScreen.ARG_FLAGS, row.stop.flags);
    args.putDouble(TzStopScreen.ARG_LAT, row.stop.lat);
    args.putDouble(TzStopScreen.ARG_LON, row.stop.lon);
    args.putDouble(TzStopScreen.ARG_ALONG, row.alongM);
    if (mRouteId != null)
    {
      // B5: the place on the preview; nothing is added before a ride.
      args.putString(TzRoutesScreen.ARG_ROUTE_ID, mRouteId);
      TzNavigator.show(TzNavigator.Screen.PREVIEW, args);
      return;
    }
    args.putDouble(TzStopScreen.ARG_OFF_ROUTE, row.offRouteM);
    args.putDouble(TzStopScreen.ARG_DISTANCE, row.distanceM);
    args.putInt(ARG_CATEGORY, mCategory);
    TzNavigator.show(TzNavigator.Screen.STOP, args);
  }
}
