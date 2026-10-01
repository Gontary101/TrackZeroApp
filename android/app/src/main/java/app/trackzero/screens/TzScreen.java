package app.trackzero.screens;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import app.trackzero.TzNavigator;

/** A TrackZero screen hosted by {@link TzNavigator} above the map. */
public abstract class TzScreen extends Fragment
{
  /**
   * Places content around the system bars: top content at {@code top + tz_top_inset_extra}, bottom content above
   * {@code bottom} (plan §X2). Called after the view exists and whenever the insets change.
   */
  public void onInsets(int top, int bottom) {}

  /** @return true if the screen consumed Back itself (before the navigator's default mapping). */
  public boolean onBackPressed()
  {
    return false;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    onInsets(TzNavigator.topInset(), TzNavigator.bottomInset());
  }
}
