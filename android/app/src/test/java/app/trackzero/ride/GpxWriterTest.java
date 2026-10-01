package app.trackzero.ride;

import app.trackzero.routes.GpxParser;
import app.trackzero.routes.GpxTrack;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import static org.junit.Assert.*;

public class GpxWriterTest
{
  private GpxTrack parse(String xml) throws Exception
  {
    return GpxParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void roundTripsAllSegments() throws Exception
  {
    final long start = 1780304400000L;
    final List<RideTrack.Point> first = Arrays.asList(
        new RideTrack.Point(start, 45.7772123, 3.0870123, 410.5),
        new RideTrack.Point(start + 60000, 45.7782345, 3.0882345, 420.2),
        new RideTrack.Point(start + 120000, 45.7793456, 3.0893456, 430.7));
    final List<RideTrack.Point> second = Arrays.asList(
        new RideTrack.Point(start + 180000, 45.7804567, 3.0904567, 440.1),
        new RideTrack.Point(start + 240000, 45.7815678, 3.0915678, 450.9));
    final StringWriter out = new StringWriter();
    GpxWriter.write(out, "Clermont Loop", Arrays.asList(first, second));
    final String xml = out.toString();
    final GpxTrack track = parse(xml);
    assertEquals("Clermont Loop", track.name);
    assertEquals(5, track.size());
    int index = 0;
    for (final List<RideTrack.Point> segment : Arrays.asList(first, second))
    {
      for (final RideTrack.Point point : segment)
      {
        assertEquals(point.lat, track.lat[index], 1e-7);
        assertEquals(point.lon, track.lon[index], 1e-7);
        assertEquals(point.altM, track.ele[index], 0.05);
        assertEquals(point.timeMs, track.timeMs[index]);
        index++;
      }
    }
    assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"));
    assertTrue(xml.contains("<gpx version=\"1.1\" creator=\"TrackZero\" xmlns=\"http://www.topografix.com/GPX/1/1\">"));
    assertTrue(xml.contains("    <time>2026-06-01T09:00:00Z</time>"));
    assertEquals(2, xml.split("<trkseg>", -1).length - 1);
    assertEquals(1, xml.split("<trk>", -1).length - 1);
  }

  @Test
  public void omitsUnknownAltitude() throws Exception
  {
    final StringWriter out = new StringWriter();
    final RideTrack.Point point = new RideTrack.Point(1780304400000L, 45.7, 3.1, Double.NaN);
    GpxWriter.write(out, "No altitude", Collections.singletonList(Collections.singletonList(point)));
    final GpxTrack track = parse(out.toString());
    assertEquals(1, track.size());
    assertTrue(Double.isNaN(track.ele[0]));
    assertFalse(out.toString().contains("<ele>"));
  }

  @Test
  public void escapesSpecialCharactersInName() throws Exception
  {
    final String name = "Café <Puy> & \"Dôme\"";
    final StringWriter out = new StringWriter();
    GpxWriter.write(out, name, Collections.emptyList());
    assertEquals(name, parse(out.toString()).name);
    assertTrue(out.toString().contains("Café &lt;Puy&gt; &amp; &quot;Dôme&quot;"));

    final StringWriter apostropheOut = new StringWriter();
    GpxWriter.write(apostropheOut, "Ride's name", Collections.emptyList());
    assertTrue(apostropheOut.toString().contains("Ride&apos;s name"));
    assertEquals("Ride's name", parse(apostropheOut.toString()).name);
  }

  @Test
  public void usesDecimalPointUnderGermanLocale() throws Exception
  {
    final Locale original = Locale.getDefault();
    try
    {
      Locale.setDefault(Locale.GERMANY);
      final StringWriter out = new StringWriter();
      final RideTrack.Point point = new RideTrack.Point(1780304400000L, 45.7772123, 3.0870123, 410.5);
      GpxWriter.write(out, "German locale", Collections.singletonList(Collections.singletonList(point)));
      final String xml = out.toString();
      assertTrue(xml.contains("lat=\"45.7772123\" lon=\"3.0870123\""));
      assertTrue(xml.contains("<ele>410.5</ele>"));
      final GpxTrack track = parse(xml);
      assertEquals(1, track.size());
      assertEquals(point.lat, track.lat[0], 1e-7);
      assertEquals(point.lon, track.lon[0], 1e-7);
      assertEquals(point.altM, track.ele[0], 0.05);
      assertEquals(point.timeMs, track.timeMs[0]);
    }
    finally
    {
      Locale.setDefault(original);
    }
  }

  @Test
  public void writesEmptyTrackWithoutTime() throws Exception
  {
    final StringWriter out = new StringWriter();
    GpxWriter.write(out, "Empty ride", Collections.emptyList());
    final GpxTrack track = parse(out.toString());
    assertEquals(0, track.size());
    assertEquals("Empty ride", track.name);
    assertFalse(out.toString().contains("<time>"));
    assertTrue(out.toString().contains("<trk>"));
  }

  @Test
  public void fixedPointFormatting()
  {
    final StringBuilder out = new StringBuilder();
    GpxWriter.appendFixed(out, 45.7772, 7);
    out.append(' ');
    GpxWriter.appendFixed(out, -3.0000051, 7);
    out.append(' ');
    GpxWriter.appendFixed(out, -0.00000004, 7);
    out.append(' ');
    GpxWriter.appendFixed(out, 1077.06, 1);
    out.append(' ');
    GpxWriter.appendFixed(out, 0.04, 1);
    assertEquals("45.7772000 -3.0000051 0.0000000 1077.1 0.0", out.toString());
  }
}
