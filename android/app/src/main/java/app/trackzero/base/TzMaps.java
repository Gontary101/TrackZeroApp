package app.trackzero.base;

import android.content.Context;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.downloader.CountryItem;
import app.organicmaps.sdk.downloader.MapManager;
import java.util.ArrayList;
import java.util.List;

/**
 * The offline maps on CoMaps' map storage: what is downloaded, the world → country → region tree, downloads and
 * their progress. Main thread; needs the core ({@link TzRoads#ready}).
 */
public final class TzMaps
{
  /** World and WorldCoasts: the overview maps that come with the app. */
  private static final String WORLD_MAPS = "World";

  public enum State
  {
    /** Can be downloaded. */
    AVAILABLE,
    /** Waiting or downloading. */
    DOWNLOADING,
    DOWNLOADED,
    /** Downloaded; a newer one is available. */
    UPDATABLE,
    FAILED,
    /** A group, some of its maps downloaded. */
    PARTLY
  }

  /** A map or a group of maps (a country with regions). Immutable snapshot. */
  public static final class Item
  {
    @NonNull
    public final String id;
    @NonNull
    public final String name;
    @NonNull
    public final State state;
    /** On the phone (downloaded) or to download, bytes. */
    public final long bytes;
    /** Download progress 0–100 while downloading. */
    public final int progress;
    /** Regions inside; 0 for a single map. */
    public final int maps;

    Item(@NonNull String id, @NonNull String name, @NonNull State state, long bytes, int progress, int maps)
    {
      this.id = id;
      this.name = name;
      this.state = state;
      this.bytes = bytes;
      this.progress = progress;
      this.maps = maps;
    }

    public boolean isGroup()
    {
      return maps > 1;
    }
  }

  /** Download progress and state changes, on the main thread. */
  public interface Listener
  {
    /** Something changed state: reload what is shown. */
    void onChanged();

    void onProgress(@NonNull String id, int percent);
  }

  private TzMaps() {}

  /** The maps downloaded, regions not groups ("Puy-de-Dôme", not "France"); the world overview left out. */
  @NonNull
  public static List<Item> downloaded()
  {
    final List<Item> out = new ArrayList<>();
    collectDownloaded(null, out);
    return out;
  }

  private static void collectDownloaded(@Nullable String root, @NonNull List<Item> out)
  {
    final List<CountryItem> items = new ArrayList<>();
    MapManager.nativeListItems(root, 0, 0, false, true, items);
    for (CountryItem c : items)
    {
      if (c.id.startsWith(WORLD_MAPS) || c.id.equals(root))
        continue;
      final Item item = item(c);
      // My-maps lists countries partly downloaded ("France"); their regions are the maps.
      if (item.state == State.DOWNLOADED || item.state == State.UPDATABLE || item.state == State.DOWNLOADING
          || item.state == State.FAILED)
        out.add(item);
      else if (c.childCount > 0)
        collectDownloaded(c.id, out);
    }
  }

  /** The maps and groups under {@code root} (null: the countries of the world), by name. */
  @NonNull
  public static List<Item> children(@Nullable String root)
  {
    final List<CountryItem> items = new ArrayList<>();
    MapManager.nativeListItems(root, 0, 0, false, false, items);
    final List<Item> out = new ArrayList<>();
    for (CountryItem c : items)
    {
      if (!c.id.equals(root) && !c.id.startsWith(WORLD_MAPS))
        out.add(item(c));
    }
    out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    return out;
  }

  /** The map the rider is in, or null without a position (or at sea). */
  @Nullable
  public static Item here(@NonNull Context context)
  {
    final Location here = TzLocation.last(context);
    if (here == null)
      return null;
    final String id = MapManager.nativeFindCountry(here.getLatitude(), here.getLongitude());
    return id == null ? null : item(CountryItem.fill(id));
  }

  @NonNull
  public static Item get(@NonNull String id)
  {
    return item(CountryItem.fill(id));
  }

  /** The downloaded map the rider is in ("Puy-de-Dôme"); null if none. */
  @Nullable
  public static String hereName(@NonNull Context context)
  {
    final Item item = here(context);
    return item != null && (item.state == State.DOWNLOADED || item.state == State.UPDATABLE) ? item.name : null;
  }

  public static void download(@NonNull String id)
  {
    MapManager.startDownload(id);
  }

  public static void update(@NonNull String id)
  {
    MapManager.startUpdate(id);
  }

  public static void retry(@NonNull String id)
  {
    MapManager.retryDownload(id);
  }

  public static void cancel(@NonNull String id)
  {
    MapManager.nativeCancel(id);
  }

  public static void delete(@NonNull String id)
  {
    MapManager.nativeDelete(id);
  }

  public static boolean hasSpaceFor(@NonNull String id)
  {
    return MapManager.nativeHasSpaceToDownloadCountry(id);
  }

  /** @return a token for {@link #unsubscribe} */
  public static int subscribe(@NonNull Listener listener)
  {
    return MapManager.nativeSubscribe(new MapManager.StorageCallback() {
      @Override
      public void onStatusChanged(List<MapManager.StorageCallbackData> data)
      {
        listener.onChanged();
      }

      @Override
      public void onProgress(String countryId, long localSize, long remoteSize)
      {
        listener.onProgress(countryId, remoteSize > 0 ? (int) (localSize * 100 / remoteSize) : 0);
      }
    });
  }

  public static void unsubscribe(int token)
  {
    MapManager.nativeUnsubscribe(token);
  }

  @NonNull
  private static Item item(@NonNull CountryItem c)
  {
    final State state = switch (c.status)
    {
      case CountryItem.STATUS_DONE -> State.DOWNLOADED;
      case CountryItem.STATUS_UPDATABLE -> State.UPDATABLE;
      case CountryItem.STATUS_PROGRESS, CountryItem.STATUS_ENQUEUED, CountryItem.STATUS_APPLYING ->
          State.DOWNLOADING;
      case CountryItem.STATUS_FAILED -> State.FAILED;
      case CountryItem.STATUS_PARTLY -> State.PARTLY;
      default -> State.AVAILABLE;
    };
    final long bytes = state == State.DOWNLOADED || state == State.UPDATABLE ? c.size : c.totalSize;
    return new Item(c.id, c.name == null ? c.id : c.name, state, bytes, Math.round(c.progress),
                    Math.max(c.totalChildCount, 1));
  }
}
