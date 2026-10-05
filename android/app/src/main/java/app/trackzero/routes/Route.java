package app.trackzero.routes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One imported GPX route as stored in {@code routes.json} (plan §6.1). Immutable. */
public final class Route
{
  @NonNull
  public final String id;
  @NonNull
  public final String name;
  /** Relative to the {@code tz} directory, e.g. "routes/<id>.gpx". */
  @NonNull
  public final String file;
  public final double distanceM;
  /** NaN when the GPX has no elevation. */
  public final double gainM;
  public final double highM;
  @NonNull
  public final RouteStats.Kind kind;
  public final boolean favorite;
  public final long importedAt;
  public final long lastRiddenAt;
  /** At most 200 simplified points for the card silhouette, so the list never parses GPX files. */
  @NonNull
  public final double[] silLat;
  @NonNull
  public final double[] silLon;
  /** The profile a planned or proposed route was made for (Phase 22); null for an imported GPX. */
  @Nullable
  public final Profile profile;

  Route(@NonNull String id, @NonNull String name, @NonNull String file, double distanceM, double gainM, double highM,
        @NonNull RouteStats.Kind kind, boolean favorite, long importedAt, long lastRiddenAt, @NonNull double[] silLat,
        @NonNull double[] silLon)
  {
    this(id, name, file, distanceM, gainM, highM, kind, favorite, importedAt, lastRiddenAt, silLat, silLon, null);
  }

  Route(@NonNull String id, @NonNull String name, @NonNull String file, double distanceM, double gainM, double highM,
        @NonNull RouteStats.Kind kind, boolean favorite, long importedAt, long lastRiddenAt, @NonNull double[] silLat,
        @NonNull double[] silLon, @Nullable Profile profile)
  {
    this.profile = profile;
    this.id = id;
    this.name = name;
    this.file = file;
    this.distanceM = distanceM;
    this.gainM = gainM;
    this.highM = highM;
    this.kind = kind;
    this.favorite = favorite;
    this.importedAt = importedAt;
    this.lastRiddenAt = lastRiddenAt;
    this.silLat = silLat;
    this.silLon = silLon;
  }

  public boolean hasElevation()
  {
    return !Double.isNaN(gainM);
  }

  @NonNull
  Route withFavorite(boolean f)
  {
    return new Route(id, name, file, distanceM, gainM, highM, kind, f, importedAt, lastRiddenAt, silLat, silLon,
                     profile);
  }

  @NonNull
  Route withName(@NonNull String n)
  {
    return new Route(id, n, file, distanceM, gainM, highM, kind, favorite, importedAt, lastRiddenAt, silLat, silLon,
                     profile);
  }

  @NonNull
  Route withLastRiddenAt(long t)
  {
    return new Route(id, name, file, distanceM, gainM, highM, kind, favorite, importedAt, t, silLat, silLon, profile);
  }

  @NonNull
  JSONObject toJson() throws JSONException
  {
    final JSONArray sil = new JSONArray();
    for (int i = 0; i < silLat.length; i++)
    {
      sil.put(round5(silLat[i]));
      sil.put(round5(silLon[i]));
    }
    final JSONObject o = new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("file", file)
        .put("distanceM", Math.round(distanceM))
        .put("gainM", hasElevation() ? Math.round(gainM) : -1)
        .put("highM", hasElevation() ? Math.round(highM) : -1)
        .put("kind", kind.name())
        .put("hasElevation", hasElevation())
        .put("favorite", favorite)
        .put("importedAt", importedAt)
        .put("lastRiddenAt", lastRiddenAt)
        .put("sil", sil);
    if (profile != null)
      o.put("profile", profile.id);
    return o;
  }

  @NonNull
  static Route fromJson(@NonNull JSONObject o) throws JSONException
  {
    final boolean ele = o.getBoolean("hasElevation");
    final JSONArray sil = o.optJSONArray("sil");
    final int n = sil == null ? 0 : sil.length() / 2;
    final double[] lat = new double[n];
    final double[] lon = new double[n];
    for (int i = 0; i < n; i++)
    {
      lat[i] = sil.getDouble(2 * i);
      lon[i] = sil.getDouble(2 * i + 1);
    }
    return new Route(o.getString("id"), o.getString("name"), o.getString("file"), o.getDouble("distanceM"),
                     ele ? o.getDouble("gainM") : Double.NaN, ele ? o.getDouble("highM") : Double.NaN,
                     RouteStats.Kind.valueOf(o.getString("kind")), o.getBoolean("favorite"), o.getLong("importedAt"),
                     o.getLong("lastRiddenAt"), lat, lon,
                     o.has("profile") ? Profile.of(o.getString("profile")) : null);
  }

  private static double round5(double v)
  {
    return Math.round(v * 1e5) / 1e5;
  }
}
