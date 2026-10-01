package app.trackzero.ride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * The crash-safety journal of the ride in progress ({@code tz/rides/current.partial}, plan §8.3): append-only text,
 * one record per line.
 * <pre>
 * H,1,{startedAt},{routeId or -},{name}     header, first line
 * S                                         the next point starts a new segment
 * P,{time},{lat},{lon},{alt or NaN}         a recorded point
 * T,{distanceM},{movingMs},{gainM},{maxKmh},{maxGrade}   totals so far (the last one wins)
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

    Content(long startedAt, @Nullable String routeId, @NonNull String name, @NonNull RideTrack track,
            @NonNull RideStats.Snapshot totals)
    {
      this.startedAt = startedAt;
      this.routeId = routeId;
      this.name = name;
      this.track = track;
      this.totals = totals;
    }
  }

  private RideJournal() {}

  static void header(@NonNull StringBuilder out, long startedAt, @Nullable String routeId, @NonNull String name)
  {
    out.append("H,").append(VERSION).append(',').append(startedAt).append(',')
        .append(routeId == null ? "-" : routeId).append(',')
        .append(name.replace('\n', ' ').replace('\r', ' ')).append('\n');
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
    for (int i = 1; i < lines.length; i++)
    {
      final String[] f = lines[i].split(",");
      try
      {
        switch (f[0])
        {
        case "S" -> track.breakSegment();
        case "P" ->
        {
          if (f.length == 5)
            track.add(new RideTrack.Point(Long.parseLong(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                                          Double.parseDouble(f[4])));
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
    return new Content(startedAt, routeId, h[4], track, totals);
  }
}
