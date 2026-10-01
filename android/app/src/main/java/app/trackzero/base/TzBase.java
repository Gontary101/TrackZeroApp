package app.trackzero.base;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.downloader.DownloaderActivity;
import app.trackzero.TzIo;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Entry points into base-app screens TrackZero reuses. */
public final class TzBase
{
  private static final String TAG = "TzBase";

  private TzBase() {}

  /** CoMaps' offline map manager. */
  public static void openDownloader(@NonNull Activity activity)
  {
    activity.startActivity(new Intent(activity, DownloaderActivity.class));
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
