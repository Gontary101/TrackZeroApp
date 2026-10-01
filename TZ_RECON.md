# TrackZero — Recon (Phase 0)

**BASE_PKG = `app.organicmaps.sdk`** (SDK module, `android/sdk`). App module package: `app.organicmaps`.
Base tag: **`v2026.08.31-14`** (CoMaps, commit `72632e4`, "[fdroid] Release version 2026.08.31-14").
Branch: `trackzero`, created from that tag.

## Build

- Build task: **`:app:assembleFdroidDebug`** (variant `fdroidDebug`). APK id `app.trackzero.fdroid.debug` after Phase 1.
- Unit tests: `:app:testFdroidDebugUnitTest`.
- NDK pinned by `android/app/build.gradle`: `28.2.13676358`. CMake `3.22.1+` (system 3.28 used).
- `./configure.sh` needs `optipng` and Qt6 Svg/Positioning dev packages for the skin generator. Without root they
  were extracted locally (`~/.local/opt/optipng`, `~/.local/opt/qt6svg`); the skin generator build dir was
  pre-configured with `-DQt6Svg_DIR=… -DQt6SvgWidgets_DIR=… -DQt6Positioning_DIR=…`. Note: `configure.sh`
  reports success even when symbol generation fails — check `data/symbols/*/*/symbols.png` exist.
- minSdk 21, targetSdk/compileSdk 36. Kotlin BOM is on the classpath (`app/build.gradle:357`); we still write Java.
- `com.google.android.material:material:1.12.0` already a dependency. `viewpager2`: not declared directly.

## Key classes (path : line)

```
android/app/src/main/java/app/organicmaps/MwmActivity.java:140: public class MwmActivity extends BaseMwmFragmentActivity
android/sdk/src/main/java/app/organicmaps/sdk/MapView.java:24: public class MapView extends SurfaceView
android/sdk/src/main/java/app/organicmaps/sdk/Map.java:18: public final class Map
android/sdk/src/main/java/app/organicmaps/sdk/MapController.java:10: public class MapController implements DefaultLifecycleObserver
android/sdk/src/main/java/app/organicmaps/sdk/Framework.java:37: public class Framework
android/sdk/src/main/java/app/organicmaps/sdk/routing/RoutingController.java:21: public class RoutingController
android/sdk/src/main/java/app/organicmaps/sdk/location/LocationHelper.java:31: public class LocationHelper implements BaseLocationProvider.Listener
android/sdk/src/main/java/app/organicmaps/sdk/location/LocationState.java:10: public final class LocationState
android/sdk/src/main/java/app/organicmaps/sdk/bookmarks/data/BookmarkManager.java (enum INSTANCE)
android/sdk/src/main/java/app/organicmaps/sdk/search/SearchEngine.java:13: public enum SearchEngine (INSTANCE)
android/sdk/src/main/java/app/organicmaps/sdk/location/TrackRecorder.java:3: public class TrackRecorder
android/app/src/main/java/app/organicmaps/location/TrackRecordingService.java:35: public class TrackRecordingService extends Service
android/sdk/src/main/java/app/organicmaps/sdk/downloader/MapManager.java:10: public final class MapManager
android/app/src/main/java/app/organicmaps/downloader/DownloaderActivity.java:6: public class DownloaderActivity extends BaseMwmFragmentActivity
android/app/src/main/java/app/organicmaps/base/BaseMwmFragmentActivity.java:26: public abstract class BaseMwmFragmentActivity extends AppCompatActivity
```
No `MapFragment` class exists: the map is `MapView` (in `activity_map.xml`) wired by `MapController`.

## Map / camera

```
MapController(MapView, LocationHelper, MapRenderingListener, Map.CallbackUnsupported, boolean launchByDeepLink)  // lifecycle observer
MapController.updateBottomWidgetsOffset(int offsetX, int offsetY)     // moves ruler + copyright (px from left / bottom)
MapController.updateCompassOffset(int offsetX, int offsetY)
MapController.isRenderingActive()
Map.zoomIn() / Map.zoomOut()                                          // static, Map.java:312/317
Map.WIDGET_RULER = 0x01 ; Map.nativeSetupWidget is private (use updateBottomWidgetsOffset)
Map.onPause()/onResume() → nativePauseSurfaceRendering()/nativeResumeSurfaceRendering() (private natives, Map.java:444/446)
Framework.nativeSetViewportCenter(double lat, double lon, int zoom)   // Framework.java:368
Framework.nativeZoomToPoint(double lat, double lon, int zoom, boolean animate)  // :332
Framework.nativeStopLocationFollow()                                  // :369
Framework.nativeSetVisibleRect(int left, int top, int right, int bottom)  // :340
Framework.nativeShowTrackRect(long track)                             // :109
Framework.nativeShowCountry(String countryId, boolean zoomToDownloadButton)  // :248
Framework.nativeGetScreenRectCenter()                                 // :116
Framework.nativeGetDrawScale()                                        // :111
Framework.nativeGetAddress(double lat, double lon)                    // :134  (reverse geocoding)
Framework.nativePlacePageActivationListener(PlacePageActivationListener) / nativeRemovePlacePageActivationListener  // :136/138
Framework.nativeDeactivatePopup()                                     // :184
Framework.nativeSwitchToUsingVehicleStyle(boolean)
LocationState.nativeSwitchToNextMode()                                // cycles NOT_FOLLOW→FOLLOW→FOLLOW_AND_ROTATE (core: my_position_controller.cpp:406)
LocationState.getMode(); constants PENDING_POSITION=0, NOT_FOLLOW_NO_POSITION=1, NOT_FOLLOW=2, FOLLOW=3, FOLLOW_AND_ROTATE=4
LocationState.nativeSetListener(ModeChangeListener) / nativeRemoveListener()
```
No direct "set follow mode" or "show lat/lon rect" API in Java.

