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
import app.trackzero.base.TzSearch;
import app.trackzero.format.TzFormat;
import app.trackzero.geo.Geo;
import app.trackzero.ui.TzCategoryChip;
import app.trackzero.ui.TzMetricView;
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
    chip(view, R.id.tz_search_chip_shelter, R.drawable.ic_tz_pin, R.string.tz_search_category_shelter);

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
      TzSearch.cancel();
      mAdapter.setPlaces(new ArrayList<>(), null, false);
      mAdapter.setCaption(caption(false, false));
      return;
    }
    search(text, false);
  }

  private void search(@NonNull String text, boolean category)
  {
    mAdapter.setCaption(caption(false, false));
    TzSearch.query(requireContext(), text, category, (places, done) -> {
      if (getView() == null)
        return;
      mAdapter.setPlaces(places, TzLocation.last(requireContext()), category);
      mAdapter.setCaption(caption(done, places.isEmpty()));
    });
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
    @NonNull
    final TzSearch.Place place;
    final double distanceM;

    Row(@NonNull TzSearch.Place place, double distanceM)
    {
      this.place = place;
      this.distanceM = distanceM;
    }
  }

  private final class Results extends RecyclerView.Adapter<RecyclerView.ViewHolder>
  {
    private static final int TYPE_PLACE = 0;
    private static final int TYPE_CAPTION = 1;

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
      return position < mRows.size() ? TYPE_PLACE : TYPE_CAPTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      if (viewType == TYPE_PLACE)
        return new RecyclerView.ViewHolder(inflater.inflate(R.layout.tz_search_row, parent, false)) {};
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
