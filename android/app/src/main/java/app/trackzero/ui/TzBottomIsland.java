package app.trackzero.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;

/** Floating bottom navigation: Map · Routes · RIDE · Search · More. */
public class TzBottomIsland extends LinearLayout
{
  public enum Tab
  {
    MAP,
    ROUTES,
    SEARCH,
    MORE
  }

  public interface Listener
  {
    void onTabSelected(@NonNull Tab tab);

    void onRideClicked();
  }

  private final ViewGroup[] mSlots = new ViewGroup[Tab.values().length];
  @Nullable
  private Listener mListener;

  public TzBottomIsland(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    final int pad = getResources().getDimensionPixelSize(R.dimen.tz_island_pad);
    setPadding(pad, pad, pad, pad);
    setBackgroundResource(R.drawable.tz_bg_surface2_r36);
    setElevation(getResources().getDimension(R.dimen.tz_elev2));
    LayoutInflater.from(context).inflate(R.layout.tz_c_bottom_island, this, true);

    mSlots[Tab.MAP.ordinal()] = findViewById(R.id.tz_island_map);
    mSlots[Tab.ROUTES.ordinal()] = findViewById(R.id.tz_island_routes);
    mSlots[Tab.SEARCH.ordinal()] = findViewById(R.id.tz_island_search);
    mSlots[Tab.MORE.ordinal()] = findViewById(R.id.tz_island_more);
    for (Tab tab : Tab.values())
      mSlots[tab.ordinal()].setOnClickListener(v -> {
        if (mListener != null)
          mListener.onTabSelected(tab);
      });
    findViewById(R.id.tz_island_ride).setOnClickListener(v -> {
      if (mListener != null)
        mListener.onRideClicked();
    });
    setActive(Tab.MAP);
  }

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  public void setActive(@NonNull Tab active)
  {
    final ColorStateList mint = ContextCompat.getColorStateList(getContext(), R.color.tz_mint);
    final ColorStateList text1 = ContextCompat.getColorStateList(getContext(), R.color.tz_text1);
    final ColorStateList text2 = ContextCompat.getColorStateList(getContext(), R.color.tz_text2);
    for (Tab tab : Tab.values())
    {
      final boolean on = tab == active;
      final ViewGroup slot = mSlots[tab.ordinal()];
      slot.setSelected(on);
      slot.setBackgroundResource(on ? R.drawable.tz_bg_surface3_r30 : 0);
      ImageViewCompat.setImageTintList((ImageView) slot.getChildAt(0), on ? mint : text2);
      ((TextView) slot.getChildAt(1)).setTextColor(on ? text1 : text2);
    }
  }
}
