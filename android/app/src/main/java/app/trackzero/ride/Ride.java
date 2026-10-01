package app.trackzero.ride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One saved ride as stored in {@code rides.json} (plan §8.5). Immutable. */
public final class Ride
{
  @NonNull
  public final String id;
  @Nullable
  public final String routeId;
  @NonNull
  public final String name;
  /** Relative to the {@code tz} directory, e.g. "rides/<id>.gpx". */
  @NonNull
  public final String file;
  public final long startedAt;
  public final long endedAt;
  public final long movingS;
  public final double distanceM;
  public final double gainM;
  public final double avgKmh;
  public final double maxKmh;
  public final double maxGrade;
  /** At most 200 simplified points of the track, for thumbnails without parsing the GPX. */
  @NonNull
  public final double[] silLat;
  @NonNull
  public final double[] silLon;

  Ride(@NonNull String id, @Nullable String routeId, @NonNull String name, @NonNull String file, long startedAt,
       long endedAt, long movingS, double distanceM, double gainM, double avgKmh, double maxKmh, double maxGrade,
       @NonNull double[] silLat, @NonNull double[] silLon)
  {
    this.id = id;
    this.routeId = routeId;
    this.name = name;
    this.file = file;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.movingS = movingS;
    this.distanceM = distanceM;
    this.gainM = gainM;
    this.avgKmh = avgKmh;
    this.maxKmh = maxKmh;
    this.maxGrade = maxGrade;
    this.silLat = silLat;
    this.silLon = silLon;
  }

  @NonNull
  JSONObject toJson() throws JSONException
  {
    final JSONArray sil = new JSONArray();
    for (int i = 0; i < silLat.length; i++)
    {
      sil.put(Math.round(silLat[i] * 1e5) / 1e5);
      sil.put(Math.round(silLon[i] * 1e5) / 1e5);
    }
    return new JSONObject()
        .put("id", id)
        .put("routeId", routeId == null ? JSONObject.NULL : routeId)
        .put("name", name)
        .put("file", file)
        .put("startedAt", startedAt)
        .put("endedAt", endedAt)
        .put("movingS", movingS)
        .put("distanceM", Math.round(distanceM))
        .put("gainM", Math.round(gainM))
        .put("avgKmh", Math.round(avgKmh * 10) / 10.0)
        .put("maxKmh", Math.round(maxKmh * 10) / 10.0)
        .put("maxGrade", Math.round(maxGrade * 10) / 10.0)
        .put("sil", sil);
  }

  @NonNull
  static Ride fromJson(@NonNull JSONObject o) throws JSONException
  {
    final JSONArray sil = o.optJSONArray("sil");
    final int n = sil == null ? 0 : sil.length() / 2;
    final double[] lat = new double[n];
    final double[] lon = new double[n];
    for (int i = 0; i < n; i++)
    {
      lat[i] = sil.getDouble(2 * i);
      lon[i] = sil.getDouble(2 * i + 1);
    }
    return new Ride(o.getString("id"), o.isNull("routeId") ? null : o.getString("routeId"), o.getString("name"),
                    o.getString("file"), o.getLong("startedAt"), o.getLong("endedAt"), o.getLong("movingS"),
                    o.getDouble("distanceM"), o.getDouble("gainM"), o.getDouble("avgKmh"), o.getDouble("maxKmh"),
                    o.getDouble("maxGrade"), lat, lon);
  }
}
