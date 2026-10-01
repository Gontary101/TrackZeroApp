package app.trackzero.follow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ClimbNamesTest
{
  @Test
  public void listNumbersAreDropped()
  {
    assertEquals("Col de Ceyssat", ClimbNames.fromWaypoint("1 — Col de Ceyssat"));
    assertEquals("Col des Goules", ClimbNames.fromWaypoint("03) Col des Goules"));
    assertEquals("Gare de Pontgibaud", ClimbNames.fromWaypoint("A/B — Gare de Pontgibaud"));
    assertEquals("Puy de Dôme", ClimbNames.fromWaypoint("  2. Puy de Dôme "));
  }

  @Test
  public void plainNamesAreKept()
  {
    assertEquals("Jonction Durtol", ClimbNames.fromWaypoint("Jonction Durtol"));
    assertEquals("Col de la Croix-Morand", ClimbNames.fromWaypoint("Col de la Croix-Morand"));
    assertEquals("A75 bridge", ClimbNames.fromWaypoint("A75 bridge"));
    assertEquals("D-Day beach", ClimbNames.fromWaypoint("D-Day beach"));
  }

  @Test
  public void routingMarkersAreNotNames()
  {
    assertNull(ClimbNames.fromWaypoint("via12"));
    assertNull(ClimbNames.fromWaypoint("from"));
    assertNull(ClimbNames.fromWaypoint("To"));
    assertNull(ClimbNames.fromWaypoint("WP 3"));
    assertNull(ClimbNames.fromWaypoint("  "));
    assertNull(ClimbNames.fromWaypoint(null));
  }
}
