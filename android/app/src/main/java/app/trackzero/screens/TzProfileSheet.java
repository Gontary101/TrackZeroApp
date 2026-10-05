package app.trackzero.screens;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.R;
import app.trackzero.TzPrefs;
import app.trackzero.routes.Profile;
import app.trackzero.ui.TzBigRow;
import app.trackzero.ui.TzProfiles;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * N2 · Profile sheet (Phase 22), opened by a ProfilePill: one row per profile switched on. A tap selects it, saves it
 * as the default for the next route and closes; the screen that opened the sheet hears of it through
 * {@link Listener}.
 */
public class TzProfileSheet extends BottomSheetDialogFragment
{
  private static final String TAG = "TzProfileSheet";

  /** Implemented by the fragment that opens the sheet (it is the sheet's parent). */
  public interface Listener
  {
    void onProfileChosen(@NonNull Profile profile);
  }

  /** Opens the sheet over {@code parent}, which implements {@link Listener}. */
  public static <T extends Fragment & Listener> void show(@NonNull T parent)
  {
    final FragmentManager fm = parent.getChildFragmentManager();
    if (fm.findFragmentByTag(TAG) == null)
      new TzProfileSheet().show(fm, TAG);
  }

  @Override
  public int getTheme()
  {
    return R.style.ThemeOverlay_TrackZero_BottomSheet;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
  {
    final BottomSheetDialog d = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    // All of it at once: there is nothing to scroll and no half-open state.
    d.getBehavior().setSkipCollapsed(true);
    d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    return d;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_sheet_profile, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    final Profile current = TzPrefs.profile(requireContext());
    final LinearLayout rows = view.findViewById(R.id.tz_profile_rows);
    final int gap = getResources().getDimensionPixelSize(R.dimen.tz_space_12);
    for (Profile p : Profile.available())
    {
      final TzBigRow row = new TzBigRow(requireContext(), null);
      row.bind(TzProfiles.icon(p), getString(TzProfiles.label(p)), getString(TzProfiles.description(p)));
      row.setChosen(p == current);
      row.setOnClickListener(v -> choose(p));
      final LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.topMargin = gap;
      rows.addView(row, lp);
    }
    // Above the navigation bar.
    final int bottom = view.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
      final Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
      v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom + bars.bottom);
      return insets;
    });
  }

  private void choose(@NonNull Profile p)
  {
    TzPrefs.setProfile(requireContext(), p);
    if (getParentFragment() instanceof Listener l)
      l.onProfileChosen(p);
    dismiss();
  }
}
