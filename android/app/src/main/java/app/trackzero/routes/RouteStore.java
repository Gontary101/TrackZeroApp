package app.trackzero.routes;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.TzIo;
import app.trackzero.TzTrace;
import app.trackzero.base.TzRoads;
import app.trackzero.follow.FollowRoute;
import app.trackzero.geo.DouglasPeucker;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * The route library: GPX copies in {@code filesDir/tz/routes/} and the index {@code filesDir/tz/routes.json}.
 * All state lives on the {@link TzIo} thread; callers get immutable snapshots on the UI thread.
 */
public final class RouteStore
{
  private static final String TAG = "TzRouteStore";
  private static final int SILHOUETTE_POINTS = 200;

  public enum ImportError
  {
    /** Not a readable GPX file. */
    NOT_GPX,
    /** GPX without at least 2 track / route points. */
    NO_TRACK
  }

  public static final class ImportResult
  {
    @Nullable
    public final Route route;
    @Nullable
    public final ImportError error;

    ImportResult(@Nullable Route route, @Nullable ImportError error)
    {
      this.route = route;
      this.error = error;
    }
  }

  @Nullable
  private static RouteStore sInstance;

  @NonNull
  private final Context mApp;
  @NonNull
  private final ContentResolver mResolver;
  @NonNull
  private final File mDir;
  @NonNull
  private final File mIndex;
  /** IO thread only; newest import first. Null until loaded. */
  @Nullable
  private List<Route> mRoutes;
  private final List<Runnable> mListeners = new CopyOnWriteArrayList<>();

  @NonNull
  public static synchronized RouteStore get(@NonNull Context context)
  {
    if (sInstance == null)
      sInstance = new RouteStore(context.getApplicationContext());
    return sInstance;
  }

  private RouteStore(@NonNull Context app)
  {
    mApp = app;
    mResolver = app.getContentResolver();
    mDir = new File(app.getFilesDir(), "tz");
    mIndex = new File(mDir, "routes.json");
  }

  /** Called on the UI thread after every change to the library. */
  public void addListener(@NonNull Runnable l)
  {
    mListeners.add(l);
  }

  public void removeListener(@NonNull Runnable l)
  {
    mListeners.remove(l);
  }

  public void list(@NonNull TzIo.Callback<List<Route>> callback)
  {
    TzIo.submit(() -> new ArrayList<>(routes()), callback);
  }

  public void find(@NonNull String id, @NonNull TzIo.Callback<Route> callback)
  {
    TzIo.submit(() -> byId(id), callback);
  }

  @NonNull
  public File gpxFile(@NonNull Route route)
  {
    return new File(mDir, route.file);
  }

