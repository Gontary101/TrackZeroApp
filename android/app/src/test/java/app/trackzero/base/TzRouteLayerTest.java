package app.trackzero.base;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import app.trackzero.TzTrace;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TzRouteLayerTest
{
  private final List<String> mCalls = new ArrayList<>();
  private int mNextId = 1;
  private final Object mPreview = new Object();
  private final Object mRide = new Object();
  private final double[] mLat = {45.0, 45.1};
  private final double[] mLon = {3.0, 3.1};

  @Before
  public void setUp()
  {
    TzTrace.enableForTests(false);
    TzRouteLayer.sEngine = new TzRouteLayer.Engine() {
      @NonNull
      @Override
      public int[] show(@NonNull double[] lat, @NonNull double[] lon, boolean dimmed, int ring)
      {
        mCalls.add("show");
        return new int[] {mNextId++};
      }

      @Override
      public void remove(@NonNull int[] ids)
      {
        mCalls.add("remove " + ids[0]);
      }
    };
  }

  @After
  public void tearDown()
  {
    TzRouteLayer.hide(mPreview);
    TzRouteLayer.hide(mRide);
    TzRouteLayer.hide(TzRouteLayer.RIDE_START);
  }

  @Test
  public void aHandedOverLineSurvivesThePreviewGoing()
  {
    TzRouteLayer.show(mPreview, mLat, mLon);
    TzRouteLayer.transfer(mPreview, TzRouteLayer.RIDE_START);
    TzRouteLayer.transfer(TzRouteLayer.RIDE_START, mRide);
    // The preview's teardown runs after the ride screen set up (fragment reordering, D39).
    TzRouteLayer.hide(mPreview);
    assertTrue(TzRouteLayer.isShown());
    assertEquals(List.of("show"), mCalls);
  }

  @Test
  public void theSameLineIsNotDrawnAgain()
  {
    TzRouteLayer.show(mPreview, mLat, mLon);
    TzRouteLayer.transfer(mPreview, mRide);
    TzRouteLayer.show(mRide, mLat.clone(), mLon.clone());
    assertEquals(List.of("show"), mCalls);
    TzRouteLayer.hide(mRide);
    assertEquals(List.of("show", "remove 1"), mCalls);
  }

  @Test
  public void anotherLineReplacesIt()
  {
    TzRouteLayer.show(mPreview, mLat, mLon);
    TzRouteLayer.transfer(mPreview, mRide);
    TzRouteLayer.show(mRide, new double[] {46, 46.1}, mLon);
    assertEquals(List.of("show", "remove 1", "show"), mCalls);
  }

  @Test
  public void transferOnlyMovesTheOwnersLines()
  {
    TzRouteLayer.show(mPreview, mLat, mLon);
    TzRouteLayer.transfer(TzRouteLayer.RIDE_START, mRide); // nothing was handed over
    TzRouteLayer.hide(mPreview);
    assertEquals(List.of("show", "remove 1"), mCalls);
  }
}