## Map style

`MapStyle` is chosen by two enums: `app.organicmaps.sdk.MapMode` (`Walking, Cycling, Driving, PublicTransport, Default`)
and `app.organicmaps.sdk.MapAppearance` (`Light, Dark`). `MapMode.set(Cycling)` + `MapAppearance.set(Dark)` →
`MapStyleCyclingDark` (libs/map/framework.cpp:2668). `ThemeSwitcher` (app/util/ThemeSwitcher.java) switches
MapAppearance with the UI theme; it is only driven from MwmActivity/settings.
Style sources: `data/styles/<mode>/<light|dark>/*.mapcss`; regenerate with `tools/unix/generate_drules.sh`
(also run by `configure.sh` when mapcss hashes change).

## Bookmarks (route line rendering)

```
BookmarkManager.INSTANCE.loadBookmarksFile(String path, boolean isTemporaryFile)   // :346, async, listener callbacks
BookmarkManager.INSTANCE.createCategory(String name) → long                         // :324
BookmarkManager.INSTANCE.deleteCategory(long catId)                                 // :309
BookmarkManager.INSTANCE.setVisibility(long catId, boolean visible)                 // :255
BookmarkManager.INSTANCE.changeTrackColor(long trackId, int color)                  // :685
BookmarkManager.INSTANCE.showBookmarkCategoryOnMap(long catId)                      // :334
```

## Search

```
SearchEngine.INSTANCE.search(Context, String query, boolean isCategory, long timestamp, boolean hasLocation, double lat, double lon)  // :113
SearchEngine.INSTANCE.addListener(SearchListener) / removeListener / cancel()
```

## Units

```
app.organicmaps.sdk.settings.UnitLocale: getUnits(), setUnits(int), initializeCurrentUnits()
```

## TTS

`app.organicmaps.sdk.sound.TtsPlayer` (enum INSTANCE) — initialized in `OrganicMaps` init; speaks only for CoMaps
routing. `TtsPlayer.setEnabled(boolean)` static.

## Track recording

`TrackRecordingService` notification channel id `"TRACK RECORDING"`, `NotificationChannelCompat.Builder(..., IMPORTANCE_LOW)`
(TrackRecordingService.java:63). Started only from MwmActivity (menu / `onRenderingInitializationFinished` restore).

## Follow-along-track (step 5)

`rg -n -i "follow.?track|track.?navigation|navigate.?along|route.?from.?track" android/ libs/routing libs/map` → **no
results**. CoMaps does not navigate along an imported track. TrackZero builds its own follow engine (Phase 10).

## Launch flow / entry points

`SplashActivity` (holds all VIEW/SEND intent filters incl. `.gpx`, `application/gpx+xml`) → `DownloadResourcesLegacyActivity`
→ `showMap()` sets component `MwmActivity` (DownloadResourcesLegacyActivity.java:364).
Other launches of MwmActivity: `DownloaderAdapter.onExploreActionSelected` (:110), `MapPlaceholderActivity` (car),
bookmarks screens, TrackRecording/Navigation service notifications (all unreachable once TZ is on).

## MwmActivity layout (`res/layout/activity_map.xml`)

| Role | ID |
|---|---|
| root | `coordinator` (CoordinatorLayout) |
| map container | `map_container` (RelativeLayout) → `map` (`app.organicmaps.sdk.MapView`) |
| map buttons (zoom, my position, layers, menu) | `map_buttons` (FragmentContainerView → `MapButtonsController`) |
| on-map downloader | `onmap_downloader` |
| position chooser | `position_chooser` |
| search toolbar | `toolbar` (`toolbar_with_search`) |
| routing plan panel | include `routing_plan` |
| bottom menu | include `menu` |
| navigation panel | include `layout_nav` |
| place page | `place_page_container_fragment` (`PlacePageController`) |

`MwmActivity.onSafeCreate` → `setContentView(R.layout.activity_map)` (MwmActivity.java:501).
