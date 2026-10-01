package app.trackzero.base;

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
}
