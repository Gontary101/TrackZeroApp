package app.trackzero.format;

import static org.junit.Assert.assertEquals;

import app.trackzero.format.TzFormat.Units;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class TzFormatTest
{
  private static final Locale US = Locale.US;

  @Test
  public void speed()
  {
    assertEquals("24.8 km/h", TzFormat.speed(24.84, Units.METRIC, US).toString());
    assertEquals("0.0 km/h", TzFormat.speed(0, Units.METRIC, US).toString());
    assertEquals("99.9 km/h", TzFormat.speed(99.94, Units.METRIC, US).toString());
    assertEquals("100 km/h", TzFormat.speed(99.96, Units.METRIC, US).toString());
    assertEquals("104 km/h", TzFormat.speed(104.4, Units.METRIC, US).toString());
    assertEquals("20.0 mph", TzFormat.speed(32.18688, Units.IMPERIAL, US).toString());
  }

  @Test
  public void distanceMetric()
  {
    assertEquals("0 m", TzFormat.distance(2, Units.METRIC, US).toString());
    assertEquals("35 m", TzFormat.distance(34, Units.METRIC, US).toString());
    assertEquals("95 m", TzFormat.distance(96.4, Units.METRIC, US).toString());
    assertEquals("120 m", TzFormat.distance(118, Units.METRIC, US).toString());
    assertEquals("990 m", TzFormat.distance(994, Units.METRIC, US).toString());
    assertEquals("1.0 km", TzFormat.distance(996, Units.METRIC, US).toString());
    assertEquals("32.3 km", TzFormat.distance(32_300, Units.METRIC, US).toString());
    assertEquals("70.5 km", TzFormat.distance(70_480, Units.METRIC, US).toString());
    assertEquals("123 km", TzFormat.distance(123_400, Units.METRIC, US).toString());
    assertEquals("1,234 km", TzFormat.distance(1_234_000, Units.METRIC, US).toString());
  }

  @Test
  public void distanceImperial()
  {
    assertEquals("330 ft", TzFormat.distance(100, Units.IMPERIAL, US).toString());
    assertEquals("0.1 mi", TzFormat.distance(161, Units.IMPERIAL, US).toString());
    assertEquals("20.0 mi", TzFormat.distance(32_186.88, Units.IMPERIAL, US).toString());
  }

  @Test
  public void distanceUsesLocaleDecimalSeparator()
  {
    assertEquals("32,3 km", TzFormat.distance(32_300, Units.METRIC, Locale.FRANCE).toString());
  }

  @Test
  public void elevation()
  {
    assertEquals("1,491 m", TzFormat.elevation(1491.4, Units.METRIC, US).toString());
    assertEquals("612 m", TzFormat.elevation(612, Units.METRIC, US).toString());
    assertEquals("4,892 ft", TzFormat.elevation(1491, Units.IMPERIAL, US).toString());
  }

  @Test
  public void grade()
  {
    assertEquals("8.4 %", TzFormat.grade(8.44, US).toString());
    assertEquals("-3.0 %", TzFormat.grade(-3, US).toString());
    assertEquals(TzFormat.UNKNOWN, TzFormat.grade(Double.NaN, US).toString());
  }

  @Test
  public void duration()
  {
    assertEquals("0:00:00", TzFormat.duration(0));
    assertEquals("0:24:10", TzFormat.duration(24 * 60 + 10));
    assertEquals("1:42:18", TzFormat.duration(3600 + 42 * 60 + 18));
    assertEquals("12:00:05", TzFormat.duration(12 * 3600 + 5));
  }

  @Test
  public void clock()
  {
    final TimeZone utc = TimeZone.getTimeZone("UTC");
    final long t = (14 * 60 + 52) * 60_000L;
    assertEquals("14:52", TzFormat.clock(t, utc, true));
    assertEquals("2:52 PM", TzFormat.clock(t, utc, false));
    assertEquals("00:05", TzFormat.clock(5 * 60_000L, utc, true));
    assertEquals("12:05 AM", TzFormat.clock(5 * 60_000L, utc, false));
  }
}
