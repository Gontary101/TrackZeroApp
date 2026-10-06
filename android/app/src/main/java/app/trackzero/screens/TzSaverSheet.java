package app.trackzero.screens;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** X1 · Battery saver, confirm (Phase 35): what keeps working and what pauses, then TURN ON. */
public class TzSaverSheet extends BottomSheetDialogFragment
{
  private static final String TAG = "TzSaverSheet";

  /** Implemented by the fragment that opens the sheet (its parent). */
  public interface Listener
  {
    void onSaverConfirmed();
  }

  public static <T extends Fragment & Listener> void show(@NonNull T parent)
  {
    final FragmentManager fm = parent.getChildFragmentManager();
    if (fm.findFragmentByTag(TAG) == null)
      new TzSaverSheet().show(fm, TAG);
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
    d.getBehavior().setSkipCollapsed(true);
    d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    return d;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_sheet_saver, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    view.findViewById(R.id.tz_saver_turn_on).setOnClickListener(v -> {
      dismiss();
      if (getParentFragment() instanceof Listener l)
        l.onSaverConfirmed();
    });
  }
}
