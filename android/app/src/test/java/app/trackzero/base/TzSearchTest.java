package app.trackzero.base;

import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TzSearchTest
{
  @Test
  public void typeIsNotRepeatedAndTheElevationKept()
  {
    assertEquals("Volcano · 940 m", TzSearch.subtitle("Puy de Gorce", "Volcano", "Volcano • ▲ 940 m", "Orcines"));
  }

  @Test
  public void withoutDetailsThePlaceIsGiven()
  {
    assertEquals("Track · Clermont-Ferrand", TzSearch.subtitle("Rue Puy Meunier", "Track", "Track", "Clermont-Ferrand"));
    assertEquals("Square · Clermont-Ferrand",
                 TzSearch.subtitle("Place de Jaude", "Square", "", "Clermont-Ferrand, Auvergne-Rhône-Alpes, France"));
  }

  @Test
  public void aPlaceNamedAfterItsTypeShowsOnlyTheDetails()
  {
    assertEquals("Clermont-Ferrand", TzSearch.subtitle("Drinking water", "Drinking water", null, "Clermont-Ferrand"));
  }

  @Test
  public void heightsAreGrouped()
  {
    final Locale saved = Locale.getDefault();
    Locale.setDefault(Locale.US);
    try
    {
      assertEquals("Volcano · 1,691 m", TzSearch.subtitle("Puy de Cliergue", "Volcano", "Volcano • ▲ 1691 m", null));
      assertEquals("Volcano · 940 m", TzSearch.subtitle("Puy de Gorce", "Volcano", "Volcano • ▲ 940 m", null));
    }
    finally
    {
      Locale.setDefault(saved);
    }
  }
}
