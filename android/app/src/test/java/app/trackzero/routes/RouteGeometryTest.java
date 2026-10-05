package app.trackzero.routes;

import app.trackzero.geo.Geo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;

import org.junit.Test;

import static org.junit.Assert.*;

public class RouteGeometryTest
{
  private RouteGeometry fixture(String name) throws Exception
  {
    try (InputStream in = getClass().getResourceAsStream("/gpx/" + name))
    {
      assertNotNull(in);
      return RouteGeometry.of(GpxParser.parse(in));
    }
  }

  private static RouteGeometry roundTrip(RouteGeometry g) throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes))
    {
      g.write(out);
    }
    return RouteGeometry.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
  }

  @Test
  public void cacheRoundTripKeepsLineProfileAndBounds() throws Exception
  {
    RouteGeometry g = fixture("trk.gpx");
    assertNotNull(g.profileDist);
    RouteGeometry r = roundTrip(g);
    assertNotNull(r);
    assertArrayEquals(g.lineLat, r.lineLat, 0);
    assertArrayEquals(g.lineLon, r.lineLon, 0);
    assertArrayEquals(g.profileDist, r.profileDist, 0);
    assertArrayEquals(g.profileAlt, r.profileAlt, 0);
    assertEquals(g.minLat, r.minLat, 0);
    assertEquals(g.minLon, r.minLon, 0);
    assertEquals(g.maxLat, r.maxLat, 0);
    assertEquals(g.maxLon, r.maxLon, 0);
  }

  @Test
  public void cacheRoundTripKeepsSurfaces() throws Exception
  {
    RouteGeometry g = fixture("trk.gpx").withSurfaces(new int[] {1, 1, 3, -1, 0, 4}, 50, 26_100_512L);
    RouteGeometry r = roundTrip(g);
    assertNotNull(r);
    assertArrayEquals(new int[] {1, 1, 3, -1, 0, 4}, r.surfaceCodes());
    assertEquals(50, r.surfaceStepM, 0);
    assertEquals(26_100_512L, r.surfaceMaps);
    assertNull("Not computed yet", roundTrip(fixture("trk.gpx")).surfaces);
  }

  /** A version 2 cache (written before surfaces) is version 3 without them: read, not rebuilt from the GPX. */
  @Test
  public void versionTwoCacheIsReadWithoutSurfaces() throws Exception
  {
    RouteGeometry g = fixture("trk.gpx");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes))
    {
      g.write(out);
    }
    byte[] v3 = bytes.toByteArray();
    // Version 2 = the same without the surface block (step, maps, count -1), under its own magic.
    byte[] v2 = java.util.Arrays.copyOf(v3, v3.length - 8 - 8 - 4);
    v2[3] = 0x02;
    RouteGeometry r = RouteGeometry.read(new DataInputStream(new ByteArrayInputStream(v2)));
    assertNotNull(r);
    assertArrayEquals(g.lineLat, r.lineLat, 0);
    assertArrayEquals(g.profileAlt, r.profileAlt, 0);
    assertNull(r.surfaces);
  }

  @Test
  public void cacheRoundTripWithoutElevation() throws Exception
  {
    RouteGeometry r = roundTrip(fixture("noele.gpx"));
    assertNotNull(r);
    assertNull(r.profileDist);
    assertNull(r.profileAlt);
  }

  @Test
  public void otherFormatVersionIsRejected() throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes))
    {
      out.writeInt(0x545A4700);
    }
    assertNull(RouteGeometry.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
  }

  @Test
  public void alongLineKeepingEveryPointEqualsCumulativeDistance()
  {
    double[] lat = {45.0, 45.001, 45.002, 45.003, 45.004};
    double[] lon = {3.0, 3.002, 3.001, 3.004, 3.003}; // Bends exercise multiple kept segments.
    double[] cumulative = RouteStats.cumulativeDistance(lat, lon);
    int[] keep = {0, 1, 2, 3, 4};
    // Sub-millimetre tolerance for spherical distance rounding.
    assertArrayEquals(cumulative, RouteGeometry.alongLine(cumulative, keep, lat, lon), 0.000001);
  }

  @Test
  public void alongLineKeepingEndpointsSpreadsCollinearPointsProportionally()
  {
    // Uneven longitude gaps at the equator give cumulative fractions 1/8, 3/8 and 1/2.
    double[] lat = {0, 0, 0, 0, 0};
    double[] lon = {0, 0.001, 0.003, 0.004, 0.008};
    double[] cumulative = RouteStats.cumulativeDistance(lat, lon);
    double length = Geo.distance(lat[0], lon[0], lat[4], lon[4]);
    double[] along = RouteGeometry.alongLine(cumulative, new int[] {0, 4},
                                           new double[] {lat[0], lat[4]}, new double[] {lon[0], lon[4]});
    double[] expected = {0, length / 8, 3 * length / 8, length / 2, length};
    assertArrayEquals(expected, along, 0.000001); // Sub-millimetre distance rounding.
    assertEquals(length, along[along.length - 1], 0);
  }

  @Test
  public void resampleInterpolatesEveryFiftyMetresAndIncludesEndpoint()
  {
    double[] x = {0, 100, 250};
    double[] y = {0, 10, 40};
    float[] expected = {0, 5, 10, 20, 30, 40};
    assertArrayEquals(expected, RouteGeometry.resample(x, y, RouteGeometry.ELE_STEP_M), 0);
  }

  @Test
  public void resampleIncludesEndpointBetweenRegularSamples()
  {
    // The last interval is 30 m, so the last sample must use x = 230 rather than 250.
    double[] x = {0, 100, 230};
    double[] y = {0, 10, 36};
    float[] expected = {0, 5, 10, 20, 30, 36};
    assertArrayEquals(expected, RouteGeometry.resample(x, y, RouteGeometry.ELE_STEP_M), 0);
  }

  @Test
  public void cacheRoundTripKeepsResampledElevation() throws Exception
  {
    RouteGeometry g = fixture("trk.gpx");
    assertNotNull(g.ele);
    assertTrue(g.ele.length > 0);
    RouteGeometry r = roundTrip(g);
    assertNotNull(r);
    assertArrayEquals(g.ele, r.ele, 0);
  }

  @Test
  public void cacheRoundTripKeepsWaypointCoordinatesAndNames() throws Exception
  {
    RouteGeometry g = fixture("wpt.gpx");
    assertEquals(3, g.waypoints.size()); // Named, accented and unnamed waypoint fixtures.
    RouteGeometry r = roundTrip(g);
    assertNotNull(r);
    assertEquals(g.waypoints.size(), r.waypoints.size());
    for (int i = 0; i < g.waypoints.size(); i++)
    {
      GpxTrack.Waypoint expected = g.waypoints.get(i);
      GpxTrack.Waypoint actual = r.waypoints.get(i);
      assertEquals(expected.lat, actual.lat, 0);
      assertEquals(expected.lon, actual.lon, 0);
      assertEquals(expected.name, actual.name);
    }
  }

  @Test
  public void versionOneCacheHeaderIsRejected() throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes))
    {
      out.writeInt(0x545A4701); // "TZG", version 1, before elevation and waypoint cache fields.
    }
    assertNull(RouteGeometry.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
  }
}
