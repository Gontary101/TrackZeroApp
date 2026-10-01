package app.trackzero.routes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import static org.junit.Assert.*;

public class GpxParserTest
{
  private GpxTrack fixture(String name) throws Exception
  {
    try (InputStream in = getClass().getResourceAsStream("/gpx/" + name))
    {
      assertNotNull(in);
      return GpxParser.parse(in);
    }
  }

  private GpxTrack xml(String value) throws GpxException
  {
    return GpxParser.parse(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void readsAllTracksAndSegments() throws Exception
  {
    GpxTrack track = fixture("trk.gpx");
    assertEquals("Clermont Loop", track.name);
    assertEquals(4, track.size());
    assertEquals(45.7772, track.lat[0], 0);
    assertEquals(3.0870, track.lon[0], 0);
    assertArrayEquals(new double[] {410.5, 420, 430, 440}, track.ele, 0);
    assertEquals(1780308000000L, track.timeMs[0]);
    assertEquals(track.timeMs[0] + 60000, track.timeMs[1]);
    assertEquals(track.timeMs[0] + 120000, track.timeMs[2]);
    assertEquals(0, track.timeMs[3]);
    assertEquals(45.7790, track.lat[2], 0);
    assertEquals(45.7800, track.lat[3], 0);
  }

  @Test
  public void fallsBackToRoutes() throws Exception
  {
    GpxTrack track = fixture("rte.gpx");
    assertEquals("Northbound", track.name);
    assertArrayEquals(new double[] {48, 48.1, 48.2}, track.lat, 0);
    assertArrayEquals(new double[] {2, 2, 2}, track.lon, 0);
    assertTrue(Double.isNaN(track.ele[2]));
  }

  @Test
  public void readsGpx10WithoutElevation() throws Exception
  {
    GpxTrack track = fixture("noele.gpx");
    assertEquals("Old trail", track.name);
    assertEquals(2, track.size());
    for (double ele : track.ele)
    {
      assertTrue(Double.isNaN(ele));
    }
    RouteStats stats = RouteStats.compute(track.lat, track.lon, track.ele);
    assertFalse(stats.hasElevation);
    assertTrue(Double.isNaN(stats.gainM));
    assertTrue(Double.isNaN(stats.highM));
  }

  @Test
  public void readsWaypointsAndIgnoresPointNames() throws Exception
  {
    GpxTrack track = fixture("wpt.gpx");
    assertNull(track.name);
    assertEquals(1, track.size());
    assertEquals(3, track.waypoints.size());
    assertEquals("Puy de Dôme", track.waypoints.get(0).name);
    assertEquals(45.7723, track.waypoints.get(0).lat, 0);
    assertEquals(2.9658, track.waypoints.get(0).lon, 0);
    assertEquals("Col des Goules", track.waypoints.get(1).name);
    assertEquals("", track.waypoints.get(2).name);
    assertThrows(UnsupportedOperationException.class, () -> track.waypoints.clear());
  }

  @Test
  public void rejectsMalformedXmlAndOtherRoots()
  {
    assertThrows(GpxException.class, () -> fixture("bad.gpx"));
    assertThrows(GpxException.class, () -> xml("not XML"));
    assertThrows(GpxException.class, () -> xml("<other><gpx/></other>"));
  }

  @Test
  public void acceptsEmptyTracks() throws Exception
  {
    GpxTrack track = fixture("empty.gpx");
    assertEquals(0, track.size());
    assertNull(track.name);
    assertEquals(0, track.lon.length);
    assertEquals(0, track.ele.length);
    assertEquals(0, track.timeMs.length);
    assertTrue(track.waypoints.isEmpty());
  }

  @Test
  public void skipsInvalidCoordinatesAndOptionalValues() throws Exception
  {
    GpxTrack track = xml("<gpx><trk><trkseg>"
        + "<trkpt lon='2'/><trkpt lat='bad' lon='2'/><trkpt lat='NaN' lon='2'/>"
        + "<trkpt lat='Infinity' lon='2'/><trkpt lat='91' lon='2'/>"
        + "<trkpt lat='45' lon='181'/><trkpt lat='45' lon='2'>"
        + "<ele>invalid</ele><time>invalid</time></trkpt></trkseg></trk></gpx>");
    assertEquals(1, track.size());
    assertTrue(Double.isNaN(track.ele[0]));
    assertEquals(0, track.timeMs[0]);
  }

  @Test
  public void usesLocalNamesAndNamePrecedence() throws Exception
  {
    GpxTrack track = xml("<g:gpx xmlns:g='http://www.topografix.com/GPX/1/1'>"
        + "<g:metadata><g:name> </g:name></g:metadata>"
        + "<g:rte><g:name>Route</g:name><g:rtept lat='45' lon='2'/></g:rte>"
        + "<g:trk><g:name> First </g:name><g:trkseg/></g:trk>"
        + "<g:trk><g:name>Second</g:name></g:trk></g:gpx>");
    assertEquals("First", track.name);
    assertEquals(1, track.size());
    assertEquals("Route", xml("<gpx><trk><name> </name></trk>"
        + "<trk><name>Second</name></trk><rte><name>Route</name></rte></gpx>").name);
  }

  @Test
  public void ignoresNestedExtensionPoints() throws Exception
  {
    GpxTrack track = xml("<gpx><extensions><wpt lat='45' lon='2'/><trk><trkseg>"
        + "<trkpt lat='45' lon='2'/></trkseg></trk></extensions></gpx>");
    assertEquals(0, track.size());
    assertTrue(track.waypoints.isEmpty());
  }

  @Test
  public void doesNotLoadExternalEntitiesOrDtds() throws Exception
  {
    GpxTrack track = xml("<!DOCTYPE gpx SYSTEM 'file:///nonexistent-trackzero-test.dtd' "
        + "[<!ENTITY external SYSTEM 'file:///nonexistent-trackzero-test.txt'>]>"
        + "<gpx><metadata><name>&external;</name></metadata></gpx>");
    assertEquals(0, track.size());
    assertNull(track.name);
  }
}
