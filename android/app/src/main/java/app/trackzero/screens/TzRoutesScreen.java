package app.trackzero.screens;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzBase;
import app.trackzero.format.TzFormat;
import app.trackzero.routes.Route;
import app.trackzero.routes.RouteStats;
import app.trackzero.routes.RouteStore;
import app.trackzero.routes.TzImport;
import app.trackzero.ui.TzProfiles;
import app.trackzero.ui.TzRouteCard;
import app.trackzero.ui.TzSegmented;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** B3 · Routes (library) and B4 · Routes · empty. */
public class TzRoutesScreen extends TzScreen implements TzNewRouteSheet.Listener
{
  public static final String ARG_ROUTE_ID = "routeId";

  private static final int FILTER_ALL = 0;
  private static final int FILTER_FAVORITES = 1;
  private static final int FILTER_RECENT = 2;
  private static final long RECENT_MS = 30L * 24 * 3600 * 1000;

  private final Adapter mAdapter = new Adapter();
  private final Runnable mOnChanged = this::reload;
  @Nullable
  private ActivityResultLauncher<String[]> mPicker;
  @NonNull
  private List<Route> mAll = new ArrayList<>();
  private int mFilter = FILTER_ALL;

  @Nullable
  private View mContent;
  @Nullable
  private View mImport;
  @Nullable
  private View mLibrary;
  @Nullable
  private View mEmpty;
  @Nullable
  private TextView mCaption;
  @Nullable
  private TextView mFilterEmpty;
  @Nullable
  private RecyclerView mList;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    mPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
      if (uri != null)
        TzImport.importUri(requireContext(), uri);
    });
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_routes, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mContent = view.findViewById(R.id.tz_routes_content);
    mImport = view.findViewById(R.id.tz_routes_import);
    mLibrary = view.findViewById(R.id.tz_routes_library);
    mEmpty = view.findViewById(R.id.tz_routes_empty);
    mCaption = view.findViewById(R.id.tz_routes_caption);
    mFilterEmpty = view.findViewById(R.id.tz_routes_filter_empty);
    mList = view.findViewById(R.id.tz_routes_list);

    mImport.setOnClickListener(v -> TzNewRouteSheet.show(this));
    view.findViewById(R.id.tz_routes_empty_import).setOnClickListener(v -> TzNewRouteSheet.show(this));

    final TzSegmented filter = view.findViewById(R.id.tz_routes_filter);
    filter.setOptions(getString(R.string.tz_filter_all), getString(R.string.tz_filter_favorites),
                      getString(R.string.tz_filter_recent));
    filter.select(mFilter);
    filter.setListener(index -> {
      mFilter = index;
      applyFilter();
    });

    mList.setLayoutManager(new LinearLayoutManager(requireContext()));
    mList.setAdapter(mAdapter);
    final int gap = getResources().getDimensionPixelSize(R.dimen.tz_space_12);
    mList.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets(@NonNull Rect out, @NonNull View v, @NonNull RecyclerView parent,
                                 @NonNull RecyclerView.State state)
      {
        if (parent.getChildAdapterPosition(v) > 0)
          out.top = gap;
      }
    });
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    RouteStore.get(requireContext()).addListener(mOnChanged);
    reload();
  }

  @Override
  public void onStop()
  {
    super.onStop();
    RouteStore.get(requireContext()).removeListener(mOnChanged);
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    mContent.setPadding(0, top + getResources().getDimensionPixelSize(R.dimen.tz_space_16), 0, 0);
    // The last card clears the island with 32dp to spare.
    final int islandSpace = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                          + getResources().getDimensionPixelSize(R.dimen.tz_island_h)
                          + getResources().getDimensionPixelSize(R.dimen.tz_space_32);
    mList.setPadding(mList.getPaddingLeft(), 0, mList.getPaddingRight(), islandSpace);
  }

  @Override
  public void onImportChosen()
  {
    pickGpx();
  }

  @Override
  public void onPlanChosen()
  {
    TzNavigator.show(TzNavigator.Screen.PLAN, null);
  }

  @Override
  public void onLoopChosen()
  {
    TzNavigator.show(TzNavigator.Screen.LOOP, null);
  }

  private void pickGpx()
  {
    if (mPicker != null)
      mPicker.launch(TzImport.PICKER_TYPES);
  }

  private void reload()
  {
    RouteStore.get(requireContext()).list(routes -> {
      if (getView() == null)
        return;
      mAll = routes;
      applyFilter();
    });
  }

  private void applyFilter()
  {
    final boolean none = mAll.isEmpty();
    mEmpty.setVisibility(none ? View.VISIBLE : View.GONE);
    mLibrary.setVisibility(none ? View.GONE : View.VISIBLE);
    mImport.setVisibility(none ? View.GONE : View.VISIBLE);

    final List<Route> shown = new ArrayList<>();
    final long now = System.currentTimeMillis();
    for (Route r : mAll)
    {
      if (mFilter == FILTER_FAVORITES && !r.favorite)
        continue;
      if (mFilter == FILTER_RECENT && (r.lastRiddenAt == 0 || now - r.lastRiddenAt > RECENT_MS))
        continue;
      shown.add(r);
    }
    // The store keeps newest imports first; Recent sorts by last ride.
    if (mFilter == FILTER_RECENT)
      Collections.sort(shown, (a, b) -> Long.compare(b.lastRiddenAt, a.lastRiddenAt));
    mAdapter.submit(shown);

    mCaption.setText(getResources().getQuantityString(
        mFilter == FILTER_RECENT ? R.plurals.tz_routes_count_recent : R.plurals.tz_routes_count, shown.size(),
        shown.size()));
    final boolean filteredEmpty = shown.isEmpty() && !none;
    mFilterEmpty.setVisibility(filteredEmpty ? View.VISIBLE : View.GONE);
    mFilterEmpty.setText(mFilter == FILTER_RECENT ? R.string.tz_routes_no_recent : R.string.tz_routes_no_favorites);
  }

  private void showActions(@NonNull Route route)
  {
    final CharSequence[] items = {getString(R.string.tz_route_rename), getString(R.string.tz_route_export),
                                  getString(R.string.tz_route_delete)};
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(route.name)
        .setItems(items, (d, which) -> {
          if (which == 0)
            rename(route);
          else if (which == 1)
            TzBase.shareGpx(requireActivity(), RouteStore.get(requireContext()).gpxFile(route), route.name,
                            getString(R.string.tz_export_gpx));
          else
            confirmDelete(route);
        })
        .show();
  }

  private void rename(@NonNull Route route)
  {
    final EditText input = new EditText(requireContext());
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setText(route.name);
    input.setSelectAllOnFocus(true);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_24);
    final FrameLayout box = new FrameLayout(requireContext());
    box.setPadding(pad, getResources().getDimensionPixelSize(R.dimen.tz_space_8), pad, 0);
    box.addView(input);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.tz_route_rename)
        .setView(box)
        .setNegativeButton(R.string.tz_cancel, null)
        .setPositiveButton(R.string.tz_save, (d, w) -> {
          final String name = input.getText().toString().trim();
          if (!name.isEmpty())
            RouteStore.get(requireContext()).rename(route.id, name);
        })
        .show();
  }

  private void confirmDelete(@NonNull Route route)
  {
    new MaterialAlertDialogBuilder(requireContext())
        .setMessage(getString(R.string.tz_route_delete_confirm, route.name))
        .setNegativeButton(R.string.tz_cancel, null)
        .setPositiveButton(R.string.tz_route_delete, (d, w) -> RouteStore.get(requireContext()).delete(route.id))
        .show()
        .getButton(DialogInterface.BUTTON_POSITIVE)
        .setTextColor(ContextCompat.getColor(requireContext(), R.color.tz_danger));
  }

  /** The card's kind line: "Loop", "Point to point"…; a planned route says "Planned · Gravel" (Phase 28). */
  @NonNull
  static String kindText(@NonNull Context c, @NonNull Route r)
  {
    if (r.profile != null)
      return c.getString(R.string.tz_kind_planned, c.getString(TzProfiles.label(r.profile)));
    return c.getString(kindLabel(r.kind));
  }

  static int kindLabel(@NonNull RouteStats.Kind kind)
  {
    return switch (kind)
    {
      case LOOP -> R.string.tz_kind_loop;
      case OUT_AND_BACK -> R.string.tz_kind_out_and_back;
      case POINT_TO_POINT -> R.string.tz_kind_point_to_point;
    };
  }

  private final class Adapter extends RecyclerView.Adapter<Adapter.Holder>
  {
    @NonNull
    private List<Route> mRoutes = new ArrayList<>();

    void submit(@NonNull List<Route> routes)
    {
      mRoutes = routes;
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount()
    {
      return mRoutes.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      final TzRouteCard card = new TzRouteCard(parent.getContext(), null);
      card.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                         ViewGroup.LayoutParams.WRAP_CONTENT));
      return new Holder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position)
    {
      final Route r = mRoutes.get(position);
      final TzFormat.Units units = TzPrefs.units(requireContext());
      final Locale locale = Locale.getDefault();
      holder.card.bind(r.name, TzFormat.distance(r.distanceM, units, locale),
                       r.hasElevation() ? TzFormat.elevation(r.gainM, units, locale) : null,
                       kindText(requireContext(), r), r.favorite, r.silLat, r.silLon);
      holder.card.setListener(new TzRouteCard.Listener() {
        @Override
        public void onOpen()
        {
          final Bundle args = new Bundle();
          args.putString(ARG_ROUTE_ID, r.id);
          TzNavigator.show(TzNavigator.Screen.PREVIEW, args);
        }

        @Override
        public void onToggleFavorite()
        {
          holder.card.setFavorite(!r.favorite);
          RouteStore.get(requireContext()).setFavorite(r.id, !r.favorite);
        }

        @Override
        public void onLongPress()
        {
          showActions(r);
        }
      });
    }

    final class Holder extends RecyclerView.ViewHolder
    {
      @NonNull
      final TzRouteCard card;

      Holder(@NonNull TzRouteCard card)
      {
        super(card);
        this.card = card;
      }
    }
  }
}
