package app.trackzero.routes;

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
}
