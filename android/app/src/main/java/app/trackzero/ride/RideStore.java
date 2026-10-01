package app.trackzero.ride;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.TzIo;
import app.trackzero.routes.RouteStore;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Saved rides ({@code files/tz/rides/<id>.gpx} + atomic {@code rides.json}) and the journal of the ride in progress.
 * All state lives on the {@link TzIo} thread.
 */
public final class RideStore
{
  private static final String TAG = "TzRideStore";

  /** A finished ride handed over for saving; the track is no longer modified. */
  static final class Finished
  {
    @Nullable
    final String routeId;
    @NonNull
    final String name;
    final long startedAt;
    final long endedAt;
    @NonNull
    final List<List<RideTrack.Point>> segments;
    @NonNull
    final RideStats.Snapshot totals;

    Finished(@Nullable String routeId, @NonNull String name, long startedAt, long endedAt,
             @NonNull List<List<RideTrack.Point>> segments, @NonNull RideStats.Snapshot totals)
    {
      this.routeId = routeId;
      this.name = name;
      this.startedAt = startedAt;
      this.endedAt = endedAt;
      this.segments = segments;
      this.totals = totals;
    }
  }

  @Nullable
  private static RideStore sInstance;

  @NonNull
  private final Context mApp;
  @NonNull
  private final File mDir;
  @NonNull
  private final File mIndex;
  @NonNull
  private final File mJournal;
  /** IO thread only; newest first. Null until loaded. */
  @Nullable
  private List<Ride> mRides;
  private final List<Runnable> mListeners = new CopyOnWriteArrayList<>();

  @NonNull
  public static synchronized RideStore get(@NonNull Context context)
  {
    if (sInstance == null)
      sInstance = new RideStore(context.getApplicationContext());
    return sInstance;
  }

  private RideStore(@NonNull Context app)
  {
    mApp = app;
    mDir = new File(app.getFilesDir(), "tz");
    mIndex = new File(mDir, "rides.json");
    mJournal = new File(mDir, "rides/current.partial");
  }

  public void addListener(@NonNull Runnable l)
  {
    mListeners.add(l);
  }

  public void removeListener(@NonNull Runnable l)
  {
    mListeners.remove(l);
  }

  public void list(@NonNull TzIo.Callback<List<Ride>> callback)
  {
    TzIo.submit(() -> new ArrayList<>(rides()), callback);
  }

  /** Writes the GPX and the index entry; the callback gets null if the GPX could not be written. */
  void save(@NonNull Finished f, @NonNull TzIo.Callback<Ride> callback)
  {
    TzIo.submit(() -> {
      final String id = UUID.randomUUID().toString();
      final String file = "rides/" + id + ".gpx";
      final File gpx = new File(mDir, file);
      final File tmp = new File(gpx.getPath() + ".tmp");
      //noinspection ResultOfMethodCallIgnored
      gpx.getParentFile().mkdirs();
      try (Writer out = new BufferedWriter(
               new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024))
      {
        GpxWriter.write(out, f.name, f.segments);
      }
      catch (IOException e)
      {
        Log.e(TAG, "Cannot write " + gpx, e);
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        return null;
      }
      if (!tmp.renameTo(gpx))
      {
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        return null;
      }
      final RideStats.Snapshot t = f.totals;
      final long movingS = t.movingMs / 1000;
      final double avgKmh = movingS > 0 ? t.distanceM / movingS * 3.6 : 0;
      final Ride ride = new Ride(id, f.routeId, f.name, file, f.startedAt, f.endedAt, movingS, t.distanceM,
                                 t.gainM, avgKmh, t.maxKmh, t.maxGradePct);
      rides().add(0, ride);
      saveIndex();
      //noinspection ResultOfMethodCallIgnored
      mJournal.delete();
      if (f.routeId != null)
        RouteStore.get(mApp).markRidden(f.routeId, f.endedAt);
      notifyChanged();
      return ride;
    }, callback);
  }

  // ---- Journal of the ride in progress ----

  /** Replaces the journal (a ride starts). */
  void startJournal(@NonNull String text)
  {
    TzIo.run(() -> write(text, false));
  }

  void appendJournal(@NonNull String text)
  {
    TzIo.run(() -> write(text, true));
  }

  /** The journal left by a ride that did not end, or null. */
  void readJournal(@NonNull TzIo.Callback<RideJournal.Content> callback)
  {
    TzIo.submit(() -> {
      if (!mJournal.exists())
        return null;
      try
      {
        return RideJournal.parse(TzIo.readText(mJournal));
      }
      catch (IOException e)
      {
        Log.e(TAG, "Unreadable " + mJournal, e);
        return null;
      }
    }, callback);
  }

  /** Whether a ride was left unfinished; a cheap check usable on the main thread. */
  boolean hasJournal()
  {
    return mJournal.exists();
  }

  private void write(@NonNull String text, boolean append)
  {
    //noinspection ResultOfMethodCallIgnored
    mJournal.getParentFile().mkdirs();
    try (FileOutputStream out = new FileOutputStream(mJournal, append))
    {
      out.write(text.getBytes(StandardCharsets.UTF_8));
    }
    catch (IOException e)
    {
      Log.e(TAG, "Cannot write " + mJournal, e);
    }
  }

  // ---- IO thread ----

  @NonNull
  private List<Ride> rides()
  {
    if (mRides == null)
      mRides = load();
    return mRides;
  }

  @NonNull
  private List<Ride> load()
  {
    final List<Ride> rides = new ArrayList<>();
    if (!mIndex.exists())
      return rides;
    try
    {
      final JSONArray a = new JSONArray(TzIo.readText(mIndex));
      for (int i = 0; i < a.length(); i++)
        rides.add(Ride.fromJson(a.getJSONObject(i)));
    }
    catch (IOException | JSONException e)
    {
      Log.e(TAG, "Unreadable " + mIndex, e);
    }
    return rides;
  }

  private void saveIndex()
  {
    try
    {
      final JSONArray a = new JSONArray();
      for (Ride r : rides())
        a.put(r.toJson());
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
