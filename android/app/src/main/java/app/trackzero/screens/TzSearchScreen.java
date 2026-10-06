package app.trackzero.screens;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.base.TzLocation;
import app.trackzero.base.TzMaps;
import app.trackzero.base.TzPois;
import app.trackzero.base.TzSearch;
import app.trackzero.follow.Stop;
import app.trackzero.follow.Stops;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.Geo;
import app.trackzero.ui.TzCategoryChip;
import app.trackzero.ui.TzMetricView;
import app.trackzero.ui.TzStopKinds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * B2 Search: offline places (in the engine's relevance order) and cyclist categories around the rider (nearest
 * first). A result opens the map centred on it, marked until the next tap.
 */
public class TzSearchScreen extends TzScreen
{
  private static final long DEBOUNCE_MS = 250;

  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final Results mAdapter = new Results();
  @Nullable
  private EditText mInput;
  @Nullable
  private View mClear;
  @Nullable
  private View mField;
  @Nullable
  private RecyclerView mList;
  private final List<TzCategoryChip> mChips = new ArrayList<>();
  /** The text was set by a chip, not typed: no text search for it. */
  private boolean mSettingText;
  private int mBottomInset;
  private int mImeInset;

  private final Runnable mSearch = this::searchText;

  @NonNull
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_search, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mField = view.findViewById(R.id.tz_search_field);
    mInput = view.findViewById(R.id.tz_search_input);
    mClear = view.findViewById(R.id.tz_search_clear);
    mList = view.findViewById(R.id.tz_search_results);
    mList.setLayoutManager(new LinearLayoutManager(requireContext()));
    mList.setAdapter(mAdapter);
    mAdapter.setCaption(caption(false, false));

