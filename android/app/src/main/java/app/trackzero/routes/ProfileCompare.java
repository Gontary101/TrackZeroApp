package app.trackzero.routes;

import android.content.Context;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import app.trackzero.TzIo;
import app.trackzero.TzTrace;
import app.trackzero.base.TzRoads;
import app.trackzero.base.TzRouter;
import app.trackzero.follow.FollowRoute;
import app.trackzero.follow.RoutedPath;
import java.util.Locale;

/**
 * The profile check of Phase 24 (About › hidden tools › Compare profiles; see {@code tools/trackzero/profile_compare.md}):
 * ten fixed trips on the Puy-de-Dôme map, each routed with every profile switched on, one line per path in the log (tag
 * TzProfileCompare, and the trace): length, climb, surface shares (Phase 23) and road-class shares.
 */
public final class ProfileCompare
{
  private static final String TAG = "TzProfileCompare";

  /** {name, from lat, from lon, to lat, to lon}. */
  private static final Object[][] TRIPS = {
      {"Clermont centre - Orcines", 45.7772, 3.0870, 45.7833, 3.0128},
      {"Aubiere - Lac d'Aydat", 45.7508, 3.1110, 45.6625, 2.9886},
      {"Royat - Col de Ceyssat", 45.7660, 3.0510, 45.7650, 2.9300},
      {"Volvic - Pontgibaud", 45.8717, 3.0383, 45.8333, 2.8500},
      {"Riom - Chatel-Guyon", 45.8936, 3.1125, 45.9228, 3.0656},
      {"Clermont - Plateau de Gergovie", 45.7772, 3.0870, 45.7092, 3.1228},
      {"Beaumont - Saint-Genes-Champanelle", 45.7519, 3.0836, 45.7206, 3.0189},
      {"Thiers - Courpiere", 45.8560, 3.5470, 45.7558, 3.5394},
      {"Issoire - Saint-Nectaire", 45.5442, 3.2489, 45.5878, 3.0011},
      {"Le Mont-Dore - La Bourboule", 45.5764, 2.8094, 45.5894, 2.7469},
      // One-way westwards with cycling both ways (oneway:bicycle=no, OSM way 4080952), 300 m: against the one-way,
      // the path should be the street itself, not a detour (plan v2 24.5).
      {"Contraflow: Rue Rameau eastwards", 45.7755983, 3.0752260, 45.7758832, 3.0790987},
  };
  /** The profiles switched on, in the sheet's order. */
  private static final Profile[] PROFILES = Profile.available().toArray(new Profile[0]);
  private static final String[] CLASSES = {"none", "trunk", "primary", "secondary", "tertiary", "minor", "track",
                                           "path"};

  private ProfileCompare() {}

  /** Runs all trips one after the other; {@code done} on the main thread at the end. */
  @MainThread
  public static void run(@NonNull Context context, @NonNull Runnable done)
  {
    log("trip | profile | km | D+ m | paved/poor/gravel/rough/none % | none/trunk/primary/secondary/tertiary/minor/"
        + "track/path %");
    next(context.getApplicationContext(), 0, done);
  }

  private static void next(@NonNull Context context, int step, @NonNull Runnable done)
  {
    if (step >= TRIPS.length * PROFILES.length)
    {
      TzRouter.release();
      log("done");
      done.run();
      return;
    }
    final Object[] trip = TRIPS[step / PROFILES.length];
    final Profile profile = PROFILES[step % PROFILES.length];
    TzRouter.route((double) trip[1], (double) trip[2], (double) trip[3], (double) trip[4], profile, Double.NaN,
                   (path, reason) -> {
                     if (path == null)
                     {
                       log(trip[0] + " | " + profile.id + " | no path: " + reason);
                       next(context, step + 1, done);
                       return;
                     }
                     TzIo.submit(() -> describe(path), line -> {
                       log(trip[0] + " | " + profile.id + " | " + line);
                       next(context, step + 1, done);
                     });
                   });
  }

  @NonNull
  private static String describe(@NonNull RoutedPath path)
  {
    final FollowRoute line = path.line;
    final double step = SurfaceProfile.step(line.totalM());
    final SurfaceProfile surfaces = SurfaceProfile.of(RouteSurfaces.sample(line, step), line.totalM(), step);
    final int n = SurfaceProfile.samples(line.totalM(), step);
    final double[] lat = new double[n];
    final double[] lon = new double[n];
    for (int i = 0; i < n; i++)
    {
      final double at = SurfaceProfile.at(i, line.totalM(), step);
      lat[i] = line.latAt(at);
      lon[i] = line.lonAt(at);
    }
    final int[] classes = TzRoads.classes(lat, lon);
    final int[] count = new int[CLASSES.length];
    for (int c : classes)
      count[Math.max(0, c)]++;
    final StringBuilder sb = new StringBuilder();
    final double gain = path.ele == null ? Double.NaN
                                         : RouteStats.compute(line.lats(), line.lons(), path.ele).gainM;
    sb.append(String.format(Locale.ROOT, "%.1f | %.0f |", line.totalM() / 1000, gain));
    for (int p : surfaces.percent)
      sb.append(' ').append(p);
    sb.append(" |");
    for (int c : count)
      sb.append(' ').append(Math.round(100.0 * c / n));
    return sb.toString();
  }

  private static void log(@NonNull String line)
  {
    Log.i(TAG, line);
    if (TzTrace.on())
      TzTrace.log("compare", line);
  }
}