  /** The route prepared for the preview / ride, from its cache (rebuilt from the GPX if missing). Null if unreadable. */
  public void loadGeometry(@NonNull Route route, @NonNull TzIo.Callback<RouteGeometry> callback)
  {
    TzIo.submit(() -> {
      final File cache = geometryFile(route.id);
      if (cache.exists())
      {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache))))
        {
          final RouteGeometry g = RouteGeometry.read(in);
          if (g != null)
            return g;
        }
        catch (IOException e)
        {
          Log.w(TAG, "Rebuilding " + cache, e);
        }
      }
      try (InputStream in = new FileInputStream(gpxFile(route)))
      {
        final GpxTrack track = GpxParser.parse(in);
        if (track.size() < 2)
          return null;
        final RouteGeometry g = RouteGeometry.of(track);
        saveGeometry(route.id, g);
        return g;
      }
      catch (IOException e)
      {
        Log.w(TAG, "Cannot read " + route.file, e);
        return null;
      }
    }, callback);
  }

  /**
   * The road surfaces along a route (Phase 23), from its geometry cache, else read from the maps and cached. Read again
   * when some samples had no map and the maps on the phone have changed since. Null before the maps load.
   */
  @MainThread
  public void loadSurfaces(@NonNull Route route, @NonNull RouteGeometry geometry,
                           @NonNull TzIo.Callback<SurfaceProfile> callback)
  {
    final boolean ready = TzRoads.ready(mApp);
    final long maps = ready ? RouteSurfaces.mapsStamp() : 0;
    TzIo.submit(() -> {
      final FollowRoute line = new FollowRoute(geometry.lineLat, geometry.lineLon);
      final SurfaceProfile cached = RouteSurfaces.of(geometry, line.totalM());
      if (!ready)
        return cached;
      if (cached != null && cached.upToDate(geometry.surfaceMaps, maps))
        return cached;
      final double step = SurfaceProfile.step(line.totalM());
      final long start = System.nanoTime();
      final int[] codes = RouteSurfaces.sample(line, step);
      if (TzTrace.on())
        TzTrace.log("surfaces", route.id + " " + codes.length + " samples in " + (System.nanoTime() - start) / 1_000_000
                                + " ms");
      saveGeometry(route.id, geometry.withSurfaces(codes, step, maps));
      return SurfaceProfile.of(codes, line.totalM(), step);
    }, callback);
  }

  public void importGpx(@NonNull Uri uri, @NonNull TzIo.Callback<ImportResult> callback)
  {
    TzIo.submit(() -> {
      final ImportResult r = doImport(uri);
      if (r.route != null)
        notifyChanged();
      return r;
    }, callback);
  }

  public void setFavorite(@NonNull String id, boolean favorite)
  {
    update(id, r -> r.withFavorite(favorite));
  }

  public void rename(@NonNull String id, @NonNull String name)
  {
    update(id, r -> r.withName(name));
  }

  public void markRidden(@NonNull String id, long when)
  {
    update(id, r -> r.withLastRiddenAt(when));
  }

  public void delete(@NonNull String id)
  {
    TzIo.run(() -> {
      final Route r = byId(id);
      if (r == null)
        return;
      routes().remove(r);
      save();
      //noinspection ResultOfMethodCallIgnored
      gpxFile(r).delete();
      //noinspection ResultOfMethodCallIgnored
      geometryFile(r.id).delete();
      notifyChanged();
    });
  }

  private interface Change
  {
    Route apply(Route r);
  }

  private void update(@NonNull String id, @NonNull Change change)
  {
    TzIo.run(() -> {
      final List<Route> routes = routes();
      for (int i = 0; i < routes.size(); i++)
      {
        if (routes.get(i).id.equals(id))
        {
          routes.set(i, change.apply(routes.get(i)));
          save();
          notifyChanged();
          return;
        }
      }
    });
  }

  // ---- IO thread ----

  @NonNull
  private ImportResult doImport(@NonNull Uri uri)
  {
    final String id = UUID.randomUUID().toString();
    final File routesDir = new File(mDir, "routes");
    //noinspection ResultOfMethodCallIgnored
    routesDir.mkdirs();
    final File tmp = new File(routesDir, id + ".gpx.tmp");
    final GpxTrack track;
    try
    {
      try (InputStream in = mResolver.openInputStream(uri); OutputStream out = new FileOutputStream(tmp))
      {
        if (in == null)
          return new ImportResult(null, ImportError.NOT_GPX);
        final byte[] buf = new byte[64 * 1024];
        for (int n; (n = in.read(buf)) > 0;)
          out.write(buf, 0, n);
      }
      try (InputStream in = new FileInputStream(tmp))
      {
        track = GpxParser.parse(in);
      }
    }
    catch (IOException | SecurityException e)
    {
      Log.w(TAG, "Import failed: " + uri, e);
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
      return new ImportResult(null, ImportError.NOT_GPX);
    }
    if (track.size() < 2)
    {
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
      return new ImportResult(null, ImportError.NO_TRACK);
    }

    final RouteStats stats = RouteStats.compute(track.lat, track.lon, track.ele);
    final RouteGeometry geometry = RouteGeometry.of(track);
    // From the already simplified line: the same shape at card size, for a fraction of the work.
    final int[] keep = DouglasPeucker.simplifyToMaxPoints(geometry.lineLat, geometry.lineLon, SILHOUETTE_POINTS);
    final double[] silLat = new double[keep.length];
    final double[] silLon = new double[keep.length];
    for (int i = 0; i < keep.length; i++)
    {
      silLat[i] = geometry.lineLat[keep[i]];
      silLon[i] = geometry.lineLon[keep[i]];
    }
    final String file = "routes/" + id + ".gpx";
    if (!tmp.renameTo(new File(mDir, file)))
    {
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
      return new ImportResult(null, ImportError.NOT_GPX);
    }
    saveGeometry(id, geometry);
    final Route route = new Route(id, track.name != null ? track.name : displayName(uri), file, stats.distanceM,
                                  stats.gainM, stats.highM, stats.kind, false, System.currentTimeMillis(), 0, silLat,
                                  silLon);
    routes().add(0, route);
    save();
    return new ImportResult(route, null);
  }

  @NonNull
  private File geometryFile(@NonNull String id)
  {
    return new File(mDir, "routes/" + id + ".geo");
  }

  /** The cache is derived data: on failure it is simply rebuilt on the next load. */
  private void saveGeometry(@NonNull String id, @NonNull RouteGeometry g)
  {
    final File file = geometryFile(id);
    final File tmp = new File(file.getPath() + ".tmp");
    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))))
    {
      g.write(out);
    }
    catch (IOException e)
    {
      Log.w(TAG, "Cannot cache " + file, e);
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
      return;
    }
    if (!tmp.renameTo(file))
    {
      //noinspection ResultOfMethodCallIgnored
      tmp.delete();
    }
  }

  /** File name without extension, or "Route". */
  @NonNull
  private String displayName(@NonNull Uri uri)
  {
    String name = null;
    try (Cursor c = mResolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null))
    {
      if (c != null && c.moveToFirst())
        name = c.getString(0);
    }
    catch (RuntimeException e)
    {
      // Some providers don't support queries; fall back to the path.
    }
    if (name == null)
      name = uri.getLastPathSegment();
    if (name == null || name.isEmpty())
      return "Route";
    final int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  @Nullable
  private Route byId(@NonNull String id)
  {
    for (Route r : routes())
      if (r.id.equals(id))
        return r;
    return null;
  }

  @NonNull
  private List<Route> routes()
  {
    if (mRoutes == null)
      mRoutes = load();
    return mRoutes;
  }

  @NonNull
  private List<Route> load()
  {
    final List<Route> routes = new ArrayList<>();
    if (!mIndex.exists())
      return routes;
    try
    {
      final JSONArray a = new JSONArray(TzIo.readText(mIndex));
      for (int i = 0; i < a.length(); i++)
        routes.add(Route.fromJson(a.getJSONObject(i)));
    }
    catch (IOException | JSONException e)
    {
      // The index is written atomically, so this means outside tampering; keep the file for inspection.
      Log.e(TAG, "Unreadable " + mIndex, e);
    }
    return routes;
  }

  private void save()
  {
    try
    {
      final JSONArray a = new JSONArray();
      for (Route r : routes())
        a.put(r.toJson());
      //noinspection ResultOfMethodCallIgnored
      mDir.mkdirs();
      TzIo.writeAtomically(mIndex, a.toString());
    }
    catch (IOException | JSONException e)
    {
      Log.e(TAG, "Cannot save " + mIndex, e);
    }
  }

  private void notifyChanged()
  {
    TzIo.post(() -> {
      for (Runnable l : mListeners)
        l.run();
    });
  }
}
