package app.trackzero.ride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.follow.Stop;
import app.trackzero.routes.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The crash-safety journal of the ride in progress ({@code tz/rides/current.partial}, plan §8.3): append-only text,
 * one record per line.
 * <pre>
 * H,1,{startedAt},{routeId or -},{name}     header, first line
 * F,{profile id}                            the ride's profile (after the header; none: road, Phase 22)
 * D,{lat},{lon}                             a ride to a place: where to (after the header)
 * S                                         the next point starts a new segment
 * P,{time},{lat},{lon},{alt or NaN}         a recorded point
 * T,{distanceM},{movingMs},{gainM},{maxKmh},{maxGrade}   totals so far (the last one wins)
 * E,{totalS},{basis}                       the ride-time estimate made at the start (Phase 33)
 * A,{kind},{lat},{lon},{name}              a stop added (Phase 30)
 * X,{lat},{lon}                            that stop reached or removed
 * </pre>
 * A line cut short by a crash is ignored.
 */
public final class RideJournal
{
  private static final String VERSION = "1";

  /** What a journal restores. */
  public static final class Content
  {
    public final long startedAt;
    @Nullable
    public final String routeId;
    @NonNull
    public final String name;
    @NonNull
    public final RideTrack track;
    @NonNull
    public final RideStats.Snapshot totals;
    /** A ride to a place: where to; NaN otherwise. */
    public final double placeLat;
    public final double placeLon;
    @NonNull
    public final Profile profile;
    /** Stops added and not yet reached or removed (Phase 30). */
    @NonNull
    public final List<Stop> stops;
    /** The estimate made at the start (Phase 33): seconds, -1 none; rides it came from, 0 a typical pace. */
    public final long estimateS;
    public final int estimateBasis;

    Content(long startedAt, @Nullable String routeId, @NonNull String name, @NonNull RideTrack track,
            @NonNull RideStats.Snapshot totals, double placeLat, double placeLon, @NonNull Profile profile,
            @NonNull List<Stop> stops, long estimateS, int estimateBasis)
    {
      this.stops = stops;
      this.estimateS = estimateS;
      this.estimateBasis = estimateBasis;
      this.profile = profile;
      this.startedAt = startedAt;
      this.routeId = routeId;
      this.name = name;
      this.track = track;
      this.totals = totals;
      this.placeLat = placeLat;
      this.placeLon = placeLon;
    }
  }

  private RideJournal() {}

  static void header(@NonNull StringBuilder out, long startedAt, @Nullable String routeId, @NonNull String name)
  {
    out.append("H,").append(VERSION).append(',').append(startedAt).append(',')
        .append(routeId == null ? "-" : routeId).append(',')
        .append(name.replace('\n', ' ').replace('\r', ' ')).append('\n');
  }

  static void profile(@NonNull StringBuilder out, @NonNull Profile p)
  {
    out.append("F,").append(p.id).append('\n');
  }

  static void place(@NonNull StringBuilder out, double lat, double lon)
  {
    out.append("D,").append(lat).append(',').append(lon).append('\n');
  }

  static void estimate(@NonNull StringBuilder out, long totalS, int basis)
  {
    out.append("E,").append(totalS).append(',').append(basis).append('\n');
  }

  static void stopAdded(@NonNull StringBuilder out, @NonNull Stop stop)
  {
    out.append("A,").append(stop.kind).append(',').append(stop.lat).append(',').append(stop.lon).append(',')
        .append(stop.name.replace('\n', ' ').replace('\r', ' ')).append('\n');
  }

  static void stopGone(@NonNull StringBuilder out, @NonNull Stop stop)
  {
    out.append("X,").append(stop.lat).append(',').append(stop.lon).append('\n');
  }

  static void segment(@NonNull StringBuilder out)
  {
    out.append("S\n");
  }

  static void point(@NonNull StringBuilder out, @NonNull RideTrack.Point p)
  {
    out.append("P,").append(p.timeMs).append(',').append(p.lat).append(',').append(p.lon).append(',')
        .append(p.altM).append('\n');
  }

  @NonNull
  static String totals(@NonNull RideStats.Snapshot s)
  {
    return String.format(Locale.ROOT, "T,%.1f,%d,%.1f,%.2f,%.2f\n", s.distanceM, s.movingMs, s.gainM, s.maxKmh,
                         s.maxGradePct);
  }

  /** Null when the text has no valid header (nothing to restore). */
  @Nullable
  public static Content parse(@NonNull String text)
  {
    final String[] lines = text.split("\n");
    if (lines.length == 0)
      return null;
    final String[] h = lines[0].split(",", 5);
    if (h.length < 5 || !"H".equals(h[0]) || !VERSION.equals(h[1]))
      return null;
    final long startedAt;
    try
    {
      startedAt = Long.parseLong(h[2]);
    }
    catch (NumberFormatException e)
    {
      return null;
    }
    final String routeId = "-".equals(h[3]) ? null : h[3];
    final RideTrack track = new RideTrack();
    RideStats.Snapshot totals = new RideStats.Snapshot(0, 0, 0, 0, 0);
    double placeLat = Double.NaN;
    double placeLon = Double.NaN;
    Profile profile = Profile.ROAD;
    final List<Stop> stops = new ArrayList<>();
    long estimateS = -1;
    int estimateBasis = 0;
    for (int i = 1; i < lines.length; i++)
    {
      if (lines[i].startsWith("A,"))
      {
        // The name may hold commas: the last field takes the rest of the line.
        final String[] a = lines[i].split(",", 5);
        try
        {
          if (a.length == 5)
            stops.add(new Stop(a[4], Integer.parseInt(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])));
        }
        catch (NumberFormatException e)
        {
          // Cut short by a crash.
        }
        continue;
      }
      final String[] f = lines[i].split(",");
      try
      {
        switch (f[0])
        {
        case "S" -> track.breakSegment();
        case "F" ->
        {
          if (f.length == 2)
            profile = Profile.of(f[1]);
        }
        case "D" ->
        {
          if (f.length == 3)
          {
            placeLat = Double.parseDouble(f[1]);
            placeLon = Double.parseDouble(f[2]);
          }
        }
        case "P" ->
        {
          if (f.length == 5)
            track.add(new RideTrack.Point(Long.parseLong(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                                          Double.parseDouble(f[4])));
        }
        case "E" ->
        {
          if (f.length == 3)
          {
            estimateS = Long.parseLong(f[1]);
            estimateBasis = Integer.parseInt(f[2]);
          }
        }
        case "X" ->
        {
          if (f.length == 3)
          {
            final Stop gone = new Stop("", 0, Double.parseDouble(f[1]), Double.parseDouble(f[2]));
            stops.removeIf(st -> st.same(gone));
          }
        }
        case "T" ->
        {
          if (f.length == 6)
            totals = new RideStats.Snapshot(Double.parseDouble(f[1]), Long.parseLong(f[2]), Double.parseDouble(f[3]),
                                            Double.parseDouble(f[4]), Double.parseDouble(f[5]));
        }
        default -> {}
        }
      }
      catch (NumberFormatException e)
      {
        // A record cut short by a crash: the rest of the journal is still good.
      }
    }
    return new Content(startedAt, routeId, h[4], track, totals, placeLat, placeLon, profile, stops, estimateS,
                       estimateBasis);
  }
}
