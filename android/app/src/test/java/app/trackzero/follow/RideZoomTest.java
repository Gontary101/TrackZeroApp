package app.trackzero.follow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RideZoomTest
{
  @Test
  public void bands()
  {
    assertEquals(17, RideZoom.band(0));
    assertEquals(17, RideZoom.band(14.9));
    assertEquals(16, RideZoom.band(15));
    assertEquals(16, RideZoom.band(30));
    assertEquals(15, RideZoom.band(30.1));
  }

  @Test
  public void nothingBeforeTheRideCamera()
  {
    final RideZoom z = new RideZoom();
    assertFalse(z.started());
    assertEquals(RideZoom.NONE, z.update(25, 13, 0));
  }

  @Test
  public void waitsForTheRideCameraToApply()
  {
    // The BUG-004 trace: the map still read 13 a second after the ride camera (the engine had not applied it yet);
    // asking again then added a second zoom. Nothing is asked before 3 s, and then the same level, never a factor.
    final RideZoom z = new RideZoom();
    assertEquals(16, z.start(25, 0));
    assertTrue(z.started());
    assertEquals(RideZoom.NONE, z.update(25, 13, 1000));
    assertEquals(RideZoom.NONE, z.update(25, 13, 1135));
    assertEquals(RideZoom.NONE, z.update(25, 16, 2000));
    assertEquals(RideZoom.NONE, z.update(25, 16, 9000));
  }

  @Test
  public void aZoomCutShortIsAskedAgain()
  {
    final RideZoom z = new RideZoom();
    z.start(25, 0);
    assertEquals(RideZoom.NONE, z.update(25, 14, 2000));
    assertEquals(16, z.update(25, 14, 3000));
    assertEquals(RideZoom.NONE, z.update(25, 14, 4000)); // just asked
    assertEquals(16, z.update(25, 14, 6000));
    assertEquals(RideZoom.NONE, z.update(25, 16, 7000));
  }

  @Test
  public void aNewBandHoldsFiveSecondsFirst()
  {
    final RideZoom z = new RideZoom();
    z.start(25, 0);
    assertEquals(RideZoom.NONE, z.update(10, 16, 2000));
    assertEquals(RideZoom.NONE, z.update(10, 16, 6000));
    assertEquals(17, z.update(10, 16, 7000));
    assertEquals(17, z.zoom());
    assertEquals(RideZoom.NONE, z.update(10, 16, 8000)); // on its way
  }

  @Test
  public void aSpeedBlipDoesNotChangeTheBand()
  {
    final RideZoom z = new RideZoom();
    z.start(25, 0);
    z.update(35, 16, 1000);
    z.update(25, 16, 2000);
    assertEquals(RideZoom.NONE, z.update(35, 16, 7000)); // the 5 s start again
    assertEquals(16, z.zoom());
  }
}
