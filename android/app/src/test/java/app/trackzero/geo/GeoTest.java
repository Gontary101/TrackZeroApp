package app.trackzero.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeoTest
{
  @Test
  public void oneDegreeOfLatitudeAtEquator()
  {
    assertEquals(111195, Geo.distance(0, 0, 1, 0), 111195 * 0.005);
  }

  @Test
  public void clermontFerrandToPuyDeDome()
  {
    assertEquals(9500, Geo.distance(45.7772, 3.0870, 45.7726, 2.9644), 9500 * 0.01);
  }

  @Test
  public void dueEastBearing()
  {
    assertEquals(90, Geo.bearing(0, 0, 0, 1), 1e-9);
  }

  @Test
  public void normalizesAnglesIntoSignedRange()
  {
    assertEquals(-90, Geo.normalizeAngle(270), 0);
    assertEquals(180, Geo.normalizeAngle(-180), 0);
    assertEquals(180, Geo.normalizeAngle(540), 0);
    assertEquals(0, Geo.normalizeAngle(-720), 0);
  }
}
