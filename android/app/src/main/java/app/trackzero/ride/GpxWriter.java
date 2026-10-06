package app.trackzero.ride;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.trackzero.routes.GpxTrack;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Writes recorded ride segments, and planned routes (Phase 28), as GPX 1.1. */
public final class GpxWriter
{
  private GpxWriter()
  {
  }

  /** Appends {@code value} rounded to {@code decimals} places with a '.' separator (String.format is slow per point). */
  static void appendFixed(@NonNull StringBuilder out, double value, int decimals)
  {
    long scale = 1;
    for (int i = 0; i < decimals; i++)
    {
      scale *= 10;
    }
    long fixed = Math.round(Math.abs(value) * scale);
    if (value < 0 && fixed != 0)
    {
      out.append('-');
    }
    out.append(fixed / scale).append('.');
    final String fraction = Long.toString(fixed % scale);
    for (int i = fraction.length(); i < decimals; i++)
    {
      out.append('0');
    }
    out.append(fraction);
  }

  public static void write(@NonNull Writer out, @NonNull String trackName,
                           @NonNull List<List<RideTrack.Point>> segments) throws IOException
  {
    final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
    timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    final Date date = new Date(0);
    final String escapedName = trackName.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");

    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    out.write("<gpx version=\"1.1\" creator=\"TrackZero\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
    out.write("  <metadata>\n");
    out.write("    <name>" + escapedName + "</name>\n");
    for (final List<RideTrack.Point> segment : segments)
    {
      if (!segment.isEmpty())
      {
        date.setTime(segment.get(0).timeMs);
        out.write("    <time>" + timeFormat.format(date) + "</time>\n");
        break;
      }
    }
    out.write("  </metadata>\n");
    out.write("  <trk>\n");
    out.write("    <name>" + escapedName + "</name>\n");
    final StringBuilder line = new StringBuilder(160);
    for (final List<RideTrack.Point> segment : segments)
    {
      out.write("    <trkseg>\n");
      for (final RideTrack.Point point : segment)
      {
        line.setLength(0);
        line.append("      <trkpt lat=\"");
        appendFixed(line, point.lat, 7);
        line.append("\" lon=\"");
        appendFixed(line, point.lon, 7);
        line.append("\">\n");
        if (!Double.isNaN(point.altM))
        {
          line.append("        <ele>");
          appendFixed(line, point.altM, 1);
          line.append("</ele>\n");
        }
        date.setTime(point.timeMs);
        line.append("        <time>").append(timeFormat.format(date)).append("</time>\n");
        line.append("      </trkpt>\n");
        out.append(line);
      }
      out.write("    </trkseg>\n");
    }
    out.write("  </trk>\n");
    out.write("</gpx>\n");
  }

  /**
   * A planned route (Phase 28): its points as {@code wpt} (named when known), then the path as one track with
   * elevations ({@code ele} may be null). No times: it has not been ridden.
   */
  public static void writeRoute(@NonNull Writer out, @NonNull String name, @NonNull double[] lat,
                                @NonNull double[] lon, @Nullable double[] ele,
                                @NonNull List<GpxTrack.Waypoint> waypoints) throws IOException
  {
    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    out.write("<gpx version=\"1.1\" creator=\"TrackZero\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
    out.write("  <metadata>\n    <name>" + escape(name) + "</name>\n  </metadata>\n");
    final StringBuilder line = new StringBuilder(160);
    for (GpxTrack.Waypoint w : waypoints)
    {
      line.setLength(0);
      line.append("  <wpt lat=\"");
      appendFixed(line, w.lat, 7);
      line.append("\" lon=\"");
      appendFixed(line, w.lon, 7);
      line.append("\">");
      if (w.name != null && !w.name.isEmpty())
        line.append("<name>").append(escape(w.name)).append("</name>");
      line.append("</wpt>\n");
      out.append(line);
    }
    out.write("  <trk>\n    <name>" + escape(name) + "</name>\n    <trkseg>\n");
    for (int i = 0; i < lat.length; i++)
    {
      line.setLength(0);
      line.append("      <trkpt lat=\"");
      appendFixed(line, lat[i], 7);
      line.append("\" lon=\"");
      appendFixed(line, lon[i], 7);
      line.append('"');
      if (ele != null && !Double.isNaN(ele[i]))
      {
        line.append("><ele>");
        appendFixed(line, ele[i], 1);
        line.append("</ele></trkpt>\n");
      }
      else
      {
        line.append("/>\n");
      }
      out.append(line);
    }
    out.write("    </trkseg>\n  </trk>\n</gpx>\n");
  }

  @NonNull
  private static String escape(@NonNull String s)
  {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
