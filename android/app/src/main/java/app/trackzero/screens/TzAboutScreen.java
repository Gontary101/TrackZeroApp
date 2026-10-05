package app.trackzero.screens;

import android.content.res.AssetManager;
import android.os.SystemClock;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.trackzero.TzIo;
import app.trackzero.TzNavigator;
import app.trackzero.TzPrefs;
import app.trackzero.TzTrace;
import app.trackzero.base.TzBase;
import app.trackzero.base.TzRouteLayer;
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
  /** Taps on the version that toggle tracing, each within {@link #TAP_GAP_MS} of the last. */
  private static final int TRACE_TAPS = 7;
  private static final long TAP_GAP_MS = 600;

  private int mTaps;
  private long mLastTapMs;
  @Nullable
  private View mDebug;

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
    final TextView version = view.findViewById(R.id.tz_about_version);
    version.setText(getString(R.string.tz_version, TzBase.versionName()));
    version.setOnClickListener(v -> onVersionTapped());
    mDebug = view.findViewById(R.id.tz_about_debug);
    final TzListRow shareTrace = view.findViewById(R.id.tz_about_share_trace);
    shareTrace.setDetail(getString(R.string.tz_about_share_trace_detail));
    shareTrace.setOnClickListener(v -> shareTrace());
    final TzListRow lineStyle = view.findViewById(R.id.tz_about_line_style);
    lineStyle.setDetail(TzRouteLayer.STYLES[TzRouteLayer.style()]);
    lineStyle.setOnClickListener(v -> {
      final int next = (TzRouteLayer.style() + 1) % TzRouteLayer.STYLES.length;
      TzPrefs.setLineStyle(requireContext(), next);
      TzRouteLayer.setStyle(next);
      lineStyle.setDetail(TzRouteLayer.STYLES[next]);
    });
    showDebug();
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

  /** Seven quick taps on the version toggle tracing (release builds; debug builds always trace). */
  private void onVersionTapped()
  {
    final long now = SystemClock.elapsedRealtime();
    mTaps = now - mLastTapMs <= TAP_GAP_MS ? mTaps + 1 : 1;
    mLastTapMs = now;
    if (mTaps < TRACE_TAPS)
      return;
    mTaps = 0;
    TzTrace.setOn(requireContext(), !TzTrace.on());
    Toast.makeText(requireContext(), TzTrace.on() ? R.string.tz_about_trace_on : R.string.tz_about_trace_off,
                   Toast.LENGTH_SHORT).show();
    showDebug();
  }

  private void showDebug()
  {
    if (mDebug != null)
      mDebug.setVisibility(TzTrace.on() ? View.VISIBLE : View.GONE);
  }

  private void shareTrace()
  {
    TzBase.shareTrace(requireActivity(), getString(R.string.tz_about_share_trace));
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
