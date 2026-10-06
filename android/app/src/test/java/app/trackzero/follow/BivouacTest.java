package app.trackzero.follow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class BivouacTest
{
  private static Stop place(int kind, String hours, int flags)
  {
    return new Stop("", kind, 45.7, 3.0, hours, flags);
  }

  @Test
  public void campsitesAndHutsAreVerifiedSheltersNever()
  {
    assertTrue(Bivouac.verified(place(Stop.CAMPSITE, "", 0)));
    assertTrue(Bivouac.verified(place(Stop.HUT, "", 0)));
    assertFalse(Bivouac.verified(place(Stop.SHELTER, "", 0)));
    assertFalse(Bivouac.verified(place(Stop.DRINKING_WATER, "", 0)));
    assertTrue(Bivouac.isBivouac(place(Stop.SHELTER, "", 0)));
    assertFalse(Bivouac.isBivouac(place(Stop.BIKE_SHOP, "", 0)));
  }

  @Test
  public void privateAccessOverridesTheType()
  {
    final Stop s = place(Stop.CAMPSITE, "", Stop.PRIVATE);
    assertFalse(Bivouac.verified(s));
    assertEquals(List.of(Bivouac.Restriction.PRIVATE), Bivouac.restrictions(s));
  }

  @Test
  public void restrictionsComeFromWhatTheMapKnows()
  {
    assertEquals(List.of(Bivouac.Restriction.HOURS, Bivouac.Restriction.FEE),
                 Bivouac.restrictions(place(Stop.CAMPSITE, "May-Oct", Stop.FEE)));
    assertEquals(List.of(Bivouac.Restriction.NO_FEE), Bivouac.restrictions(place(Stop.HUT, "", Stop.NO_FEE)));
    assertTrue(Bivouac.restrictions(place(Stop.SHELTER, "", 0)).isEmpty());
  }
}
