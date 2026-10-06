package app.trackzero.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class TzShareTest
{
  private static final TimeZone PARIS = TimeZone.getTimeZone("Europe/Paris");
  /** 2026-10-06 14:32:00 in Paris. */
  private static final long AT = 1_791_289_920_000L;

  private static ShareText.Words words()
  {
    final ShareText.Words w = new ShareText.Words();
    w.checkIn = "All good. My position at %1$s:";
    w.lowBattery = "Low battery (%1$d %%). My position at %2$s:";
    w.help = "I need help. My position at %1$s:";
    w.coordinates = "%1$s, %2$s (±%3$d m)";
    w.near = "Near %1$s";
    w.stale = "(position from %1$s, may be out of date)";
    w.noFix = "I don't have a GPS position right now.";
    w.checkInNoFix = "All good. I don't have a GPS position right now.";
    w.lowBatteryNoFix = "Low battery (%1$d %%). I don't have a GPS position right now.";
    w.helpNoFix = "I need help. I don't have a GPS position right now.";
    return w;
  }

  private static final ShareText.Fix FIX = new ShareText.Fix(45.772041, 2.965138, 6.4, AT);

  @Test
  public void freshCheckInWithThePlace()
  {
    final String m = ShareText.message(words(), ShareText.Wording.CHECK_IN, FIX, 4_000, 80,
                                       "Route du Puy de Dôme, Orcines", PARIS, true);
    assertEquals("All good. My position at 14:32:\n45.77204, 2.96514 (±6 m)\nNear Route du Puy de Dôme, Orcines\n"
                     + "https://www.openstreetmap.org/?mlat=45.77204&mlon=2.96514#map=17/45.77204/2.96514",
                 m);
  }

  @Test
  public void lowBatteryAndHelpWithoutAPlace()
  {
    final String low = ShareText.message(words(), ShareText.Wording.LOW_BATTERY, FIX, 4_000, 12, null, PARIS, true);
    assertTrue(low.startsWith("Low battery (12 %). My position at 14:32:\n45.77204"));
    assertFalse(low.contains("Near"));
    final String help = ShareText.message(words(), ShareText.Wording.HELP, FIX, 4_000, 12, "", PARIS, true);
    assertTrue(help.startsWith("I need help. My position at 14:32:"));
  }

  @Test
  public void aStaleFixSaysSo()
  {
    final String m = ShareText.message(words(), ShareText.Wording.CHECK_IN, FIX, 12 * 60_000, 50, null, PARIS, false);
    assertTrue(m, m.contains("(±6 m) (position from 2:32 PM, may be out of date)"));
    assertEquals(ShareText.Age.STALE, ShareText.age(true, 12 * 60_000));
    assertEquals(ShareText.Age.RECENT, ShareText.age(true, 90_000));
    assertEquals(ShareText.Age.FRESH, ShareText.age(true, 30_000));
  }

  @Test
  public void noFixMeansNoCoordinatesAndNoLink()
  {
    final String m = ShareText.message(words(), ShareText.Wording.LOW_BATTERY, null, 0, 9, "x", PARIS, true);
    assertEquals("Low battery (9 %). I don't have a GPS position right now.", m);
    assertFalse(m.contains("openstreetmap"));
    assertEquals(ShareText.Age.NONE, ShareText.age(false, 0));
  }

  @Test
  public void coordinatesUseADotWhateverTheLanguage()
  {
    final Locale was = Locale.getDefault();
    try
    {
      Locale.setDefault(Locale.FRANCE);
      assertEquals("45.77204", ShareText.coordinate(45.772041));
      assertEquals("-0.00001", ShareText.coordinate(-0.0000149));
    }
    finally
    {
      Locale.setDefault(was);
    }
  }
}
