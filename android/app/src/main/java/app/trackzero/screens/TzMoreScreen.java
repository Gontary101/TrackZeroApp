package app.trackzero.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.base.TzBase;
import app.trackzero.ui.TzListRow;

/** B7 · More: offline maps, ride history, settings, about. */
public class TzMoreScreen extends TzScreen
{
  @Nullable
  private View mContent;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_more, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mContent = view.findViewById(R.id.tz_more_content);
    final TzListRow offline = view.findViewById(R.id.tz_more_offline);
    offline.setDetail(getString(R.string.tz_more_manage));
    offline.setOnClickListener(v -> TzBase.openDownloader(requireActivity()));
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    // Page titles start 16dp under the status bar; the list clears the island with 32dp to spare.
    final int islandSpace = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                          + getResources().getDimensionPixelSize(R.dimen.tz_island_h)
                          + getResources().getDimensionPixelSize(R.dimen.tz_space_32);
    mContent.setPadding(mContent.getPaddingLeft(), top + getResources().getDimensionPixelSize(R.dimen.tz_space_16),
                        mContent.getPaddingRight(), islandSpace);
  }
}