    view.findViewById(R.id.tz_search_back).setOnClickListener(v -> TzNavigator.show(TzNavigator.Screen.MAP, null));
    mClear.setOnClickListener(v -> {
      mInput.setText("");
      showKeyboard();
    });
    mInput.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s)
      {
        mClear.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
        if (mSettingText)
          return;
        checkChip(null);
        mHandler.removeCallbacks(mSearch);
        mHandler.postDelayed(mSearch, DEBOUNCE_MS);
      }
    });
    mInput.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId != EditorInfo.IME_ACTION_SEARCH)
        return false;
      mHandler.removeCallbacks(mSearch);
      searchText();
      hideKeyboard();
      return true;
    });

    chip(view, R.id.tz_search_chip_water, R.drawable.ic_tz_water, R.string.tz_search_category_water);
    chip(view, R.id.tz_search_chip_bike, R.drawable.ic_tz_wrench, R.string.tz_search_category_bike);
    chip(view, R.id.tz_search_chip_cafe, R.drawable.ic_tz_cup, R.string.tz_search_category_cafe);
    final TzCategoryChip bivouac = view.findViewById(R.id.tz_search_chip_bivouac);
    bivouac.setIcon(R.drawable.ic_tz_tent);
    bivouac.setOnClickListener(v -> {
      mHandler.removeCallbacks(mSearch);
      checkChip(bivouac);
      setText(bivouac.getText().toString());
      hideKeyboard();
      searchBivouac();
    });
    mChips.add(bivouac);

    // The results end above the keyboard.
    ViewCompat.setOnApplyWindowInsetsListener(mList, (v, insets) -> {
      mImeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
      applyListPadding();
      return insets;
    });
    super.onViewCreated(view, savedInstanceState);
    // B2 opens with the keyboard up.
    mInput.requestFocus();
    showKeyboard();
  }

  @Override
  public void onDestroyView()
  {
    mHandler.removeCallbacks(mSearch);
    TzSearch.cancel();
    hideKeyboard();
    mChips.clear();
    super.onDestroyView();
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    if (mField == null)
      return;
    final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mField.getLayoutParams();
    lp.topMargin = top + getResources().getDimensionPixelSize(R.dimen.tz_top_inset_extra);
    mField.setLayoutParams(lp);
    mBottomInset = bottom;
    applyListPadding();
  }

  private void applyListPadding()
  {
    if (mList != null)
      mList.setPadding(0, 0, 0, Math.max(mBottomInset, mImeInset));
  }

  private void chip(@NonNull View root, int id, int icon, @StringRes int category)
  {
    final TzCategoryChip chip = root.findViewById(id);
    chip.setIcon(icon);
    chip.setOnClickListener(v -> {
      mHandler.removeCallbacks(mSearch);
      checkChip(chip);
      // The field shows what is searched; the engine gets the category's own name.
      setText(chip.getText().toString());
      hideKeyboard();
      search(getString(category), true);
    });
    mChips.add(chip);
  }

  private void checkChip(@Nullable TzCategoryChip checked)
  {
    for (TzCategoryChip c : mChips)
      c.setChecked(c == checked);
  }

  private void setText(@NonNull String text)
  {
    if (mInput == null)
      return;
    mSettingText = true;
    mInput.setText(text);
    mInput.setSelection(text.length());
    mSettingText = false;
  }

  private void searchText()
  {
    if (mInput == null)
      return;
    final String text = mInput.getText().toString().trim();
    if (text.isEmpty())
    {
      mBivouacSearch++;
      TzSearch.cancel();
      mAdapter.setPlaces(new ArrayList<>(), null, false);
      mAdapter.setCaption(caption(false, false));
      return;
    }
    search(text, false);
  }

  private void search(@NonNull String text, boolean category)
  {
    mBivouacSearch++;
    mAdapter.setCaption(caption(false, false));
    TzSearch.query(requireContext(), text, category, (places, done) -> {
      if (getView() == null)
        return;
      mAdapter.setPlaces(places, TzLocation.last(requireContext()), category);
      mAdapter.setCaption(caption(done, places.isEmpty()));
    });
  }

  // ---- Bivouac (Phase 31) ----

  /** Answers to an older bivouac search (or one replaced by a text search) are dropped. */
  private int mBivouacSearch;

  /** The same places and badges as S1's Bivouac, around the rider: within 5 km, or 20 km when there are none. */
  private void searchBivouac()
  {
    TzSearch.cancel();
    final int search = ++mBivouacSearch;
    final Location here = TzLocation.last(requireContext());
    mAdapter.setPlaces(new ArrayList<>(), null, false);
    if (here == null)
    {
      mAdapter.setCaption(getString(R.string.tz_stops_no_gps));
      return;
    }
    mAdapter.setCaption(getString(R.string.tz_stops_searching));
    findBivouac(here.getLatitude(), here.getLongitude(), Stops.FREE_RADIUS_M, search);
  }

  private void findBivouac(double lat, double lon, double radiusM, int search)
  {
    TzPois.along(requireContext(), new double[] {lat, lat}, new double[] {lon, lon}, radiusM, Stops.BIVOUAC,
                 places -> {
                   if (search != mBivouacSearch || getView() == null)
                     return;
                   final List<Stops.Row> rows = Stops.near(lat, lon, places, radiusM);
                   if (rows.isEmpty() && radiusM < Stops.BIVOUAC_WIDE_M)
                   {
                     findBivouac(lat, lon, Stops.BIVOUAC_WIDE_M, search);
                     return;
                   }
                   final String within = TzFormat.distance(radiusM, TzPrefs.units(requireContext()),
                                                           Locale.getDefault()).toString();
                   mAdapter.setStops(rows);
                   mAdapter.setCaption(rows.isEmpty() ? getString(R.string.tz_search_bivouac_none, within)
                                                      : getString(R.string.tz_search_bivouac_caption, within) + "\n"
                                                            + getString(R.string.tz_bivouac_footer));
                 });
  }

  /** A place to sleep: the route there by bike (B5, a ride to a place). */
  private void openStop(@NonNull Stop stop)
  {
    hideKeyboard();
    final Bundle args = new Bundle();
    args.putString(TzPreviewScreen.ARG_PLACE_NAME, TzStopKinds.name(requireContext(), stop));
    args.putDouble(TzPreviewScreen.ARG_PLACE_LAT, stop.lat);
    args.putDouble(TzPreviewScreen.ARG_PLACE_LON, stop.lon);
    TzNavigator.show(TzNavigator.Screen.PREVIEW, args);
  }

  @NonNull
  private String caption(boolean done, boolean empty)
  {
    if (done && empty)
      return getString(R.string.tz_search_nothing);
    final String region = TzMaps.hereName(requireContext());
    return region == null ? getString(R.string.tz_search_caption)
                          : getString(R.string.tz_search_caption_region, region);
  }

  private void open(@NonNull TzSearch.Place place)
  {
    hideKeyboard();
    TzNavigator.show(TzNavigator.Screen.MAP, null);
    TzSearch.show(place);
  }

  private void showKeyboard()
  {
    if (mInput != null && getActivity() != null)
      WindowCompat.getInsetsController(requireActivity().getWindow(), mInput).show(WindowInsetsCompat.Type.ime());
  }

  private void hideKeyboard()
  {
    if (mInput != null && getActivity() != null)
      WindowCompat.getInsetsController(requireActivity().getWindow(), mInput).hide(WindowInsetsCompat.Type.ime());
  }

  // ---- Results ----

  private static final class Row
  {
    /** A search result, or (Bivouac) a place from the map's features. */
    @Nullable
    final TzSearch.Place place;
    @Nullable
    final Stop stop;
    final double distanceM;

    Row(@NonNull TzSearch.Place place, double distanceM)
    {
      this.place = place;
      this.stop = null;
      this.distanceM = distanceM;
    }

    Row(@NonNull Stop stop, double distanceM)
    {
      this.place = null;
      this.stop = stop;
      this.distanceM = distanceM;
    }
  }

  private final class Results extends RecyclerView.Adapter<RecyclerView.ViewHolder>
  {
    private static final int TYPE_PLACE = 0;
    private static final int TYPE_CAPTION = 1;
    private static final int TYPE_STOP = 2;

    private final List<Row> mRows = new ArrayList<>();
    @NonNull
    private String mCaption = "";

    /**
     * @param nearestFirst sort by distance (a category: all equally relevant); else keep the engine's order, which
     *                     weighs how well the name matches with how far it is ("Puy de Dôme" the summit before the
     *                     ten nearer roads named after it)
     */
    void setPlaces(@NonNull List<TzSearch.Place> places, @Nullable Location here, boolean nearestFirst)
    {
      mRows.clear();
      for (TzSearch.Place p : places)
      {
        final double d = here == null ? Double.NaN
                                      : Geo.distance(here.getLatitude(), here.getLongitude(), p.lat, p.lon);
        mRows.add(new Row(p, d));
      }
      if (nearestFirst && here != null)
        mRows.sort((a, b) -> Double.compare(a.distanceM, b.distanceM));
      notifyDataSetChanged();
    }

    /** Places to sleep, nearest first. */
    void setStops(@NonNull List<Stops.Row> rows)
    {
      mRows.clear();
      for (Stops.Row r : rows)
        mRows.add(new Row(r.stop, r.distanceM));
      notifyDataSetChanged();
    }

    void setCaption(@NonNull String caption)
    {
      if (caption.equals(mCaption))
        return;
      mCaption = caption;
      notifyItemChanged(mRows.size());
    }

    @Override
    public int getItemCount()
    {
      return mRows.size() + 1;
    }

    @Override
    public int getItemViewType(int position)
    {
      if (position >= mRows.size())
        return TYPE_CAPTION;
      return mRows.get(position).stop != null ? TYPE_STOP : TYPE_PLACE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      if (viewType == TYPE_PLACE)
        return new RecyclerView.ViewHolder(inflater.inflate(R.layout.tz_search_row, parent, false)) {};
      if (viewType == TYPE_STOP)
        return new RecyclerView.ViewHolder(inflater.inflate(R.layout.tz_stop_row, parent, false)) {};
      final TextView caption = new TextView(parent.getContext());
      TextViewCompat.setTextAppearance(caption, R.style.TzText_Caption);
      final int pad = getResources().getDimensionPixelSize(R.dimen.tz_space_20);
      caption.setPadding(pad, getResources().getDimensionPixelSize(R.dimen.tz_space_16), pad, pad);
      caption.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                            ViewGroup.LayoutParams.WRAP_CONTENT));
      return new RecyclerView.ViewHolder(caption) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position)
    {
      if (position >= mRows.size())
      {
        ((TextView) holder.itemView).setText(mCaption);
        return;
      }
      final Row row = mRows.get(position);
      final View v = holder.itemView;
      if (row.stop != null)
      {
        bindStop(v, row.stop, row.distanceM);
        return;
      }
      ((ImageView) v.findViewById(R.id.tz_search_row_icon)).setImageResource(icon(row.place.kind));
      ((TextView) v.findViewById(R.id.tz_search_row_name)).setText(row.place.name);
      final TextView subtitle = v.findViewById(R.id.tz_search_row_subtitle);
      subtitle.setText(row.place.subtitle);
      subtitle.setVisibility(row.place.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
      final TzMetricView distance = v.findViewById(R.id.tz_search_row_distance);
      distance.setVisibility(Double.isNaN(row.distanceM) ? View.GONE : View.VISIBLE);
      if (!Double.isNaN(row.distanceM))
        distance.setValue(TzFormat.distance(row.distanceM, TzPrefs.units(requireContext()), Locale.getDefault()));
      v.setOnClickListener(x -> open(row.place));
    }
  }

  /** A place to sleep: S1's row (name, kind, badge, what limits a stay), its straight distance. */
  private void bindStop(@NonNull View v, @NonNull Stop stop, double distanceM)
  {
    ((ImageView) v.findViewById(R.id.tz_stop_row_icon)).setImageResource(TzStopKinds.icon(stop.kind));
    ((TextView) v.findViewById(R.id.tz_stop_row_name)).setText(TzStopKinds.name(requireContext(), stop));
    final TextView subtitle = v.findViewById(R.id.tz_stop_row_subtitle);
    subtitle.setText(stop.name.isEmpty() ? "" : getString(TzStopKinds.label(stop.kind)));
    subtitle.setVisibility(stop.name.isEmpty() ? View.GONE : View.VISIBLE);
    TzStopKinds.bindBadge(v.findViewById(R.id.tz_stop_row_badge), stop);
    final String note = TzStopKinds.bivouacNote(requireContext(), stop);
    final TextView noteView = v.findViewById(R.id.tz_stop_row_note);
    noteView.setText(note);
    noteView.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
    ((TzMetricView) v.findViewById(R.id.tz_stop_row_distance))
        .setValue(TzFormat.distance(distanceM, TzPrefs.units(requireContext()), Locale.getDefault()));
    v.setOnClickListener(x -> openStop(stop));
  }

  private static int icon(@NonNull TzSearch.Kind kind)
  {
    return switch (kind)
    {
      case SUMMIT -> R.drawable.ic_tz_mountain;
      case WATER -> R.drawable.ic_tz_water;
      case CAFE -> R.drawable.ic_tz_cup;
      case BIKE -> R.drawable.ic_tz_wrench;
      case PLACE -> R.drawable.ic_tz_pin;
    };
  }
}
