package app.trackzero.screens;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.format.TzFormat;
import app.trackzero.ride.Ride;
import app.trackzero.ride.RideStore;
import app.trackzero.ui.TzRouteCard;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Ride history (plan Phase 13): the saved rides as B3 cards, newest first; a ride opens its R8 summary. */
public class TzHistoryScreen extends TzScreen
{
  private final Adapter mAdapter = new Adapter();
  @Nullable
  private View mContent;
  @Nullable
  private TextView mCaption;
  @Nullable
  private View mEmpty;
  @Nullable
  private RecyclerView mList;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_history, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mContent = view.findViewById(R.id.tz_history_content);
    mCaption = view.findViewById(R.id.tz_history_caption);
    mEmpty = view.findViewById(R.id.tz_history_empty);
    mList = view.findViewById(R.id.tz_history_list);
    view.findViewById(R.id.tz_history_back).setOnClickListener(v -> TzNavigator.show(TzNavigator.Screen.MORE, null));
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
    RideStore.get(requireContext()).list(rides -> {
      if (getView() == null)
        return;
      mAdapter.submit(rides);
      mCaption.setText(getResources().getQuantityString(R.plurals.tz_more_rides, rides.size(), rides.size()));
      mEmpty.setVisibility(rides.isEmpty() ? View.VISIBLE : View.GONE);
    });
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    if (mContent == null)
      return;
    mContent.setPadding(0, top + getResources().getDimensionPixelSize(R.dimen.tz_space_8), 0, 0);
    // The last card clears the island with 32dp to spare.
    final int islandSpace = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                          + getResources().getDimensionPixelSize(R.dimen.tz_island_h)
                          + getResources().getDimensionPixelSize(R.dimen.tz_space_32);
    mList.setPadding(mList.getPaddingLeft(), 0, mList.getPaddingRight(), islandSpace);
  }

  private void open(@NonNull Ride ride)
  {
    final Bundle args = new Bundle();
    args.putString(TzSummaryScreen.ARG_RIDE_ID, ride.id);
    args.putBoolean(TzSummaryScreen.ARG_FROM_HISTORY, true);
    TzNavigator.show(TzNavigator.Screen.SUMMARY, args);
  }

  private final class Adapter extends RecyclerView.Adapter<Adapter.Holder>
  {
    private final List<Ride> mRides = new ArrayList<>();

    void submit(@NonNull List<Ride> rides)
    {
      mRides.clear();
      mRides.addAll(rides);
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount()
    {
      return mRides.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      final TzRouteCard card = new TzRouteCard(parent.getContext(), null);
      card.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                         ViewGroup.LayoutParams.WRAP_CONTENT));
      card.setStarVisible(false);
      return new Holder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position)
    {
      final Ride ride = mRides.get(position);
      final Locale locale = Locale.getDefault();
      final TzFormat.Units units = TzPrefs.units(requireContext());
      // The date takes the place of the route kind: "Thu, Oct 1".
      final String date = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"),
                                            new Date(ride.startedAt)).toString();
      holder.card.bind(ride.name, TzFormat.distance(ride.distanceM, units, locale),
                       TzFormat.elevation(ride.gainM, units, locale), date.toUpperCase(locale), false, ride.silLat,
                       ride.silLon);
      holder.card.setListener(new TzRouteCard.Listener() {
        @Override
        public void onOpen()
        {
          open(ride);
        }

        @Override
        public void onToggleFavorite() {}

        @Override
        public void onLongPress() {}
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
