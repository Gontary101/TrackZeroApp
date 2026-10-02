package app.trackzero.base;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.sdk.settings.UnitLocale;
import app.trackzero.TzIo;
import app.trackzero.TzPrefs;
import app.trackzero.format.TzFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Base-app services TrackZero uses: permissions, sharing, units, version. */
public final class TzBase
{
  private static final String TAG = "TzBase";

  private TzBase() {}

  /**
   * TrackZero's units setting drives the map's scale bar too. The first time, it takes CoMaps' default for the
   * phone's country (miles in the US and the UK); then TrackZero's choice is applied to the map. Needs the core.
   */
  public static void syncUnits(@NonNull Context context)
  {
    if (!TzPrefs.hasUnits(context))
      TzPrefs.setUnits(context, UnitLocale.getUnits() == UnitLocale.UNITS_FOOT ? TzFormat.Units.IMPERIAL
                                                                              : TzFormat.Units.METRIC);
    UnitLocale.setUnits(TzPrefs.units(context) == TzFormat.Units.IMPERIAL ? UnitLocale.UNITS_FOOT
                                                                          : UnitLocale.UNITS_METRIC);
  }

  /** "2026.10.01-13" (the build's flavor and type left out). */
  @NonNull
  public static String versionName()
  {
    final String name = BuildConfig.VERSION_NAME;
    final int cut = name.toLowerCase(Locale.ROOT).indexOf('-' + BuildConfig.FLAVOR.toLowerCase(Locale.ROOT));
    return cut > 0 ? name.substring(0, cut) : name;
  }

  /** Runs {@code onGranted} once the ride may record location (asks first if needed). */
  public static void requestRidePermissions(@NonNull Activity activity, @NonNull Runnable onGranted)
  {
    if (activity instanceof TzActivity host)
      host.requestRidePermissions(onGranted);
  }

  /**
   * The system share sheet for a GPX file in the app's files, sent as "{name}.gpx" (a copy in the cache, through
   * the app's FileProvider) so the receiver does not see an internal file name.
   */
  public static void shareGpx(@NonNull Activity activity, @NonNull File gpx, @NonNull String name,
                              @NonNull CharSequence chooserTitle)
  {
    final File dir = new File(activity.getCacheDir(), "export");
    TzIo.submit(() -> {
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
      final File copy = new File(dir, fileName(name));
      try (InputStream in = new FileInputStream(gpx); OutputStream out = new FileOutputStream(copy))
      {
        final byte[] buf = new byte[64 * 1024];
        for (int n; (n = in.read(buf)) > 0;)
          out.write(buf, 0, n);
        return copy;
      }
      catch (IOException e)
      {
        Log.e(TAG, "Cannot export " + gpx, e);
        return null;
      }
    }, copy -> {
      if (copy == null || activity.isFinishing())
        return;
      final Uri uri = FileProvider.getUriForFile(activity, BuildConfig.FILE_PROVIDER_AUTHORITY, copy);
      final Intent send = new Intent(Intent.ACTION_SEND)
                              .setType("application/gpx+xml")
                              .putExtra(Intent.EXTRA_STREAM, uri)
                              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      // The ClipData carries the read grant through the chooser to the target app.
      send.setClipData(ClipData.newRawUri(copy.getName(), uri));
      activity.startActivity(Intent.createChooser(send, chooserTitle));
    });
  }

  @NonNull
  private static String fileName(@NonNull String name)
  {
    final String safe = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").trim();
    return (safe.isEmpty() ? "ride" : safe) + ".gpx";
  }
}
