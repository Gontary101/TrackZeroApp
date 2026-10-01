package app.trackzero.base;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.POST_NOTIFICATIONS;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.base.BaseMwmFragmentActivity;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.MapController;
import app.organicmaps.sdk.MapMode;
import app.organicmaps.sdk.MapRenderingListener;
import app.organicmaps.sdk.MapView;
import app.organicmaps.sdk.PlacePageActivationListener;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.sdk.settings.UnitLocale;
import app.organicmaps.sdk.util.LocationUtils;
import app.organicmaps.sdk.widget.placepage.PlacePageData;
import app.organicmaps.util.ThemeSwitcher;
import app.trackzero.TzConfig;
import app.trackzero.TzNavigator;
import app.trackzero.ride.TzRideService;
import app.trackzero.routes.TzImport;

/**
 * TrackZero's main activity: a full-screen CoMaps map with the TrackZero overlay ({@code tz_root}) on top.
 * Replaces MwmActivity when {@link TzConfig#ENABLED}, so none of the base-app chrome is ever created.
 */
public class TzActivity extends BaseMwmFragmentActivity
    implements MapRenderingListener, LocationState.ModeChangeListener, PlacePageActivationListener
{
  private static final String STATE_PERMISSION_ASKED = "tz_permission_asked";
  private static final String EXTRA_CONSUMED = "tz.extra.intent_consumed";

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private MapController mMapController;
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private MapView mMapView;
  @Nullable
  private ActivityResultLauncher<String[]> mLocationPermissionRequest;
  @Nullable
  private ActivityResultLauncher<String[]> mRidePermissionRequest;
  @Nullable
  private Runnable mOnRidePermitted;
  private boolean mPermissionAsked;

  // Map drawing runs only while the activity is resumed AND no opaque TrackZero screen covers the map.
  private boolean mResumed;
  private boolean mRenderingWanted = true;
  private boolean mMapResumed;

  private int mVisibleTopPx;
  private int mVisibleBottomPx;

  /** The activity that shows the map: TrackZero's when enabled, else the base app's. */
  @NonNull
  public static Class<? extends Activity> mapActivityClass()
  {
    return TzConfig.ENABLED ? TzActivity.class : MwmActivity.class;
  }

  @Override
  protected void onSafeCreate(@Nullable Bundle savedInstanceState)
  {
    super.onSafeCreate(savedInstanceState);
    if (savedInstanceState != null)
      mPermissionAsked = savedInstanceState.getBoolean(STATE_PERMISSION_ASKED);

    setUpEdgeToEdge();
    setContentView(R.layout.tz_activity);

    mLocationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                                                           result -> onLocationPermissionsResult());
    mRidePermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                                                       result -> onRidePermissionsResult());

    final Intent intent = getIntent();
    final boolean launchByDeepLink = intent != null && !intent.hasCategory(Intent.CATEGORY_LAUNCHER);
    mMapView = findViewById(R.id.tz_map);
    // No compass in TrackZero's map UI; the ride map is heading-up and says so with the arrow.
    Map.setCompassEnabled(false);
    mMapController = new MapController(mMapView, locationHelper(), this, null, launchByDeepLink);
    mMapView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyVisibleArea());

    TzMap.attach(this);
    TzNavigator.attach(this, findViewById(R.id.tz_root));
    if (savedInstanceState == null)
      TzRideService.restoreIfNeeded(this);

    // Not called again when the activity is recreated with an already running engine.
    if (Map.isEngineCreated())
      onRenderingInitializationFinished();
  }

  private void setUpEdgeToEdge()
  {
    // Transparent bars with light icons over the black map.
    EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState)
  {
    super.onSaveInstanceState(outState);
    outState.putBoolean(STATE_PERMISSION_ASKED, mPermissionAsked);
  }

  @Override
  protected void onStart()
  {
    super.onStart();
    mMapController.onStart(this);
    Framework.nativePlacePageActivationListener(this);
    LocationState.nativeSetListener(this);
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    mResumed = true;
    ThemeSwitcher.INSTANCE.restart(mMapController.isRenderingActive());
    updateMapRendering();
  }

  @Override
  protected void onPause()
  {
    mResumed = false;
    updateMapRendering();
    super.onPause();
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    Framework.nativeRemovePlacePageActivationListener(this);
    LocationState.nativeRemoveListener();
    mMapController.onStop(this);
  }

  @Override
  protected void onSafeDestroy()
  {
    super.onSafeDestroy();
    mMapController.onDestroy(this);
    if (mLocationPermissionRequest != null)
      mLocationPermissionRequest.unregister();
    mLocationPermissionRequest = null;
    if (mRidePermissionRequest != null)
      mRidePermissionRequest.unregister();
    mRidePermissionRequest = null;
    TzNavigator.detach(this);
    TzMap.detach(this);
  }

  @Override
  protected void onNewIntent(Intent intent)
  {
    super.onNewIntent(intent);
    setIntent(intent);
    if (mMapController.isRenderingActive())
      processIntent();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev)
  {
    TzMap.dispatchTouch(ev);
    return super.dispatchTouchEvent(ev);
  }

  void setMapRenderingEnabled(boolean enabled)
  {
    mRenderingWanted = enabled;
    updateMapRendering();
  }

  private void updateMapRendering()
  {
    final boolean run = mResumed && mRenderingWanted;
    if (run == mMapResumed)
      return;
    mMapResumed = run;
    if (run)
      mMapController.onResume(this);
    else
      mMapController.onPause(this);
  }

  void setVisibleMapArea(int topPx, int bottomPx)
  {
    mVisibleTopPx = topPx;
    mVisibleBottomPx = bottomPx;
    applyVisibleArea();
  }

  private void applyVisibleArea()
  {
    final int w = mMapView.getWidth();
    final int h = mMapView.getHeight();
    if (w > 0 && h > 0)
      Framework.nativeSetVisibleRect(0, mVisibleTopPx, w, h - mVisibleBottomPx);
  }

  int visibleMapWidth()
  {
    return mMapView.getWidth();
  }

  int visibleMapHeight()
  {
    return mMapView.getHeight() - mVisibleTopPx - mVisibleBottomPx;
  }

  void setRulerOffset(int xPx, int yPx)
  {
    // The SDK adds its own margin_ruler to the offsets.
    final int margin = getResources().getDimensionPixelSize(app.organicmaps.sdk.R.dimen.margin_ruler);
    mMapController.updateBottomWidgetsOffset(Math.max(0, xPx - margin), Math.max(0, yPx - margin));
  }

  @NonNull
  private LocationHelper locationHelper()
  {
    return MwmApplication.from(this).getLocationHelper();
  }

  private void processIntent()
  {
    final Intent intent = getIntent();
    if (intent == null || intent.getBooleanExtra(EXTRA_CONSUMED, false))
      return;
    intent.putExtra(EXTRA_CONSUMED, true);

    // "Open with" / "Share to" TrackZero: the base app's intent filters bring GPX files here.
    final Uri file = TzImport.fileOf(intent);
    if (file != null)
    {
      TzImport.importUri(this, file);
      return;
    }

    // "Explore" from the map downloader.
    final String countryId = intent.getStringExtra(MwmActivity.EXTRA_COUNTRY_ID);
    if (countryId != null)
      Framework.nativeShowCountry(countryId, false);
  }

  // MapRenderingListener

  @Override
  public void onRenderingCreated()
  {
    UnitLocale.initializeCurrentUnits();
  }

  @Override
  public void onRenderingInitializationFinished()
  {
    // Dark first: only the dark cycling style is shipped (ThemeSwitcher is dark-only when TrackZero is enabled).
    ThemeSwitcher.INSTANCE.restart(true);
    // Needs the engine. Persisted by the core, so later launches start in the cycling style directly.
    MapMode.set(MapMode.Cycling);
    applyVisibleArea();
    TzMap.onEngineReady();
    processIntent();
  }

  // LocationState.ModeChangeListener: the core drives location updates through position-mode changes.

  @Override
  public void onMyPositionModeChanged(int newMode)
  {
    final LocationHelper locationHelper = locationHelper();
    if (newMode == LocationState.NOT_FOLLOW_NO_POSITION)
    {
      if (locationHelper.isActive())
        locationHelper.stop();
    }
    else if (!LocationUtils.checkLocationPermission(this))
    {
      // Calls back with NOT_FOLLOW_NO_POSITION.
      LocationState.nativeOnLocationError(LocationState.ERROR_DENIED);
      requestLocationPermission();
    }
    else
    {
      locationHelper.restartWithNewMode();
    }
    TzMap.onModeChanged(newMode);
  }

  /** Asks at most once per activity instance so a refusal is never nagged about. */
  void requestLocationPermission()
  {
    if (mPermissionAsked || mLocationPermissionRequest == null)
      return;
    mPermissionAsked = true;
    mLocationPermissionRequest.launch(new String[] {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION});
  }

  /**
   * Runs {@code onGranted} once precise location is allowed, asking for it (and, on Android 13+, for the ride
   * notification) first if needed. A refused notification does not stop the ride.
   */
  void requestRidePermissions(@NonNull Runnable onGranted)
  {
    final boolean location = ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
                             == PackageManager.PERMISSION_GRANTED;
    final boolean notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                                  || ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS)
                                         == PackageManager.PERMISSION_GRANTED;
    if (location && notifications)
    {
      onGranted.run();
      return;
    }
    if (mRidePermissionRequest == null)
      return;
    mOnRidePermitted = onGranted;
    mRidePermissionRequest.launch(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                      ? new String[] {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, POST_NOTIFICATIONS}
                                      : new String[] {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION});
  }

  private void onRidePermissionsResult()
  {
    final Runnable onGranted = mOnRidePermitted;
    mOnRidePermitted = null;
    if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    {
      if (onGranted != null)
        onGranted.run();
    }
    else
    {
      Toast.makeText(this, R.string.tz_ride_needs_location, Toast.LENGTH_LONG).show();
    }
  }

  private void onLocationPermissionsResult()
  {
    if (LocationUtils.checkLocationPermission(this))
      LocationState.nativeStartPendingPositionMode();
  }

  // PlacePageActivationListener: TrackZero has no place page, tapping a POI does nothing. A search result shown on
  // the map keeps its selection as its pin until the next tap.

  @Override
  public void onPlacePageActivated(@NonNull PlacePageData data)
  {
    if (!TzSearch.takeShowing())
      Framework.nativeDeactivatePopup();
  }

  @Override
  public void onPlacePageDeactivated() {}

  @Override
  public void onSwitchFullScreenMode() {}
}
