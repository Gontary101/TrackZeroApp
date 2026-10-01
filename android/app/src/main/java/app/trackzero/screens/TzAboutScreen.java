package app.trackzero.screens;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzIo;
import app.trackzero.TzNavigator;
import app.trackzero.base.TzBase;
import app.trackzero.ui.TzListRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** About TrackZero (from More): version, what it is built on, map data and font licences (plan Phase 13). */
public class TzAboutScreen extends TzScreen
{
  private static final String TAG = "TzAbout";
  private static final String LICENCES_DIR = "licenses";

  @Nullable
  private View mContent;
  @Nullable
  private TextView mLicences;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.tz_screen_about, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    mContent = view.findViewById(R.id.tz_about_content);
    mLicences = view.findViewById(R.id.tz_about_licences);
    view.findViewById(R.id.tz_about_back).setOnClickListener(v -> TzNavigator.show(TzNavigator.Screen.MORE, null));
    ((TextView) view.findViewById(R.id.tz_about_version)).setText(getString(R.string.tz_version,
                                                                            TzBase.versionName()));
    final TzListRow fonts = view.findViewById(R.id.tz_about_fonts);
    fonts.setDetail(getString(R.string.tz_about_fonts_detail));
    fonts.setOnClickListener(v -> toggleLicences());
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onInsets(int top, int bottom)
  {
    if (mContent == null)
      return;
    final int islandSpace = bottom + getResources().getDimensionPixelSize(R.dimen.tz_bottom_inset_extra)
                          + getResources().getDimensionPixelSize(R.dimen.tz_island_h)
                          + getResources().getDimensionPixelSize(R.dimen.tz_space_32);
    mContent.setPadding(0, top + getResources().getDimensionPixelSize(R.dimen.tz_space_8), 0, islandSpace);
  }

  /** The full licence texts, from the app's assets, under the row. */
  private void toggleLicences()
  {
    if (mLicences == null)
      return;
    if (mLicences.getVisibility() == View.VISIBLE)
    {
      mLicences.setVisibility(View.GONE);
      return;
    }
    final AssetManager assets = requireContext().getAssets();
    final String intro = getString(R.string.tz_about_fonts_list);
    TzIo.submit(() -> {
      final StringBuilder text = new StringBuilder(intro);
      try
      {
        final String[] files = assets.list(LICENCES_DIR);
        if (files != null)
        {
          for (String file : files)
            text.append("\n\n").append(file).append("\n\n").append(read(assets, LICENCES_DIR + "/" + file));
        }
      }
      catch (IOException e)
      {
        Log.w(TAG, "Cannot read the licences", e);
      }
      return text.toString();
    }, text -> {
      if (mLicences == null || getView() == null)
        return;
      mLicences.setText(text);
      mLicences.setVisibility(View.VISIBLE);
    });
  }

  @NonNull
  private static String read(@NonNull AssetManager assets, @NonNull String path) throws IOException
  {
    try (InputStream in = assets.open(path))
    {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final byte[] buf = new byte[8192];
      for (int n; (n = in.read(buf)) > 0;)
        out.write(buf, 0, n);
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }
}
