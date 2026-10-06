package app.trackzero.routes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileTest
{
  @Test
  public void idsRoundTrip()
  {
    for (Profile p : Profile.values())
      assertEquals(p, Profile.of(p.id));
  }

  @Test
  public void unknownOrMissingIsRoad()
  {
    assertEquals(Profile.ROAD, Profile.of(null));
    assertEquals(Profile.ROAD, Profile.of(""));
    assertEquals(Profile.ROAD, Profile.of("ROAD"));
    assertEquals(Profile.ROAD, Profile.of("unicycle"));
  }

  @Test
  public void footProfiles()
  {
    assertFalse(Profile.ROAD.foot);
    assertFalse(Profile.GRAVEL.foot);
    assertTrue(Profile.WALK.foot);
    assertTrue(Profile.HIKE.foot);
  }

  @Test
  public void allFourAreSwitchedOn()
  {
    assertEquals(java.util.List.of(Profile.ROAD, Profile.GRAVEL, Profile.WALK, Profile.HIKE), Profile.available());
  }

  /** The native router is given the ordinal: it must not move. */
  @Test
  public void ordinalsAreWhatTheRouterExpects()
  {
    assertEquals(0, Profile.ROAD.ordinal());
    assertEquals(1, Profile.GRAVEL.ordinal());
    assertEquals(2, Profile.WALK.ordinal());
    assertEquals(3, Profile.HIKE.ordinal());
  }
}
