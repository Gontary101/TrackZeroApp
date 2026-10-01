package app.trackzero.base;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;
import app.organicmaps.downloader.DownloaderActivity;

/** Entry points into base-app screens TrackZero reuses. */
public final class TzBase
{
  private TzBase() {}

  /** CoMaps' offline map manager. */
  public static void openDownloader(@NonNull Activity activity)
  {
    activity.startActivity(new Intent(activity, DownloaderActivity.class));
  }
}
