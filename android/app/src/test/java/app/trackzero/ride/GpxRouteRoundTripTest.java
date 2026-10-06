package app.trackzero.ride;

import static org.junit.Assert.assertEquals;

import app.trackzero.routes.GpxParser;
import app.trackzero.routes.GpxTrack;
import app.trackzero.routes.RouteStats;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

/** A planned route (Phase 28) written as GPX parses back the same: distance within 0.5 %, its points as waypoints. */
public class GpxRouteRoundTripTest
{
  @Test
  public void plannedRouteParsesBack() throws Exception
  {
    final int n = 400;
    final double[] lat = new double[n], lon = new double[n], ele = new double[n];
    for (int i = 0; i < n; i++)
    {
      // A 20 km wiggle north-east of Clermont-Ferrand, climbing.
      lat[i] = 45.77 + i * 0.00035;
      lon[i] = 3.08 + 0.002 * Math.sin(i / 10.0) + i * 0.0002;
      ele[i] = 400 + i * 0.8;
    }
    final List<GpxTrack.Waypoint> points = List.of(new GpxTrack.Waypoint(lat[150], lon[150], "Chemin & co"),
                                                   new GpxTrack.Waypoint(lat[n - 1], lon[n - 1], "Col de Ceyssat"));
    final StringWriter out = new StringWriter();
    GpxWriter.writeRoute(out, "To Col de Ceyssat", lat, lon, ele, points);

    final GpxTrack back = GpxParser.parse(new ByteArrayInputStream(out.toString().getBytes(StandardCharsets.UTF_8)));
    assertEquals("To Col de Ceyssat", back.name);
    assertEquals(n, back.size());
    final double before = RouteStats.compute(lat, lon, ele).distanceM;
    final double after = RouteStats.compute(back.lat, back.lon, back.ele).distanceM;
    assertEquals(before, after, before * 0.005);
    assertEquals(2, back.waypoints.size());
    assertEquals("Chemin & co", back.waypoints.get(0).name);
    assertEquals(ele[n - 1], back.ele[n - 1], 0.1);
  }
}
