package app.trackzero.routes;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;
import app.organicmaps.R;
import app.trackzero.TzNavigator;

/** GPX import from the file picker or from "Share / Open with TrackZero", then shows the library. */
public final class TzImport
{
  /** MIME types offered to the system file picker; GPX files often come as octet-stream. */
  public static final String[] PICKER_TYPES = {"application/gpx+xml", "application/octet-stream", "text/xml",
                                               "application/xml", "*/*"};

  private TzImport() {}

  public static void importUri(@NonNull Context context, @NonNull Uri uri)
  {
    final Context app = context.getApplicationContext();
    RouteStore.get(app).importGpx(uri, result -> {
      if (result.route != null)
      {
        Toast.makeText(app, R.string.tz_route_imported, Toast.LENGTH_SHORT).show();
        if (TzNavigator.current() != TzNavigator.Screen.ROUTES)
          TzNavigator.show(TzNavigator.Screen.ROUTES, null);
      }
      else
      {
        Toast.makeText(app, result.error == RouteStore.ImportError.NO_TRACK ? R.string.tz_import_no_track
                                                                            : R.string.tz_import_not_gpx,
                       Toast.LENGTH_LONG).show();
      }
    });
  }

  /** The file URI of a VIEW / SEND intent, or null if the intent carries no file. */
  @Nullable
  public static Uri fileOf(@NonNull Intent intent)
  {
    final String action = intent.getAction();
    if (Intent.ACTION_VIEW.equals(action))
    {
      final Uri data = intent.getData();
      final String scheme = data == null ? null : data.getScheme();
      return "content".equals(scheme) || "file".equals(scheme) ? data : null;
    }
    if (Intent.ACTION_SEND.equals(action))
      return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
    return null;
  }
}
