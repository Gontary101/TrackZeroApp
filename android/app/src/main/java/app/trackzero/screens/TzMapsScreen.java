package app.trackzero.screens;

import android.content.Context;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.base.TzMaps;
import app.trackzero.base.TzRoads;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline maps (from More; replaces CoMaps' downloader, D14): the maps on the phone, the one for where the rider is,
 * and all maps by country. A map downloads, updates or goes away from its row.
 */
public class TzMapsScreen extends TzScreen
{
  private final Adapter mAdapter = new Adapter();
  /** The countries opened, innermost last; empty at the top. */
  private final Deque<TzMaps.Item> mPath = new ArrayDeque<>();
  /** Progress reported since the last reload, by map id. */
  private final Map<String, Integer> mProgress = new HashMap<>();
  private int mSubscription;
  @Nullable
  private View mContent;
  @Nullable
  private TextView mTitle;
  @Nullable
  private RecyclerView mList;

  private final TzMaps.Listener mListener = new TzMaps.Listener() {
    @Override
    public void onChanged()
    {
      reload();
    }

    @Override
    public void onProgress(@NonNull String id, int percent)
    {
      mProgress.put(id, percent);
      mAdapter.progressChanged(id);
    }
  };

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_maps, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mContent = view.findViewById(R.id.tz_maps_content);
    mTitle = view.findViewById(R.id.tz_maps_title);
    mList = view.findViewById(R.id.tz_maps_list);
    mList.setLayoutManager(new LinearLayoutManager(requireContext()));
    mList.setAdapter(mAdapter);
    view.findViewById(R.id.tz_maps_back).setOnClickListener(v -> {
      if (!onBackPressed())
        TzNavigator.show(TzNavigator.Screen.MORE, null);
    });
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    if (!TzRoads.ready(requireContext()))
      return;
    mSubscription = TzMaps.subscribe(mListener);
    reload();
  }

  @Override
  public void onStop()
  {
    super.onStop();
    if (mSubscription != 0)
      TzMaps.unsubscribe(mSubscription);
    mSubscription = 0;
  }

  /** Back leaves a country first. */
  @Override
  public boolean onBackPressed()
  {
    if (mPath.isEmpty())
      return false;
    mPath.removeLast();
    reload();
    return true;
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    if (mContent == null)
      return;
    mContent.setPadding(0, top + getResources().getDimensionPixelSize(R.dimen.tz_space_8), 0, 0);
    // The last row clears the island with 32dp to spare.
    final int islandSpace = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                          + getResources().getDimensionPixelSize(R.dimen.tz_island_h)
                          + getResources().getDimensionPixelSize(R.dimen.tz_space_32);
    mList.setPadding(0, 0, 0, islandSpace);
  }

  private void reload()
  {
    if (getView() == null)
      return;
    mProgress.clear();
    final List<Row> rows = new ArrayList<>();
    final TzMaps.Item country = mPath.peekLast();
    mTitle.setText(country == null ? getString(R.string.tz_maps_title)
                                   : country.name.toUpperCase(Locale.getDefault()));
    if (country != null)
    {
      for (TzMaps.Item item : TzMaps.children(country.id))
        rows.add(Row.item(item));
      mAdapter.submit(rows);
      return;
    }

    rows.add(Row.label(getString(R.string.tz_maps_on_phone)));
    final List<TzMaps.Item> downloaded = TzMaps.downloaded();
    if (downloaded.isEmpty())
      rows.add(Row.text(getString(R.string.tz_maps_none)));
    for (TzMaps.Item item : downloaded)
      rows.add(Row.item(item));

    final TzMaps.Item here = TzMaps.here(requireContext());
    if (here != null && here.state == TzMaps.State.AVAILABLE)
    {
      rows.add(Row.label(getString(R.string.tz_maps_here)));
      rows.add(Row.item(here));
    }

    rows.add(Row.label(getString(R.string.tz_maps_all)));
    for (TzMaps.Item item : TzMaps.children(null))
      rows.add(Row.item(item));
    mAdapter.submit(rows);
  }

  private void onTap(@NonNull TzMaps.Item item)
  {
    final Context c = requireContext();
    if (item.isGroup())
    {
      mPath.addLast(item);
      reload();
      mList.scrollToPosition(0);
      return;
    }
    final String size = Formatter.formatShortFileSize(c, item.bytes);
    switch (item.state)
    {
    case AVAILABLE ->
    {
      if (!TzMaps.hasSpaceFor(item.id))
      {
        Toast.makeText(c, R.string.tz_maps_no_space, Toast.LENGTH_LONG).show();
        return;
      }
      new MaterialAlertDialogBuilder(c)
          .setMessage(getString(R.string.tz_maps_download_ask, item.name, size))
          .setNegativeButton(R.string.tz_cancel, null)
          .setPositiveButton(R.string.tz_maps_action_download, (d, w) -> TzMaps.download(item.id))
          .show();
    }
    case DOWNLOADING -> new MaterialAlertDialogBuilder(c)
                            .setMessage(getString(R.string.tz_maps_stop_ask, item.name))
                            .setNegativeButton(R.string.tz_cancel, null)
                            .setPositiveButton(R.string.tz_maps_stop, (d, w) -> TzMaps.cancel(item.id))
                            .show();
    case FAILED -> TzMaps.retry(item.id);
    case DOWNLOADED, UPDATABLE ->
    {
      final MaterialAlertDialogBuilder dialog =
          new MaterialAlertDialogBuilder(c)
              .setTitle(item.name)
              .setNegativeButton(R.string.tz_maps_delete, (d, w) -> TzMaps.delete(item.id))
              .setNeutralButton(R.string.tz_cancel, null);
      if (item.state == TzMaps.State.UPDATABLE)
        dialog.setPositiveButton(R.string.tz_maps_update_action, (d, w) -> TzMaps.update(item.id));
      dialog.show();
    }
    case PARTLY -> {}
    }
  }

  @NonNull
  private String stateText(@NonNull TzMaps.Item item)
  {
    final Context c = requireContext();
    final String size = Formatter.formatShortFileSize(c, item.bytes);
    if (item.isGroup())
      return item.state == TzMaps.State.PARTLY || item.state == TzMaps.State.DOWNLOADED
          ? getString(R.string.tz_maps_partly)
          : getResources().getQuantityString(R.plurals.tz_maps_count, item.maps, item.maps);
    return switch (item.state)
    {
      case AVAILABLE -> size;
      case DOWNLOADING ->
      {
        final Integer p = mProgress.get(item.id);
        final int percent = p != null ? p : item.progress;
        yield percent > 0 ? getString(R.string.tz_maps_downloading, percent) : getString(R.string.tz_maps_waiting);
      }
      case DOWNLOADED, PARTLY -> getString(R.string.tz_maps_on_phone_state, size);
      case UPDATABLE -> getString(R.string.tz_maps_update, size);
      case FAILED -> getString(R.string.tz_maps_failed);
    };
  }

  // ---- List ----

  private static final class Row
  {
    static final int LABEL = 0;
    static final int TEXT = 1;
    static final int ITEM = 2;

    final int type;
    @Nullable
    final String text;
    @Nullable
    final TzMaps.Item item;

    private Row(int type, @Nullable String text, @Nullable TzMaps.Item item)
    {
      this.type = type;
      this.text = text;
      this.item = item;
    }

    static Row label(@NonNull String text)
    {
      return new Row(LABEL, text, null);
    }

    static Row text(@NonNull String text)
    {
      return new Row(TEXT, text, null);
    }

    static Row item(@NonNull TzMaps.Item item)
    {
      return new Row(ITEM, null, item);
    }
  }

  private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
  {
    private final List<Row> mRows = new ArrayList<>();

    void submit(@NonNull List<Row> rows)
    {
      mRows.clear();
      mRows.addAll(rows);
      notifyDataSetChanged();
    }

    void progressChanged(@NonNull String id)
    {
      for (int i = 0; i < mRows.size(); i++)
      {
        final TzMaps.Item item = mRows.get(i).item;
        if (item != null && item.id.equals(id))
          notifyItemChanged(i);
      }
    }

    @Override
    public int getItemCount()
    {
      return mRows.size();
    }

    @Override
    public int getItemViewType(int position)
    {
      return mRows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      if (viewType == Row.ITEM)
        return new RecyclerView.ViewHolder(
            LayoutInflater.from(parent.getContext()).inflate(R.layout.tz_maps_row, parent, false)) {};
      final TextView t = new TextView(parent.getContext());
      TextViewCompat.setTextAppearance(t, viewType == Row.LABEL ? R.style.TzText_Label : R.style.TzText_Body);
      final int side = getResources().getDimensionPixelSize(R.dimen.tz_space_20);
      final int top = getResources().getDimensionPixelSize(viewType == Row.LABEL ? R.dimen.tz_space_24
                                                                                 : R.dimen.tz_space_8);
      t.setPadding(side, top, side, getResources().getDimensionPixelSize(R.dimen.tz_space_8));
      t.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                      ViewGroup.LayoutParams.WRAP_CONTENT));
      return new RecyclerView.ViewHolder(t) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position)
    {
      final Row row = mRows.get(position);
      if (row.item == null)
      {
        ((TextView) holder.itemView).setText(row.text);
        return;
      }
      final TzMaps.Item item = row.item;
      final View v = holder.itemView;
      ((ImageView) v.findViewById(R.id.tz_maps_row_icon)).setImageResource(
          item.state == TzMaps.State.AVAILABLE && !item.isGroup() ? R.drawable.ic_tz_download : R.drawable.ic_tz_map);
      ((TextView) v.findViewById(R.id.tz_maps_row_name)).setText(item.name);
      ((TextView) v.findViewById(R.id.tz_maps_row_state)).setText(stateText(item));
      final TextView action = v.findViewById(R.id.tz_maps_row_action);
      action.setVisibility(!item.isGroup() && item.state == TzMaps.State.AVAILABLE ? View.VISIBLE : View.GONE);
      action.setText(R.string.tz_maps_action_download);
      v.findViewById(R.id.tz_maps_row_chevron).setVisibility(item.isGroup() ? View.VISIBLE : View.GONE);
      v.setOnClickListener(x -> onTap(item));
    }
  }
}
