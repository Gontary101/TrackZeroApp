package app.trackzero.ride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

  Ride(@NonNull String id, @Nullable String routeId, @NonNull String name, @NonNull String file, long startedAt,
       long endedAt, long movingS, double distanceM, double gainM, double avgKmh, double maxKmh, double maxGrade)
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
  }

  @NonNull
  JSONObject toJson() throws JSONException
  {
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
        .put("maxGrade", Math.round(maxGrade * 10) / 10.0);
  }

  @NonNull
  static Ride fromJson(@NonNull JSONObject o) throws JSONException
  {
    return new Ride(o.getString("id"), o.isNull("routeId") ? null : o.getString("routeId"), o.getString("name"),
                    o.getString("file"), o.getLong("startedAt"), o.getLong("endedAt"), o.getLong("movingS"),
                    o.getDouble("distanceM"), o.getDouble("gainM"), o.getDouble("avgKmh"), o.getDouble("maxKmh"),
                    o.getDouble("maxGrade"));
  }
}
