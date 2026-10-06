#include "app/organicmaps/sdk/Framework.hpp"

#include "app/organicmaps/sdk/UserMarkHelper.hpp"
#include "app/organicmaps/sdk/core/jni_helper.hpp"
#include "app/organicmaps/sdk/opengl/androidoglcontextfactory.hpp"
#include "app/organicmaps/sdk/platform/AndroidPlatform.hpp"
#include "app/organicmaps/sdk/routing/JunctionInfo.hpp"
#include "app/organicmaps/sdk/routing/RouteMarkData.hpp"
#include "app/organicmaps/sdk/routing/RouteMarkType.hpp"
#include "app/organicmaps/sdk/routing/RouteRecommendationType.hpp"
#include "app/organicmaps/sdk/routing/RouteStepInfo.hpp"
#include "app/organicmaps/sdk/routing/RoutingInfo.hpp"
#include "app/organicmaps/sdk/routing/TransitRouteInfo.hpp"
#include "app/organicmaps/sdk/util/Distance.hpp"
#include "app/organicmaps/sdk/util/FeatureIdBuilder.hpp"
#include "app/organicmaps/sdk/util/NetworkPolicy.hpp"
#include "app/organicmaps/sdk/vulkan/android_vulkan_context_factory.hpp"

#include "map/bookmark_helpers.hpp"
#include "map/chart_generator.hpp"
#include "map/everywhere_search_params.hpp"
#include "map/user_mark.hpp"

#include "storage/storage_defines.hpp"
#include "storage/storage_helpers.hpp"

#include "drape_frontend/drape_engine.hpp"
#include "drape_frontend/route_shape.hpp"
#include "drape_frontend/user_event_stream.hpp"
#include "drape_frontend/visual_params.hpp"

#include "drape/pointers.hpp"
#include "drape/support_manager.hpp"
#include "drape/visual_scale.hpp"

#include "coding/files_container.hpp"

#include "geometry/angles.hpp"
#include "geometry/distance_on_sphere.hpp"
#include "geometry/mercator.hpp"
#include "geometry/point_with_altitude.hpp"

#include "geometry/parametrized_segment.hpp"

#include "indexer/classificator.hpp"
#include "indexer/feature_algo.hpp"
#include "indexer/feature_altitude.hpp"
#include "indexer/feature_data.hpp"
#include "indexer/ftypes_matcher.hpp"
#include "indexer/scales.hpp"
#include "indexer/validate_and_format_contacts.hpp"

#include "routing/following_info.hpp"
#include "routing/speed_camera_manager.hpp"

#include "platform/country_file.hpp"
#include "platform/local_country_file.hpp"
#include "platform/local_country_file_utils.hpp"
#include "platform/locale.hpp"
#include "platform/localization.hpp"
#include "platform/location.hpp"
#include "platform/measurement_utils.hpp"
#include "platform/network_policy.hpp"
#include "platform/platform.hpp"
#include "platform/preferred_languages.hpp"
#include "platform/settings.hpp"
#include "platform/utm_mgrs_utils.hpp"

#include "base/assert.hpp"
#include "base/file_name_utils.hpp"
#include "base/logging.hpp"
#include "base/math.hpp"
#include "base/sunrise_sunset.hpp"

#include "ge0/url_generator.hpp"

#include "3party/open-location-code/openlocationcode.h"

#include <limits>
#include <map>
#include <set>  // TrackZero
#include <memory>
#include <string>
#include <vector>

#include <android/api-level.h>

#include "drape/accessibility_data.hpp"

#include "search/city_finder.hpp"  // TrackZero
#include "search/mwm_context.hpp"
#include "search/reverse_geocoder.hpp"

#include "storage/country_info_getter.hpp"

using namespace std;
using namespace std::placeholders;

unique_ptr<android::Framework> g_framework;

namespace platform
{
NetworkPolicy ToNativeNetworkPolicy(JNIEnv * env, jobject obj)
{
  return NetworkPolicy(network_policy::GetNetworkPolicyStatus(env, obj));
}
}  // namespace platform

using namespace storage;
using platform::CountryFile;
using platform::LocalCountryFile;
using platform::ToNativeNetworkPolicy;

static_assert(sizeof(int) >= 4, "Size of jint in less than 4 bytes.");

::Framework * frm()
{
  return g_framework->NativeFramework();
}

namespace
{
jobject g_placePageActivationListener = nullptr;

android::AndroidVulkanContextFactory * CastFactory(drape_ptr<dp::GraphicsContextFactory> const & f)
{
  return dynamic_cast<android::AndroidVulkanContextFactory *>(f.get());
}
}  // namespace

namespace android
{

enum MultiTouchAction
{
  MULTITOUCH_UP = 0x00000001,
  MULTITOUCH_DOWN = 0x00000002,
  MULTITOUCH_MOVE = 0x00000003,
  MULTITOUCH_CANCEL = 0x00000004
};

Framework::Framework(std::function<void()> && afterMapsLoaded) : m_work({} /* params */, false /* loadMaps */)
{
  m_work.LoadMapsAsync(std::move(afterMapsLoaded));

  m_work.GetTrafficManager().SetStateListener(bind(&Framework::TrafficStateChanged, this, _1));
  m_work.GetTransitManager().SetStateListener(bind(&Framework::TransitSchemeStateChanged, this, _1));
  m_work.GetIsolinesManager().SetStateListener(bind(&Framework::IsolinesSchemeStateChanged, this, _1));
  m_work.GetPowerManager().Subscribe(this);
}

void Framework::OnLocationError(int errorCode)
{
  m_work.OnLocationError(static_cast<location::TLocationError>(errorCode));
}

void Framework::OnLocationUpdated(location::GpsInfo const & info)
{
  m_work.OnLocationUpdate(info);
}

void Framework::OnCompassUpdated(location::CompassInfo const & info, bool forceRedraw)
{
  static double const COMPASS_THRESHOLD = math::DegToRad(1.0);

  /// @todo Do not emit compass bearing too often.
  /// Need to make more experiments in future.
  if (forceRedraw || fabs(ang::GetShortestDistance(m_lastCompass, info.m_bearing)) >= COMPASS_THRESHOLD)
  {
    m_lastCompass = info.m_bearing;
    m_work.OnCompassUpdate(info);
  }
}

void Framework::MyPositionModeChanged(location::EMyPositionMode mode, bool routingActive)
{
  if (m_myPositionModeSignal)
    m_myPositionModeSignal(mode, routingActive);
}

void Framework::TrafficStateChanged(TrafficManager::TrafficState state)
{
  if (m_onTrafficStateChangedFn)
    m_onTrafficStateChangedFn(state);
}

void Framework::TransitSchemeStateChanged(TransitReadManager::TransitSchemeState state)
{
  if (m_onTransitStateChangedFn)
    m_onTransitStateChangedFn(state);
}

void Framework::IsolinesSchemeStateChanged(IsolinesManager::IsolinesState state)
{
  if (m_onIsolinesStateChangedFn)
    m_onIsolinesStateChangedFn(state);
}

bool Framework::DestroySurfaceOnDetach()
{
  if (m_vulkanContextFactory)
    return false;
  return true;
}

bool Framework::CreateDrapeEngine(JNIEnv * env, jobject jSurface, int densityDpi, bool firstLaunch,
                                  bool launchByDeepLink, uint32_t appVersionCode, bool isCustomROM)
{
  // Vulkan is supported only since Android 8.0, because some Android devices with Android 7.x
  // have fatal driver issue, which can lead to process termination and whole OS destabilization.
  int constexpr kMinSdkVersionForVulkan = 26;
  int const sdkVersion = android_get_device_api_level();
  LOG(LINFO, ("Android SDK version in the Drape Engine:", sdkVersion));
  auto const vulkanForbidden =
      sdkVersion < kMinSdkVersionForVulkan || dp::SupportManager::Instance().IsVulkanForbidden();
  if (vulkanForbidden)
    LOG(LWARNING, ("Vulkan API is forbidden on this device."));

  m_vulkanContextFactory.reset();
  m_oglContextFactory.reset();
  ::Framework::DrapeCreationParams p;

  if (m_work.LoadPreferredGraphicsAPI() == dp::ApiVersion::Vulkan && !vulkanForbidden)
  {
    auto vkFactory = make_unique_dp<AndroidVulkanContextFactory>(appVersionCode, sdkVersion, isCustomROM);
    if (!vkFactory->IsVulkanSupported())
    {
      LOG(LWARNING, ("Vulkan API is not supported."));
    }
    else
    {
      vkFactory->SetSurface(env, jSurface);
      if (!vkFactory->IsValid())
      {
        LOG(LWARNING, ("Invalid Vulkan API context."));
      }
      else
      {
        p.m_apiVersion = dp::ApiVersion::Vulkan;
        p.m_surfaceWidth = vkFactory->GetWidth();
        p.m_surfaceHeight = vkFactory->GetHeight();

        m_vulkanContextFactory = std::move(vkFactory);
      }
    }
  }

  if (!m_vulkanContextFactory)
  {
    auto oglFactory = make_unique_dp<AndroidOGLContextFactory>(env, jSurface);
    if (!oglFactory->IsValid())
    {
      LOG(LWARNING, ("Invalid GL context."));
      return false;
    }
    p.m_apiVersion = dp::ApiVersion::OpenGLES3;
    p.m_surfaceWidth = oglFactory->GetWidth();
    p.m_surfaceHeight = oglFactory->GetHeight();

    m_oglContextFactory = make_unique_dp<dp::ThreadSafeFactory>(oglFactory.release());
  }

  p.m_visualScale = static_cast<float>(dp::VisualScale(densityDpi));
  // Drape doesn't care about Editor vs Api mode differences.
  p.m_isChoosePositionMode = m_isChoosePositionMode != ChoosePositionMode::None;
  p.m_hints.m_isFirstLaunch = firstLaunch;
  p.m_hints.m_isLaunchByDeepLink = launchByDeepLink;
  ASSERT(!m_guiPositions.empty(), ("GUI elements must be set-up before engine is created"));
  p.m_widgetsInitInfo = m_guiPositions;

  m_work.SetMyPositionModeListener(bind(&Framework::MyPositionModeChanged, this, _1, _2));

  if (m_vulkanContextFactory)
    m_work.CreateDrapeEngine(make_ref(m_vulkanContextFactory), std::move(p));
  else
    m_work.CreateDrapeEngine(make_ref(m_oglContextFactory), std::move(p));
  m_work.EnterForeground();

  return true;
}

bool Framework::IsDrapeEngineCreated() const
{
  return m_work.IsDrapeEngineCreated();
}

void Framework::UpdateDpi(int dpi)
{
  ASSERT_GREATER(dpi, 0, ());
  m_work.UpdateVisualScale(dp::VisualScale(dpi));
}

void Framework::Resize(JNIEnv * env, jobject jSurface, int w, int h)
{
  if (m_vulkanContextFactory)
  {
    auto vulkanContextFactory = CastFactory(m_vulkanContextFactory);
    if (vulkanContextFactory->GetWidth() != w || vulkanContextFactory->GetHeight() != h)
    {
      m_vulkanContextFactory->SetPresentAvailable(false);
      m_work.SetRenderingDisabled(false /* destroySurface */);

      vulkanContextFactory->ChangeSurface(env, jSurface, w, h);

      vulkanContextFactory->SetPresentAvailable(true);
      m_work.SetRenderingEnabled();
    }
  }
  else
  {
    m_oglContextFactory->CastFactory<AndroidOGLContextFactory>()->UpdateSurfaceSize(w, h);
  }
  m_work.OnSize(w, h);
}

void Framework::DetachSurface(bool destroySurface)
{
  LOG(LINFO, ("Detach surface started. destroySurface =", destroySurface));
  if (m_vulkanContextFactory)
  {
    m_vulkanContextFactory->SetPresentAvailable(false);
  }
  else
  {
    ASSERT(m_oglContextFactory != nullptr, ());
    m_oglContextFactory->SetPresentAvailable(false);
  }

  if (destroySurface)
  {
    LOG(LINFO, ("Destroy surface."));
    m_isSurfaceDestroyed = true;
    m_work.OnDestroySurface();
  }

  if (m_vulkanContextFactory)
  {
    // With Vulkan we don't need to recreate all graphics resources,
    // we have to destroy only resources bound with surface (swapchains,
    // image views, framebuffers and command buffers). All these resources will be
    // destroyed in ResetSurface().
    m_work.SetRenderingDisabled(false /* destroySurface */);

    // Allow pipeline dump only on enter background.
    CastFactory(m_vulkanContextFactory)->ResetSurface(destroySurface /* allowPipelineDump */);
  }
  else
  {
    m_work.SetRenderingDisabled(destroySurface);
    auto factory = m_oglContextFactory->CastFactory<AndroidOGLContextFactory>();
    factory->ResetSurface();
  }
  LOG(LINFO, ("Detach surface finished."));
}

bool Framework::AttachSurface(JNIEnv * env, jobject jSurface)
{
  LOG(LINFO, ("Attach surface started."));

  int w = 0, h = 0;
  if (m_vulkanContextFactory)
  {
    auto factory = CastFactory(m_vulkanContextFactory);
    factory->SetSurface(env, jSurface);
    if (!factory->IsValid())
    {
      LOG(LWARNING, ("Invalid Vulkan API context."));
      return false;
    }
    w = factory->GetWidth();
    h = factory->GetHeight();
  }
  else
  {
    ASSERT(m_oglContextFactory != nullptr, ());
    auto factory = m_oglContextFactory->CastFactory<AndroidOGLContextFactory>();
    factory->SetSurface(env, jSurface);
    if (!factory->IsValid())
    {
      LOG(LWARNING, ("Invalid GL context."));
      return false;
    }
    w = factory->GetWidth();
    h = factory->GetHeight();
  }

  ASSERT(!m_guiPositions.empty(), ("GUI elements must be set-up before engine is created"));

  if (m_vulkanContextFactory)
  {
    m_vulkanContextFactory->SetPresentAvailable(true);
    m_work.SetRenderingEnabled();
  }
  else
  {
    m_oglContextFactory->SetPresentAvailable(true);
    m_work.SetRenderingEnabled(make_ref(m_oglContextFactory));
  }

  if (m_isSurfaceDestroyed)
  {
    LOG(LINFO, ("Recover surface, viewport size:", w, h));
    bool const recreateContextDependentResources = (m_vulkanContextFactory == nullptr);
    m_work.OnRecoverSurface(w, h, recreateContextDependentResources);
    m_isSurfaceDestroyed = false;
  }

  LOG(LINFO, ("Attach surface finished."));

  return true;
}

void Framework::PauseSurfaceRendering()
{
  if (m_vulkanContextFactory)
    m_vulkanContextFactory->SetPresentAvailable(false);
  if (m_oglContextFactory)
    m_oglContextFactory->SetPresentAvailable(false);

  LOG(LINFO, ("Pause surface rendering."));
}

void Framework::ResumeSurfaceRendering()
{
  if (m_vulkanContextFactory)
  {
    if (CastFactory(m_vulkanContextFactory)->IsValid())
      m_vulkanContextFactory->SetPresentAvailable(true);
  }
  if (m_oglContextFactory)
  {
    AndroidOGLContextFactory * factory = m_oglContextFactory->CastFactory<AndroidOGLContextFactory>();
    if (factory->IsValid())
      factory->SetPresentAvailable(true);
  }
  LOG(LINFO, ("Resume surface rendering."));
}

void Framework::SwitchToMapAppearance(MapAppearance mapAppearance)
{
  m_work.SwitchToMapAppearance(mapAppearance);
}

MapAppearance Framework::CurrentMapAppearance()
{
  return m_work.CurrentMapAppearance();
}

void Framework::SwitchToMapMode(MapMode mapMode)
{
  m_work.SwitchToMapMode(mapMode);
}

MapMode Framework::CurrentMapMode()
{
  return m_work.CurrentMapMode();
}

void Framework::SwitchToUsingVehicleStyle(bool enabled)
{
  m_work.SwitchToUsingVehicleStyle(enabled);
}

bool Framework::IsUsingVehicleStyle()
{
  return m_work.IsUsingVehicleStyle();
}

void Framework::Save3dMode(bool allow3d, bool allow3dBuildings)
{
  m_work.Save3dMode(allow3d, allow3dBuildings);
}

void Framework::Set3dMode(bool allow3d, bool allow3dBuildings)
{
  m_work.Allow3dMode(allow3d, allow3dBuildings);
}

void Framework::Get3dMode(bool & allow3d, bool & allow3dBuildings)
{
  m_work.Load3dMode(allow3d, allow3dBuildings);
}

void Framework::SetMapLanguageCode(std::string const & languageCode)
{
  m_work.SetCustomMapLanguageCode(languageCode);
}

std::string Framework::GetMapLanguageCode()
{
  return m_work.GetCustomMapLanguageCode().value_or("auto");
}

void Framework::SetChoosePositionMode(ChoosePositionMode mode, bool isBusiness, m2::PointD const * optionalPosition)
{
  m_isChoosePositionMode = mode;
  m_work.BlockTapEvents(mode != ChoosePositionMode::None);
  m_work.EnableChoosePositionMode(mode != ChoosePositionMode::None, isBusiness, optionalPosition);
}

ChoosePositionMode Framework::GetChoosePositionMode()
{
  return m_isChoosePositionMode;
}

Storage & Framework::GetStorage()
{
  return m_work.GetStorage();
}

DataSource const & Framework::GetDataSource()
{
  return m_work.GetDataSource();
}

void Framework::ShowNode(CountryId const & idx, bool zoomToDownloadButton)
{
  if (zoomToDownloadButton)
  {
    m2::RectD const rect = CalcLimitRect(idx, m_work.GetStorage(), m_work.GetCountryInfoGetter());
    m_work.SetViewportCenter(rect.Center(), 10);
  }
  else
  {
    m_work.ShowNode(idx);
  }
}

void Framework::Scale(double factor, m2::PointD const & pxPoint, bool isAnim)
{
  m_work.Scale(factor, pxPoint, isAnim);
}

void Framework::Scroll(double distanceX, double distanceY)
{
  m_work.Scroll(distanceX, distanceY);
}

void Framework::Touch(int action, Finger const & f1, Finger const & f2, uint8_t maskedPointer)
{
  MultiTouchAction eventType = static_cast<MultiTouchAction>(action);
  df::TouchEvent event;

  switch (eventType)
  {
  case MULTITOUCH_DOWN: event.SetTouchType(df::TouchEvent::TOUCH_DOWN); break;
  case MULTITOUCH_MOVE: event.SetTouchType(df::TouchEvent::TOUCH_MOVE); break;
  case MULTITOUCH_UP: event.SetTouchType(df::TouchEvent::TOUCH_UP); break;
  case MULTITOUCH_CANCEL: event.SetTouchType(df::TouchEvent::TOUCH_CANCEL); break;
  default: return;
  }

  df::Touch touch;
  touch.m_location = m2::PointD(f1.m_x, f1.m_y);
  touch.m_id = f1.m_id;
  event.SetFirstTouch(touch);

  touch.m_location = m2::PointD(f2.m_x, f2.m_y);
  touch.m_id = f2.m_id;
  event.SetSecondTouch(touch);

  event.SetFirstMaskedPointer(maskedPointer);
  m_work.TouchEvent(event);
}

m2::PointD Framework::GetViewportCenter() const
{
  return m_work.GetViewportCenter();
}

void Framework::AddString(string const & name, string const & value)
{
  m_work.AddString(name, value);
}

void Framework::Scale(::Framework::EScaleMode mode)
{
  m_work.Scale(mode, true);
}

void Framework::Scale(m2::PointD const & centerPt, int targetZoom, bool animate)
{
  ref_ptr<df::DrapeEngine> engine = m_work.GetDrapeEngine();
  if (engine)
    engine->SetModelViewCenter(centerPt, targetZoom, animate, false);
}

dp::TAccessibilityStableID Framework::GetAccessibilityNodeAtPoint(m2::PointD const & point)
{
  ref_ptr<df::DrapeEngine> engine = m_work.GetDrapeEngine();
  if (engine)
  {
    auto presenter = engine->GetAccessibilityPresenter();
    if (presenter)
      return (*presenter)->GetNodeAtPoint(point);
  }
  return 0;
}

// Not reentrant-safe; return value is only valid until the next call of this method.
dp::TAccessibilityStableIDContainer & Framework::GetAllAccessibilityNodes()
{
  m_allAccessibilityNodes.clear();

  ref_ptr<df::DrapeEngine> engine = m_work.GetDrapeEngine();
  if (engine)
  {
    auto presenter = engine->GetAccessibilityPresenter();
    if (presenter)
      (*presenter)->GetAllNodes(m_allAccessibilityNodes);
  }
  return m_allAccessibilityNodes;
}

// Not async-safe; return value is only valid until control of the thread is yielded.
// Make a copy if you want to store it.
std::optional<ref_ptr<dp::AccessibilityNodeContext>> Framework::GetAccessibilityNodeContext(
    dp::TAccessibilityStableID id)
{
  ref_ptr<df::DrapeEngine> engine = m_work.GetDrapeEngine();
  if (engine)
  {
    auto presenter = engine->GetAccessibilityPresenter();
    if (presenter)
    {
      auto overlay = (*presenter)->GetNode(id);
      if (overlay)
        return overlay;
    }
  }
  return {};
}

bool Framework::SetAccessibilityUpdateCallback(std::optional<dp::AccessibilityPresenter::TUpdateCallback> const & cb)
{
  ref_ptr<df::DrapeEngine> engine = m_work.GetDrapeEngine();
  if (engine)
  {
    auto presenter = engine->GetAccessibilityPresenter();

    if (static_cast<bool>(presenter) != static_cast<bool>(cb))
    {
      std::optional<drape_ptr<dp::AccessibilityPresenter>> new_presenter;
      if (cb)
        new_presenter = make_unique_dp<dp::AccessibilityPresenter>();
      else
        new_presenter = {};
      engine->SetAccessibilityPresenter(std::move(new_presenter));
      presenter = engine->GetAccessibilityPresenter();
    }

    if (presenter)
    {
      (*presenter)->SetUpdateCallback(cb);
      return true;
    }
  }
  return !cb;
}

void Framework::SetAccessibilityFreezeFrame(bool freeze)
{
  // we could just freeze the accessibility presenter, but better to freeze the whole drape engine so that the screen aligns
  // (e.g. for low vision users who still use sight during touch exploration)
  if (freeze)
    m_work.SetRenderingDisabled(false /* destroySurface */);
  else
    m_work.SetRenderingEnabled();
}

::Framework * Framework::NativeFramework()
{
  return &m_work;
}

bool Framework::Search(search::EverywhereSearchParams const & params)
{
  m_searchQuery = params.m_query;
  return m_work.GetSearchAPI().SearchEverywhere(params);
}

void Framework::AddLocalMaps()
{
  m_work.RegisterAllMaps();
}

void Framework::RemoveLocalMaps()
{
  m_work.DeregisterAllMaps();
}

void Framework::ReloadWorldMaps()
{
  /// @todo Can invent more optimal routine to remove/add World files only.
  RemoveLocalMaps();
  AddLocalMaps();
}

void Framework::ReplaceBookmark(kml::MarkId markId, kml::BookmarkData & bm)
{
  m_work.GetBookmarkManager().GetEditSession().UpdateBookmark(markId, bm);
}

void Framework::ReplaceTrack(kml::TrackId trackId, kml::TrackData & trackData)
{
  m_work.GetBookmarkManager().GetEditSession().UpdateTrack(trackId, trackData);
}

void Framework::ChangeTrackColor(kml::TrackId trackId, dp::Color color)
{
  m_work.GetBookmarkManager().GetEditSession().ChangeTrackColor(trackId, color);
}

void Framework::MoveBookmark(kml::MarkId markId, kml::MarkGroupId curCat, kml::MarkGroupId newCat)
{
  m_work.GetBookmarkManager().GetEditSession().MoveBookmark(markId, curCat, newCat);
}

void Framework::MoveTrack(kml::TrackId trackId, kml::MarkGroupId curCat, kml::MarkGroupId newCat)
{
  m_work.GetBookmarkManager().GetEditSession().MoveTrack(trackId, curCat, newCat);
}

void Framework::ExecuteMapApiRequest()
{
  return m_work.ExecuteMapApiRequest();
}

void Framework::DeactivatePopup()
{
  m_work.DeactivateMapSelection();
}

void Framework::DeactivateMapSelectionCircle(bool restoreViewport)
{
  m_work.DeactivateMapSelectionCircle(restoreViewport);
}

/*
string Framework::GetOutdatedCountriesString()
{
  vector<Country const *> countries;
  class Storage const & storage = GetStorage();
  storage.GetOutdatedCountries(countries);

  string res;
  NodeAttrs attrs;

  for (size_t i = 0; i < countries.size(); ++i)
  {
    storage.GetNodeAttrs(countries[i]->Name(), attrs);

    if (i > 0)
      res += ", ";

    res += attrs.m_nodeLocalName;
  }

  return res;
}
*/

void Framework::SetTrafficStateListener(TrafficManager::TrafficStateChangedFn const & fn)
{
  m_onTrafficStateChangedFn = fn;
}

void Framework::SetTransitSchemeListener(TransitReadManager::TransitStateChangedFn const & function)
{
  m_onTransitStateChangedFn = function;
}

void Framework::SetIsolinesListener(IsolinesManager::IsolinesStateChangedFn const & function)
{
  m_onIsolinesStateChangedFn = function;
}

bool Framework::IsTrafficEnabled()
{
  return NativeFramework()->DrivingMapModeHasTraffic();
}

void Framework::EnableTraffic()
{
  NativeFramework()->DrivingMapModeSetTraffic(true);
}

void Framework::DisableTraffic()
{
  NativeFramework()->DrivingMapModeSetTraffic(false);
}

void Framework::SetMyPositionModeListener(location::TMyPositionModeChanged const & fn)
{
  m_myPositionModeSignal = fn;
}

location::EMyPositionMode Framework::GetMyPositionMode() const
{
  // No need in assertion here, return location::PendingPosition if no engine created.
  // ASSERT(IsDrapeEngineCreated(), ());

  return m_work.GetMyPositionMode();
}

void Framework::SwitchMyPositionNextMode()
{
  ASSERT(IsDrapeEngineCreated(), ());
  m_work.SwitchMyPositionNextMode();
}

void Framework::StartPendingPositionMode()
{
  ASSERT(IsDrapeEngineCreated(), ());
  m_work.StartPendingPositionMode();
}

void Framework::SetupWidget(gui::EWidget widget, float x, float y, dp::Anchor anchor)
{
  m_guiPositions[widget] = gui::Position(m2::PointF(x, y), anchor);
}

void Framework::UpdateMyPositionRoutingOffset(int offsetY)
{
  NativeFramework()->UpdateMyPositionRoutingOffset(false, offsetY);
}

void Framework::ApplyWidgets()
{
  gui::TWidgetsLayoutInfo layout;
  for (auto const & widget : m_guiPositions)
    layout[widget.first] = widget.second.m_pixelPivot;

  m_work.SetWidgetLayout(std::move(layout));
}

void Framework::CleanWidgets()
{
  m_guiPositions.clear();
}

void Framework::SetupMeasurementSystem()
{
  m_work.SetupMeasurementSystem();
}

place_page::Info & Framework::GetPlacePageInfo()
{
  return m_work.GetCurrentPlacePageInfo();
}

bool Framework::IsAutoRetryDownloadFailed()
{
  return m_work.GetDownloadingPolicy().IsAutoRetryDownloadFailed();
}

bool Framework::IsDownloadOn3gEnabled()
{
  return m_work.GetDownloadingPolicy().IsCellularDownloadEnabled();
}

void Framework::EnableDownloadOn3g()
{
  m_work.GetDownloadingPolicy().EnableCellularDownload(true);
}

/*
int Framework::ToDoAfterUpdate() const
{
  return (int) m_work.ToDoAfterUpdate();
}
*/

void Framework::OnPowerFacilityChanged(power_management::Facility const facility, bool enabled)
{
  // Dummy
  // TODO: provide information for UI Properties.
}

void Framework::OnPowerSchemeChanged(power_management::Scheme const actualScheme)
{
  // Dummy
  // TODO: provide information for UI Properties.
}

FeatureID Framework::BuildFeatureId(JNIEnv * env, jobject featureId)
{
  static FeatureIdBuilder const builder(env);

  return builder.Build(env, featureId);
}
}  // namespace android

//============ GLUE CODE for app.organicmaps.Framework class =============//
/*            ____
 *          _ |||| _
 *          \\    //
 *           \\  //
 *            \\//
 *             \/
 */

extern "C"
{
void CallRoutingListener(shared_ptr<jobject> listener, int errorCode, storage::CountriesSet const & absentMaps)
{
  JNIEnv * env = jni::GetEnv();
  jmethodID const method = jni::GetMethodID(env, *listener, "onRoutingEvent", "(I[Ljava/lang/String;)V");
  ASSERT(method, ());

  env->CallVoidMethod(*listener, method, errorCode,
                      jni::TScopedLocalObjectArrayRef(env, jni::ToJavaStringArray(env, absentMaps)).get());
}

void CallRouteProgressListener(shared_ptr<jobject> listener, float progress)
{
  JNIEnv * env = jni::GetEnv();
  jmethodID const methodId = jni::GetMethodID(env, *listener, "onRouteBuildingProgress", "(F)V");
  env->CallVoidMethod(*listener, methodId, progress);
}

void CallRouteRecommendationListener(shared_ptr<jobject> listener, RoutingManager::Recommendation recommendation)
{
  JNIEnv * env = jni::GetEnv();
  jmethodID const methodId =
      jni::GetMethodID(env, *listener, "onRecommend", "(Lapp/organicmaps/sdk/routing/RouteRecommendationType;)V");
  env->CallVoidMethod(*listener, methodId, GetRouteRecommendationType(env, recommendation));
}

void CallSetRoutingLoadPointsListener(shared_ptr<jobject> listener, bool success)
{
  JNIEnv * env = jni::GetEnv();
  jmethodID const methodId = jni::GetMethodID(env, *listener, "onRoutePointsLoaded", "(Z)V");
  env->CallVoidMethod(*listener, methodId, static_cast<jboolean>(success));
}

RoutingManager::LoadRouteHandler g_loadRouteHandler;

/// @name JNI EXPORTS
JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetAddress(JNIEnv * env, jclass clazz, jdouble lat,
                                                                              jdouble lon)
{
  auto const info = frm()->GetAddressAtPoint(mercator::FromLatLon(lat, lon));
  return jni::ToJavaString(env, info.FormatAddress());
}

// TrackZero: name of the street closest to a point, within maxDistanceM; "" when there is none or no map.
// nativeGetAddress only looks at buildings under the point, so it can't name the road a route turns into.
JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetStreetName(JNIEnv * env, jclass, jdouble lat,
                                                                                jdouble lon, jdouble maxDistanceM)
{
  m2::PointD const pt = mercator::FromLatLon(lat, lon);
  auto const & dataSource = frm()->GetDataSource();
  auto const mwmId = dataSource.GetMwmIdByCountryFile(
      platform::CountryFile(frm()->GetCountryInfoGetter().GetRegionCountryId(pt)));
  auto handle = dataSource.GetMwmHandleById(mwmId);
  if (!handle.IsAlive())
    return jni::ToJavaString(env, "");
  search::MwmContext context(std::move(handle));
  auto const streets = search::ReverseGeocoder::GetNearbyStreets(context, pt, maxDistanceM);
  if (streets.empty() || streets.front().m_distanceMeters > maxDistanceM)
    return jni::ToJavaString(env, "");
  return jni::ToJavaString(env, streets.front().m_name);
}

// TrackZero: for each point, whether the road network has a junction (3 or more road arms meeting) within radiusM:
// 1 yes, 0 no, -1 unknown (no map there). Tells a turn at a crossroads from a bend in a single road.
// A way counts 1 arm at a node it ends at and 2 at a node it passes through. Its own vertex or not: the generator may
// drop a straight road's node where a side road joins it.
JNIEXPORT jintArray JNICALL Java_app_organicmaps_sdk_Framework_nativeRoadJunctions(JNIEnv * env, jclass,
                                                                                  jdoubleArray lats, jdoubleArray lons,
                                                                                  jdouble radiusM)
{
  double constexpr kSameNodeM = 1.0;
  jsize const n = env->GetArrayLength(lats);
  std::vector<jint> result(n, -1);
  jdouble * lat = env->GetDoubleArrayElements(lats, nullptr);
  jdouble * lon = env->GetDoubleArrayElements(lons, nullptr);
  auto const & dataSource = frm()->GetDataSource();
  auto const & infoGetter = frm()->GetCountryInfoGetter();
  auto const & isWay = ftypes::IsWayChecker::Instance();

  std::vector<std::vector<m2::PointD>> ways;  // In metres around the point.
  for (jsize i = 0; i < n; ++i)
  {
    m2::PointD const center = mercator::FromLatLon(lat[i], lon[i]);
    auto const mwmId = dataSource.GetMwmIdByCountryFile(platform::CountryFile(infoGetter.GetRegionCountryId(center)));
    if (!mwmId.IsAlive())
      continue;

    // Mercator is conformal: one scale for both axes this close to the point.
    double const metresPerUnit = mercator::DistanceOnEarth(center, center + m2::PointD(1e-4, 0)) / 1e-4;
    ways.clear();
    dataSource.ForEachInRectForMWM([&](FeatureType & ft)
    {
      if (ft.GetGeomType() != feature::GeomType::Line || !isWay(ft))
        return;
      ft.ParseGeometry(FeatureType::BEST_GEOMETRY);
      auto & way = ways.emplace_back();
      for (size_t k = 0; k < ft.GetPointsCount(); ++k)
        way.push_back((ft.GetPoint(k) - center) * metresPerUnit);
    }, mercator::RectByCenterXYAndSizeInMeters(center, 2 * (radiusM + kSameNodeM)), scales::GetUpperScale(), mwmId);

    auto const armsAt = [&ways](m2::PointD const & node)
    {
      int arms = 0;
      for (auto const & way : ways)
      {
        bool const closed = way.size() > 2 && way.front().Length(way.back()) < kSameNodeM;
        if (!closed && (node.Length(way.front()) < kSameNodeM || node.Length(way.back()) < kSameNodeM))
        {
          arms += 1;
          continue;
        }
        for (size_t k = 0; k + 1 < way.size(); ++k)
        {
          m2::ParametrizedSegment<m2::PointD> const segment(way[k], way[k + 1]);
          if (segment.SquaredDistanceToPoint(node) < kSameNodeM * kSameNodeM)
          {
            arms += 2;
            break;
          }
        }
      }
      return arms;
    };

    result[i] = 0;
    for (auto const & way : ways)
    {
      for (auto const & node : way)
      {
        if (node.Length() <= radiusM && armsAt(node) >= 3)
        {
          result[i] = 1;
          break;
        }
      }
      if (result[i] == 1)
        break;
    }
  }
  env->ReleaseDoubleArrayElements(lats, lat, JNI_ABORT);
  env->ReleaseDoubleArrayElements(lons, lon, JNI_ABORT);
  jintArray out = env->NewIntArray(n);
  env->SetIntArrayRegion(out, 0, n, result.data());
  return out;
}

// TrackZero: for each point, a code for the closest road within radiusM: codeOf(that road), 0 with no road there,
// -1 without a map at the point. The map index answers a query with whole cells, far larger than a point's radius (a
// 30 m query read about 560 features). So consecutive points (samples along a route) are read together, a stretch of
// up to 1 km at a time, and each takes the closest road from what was read. Reads map data only: safe off the main
// thread. (C++ linkage: this file's JNI functions sit in an extern "C" block.)
extern "C++"
{
namespace
{
template <typename CodeOf>
jintArray ClosestRoadCodes(JNIEnv * env, jdoubleArray lats, jdoubleArray lons, double radiusM, CodeOf && codeOf)
{
  double constexpr kStretchM = 1000.0;
  size_t constexpr kStretchPoints = 32;
  jsize const n = env->GetArrayLength(lats);
  std::vector<jint> result(n, -1);
  std::vector<m2::PointD> points(n);
  {
    jdouble * lat = env->GetDoubleArrayElements(lats, nullptr);
    jdouble * lon = env->GetDoubleArrayElements(lons, nullptr);
    for (jsize i = 0; i < n; ++i)
      points[i] = mercator::FromLatLon(lat[i], lon[i]);
    env->ReleaseDoubleArrayElements(lats, lat, JNI_ABORT);
    env->ReleaseDoubleArrayElements(lons, lon, JNI_ABORT);
  }
  auto const & dataSource = frm()->GetDataSource();
  auto const & infoGetter = frm()->GetCountryInfoGetter();
  auto const & isWay = ftypes::IsWayChecker::Instance();

  std::vector<double> closestM;
  std::vector<jint> codes;
  for (jsize first = 0; first < n;)
  {
    // The stretch: points from `first` while their bounding box stays small.
    m2::RectD box(points[first], points[first]);
    jsize last = first + 1;
    while (last < n && static_cast<size_t>(last - first) < kStretchPoints)
    {
      m2::RectD grown = box;
      grown.Add(points[last]);
      if (mercator::DistanceOnEarth(grown.LeftBottom(), grown.RightTop()) > kStretchM)
        break;
      box = grown;
      ++last;
    }
    // Mercator is conformal: one scale for both axes over a kilometre.
    double const metresPerUnit = mercator::DistanceOnEarth(box.Center(), box.Center() + m2::PointD(1e-4, 0)) / 1e-4;
    box.Inflate(radiusM / metresPerUnit, radiusM / metresPerUnit);

    closestM.assign(last - first, radiusM);
    codes.assign(last - first, -1);
    // Every country map around the stretch at once (the world map's simplified roads do not count).
    dataSource.ForEachInRect([&](FeatureType & ft)
    {
      if (ft.GetGeomType() != feature::GeomType::Line ||
          ft.GetID().m_mwmId.GetInfo()->GetType() != MwmInfo::COUNTRY || !isWay(ft))
        return;
      ft.ParseGeometry(FeatureType::BEST_GEOMETRY);
      if (!ft.GetLimitRect(FeatureType::BEST_GEOMETRY).IsIntersect(box))
        return;
      jint code = -1;  // Worked out when the road is the closest to some point.
      for (jsize i = first; i < last; ++i)
      {
        double d2 = std::numeric_limits<double>::max();
        for (size_t k = 0; k + 1 < ft.GetPointsCount(); ++k)
        {
          m2::ParametrizedSegment<m2::PointD> const segment(ft.GetPoint(k), ft.GetPoint(k + 1));
          d2 = std::min(d2, segment.SquaredDistanceToPoint(points[i]));
        }
        double const d = std::sqrt(d2) * metresPerUnit;
        if (d > closestM[i - first])
          continue;
        if (code < 0)
          code = codeOf(feature::TypesHolder(ft));
        closestM[i - first] = d;
        codes[i - first] = code;
      }
    }, box, scales::GetUpperScale());

    for (jsize i = first; i < last; ++i)
    {
      // No road near: 0 when there is a map here, -1 when there is none.
      if (codes[i - first] < 0)
        result[i] = dataSource.GetMwmIdByCountryFile(platform::CountryFile(infoGetter.GetRegionCountryId(points[i])))
                            .IsAlive()
                      ? 0
                      : -1;
      else
        result[i] = codes[i - first];
    }
    first = last;
  }
  jintArray out = env->NewIntArray(n);
  env->SetIntArrayRegion(out, 0, n, result.data());
  return out;
}

// TrackZero (Phase 30): the places of a category near a line, as {String[] names, double[] lats, double[] lons,
// int[] kinds, String[] openingHours, int[] flags}. Kinds: 1 drinking water, 2 water point, 3 water tap, 4 spring
// (category 1); 5 bicycle repair station, 6 bicycle shop (category 2); 7 campsite, 8 hut (wilderness or alpine),
// 9 shelter (category 3, Phase 31; not one that is a stop or platform: those are for waiting, not sleeping). Flags: 1 a fee, 2 no fee, 4 private access.
// Taps, springs and water points the map marks as not drinkable are left out. The line
// is read 2 km at a time (its box grown by radiusM), country maps only; point and area features, each once. Map data
// only: safe off the main thread.
jobjectArray PoisAlong(JNIEnv * env, jdoubleArray lats, jdoubleArray lons, double radiusM, jint category)
{
  double constexpr kPieceM = 2000.0;
  auto const & c = classif();
  static std::map<uint32_t, jint> const kKinds = [&c]
  {
    std::map<uint32_t, jint> m;
    m[c.GetTypeByPath({"amenity", "drinking_water"})] = 1;
    m[c.GetTypeByPath({"amenity", "water_point"})] = 2;
    m[c.GetTypeByPath({"man_made", "water_tap"})] = 3;
    m[c.GetTypeByPath({"natural", "spring"})] = 4;
    m[c.GetTypeByPath({"amenity", "bicycle_repair_station"})] = 5;
    m[c.GetTypeByPath({"shop", "bicycle"})] = 6;
    m[c.GetTypeByPath({"tourism", "camp_site"})] = 7;
    m[c.GetTypeByPath({"tourism", "wilderness_hut"})] = 8;
    m[c.GetTypeByPath({"tourism", "alpine_hut"})] = 8;
    m[c.GetTypeByPath({"amenity", "shelter"})] = 9;
    return m;
  }();
  static uint32_t const kBusShelter = c.GetTypeByPath({"amenity", "shelter", "public_transport"});
  // A shelter mapped on a stop or platform (often without its public_transport subtype) is for waiting.
  static std::vector<uint32_t> const kTransport = {
      c.GetTypeByPath({"highway", "bus_stop"}),          c.GetTypeByPath({"amenity", "bus_station"}),
      c.GetTypeByPath({"railway", "halt"}),              c.GetTypeByPath({"railway", "platform"}),
      c.GetTypeByPath({"public_transport", "platform"})};
  static uint32_t const kFeeYes = c.GetTypeByPath({"fee", "yes"});
  static uint32_t const kFeeNo = c.GetTypeByPath({"fee", "no"});
  static uint32_t const kPrivate = c.GetTypeByPath({"hwtag", "private"});
  static std::vector<uint32_t> const kNotDrinkable = {
      c.GetTypeByPath({"man_made", "water_tap", "drinking_water_no"}),
      c.GetTypeByPath({"natural", "spring", "drinking_water_no"}),
      c.GetTypeByPath({"amenity", "water_point", "drinking_water_no"})};
  auto const kindOf = [category](feature::TypesHolder const & types) -> jint
  {
    for (uint32_t const t : kNotDrinkable)
      if (types.Has(t))
        return 0;
    if (types.Has(kBusShelter))
      return 0;
    for (uint32_t t : types)
    {
      if (ftype::GetLevel(t) > 2)
        ftype::TruncValue(t, 2);
      if (std::find(kTransport.begin(), kTransport.end(), t) != kTransport.end())
        return 0;
    }
    for (uint32_t t : types)
    {
      if (ftype::GetLevel(t) > 2)
        ftype::TruncValue(t, 2);
      auto const it = kKinds.find(t);
      if (it == kKinds.end())
        continue;
      jint const kind = it->second;
      if ((category == 1 && kind <= 4) || (category == 2 && kind >= 5 && kind <= 6) || (category == 3 && kind >= 7))
        return kind;
    }
    return 0;
  };

  jsize const n = env->GetArrayLength(lats);
  std::vector<m2::PointD> points(n);
  {
    jdouble * lat = env->GetDoubleArrayElements(lats, nullptr);
    jdouble * lon = env->GetDoubleArrayElements(lons, nullptr);
    for (jsize i = 0; i < n; ++i)
      points[i] = mercator::FromLatLon(lat[i], lon[i]);
    env->ReleaseDoubleArrayElements(lats, lat, JNI_ABORT);
    env->ReleaseDoubleArrayElements(lons, lon, JNI_ABORT);
  }
  std::set<FeatureID> seen;
  std::vector<std::string> names;
  std::vector<jdouble> outLat, outLon;
  std::vector<jint> kinds;
  std::vector<std::string> hours;
  std::vector<jint> flags;
  auto const & dataSource = frm()->GetDataSource();
  for (jsize first = 0; first < n;)
  {
    m2::RectD box(points[first], points[first]);
    double pieceM = 0;
    jsize last = first + 1;
    while (last < n && pieceM < kPieceM)
    {
      pieceM += mercator::DistanceOnEarth(points[last - 1], points[last]);
      box.Add(points[last]);
      ++last;
    }
    double const metresPerUnit = mercator::DistanceOnEarth(box.Center(), box.Center() + m2::PointD(1e-4, 0)) / 1e-4;
    box.Inflate(radiusM / metresPerUnit, radiusM / metresPerUnit);
    dataSource.ForEachInRect([&](FeatureType & ft)
    {
      if (ft.GetGeomType() == feature::GeomType::Line ||
          ft.GetID().m_mwmId.GetInfo()->GetType() != MwmInfo::COUNTRY || seen.count(ft.GetID()) != 0)
        return;
      feature::TypesHolder const types(ft);
      jint const kind = kindOf(types);
      if (kind == 0)
        return;
      seen.insert(ft.GetID());
      auto const ll = mercator::ToLatLon(feature::GetCenter(ft, FeatureType::BEST_GEOMETRY));
      names.push_back(ft.GetTranslatedName().m_primary.value_or(std::string()));
      outLat.push_back(ll.m_lat);
      outLon.push_back(ll.m_lon);
      kinds.push_back(kind);
      hours.emplace_back(ft.GetMetadata(feature::Metadata::FMD_OPEN_HOURS));
      flags.push_back((types.Has(kFeeYes) ? 1 : 0) | (types.Has(kFeeNo) ? 2 : 0) | (types.Has(kPrivate) ? 4 : 0));
    }, box, scales::GetUpperScale());
    // The next piece starts where this one ended, so no stretch of the line is left out.
    first = last == n ? n : last - 1;
  }

  jsize const found = static_cast<jsize>(kinds.size());
  jdoubleArray jlat = env->NewDoubleArray(found);
  env->SetDoubleArrayRegion(jlat, 0, found, outLat.data());
  jdoubleArray jlon = env->NewDoubleArray(found);
  env->SetDoubleArrayRegion(jlon, 0, found, outLon.data());
  jintArray jkinds = env->NewIntArray(found);
  env->SetIntArrayRegion(jkinds, 0, found, kinds.data());
  jintArray jflags = env->NewIntArray(found);
  env->SetIntArrayRegion(jflags, 0, found, flags.data());
  jobjectArray out = env->NewObjectArray(6, env->FindClass("java/lang/Object"), nullptr);
  env->SetObjectArrayElement(out, 0, jni::TScopedLocalObjectArrayRef(env, jni::ToJavaStringArray(env, names)).get());
  env->SetObjectArrayElement(out, 1, jlat);
  env->SetObjectArrayElement(out, 2, jlon);
  env->SetObjectArrayElement(out, 3, jkinds);
  env->SetObjectArrayElement(out, 4, jni::TScopedLocalObjectArrayRef(env, jni::ToJavaStringArray(env, hours)).get());
  env->SetObjectArrayElement(out, 5, jflags);
  env->DeleteLocalRef(jflags);
  env->DeleteLocalRef(jlat);
  env->DeleteLocalRef(jlon);
  env->DeleteLocalRef(jkinds);
  return out;
}
}  // namespace
}  // extern "C++"

// TrackZero (FEAT-006): the surface of the closest road within radiusM of each point, as CoMaps' maps classify it:
// -1 no map at the point, 0 no surface information (no road within the radius, or one without a psurface type),
// 1 paved_good, 2 paved_bad, 3 unpaved_good, 4 unpaved_bad.
JNIEXPORT jintArray JNICALL Java_app_organicmaps_sdk_Framework_nativeRoadSurfaces(JNIEnv * env, jclass,
                                                                                 jdoubleArray lats, jdoubleArray lons,
                                                                                 jdouble radiusM)
{
  static uint32_t const kSurfaces[] = {
      classif().GetTypeByPath({"psurface", "paved_good"}), classif().GetTypeByPath({"psurface", "paved_bad"}),
      classif().GetTypeByPath({"psurface", "unpaved_good"}), classif().GetTypeByPath({"psurface", "unpaved_bad"})};
  return ClosestRoadCodes(env, lats, lons, radiusM, [](feature::TypesHolder const & types)
  {
    for (jint s = 0; s < 4; ++s)
      if (types.Has(kSurfaces[s]))
        return s + 1;
    return 0;
  });
}

// TrackZero (Phase 26): whether the closest way within radiusM is a demanding trail per the map: -1 no map, 0 not (or
// no way), 1 a path the map grades difficult or expert (from sac_scale and trail_visibility).
JNIEXPORT jintArray JNICALL Java_app_organicmaps_sdk_Framework_nativeRoadDifficulty(JNIEnv * env, jclass,
                                                                                   jdoubleArray lats, jdoubleArray lons,
                                                                                   jdouble radiusM)
{
  static uint32_t const kDifficult = classif().GetTypeByPath({"highway", "path", "difficult"});
  static uint32_t const kExpert = classif().GetTypeByPath({"highway", "path", "expert"});
  return ClosestRoadCodes(env, lats, lons, radiusM, [](feature::TypesHolder const & types)
  { return types.Has(kDifficult) || types.Has(kExpert) ? 1 : 0; });
}

// TrackZero (Phase 24, the profile comparison): the class of the closest road within radiusM of each point: -1 no
// map, 0 no road, 1 motorway or trunk, 2 primary, 3 secondary, 4 tertiary, 5 minor road (unclassified, residential,
// living street, service, road), 6 track, 7 path, cycleway, bridleway, footway, pedestrian street or steps.
JNIEXPORT jintArray JNICALL Java_app_organicmaps_sdk_Framework_nativeRoadClasses(JNIEnv * env, jclass,
                                                                                jdoubleArray lats, jdoubleArray lons,
                                                                                jdouble radiusM)
{
  static std::map<uint32_t, jint> const kClasses = []
  {
    std::map<uint32_t, jint> m;
    auto const add = [&m](jint code, std::initializer_list<char const *> names)
    {
      for (char const * name : names)
        m[classif().GetTypeByPath({"highway", name})] = code;
    };
    add(1, {"motorway", "motorway_link", "trunk", "trunk_link"});
    add(2, {"primary", "primary_link"});
    add(3, {"secondary", "secondary_link"});
    add(4, {"tertiary", "tertiary_link"});
    add(5, {"unclassified", "residential", "living_street", "service", "road"});
    add(6, {"track"});
    add(7, {"path", "cycleway", "bridleway", "footway", "pedestrian", "steps"});
    return m;
  }();
  return ClosestRoadCodes(env, lats, lons, radiusM, [](feature::TypesHolder const & types)
  {
    for (uint32_t t : types)
    {
      if (ftype::GetLevel(t) > 2)
        ftype::TruncValue(t, 2);
      if (auto const it = kClasses.find(t); it != kClasses.end())
        return it->second;
    }
    return 0;
  });
}

// TrackZero: name of the closest named peak or mountain pass within radiusM (a climb's summit), "" when none.
JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetSummitName(JNIEnv * env, jclass, jdouble lat,
                                                                                jdouble lon, jdouble radiusM)
{
  m2::PointD const center = mercator::FromLatLon(lat, lon);
  auto const & dataSource = frm()->GetDataSource();
  auto const mwmId = dataSource.GetMwmIdByCountryFile(
      platform::CountryFile(frm()->GetCountryInfoGetter().GetRegionCountryId(center)));
  if (!mwmId.IsAlive())
    return jni::ToJavaString(env, "");

  auto const & c = classif();
  uint32_t const peak = c.GetTypeByPath({"natural", "peak"});
  uint32_t const pass = c.GetTypeByPath({"mountain_pass"});
  std::string best;
  double bestDistance = radiusM;
  dataSource.ForEachInRectForMWM([&](FeatureType & ft)
  {
    if (ft.GetGeomType() != feature::GeomType::Point)
      return;
    feature::TypesHolder const types(ft);
    if (!types.Has(peak) && !types.Has(pass))
      return;
    auto const name = ft.GetTranslatedName().m_primary.value_or(std::string());
    if (name.empty())
      return;
    double const d = mercator::DistanceOnEarth(center, feature::GetCenter(ft));
    if (d <= bestDistance)
    {
      bestDistance = d;
      best = name;
    }
  }, mercator::RectByCenterXYAndSizeInMeters(center, 2 * radiusM), scales::GetUpperScale(), mwmId);
  return jni::ToJavaString(env, best);
}

// TrackZero (Phase 30): places of a category within radiusM of a line; see PoisAlong.
JNIEXPORT jobjectArray JNICALL Java_app_organicmaps_sdk_Framework_nativePoisAlong(JNIEnv * env, jclass,
                                                                                 jdoubleArray lats, jdoubleArray lons,
                                                                                 jdouble radiusM, jint category)
{
  return PoisAlong(env, lats, lons, radiusM, category);
}

// TrackZero (Phase 29): the town or village nearest the point (a loop's far side), "" when none. Main thread.
JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetPlaceName(JNIEnv * env, jclass, jdouble lat,
                                                                               jdouble lon)
{
  static search::CityFinder finder(frm()->GetDataSource());
  return jni::ToJavaString(env, finder.GetCityReadableName(mercator::FromLatLon(lat, lon)));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeClearApiPoints(JNIEnv * env, jclass clazz)
{
  frm()->GetBookmarkManager().GetEditSession().ClearGroup(UserMark::Type::API);
}

JNIEXPORT jint JNICALL Java_app_organicmaps_sdk_Framework_nativeParseAndSetApiUrl(JNIEnv * env, jclass clazz,
                                                                                  jstring url)
{
  return static_cast<jint>(frm()->ParseAndSetApiURL(jni::ToNativeString(env, url)));
}

JNIEXPORT jobject JNICALL Java_app_organicmaps_sdk_Framework_nativeGetParsedRoutingData(JNIEnv * env, jclass clazz)
{
  using namespace url_scheme;
  static jclass const pointClazz = jni::GetGlobalClassRef(env, "app/organicmaps/sdk/api/RoutePoint");
  // Java signature : RoutePoint(double lat, double lon, String name)
  static jmethodID const pointConstructor = jni::GetConstructorID(env, pointClazz, "(DDLjava/lang/String;)V");

  static jclass const routeDataClazz = jni::GetGlobalClassRef(env, "app/organicmaps/sdk/api/ParsedRoutingData");
  // Java signature : ParsedRoutingData(RoutePoint[] points, int routerType) {
  static jmethodID const routeDataConstructor =
      jni::GetConstructorID(env, routeDataClazz, "([Lapp/organicmaps/sdk/api/RoutePoint;I)V");

  auto const & routingData = frm()->GetParsedRoutingData();
  jobjectArray points =
      jni::ToJavaArray(env, pointClazz, routingData.m_points, [](JNIEnv * env, RoutePoint const & point)
  {
    jni::TScopedLocalRef const name(env, jni::ToJavaString(env, point.m_name));
    return env->NewObject(pointClazz, pointConstructor, mercator::YToLat(point.m_org.y),
                          mercator::XToLon(point.m_org.x), name.get());
  });

  return env->NewObject(routeDataClazz, routeDataConstructor, points, routingData.m_type);
}

JNIEXPORT jobject JNICALL Java_app_organicmaps_sdk_Framework_nativeGetParsedSearchRequest(JNIEnv * env, jclass clazz)
{
  using namespace url_scheme;
  static jclass const cl = jni::GetGlobalClassRef(env, "app/organicmaps/sdk/api/ParsedSearchRequest");
  // Java signature : ParsedSearchRequest(String query, String locale, double lat, double lon, boolean isSearchOnMap)
  static jmethodID const ctor = jni::GetConstructorID(env, cl, "(Ljava/lang/String;Ljava/lang/String;DDZ)V");
  auto const & r = frm()->GetParsedSearchRequest();
  ms::LatLon const center = frm()->GetParsedCenterLatLon();
  return env->NewObject(cl, ctor, jni::ToJavaString(env, r.m_query), jni::ToJavaString(env, r.m_locale), center.m_lat,
                        center.m_lon, r.m_isSearchOnMap);
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetParsedAppName(JNIEnv * env, jclass)
{
  std::string const & appName = frm()->GetParsedAppName();
  return (appName.empty()) ? nullptr : jni::ToJavaString(env, appName);
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetParsedOAuth2Code(JNIEnv * env, jclass)
{
  std::string const & code = frm()->GetParsedOAuth2Code();
  return jni::ToJavaString(env, code);
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetParsedBackUrl(JNIEnv * env, jclass)
{
  std::string const & backUrl = frm()->GetParsedBackUrl();
  return (backUrl.empty()) ? nullptr : jni::ToJavaString(env, backUrl);
}

JNIEXPORT jdoubleArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetParsedCenterLatLon(JNIEnv * env, jclass)
{
  ms::LatLon const center = frm()->GetParsedCenterLatLon();
  if (!center.IsValid())
    return nullptr;

  double latlon[] = {center.m_lat, center.m_lon};
  jdoubleArray jLatLon = env->NewDoubleArray(2);
  env->SetDoubleArrayRegion(jLatLon, 0, 2, latlon);

  return jLatLon;
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativePlacePageActivationListener(JNIEnv * env, jclass,
                                                                                            jobject jListener)
{
  LOG(LINFO, ("Set global map object listener"));
  g_placePageActivationListener = env->NewGlobalRef(jListener);
  // void onPlacePageActivated(MapObject object);
  jmethodID const activatedId = jni::GetMethodID(env, g_placePageActivationListener, "onPlacePageActivated",
                                                 "(Lapp/organicmaps/sdk/widget/placepage/PlacePageData;)V");
  // void onPlacePageDeactivated();
  jmethodID const deactivateId = jni::GetMethodID(env, g_placePageActivationListener, "onPlacePageDeactivated", "()V");
  // void onPlacePageDeactivated();
  jmethodID const switchFullscreenId =
      jni::GetMethodID(env, g_placePageActivationListener, "onSwitchFullScreenMode", "()V");
  auto const fillPlacePage = [activatedId]()
  {
    JNIEnv * env = jni::GetEnv();
    auto const & info = frm()->GetCurrentPlacePageInfo();
    jni::TScopedLocalRef placePageDataRef(env, nullptr);
    placePageDataRef.reset(usermark_helper::CreateMapObject(env, info));
    env->CallVoidMethod(g_placePageActivationListener, activatedId, placePageDataRef.get());
  };
  auto const closePlacePage = [deactivateId]()
  {
    JNIEnv * env = jni::GetEnv();
    env->CallVoidMethod(g_placePageActivationListener, deactivateId);
  };
  auto const switchFullscreen = [switchFullscreenId]()
  {
    JNIEnv * env = jni::GetEnv();
    env->CallVoidMethod(g_placePageActivationListener, switchFullscreenId);
  };

  frm()->SetPlacePageListeners(fillPlacePage, closePlacePage, fillPlacePage, switchFullscreen);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeRemovePlacePageActivationListener(JNIEnv * env, jclass,
                                                                                                  jobject jListener)
{
  if (g_placePageActivationListener == nullptr)
    return;

  if (!env->IsSameObject(g_placePageActivationListener, jListener))
    return;

  frm()->SetPlacePageListeners({} /* onOpen */, {} /* onClose */, {} /* onUpdate */, {} /* onSwitchFullScreen */);
  LOG(LINFO, ("Remove global map object listener"));
  env->DeleteGlobalRef(g_placePageActivationListener);
  g_placePageActivationListener = nullptr;
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetGe0Url(JNIEnv * env, jclass, jdouble lat,
                                                                             jdouble lon, jdouble zoomLevel,
                                                                             jstring name)
{
  ::Framework * fr = frm();
  double const scale = (zoomLevel > 0 ? zoomLevel : fr->GetDrawScale());
  string const url = fr->CodeGe0url(lat, lon, scale, jni::ToNativeString(env, name));
  return jni::ToJavaString(env, url);
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetGeoUri(JNIEnv * env, jclass, jdouble lat,
                                                                             jdouble lon, jdouble zoomLevel,
                                                                             jstring name)
{
  ::Framework * fr = frm();
  double const scale = (zoomLevel > 0 ? zoomLevel : fr->GetDrawScale());
  string const url = ge0::GenerateGeoUri(lat, lon, scale, jni::ToNativeString(env, name));
  return jni::ToJavaString(env, url);
}

JNIEXPORT jobject JNICALL Java_app_organicmaps_sdk_Framework_nativeGetDistanceAndAzimuth(JNIEnv * env, jclass,
                                                                                         jdouble merX, jdouble merY,
                                                                                         jdouble cLat, jdouble cLon,
                                                                                         jdouble north)
{
  platform::Distance distance;
  double azimut = -1.0;
  frm()->GetDistanceAndAzimut(m2::PointD(merX, merY), cLat, cLon, north, distance, azimut);

  static jclass const daClazz = jni::GetGlobalClassRef(env, "app/organicmaps/sdk/bookmarks/data/DistanceAndAzimut");
  // Java signature : DistanceAndAzimut(Distance distance, double azimuth)
  static jmethodID const methodID = jni::GetConstructorID(env, daClazz, "(Lapp/organicmaps/sdk/util/Distance;D)V");

  return env->NewObject(daClazz, methodID, ToJavaDistance(env, distance), static_cast<jdouble>(azimut));
}

JNIEXPORT jobject JNICALL Java_app_organicmaps_sdk_Framework_nativeGetDistanceAndAzimuthFromLatLon(
    JNIEnv * env, jclass clazz, jdouble lat, jdouble lon, jdouble cLat, jdouble cLon, jdouble north)
{
  double const merY = mercator::LatToY(lat);
  double const merX = mercator::LonToX(lon);
  return Java_app_organicmaps_sdk_Framework_nativeGetDistanceAndAzimuth(env, clazz, merX, merY, cLat, cLon, north);
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeFormatLatLon(JNIEnv * env, jclass, jdouble lat,
                                                                                jdouble lon, int coordsFormat)
{
  switch (static_cast<android::CoordinatesFormat>(coordsFormat))
  {
  default:
  case android::CoordinatesFormat::LatLonDMS:  // DMS, comma separated
    return jni::ToJavaString(env, measurement_utils::FormatLatLonAsDMS(lat, lon, true /*withComma*/, 2));
  case android::CoordinatesFormat::LatLonDecimal:  // Decimal, comma separated
    return jni::ToJavaString(env, measurement_utils::FormatLatLon(lat, lon, true /* withComma */, 6));
  case android::CoordinatesFormat::OLCFull:  // Open location code, long format
    return jni::ToJavaString(env, openlocationcode::Encode({lat, lon}));
  case android::CoordinatesFormat::OSMLink:  // Link to osm.org
    return jni::ToJavaString(env, measurement_utils::FormatOsmLink(lat, lon, 14));
  case android::CoordinatesFormat::UTM:  // Universal Transverse Mercator
  {
    string utmFormat = utm_mgrs_utils::FormatUTM(lat, lon);
    if (!utmFormat.empty())
      return jni::ToJavaString(env, utmFormat);
    else
      return nullptr;
  }
  case android::CoordinatesFormat::MGRS:  // Military Grid Reference System
  {
    string mgrsFormat = utm_mgrs_utils::FormatMGRS(lat, lon, 5);
    if (!mgrsFormat.empty())
      return jni::ToJavaString(env, mgrsFormat);
    else
      return nullptr;
  }
  }
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeFormatAltitude(JNIEnv * env, jclass, jdouble alt)
{
  return jni::ToJavaString(env, platform::Distance::FormatAltitude(alt));
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeFormatSpeed(JNIEnv * env, jclass, jdouble speed)
{
  auto const units = measurement_utils::GetMeasurementUnits();
  return jni::ToJavaString(
      env, measurement_utils::FormatSpeedNumeric(speed, units) + " " + platform::GetLocalizedSpeedUnits(units));
}

/*
JNIEXPORT jobject JNICALL
Java_app_organicmaps_sdk_Framework_nativeGetOutdatedCountriesString(JNIEnv * env, jclass)
{
  return jni::ToJavaString(env, g_framework->GetOutdatedCountriesString());
}

JNIEXPORT jobjectArray JNICALL
Java_app_organicmaps_sdk_Framework_nativeGetOutdatedCountries(JNIEnv * env, jclass)
{
  vector<Country const *> countries;
  Storage const & storage = g_framework->GetStorage();
  storage.GetOutdatedCountries(countries);

  vector<string> ids;
  for (auto country : countries)
    ids.push_back(country->Name());

  return jni::ToJavaStringArray(env, ids);
}

JNIEXPORT jint JNICALL
Java_app_organicmaps_sdk_Framework_nativeToDoAfterUpdate(JNIEnv * env, jclass)
{
  return g_framework->ToDoAfterUpdate();
}

JNIEXPORT jboolean JNICALL
Java_app_organicmaps_sdk_Framework_nativeIsDataVersionChanged(JNIEnv * env, jclass)
{
  return frm()->IsDataVersionUpdated() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_organicmaps_sdk_Framework_nativeUpdateSavedDataVersion(JNIEnv * env, jclass)
{
  frm()->UpdateSavedDataVersion();
}
*/

JNIEXPORT jlong JNICALL Java_app_organicmaps_sdk_Framework_nativeGetDataVersion(JNIEnv * env, jclass)
{
  return frm()->GetCurrentDataVersion();
}

JNIEXPORT jint JNICALL Java_app_organicmaps_sdk_Framework_nativeGetDrawScale(JNIEnv * env, jclass)
{
  return static_cast<jint>(frm()->GetDrawScale());
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativePokeSearchInViewport(JNIEnv * env, jclass)
{
  frm()->GetSearchAPI().PokeSearchInViewport();
}

JNIEXPORT jdoubleArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetScreenRectCenter(JNIEnv * env, jclass)
{
  m2::PointD const center = frm()->GetViewportCenter();

  double latlon[] = {mercator::YToLat(center.y), mercator::XToLon(center.x)};
  jdoubleArray jLatLon = env->NewDoubleArray(2);
  env->SetDoubleArrayRegion(jLatLon, 0, 2, latlon);

  return jLatLon;
}

// TrackZero (Phase 28): a pixel of the map view as {lat, lon}, with the camera as last seen by the UI.
JNIEXPORT jdoubleArray JNICALL Java_app_organicmaps_sdk_Framework_nativeScreenToLatLon(JNIEnv * env, jclass, jfloat x,
                                                                                    jfloat y)
{
  auto const ll = mercator::ToLatLon(frm()->PtoG(m2::PointD(x, y)));
  double const out[] = {ll.m_lat, ll.m_lon};
  jdoubleArray result = env->NewDoubleArray(2);
  env->SetDoubleArrayRegion(result, 0, 2, out);
  return result;
}

// TrackZero (Phase 28): where {lat, lon} is in the map view, in pixels (may be off screen).
JNIEXPORT jfloatArray JNICALL Java_app_organicmaps_sdk_Framework_nativeLatLonToScreen(JNIEnv * env, jclass,
                                                                                   jdouble lat, jdouble lon)
{
  auto const p = frm()->GtoP(mercator::FromLatLon(lat, lon));
  float const out[] = {static_cast<float>(p.x), static_cast<float>(p.y)};
  jfloatArray result = env->NewFloatArray(2);
  env->SetFloatArrayRegion(result, 0, 2, out);
  return result;
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeShowTrackRect(JNIEnv * env, jclass, jlong track)
{
  frm()->ShowTrack(static_cast<kml::TrackId>(track));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSaveRoute(JNIEnv *, jclass)
{
  frm()->SaveRoute();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetAutoReroute(JNIEnv * env, jclass,
                                                                               jboolean autoReroute)
{
  frm()->GetRoutingManager().SetAutoReroute(static_cast<bool>(autoReroute));
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeAutoReroute(JNIEnv * env, jclass)
{
  return static_cast<jboolean>(frm()->GetRoutingManager().AutoReroute());
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetBookmarkDir(JNIEnv * env, jclass)
{
  return jni::ToJavaString(env, GetPlatform().SettingsDir().c_str());
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetWritableDir(JNIEnv * env, jclass)
{
  return jni::ToJavaString(env, GetPlatform().WritableDir().c_str());
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetSettingsDir(JNIEnv * env, jclass)
{
  return jni::ToJavaString(env, GetPlatform().SettingsDir().c_str());
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetDataFileExt(JNIEnv * env, jclass)
{
  return jni::ToJavaString(env, DATA_FILE_EXTENSION);
}

JNIEXPORT jobjectArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetMovableFilesExts(JNIEnv * env, jclass)
{
  vector<string> exts = {DATA_FILE_EXTENSION, FONT_FILE_EXTENSION};
  platform::CountryIndexes::GetIndexesExts(exts);
  return jni::ToJavaStringArray(env, exts);
}

JNIEXPORT jobjectArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetBookmarksFilesExts(JNIEnv * env, jclass)
{
  static std::array<std::string, 4> const kBookmarkExtensions = {
      std::string{kKmzExtension}, std::string{kKmlExtension}, std::string{kKmbExtension}, std::string{kGpxExtension}};

  return jni::ToJavaStringArray(env, kBookmarkExtensions);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeChangeWritableDir(JNIEnv * env, jclass,
                                                                                  jstring jNewPath)
{
  string newPath = jni::ToNativeString(env, jNewPath);
  g_framework->RemoveLocalMaps();
  android::Platform::Instance().SetWritableDir(newPath);
  g_framework->AddLocalMaps();
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsRoutingActive(JNIEnv * env, jclass)
{
  return frm()->GetRoutingManager().IsRoutingActive();
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsRouteBuilding(JNIEnv * env, jclass)
{
  return frm()->GetRoutingManager().IsRouteBuilding();
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsRouteBuilt(JNIEnv * env, jclass)
{
  return frm()->GetRoutingManager().IsRouteBuilt();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeCloseRouting(JNIEnv * env, jclass)
{
  frm()->GetRoutingManager().CloseRouting(true /* remove route points */);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeBuildRoute(JNIEnv * env, jclass)
{
  frm()->GetRoutingManager().BuildRoute();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeRemoveRoute(JNIEnv * env, jclass)
{
  frm()->GetRoutingManager().RemoveRoute(false /* deactivateFollowing */);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeFollowRoute(JNIEnv * env, jclass)
{
  frm()->GetRoutingManager().FollowRoute();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeDisableFollowing(JNIEnv * env, jclass)
{
  frm()->GetRoutingManager().DisableFollowMode();
}

JNIEXPORT jobjectArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGenerateNotifications(JNIEnv * env, jclass,
                                                                                              jboolean announceStreets)
{
  ::Framework * fr = frm();
  if (!fr->GetRoutingManager().IsRoutingActive())
    return nullptr;

  vector<string> notifications;
  fr->GetRoutingManager().GenerateNotifications(notifications, announceStreets);
  if (notifications.empty())
    return nullptr;

  return jni::ToJavaStringArray(env, notifications);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetSpeedCamManagerMode(JNIEnv * env, jclass, jint mode)
{
  frm()->GetRoutingManager().GetSpeedCamManager().SetMode(static_cast<routing::SpeedCameraManagerMode>(mode));
}

JNIEXPORT jint JNICALL Java_app_organicmaps_sdk_Framework_nativeGetSpeedCamManagerMode(JNIEnv * env, jclass)
{
  return static_cast<jint>(frm()->GetRoutingManager().GetSpeedCamManager().GetMode());
}

JNIEXPORT jobject JNICALL Java_app_organicmaps_sdk_Framework_nativeGetRouteFollowingInfo(JNIEnv * env, jclass)
{
  RoutingManager & rm = frm()->GetRoutingManager();
  if (!rm.IsRoutingActive())
    return nullptr;

  routing::FollowingInfo info;
  rm.GetRouteFollowingInfo(info);
  if (!info.IsValid())
    return nullptr;

  return CreateRoutingInfo(env, info, rm);
}

JNIEXPORT jobjectArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetRouteJunctionPoints(JNIEnv * env, jclass,
                                                                                               jdouble maxDistM)
{
  vector<geometry::PointWithAltitude> points;
  if (!frm()->GetRoutingManager().RoutingSession().GetRouteJunctionPoints(points))
  {
    LOG(LWARNING, ("Can't get the route junction points"));
    return nullptr;
  }

  vector<geometry::PointWithAltitude> result;
  result.reserve(points.size());
  result.push_back(points[0]);
  for (size_t i = 1; i < points.size(); ++i)
  {
    double const dist = ms::DistanceOnEarth(points[i - 1].ToLatLon(), points[i].ToLatLon());
    if (dist > maxDistM)
    {
      int const steps = static_cast<int>(dist / maxDistM) + 1;
      for (int s = 1; s < steps; ++s)
        result.push_back(points[i - 1].Interpolate(points[i], static_cast<double>(s) / steps));
    }
    result.push_back(points[i]);
  }

  return CreateJunctionInfoArray(env, result);
}

JNIEXPORT jintArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGenerateRouteAltitudeChartBits(
    JNIEnv * env, jclass, jint width, jint height, jobject routeAltitudeLimits)
{
  RoutingManager::DistanceAltitude altitudes;
  if (!frm()->GetRoutingManager().GetRouteAltitudesAndDistancesM(altitudes))
  {
    LOG(LWARNING, ("Can't get distance to route points and altitude."));
    return nullptr;
  }

  altitudes.Simplify();

  vector<uint8_t> imageRGBAData;
  if (!altitudes.GenerateRouteAltitudeChart(width, height, imageRGBAData))
  {
    LOG(LWARNING, ("Can't generate route altitude image."));
    return nullptr;
  }

  uint32_t totalAscent, totalDescent;
  altitudes.CalculateAscentDescent(totalAscent, totalDescent);

  // Android platform code has specific result string formatting, so make conversion here.
  using namespace measurement_utils;
  auto units = Units::Metric;
  if (settings::Get(settings::kMeasurementUnits, units) && units == Units::Imperial)
  {
    totalAscent = measurement_utils::MetersToFeet(totalAscent);
    totalDescent = measurement_utils::MetersToFeet(totalDescent);
  }

  jni::TScopedLocalRef const totalAscentString(env, jni::ToJavaString(env, ToStringPrecision(totalAscent, 0)));

  jni::TScopedLocalRef const totalDescentString(env, jni::ToJavaString(env, ToStringPrecision(totalDescent, 0)));

  // Passing route limits.
  // Do not use jni::GetGlobalClassRef, because this class is used only to init static fieldId vars.
  static jclass const routeAltitudeLimitsClass = env->GetObjectClass(routeAltitudeLimits);
  ASSERT(routeAltitudeLimitsClass, ());

  static jfieldID const totalAscentField = env->GetFieldID(routeAltitudeLimitsClass, "totalAscent", "I");
  ASSERT(totalAscentField, ());
  env->SetIntField(routeAltitudeLimits, totalAscentField, static_cast<jint>(totalAscent));

  static jfieldID const totalDescentField = env->GetFieldID(routeAltitudeLimitsClass, "totalDescent", "I");
  ASSERT(totalDescentField, ());
  env->SetIntField(routeAltitudeLimits, totalDescentField, static_cast<jint>(totalDescent));

  static jfieldID const totalAscentStringField =
      env->GetFieldID(routeAltitudeLimitsClass, "totalAscentString", "Ljava/lang/String;");
  ASSERT(totalAscentStringField, ());
  env->SetObjectField(routeAltitudeLimits, totalAscentStringField, totalAscentString.get());

  static jfieldID const totalDescentStringField =
      env->GetFieldID(routeAltitudeLimitsClass, "totalDescentString", "Ljava/lang/String;");
  ASSERT(totalDescentStringField, ());
  env->SetObjectField(routeAltitudeLimits, totalDescentStringField, totalDescentString.get());

  static jfieldID const isMetricUnitsField = env->GetFieldID(routeAltitudeLimitsClass, "isMetricUnits", "Z");
  ASSERT(isMetricUnitsField, ());
  env->SetBooleanField(routeAltitudeLimits, isMetricUnitsField, units == Units::Metric);

  size_t const imageRGBADataSize = imageRGBAData.size();
  ASSERT_NOT_EQUAL(imageRGBADataSize, 0,
                   ("GenerateRouteAltitudeChart returns true but the vector with altitude image bits is empty."));

  size_t const pxlCount = width * height;
  if (maps::kAltitudeChartBPP * pxlCount != imageRGBADataSize)
  {
    LOG(LWARNING,
        ("Wrong size of vector with altitude image bits. Expected size:", pxlCount, ". Real size:", imageRGBADataSize));
    return nullptr;
  }

  jintArray imageRGBADataArray = env->NewIntArray(static_cast<jsize>(pxlCount));
  ASSERT(imageRGBADataArray, ());
  jint * arrayElements = env->GetIntArrayElements(imageRGBADataArray, 0);
  ASSERT(arrayElements, ());

  for (size_t i = 0; i < pxlCount; ++i)
  {
    size_t const shiftInBytes = i * maps::kAltitudeChartBPP;
    // Type of |imageRGBAData| elements is uint8_t. But uint8_t is promoted to unsinged int in code below before
    // shifting. So there's no data lost in code below.
    arrayElements[i] = (imageRGBAData[shiftInBytes + 3] << 24) /* alpha */
                     | (imageRGBAData[shiftInBytes] << 16)     /* red */
                     | (imageRGBAData[shiftInBytes + 1] << 8)  /* green */
                     | (imageRGBAData[shiftInBytes + 2]);      /* blue */
  }
  env->ReleaseIntArrayElements(imageRGBADataArray, arrayElements, 0);

  return imageRGBADataArray;
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeShowCountry(JNIEnv * env, jclass, jstring countryId,
                                                                            jboolean zoomToDownloadButton)
{
  g_framework->ShowNode(jni::ToNativeString(env, countryId), (bool)zoomToDownloadButton);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetRoutingListener(JNIEnv * env, jclass,
                                                                                   jobject listener)
{
  CHECK(g_framework, ("Framework isn't created yet!"));
  frm()->GetRoutingManager().SetRouteBuildingListener(
      [rf = jni::make_global_ref(listener)](routing::RouterResultCode e, storage::CountriesSet const & countries)
  { CallRoutingListener(rf, static_cast<int>(e), countries); });
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetRouteProgressListener(JNIEnv * env, jclass,
                                                                                         jobject listener)
{
  CHECK(g_framework, ("Framework isn't created yet!"));
  frm()->GetRoutingManager().SetRouteProgressListener(
      bind(&CallRouteProgressListener, jni::make_global_ref(listener), _1));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetRoutingRecommendationListener(JNIEnv * env, jclass,
                                                                                                 jobject listener)
{
  CHECK(g_framework, ("Framework isn't created yet!"));
  frm()->GetRoutingManager().SetRouteRecommendationListener(
      bind(&CallRouteRecommendationListener, jni::make_global_ref(listener), _1));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetRoutingLoadPointsListener(JNIEnv *, jclass,
                                                                                             jobject listener)
{
  CHECK(g_framework, ("Framework isn't created yet!"));
  if (listener != nullptr)
    g_loadRouteHandler = bind(&CallSetRoutingLoadPointsListener, jni::make_global_ref(listener), _1);
  else
    g_loadRouteHandler = nullptr;
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeDeactivatePopup(JNIEnv * env, jclass)
{
  return g_framework->DeactivatePopup();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeDeactivateMapSelectionCircle(JNIEnv * env, jclass,
                                                                                             jboolean restoreViewport)
{
  return g_framework->DeactivateMapSelectionCircle(restoreViewport);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeAddRoutePoint(
    JNIEnv * env, jclass, jstring title, jstring subtitle, jobject markType, jint intermediateIndex,
    jboolean isMyPosition, jdouble lat, jdouble lon, jboolean reorderIntermediatePoints)
{
  RouteMarkData data;
  data.m_title = jni::ToNativeString(env, title);
  data.m_subTitle = jni::ToNativeString(env, subtitle);
  data.m_pointType = GetRouteMarkType(env, markType);
  data.m_intermediateIndex = static_cast<size_t>(intermediateIndex);
  data.m_isMyPosition = static_cast<bool>(isMyPosition);
  data.m_position = m2::PointD(mercator::FromLatLon(lat, lon));

  frm()->GetRoutingManager().AddRoutePoint(std::move(data), reorderIntermediatePoints);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeRemoveRoutePoints(JNIEnv * env, jclass)
{
  frm()->GetRoutingManager().RemoveRoutePoints();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeRemoveRoutePoint(JNIEnv * env, jclass, jobject markType,
                                                                                 jint intermediateIndex)
{
  frm()->GetRoutingManager().RemoveRoutePoint(GetRouteMarkType(env, markType), static_cast<size_t>(intermediateIndex));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeRemoveIntermediateRoutePoints(JNIEnv * env, jclass)
{
  frm()->GetRoutingManager().RemoveIntermediateRoutePoints();
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeCouldAddIntermediatePoint(JNIEnv * env, jclass)
{
  return frm()->GetRoutingManager().CouldAddIntermediatePoint();
}

JNIEXPORT jobjectArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetRoutePoints(JNIEnv * env, jclass)
{
  return CreateRouteMarkDataArray(env, frm()->GetRoutingManager().GetRoutePoints());
}

JNIEXPORT jdoubleArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetIntermediateStopsProgress(JNIEnv * env, jclass)
{
  return CreateIntermediateStopsProgressArray(env, frm()->GetRoutingManager());
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeMoveRoutePoint(JNIEnv * env, jclass, jint currentIndex,
                                                                               jint targetIndex)
{
  frm()->GetRoutingManager().MoveRoutePoint(currentIndex, targetIndex);
}

JNIEXPORT jobjectArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetRouteSteps(JNIEnv * env, jclass,
                                                                                      jstring language)
{
  std::string nativeLanguage = jni::ToNativeString(env, language);
  RoutingManager & rm = frm()->GetRoutingManager();
  if (!rm.IsRoutingActive() || !rm.IsRouteValid())
    return nullptr;

  auto const steps = rm.GetRouteTurnsForDisplay(nativeLanguage);
  return CreateRouteStepInfoArray(env, steps);
}

JNIEXPORT jobject JNICALL Java_app_organicmaps_sdk_Framework_nativeGetTransitRouteInfo(JNIEnv * env, jclass)
{
  return CreateTransitRouteInfo(env, frm()->GetRoutingManager().GetTransitRouteInfo());
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeReloadWorldMaps(JNIEnv * env, jclass)
{
  g_framework->ReloadWorldMaps();
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsDayTime(JNIEnv * env, jclass,
                                                                              jlong utcTimeSeconds, jdouble lat,
                                                                              jdouble lon)
{
  DayTimeType const dt = GetDayTime(static_cast<time_t>(utcTimeSeconds), lat, lon);
  return (dt == DayTimeType::Day || dt == DayTimeType::PolarDay);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSet3dMode(JNIEnv * env, jclass, jboolean allow,
                                                                          jboolean allowBuildings)
{
  bool const allow3d = static_cast<bool>(allow);
  bool const allow3dBuildings = static_cast<bool>(allowBuildings);

  g_framework->Save3dMode(allow3d, allow3dBuildings);
  g_framework->Set3dMode(allow3d, allow3dBuildings);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetCarScreenMode(JNIEnv * env, jclass, jboolean enabled)
{
  if (g_framework)
    frm()->SetCarScreenMode(static_cast<bool>(enabled));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeGet3dMode(JNIEnv * env, jclass, jobject result)
{
  bool enabled;
  bool buildings;
  g_framework->Get3dMode(enabled, buildings);

  static jclass const resultClass = env->GetObjectClass(result);

  static jfieldID const enabledField = env->GetFieldID(resultClass, "enabled", "Z");
  env->SetBooleanField(result, enabledField, enabled);

  static jfieldID const buildingsField = env->GetFieldID(resultClass, "buildings", "Z");
  env->SetBooleanField(result, buildingsField, buildings);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetCustomMapDownloadUrl(JNIEnv * env, jclass,
                                                                                        jstring url)
{
  std::string nativeUrl = jni::ToNativeString(env, url);
  GetPlatform().SetCustomMapServerUrl(nativeUrl);

  if (g_framework)
    frm()->GetStorage().ResetMapDownloadMetaConfig();
  else
    LOG(LINFO, ("nativeSetCustomMapDownloadUrl: framework not created yet, skipping ResetMapDownloadMetaConfig"));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetAutoZoomEnabled(JNIEnv * env, jclass,
                                                                                   jboolean enabled)
{
  bool const autoZoomEnabled = static_cast<bool>(enabled);
  frm()->SaveAutoZoom(autoZoomEnabled);
  frm()->AllowAutoZoom(autoZoomEnabled);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetTransitSchemeEnabled(JNIEnv * env, jclass,
                                                                                        jboolean enabled)
{
  if (enabled)
  {
    frm()->SwitchToMapMode(MapMode::PublicTransport);
    frm()->PublicTransportMapModeSetTransitLines(true);
  }
  else
  {
    frm()->PublicTransportMapModeSetTransitLines(false);
    frm()->SwitchToMapMode(MapMode::Default);
  }
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsTransitSchemeEnabled(JNIEnv * env, jclass)
{
  return static_cast<jboolean>(frm()->PublicTransportMapModeHasTransitLines());
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetIsolinesLayerEnabled(JNIEnv * env, jclass,
                                                                                        jboolean enabled)
{
  frm()->SetContourLinesLayer(static_cast<bool>(enabled));
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsIsolinesLayerEnabled(JNIEnv * env, jclass)
{
  return static_cast<jboolean>(frm()->HasContourLinesLayer());
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetOutdoorsLayerEnabled(JNIEnv * env, jclass,
                                                                                        jboolean enabled)
{
  frm()->SetOutdoorLayer(static_cast<bool>(enabled));
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsOutdoorsLayerEnabled(JNIEnv * env, jclass)
{
  return static_cast<jboolean>(frm()->HasOutdoorLayer());
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSwitchToUsingVehicleStyle(JNIEnv * env, jclass,
                                                                                          jboolean enabled)
{
  frm()->SwitchToUsingVehicleStyle(static_cast<bool>(enabled));
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsUsingVehicleStyle(JNIEnv * env, jclass)
{
  return static_cast<jboolean>(frm()->IsUsingVehicleStyle());
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeGetAutoZoomEnabled(JNIEnv *, jclass)
{
  return frm()->LoadAutoZoom();
}

// static void nativeZoomToPoint(double lat, double lon, int zoom, boolean animate);
JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeZoomToPoint(JNIEnv * env, jclass, jdouble lat,
                                                                            jdouble lon, jint zoom, jboolean animate)
{
  g_framework->Scale(m2::PointD(mercator::FromLatLon(lat, lon)), zoom, animate);
}

JNIEXPORT jobject JNICALL Java_app_organicmaps_sdk_Framework_nativeDeleteBookmarkFromMapObject(JNIEnv * env, jclass)
{
  if (!frm()->HasPlacePageInfo())
    return nullptr;

  place_page::Info const & info = g_framework->GetPlacePageInfo();
  auto const bookmarkId = info.GetBookmarkId();
  frm()->GetBookmarkManager().GetEditSession().DeleteBookmark(bookmarkId);

  auto buildInfo = info.GetBuildInfo();
  buildInfo.m_match = place_page::BuildInfo::Match::FeatureOnly;
  buildInfo.m_userMarkId = kml::kInvalidMarkId;
  buildInfo.m_source = place_page::BuildInfo::Source::Other;
  frm()->UpdatePlacePageInfoForCurrentSelection(buildInfo);

  return usermark_helper::CreateMapObject(env, g_framework->GetPlacePageInfo());
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetPoiContactUrl(JNIEnv * env, jclass, jint id)
{
  auto const metaID = static_cast<osm::MapObject::MetadataID>(id);
  string_view const value = g_framework->GetPlacePageInfo().GetMetadata(metaID);
  if (osm::isSocialContactTag(metaID))
    return jni::ToJavaString(env, osm::socialContactToURL(metaID, value));
  return jni::ToJavaString(env, value);
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsDownloadedMapAtScreenCenter(JNIEnv *, jclass)
{
  ::Framework * fr = frm();
  return storage::IsPointCoveredByDownloadedMaps(fr->GetViewportCenter(), fr->GetStorage(), fr->GetCountryInfoGetter());
}

JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeGetActiveObjectFormattedCuisine(JNIEnv * env, jclass)
{
  ::Framework * frm = g_framework->NativeFramework();
  if (!frm->HasPlacePageInfo())
    return {};

  return jni::ToJavaString(env, g_framework->GetPlacePageInfo().FormatCuisines());
}

JNIEXPORT jobjectArray JNICALL Java_app_organicmaps_sdk_Framework_nativeGetActiveObjectChargeSockets(JNIEnv * env,
                                                                                                     jclass)
{
  auto sockets = g_framework->GetPlacePageInfo().GetChargeSockets();

  jclass descClass = env->FindClass("app/organicmaps/sdk/bookmarks/data/ChargeSocketDescriptor");
  jmethodID ctor = env->GetMethodID(descClass, "<init>", "(Ljava/lang/String;ID)V");

  // Create a Java array
  jobjectArray result = env->NewObjectArray(sockets.size(), descClass, nullptr);

  for (size_t i = 0; i < sockets.size(); ++i)
  {
    auto const & s = sockets[i];

    jstring jType = env->NewStringUTF(s.type.c_str());
    jobject jDesc = env->NewObject(descClass, ctor, jType, (jint)s.count, (jdouble)s.power);

    env->SetObjectArrayElement(result, i, jDesc);

    env->DeleteLocalRef(jType);
    env->DeleteLocalRef(jDesc);
  }

  return result;
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetVisibleRect(JNIEnv * env, jclass, jint left,
                                                                               jint top, jint right, jint bottom)
{
  frm()->SetVisibleViewport(m2::RectD(left, top, right, bottom));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeShowRect(JNIEnv *, jclass, jdouble minLat,
                                                                         jdouble minLon, jdouble maxLat,
                                                                         jdouble maxLon, jboolean animate)
{
  m2::RectD const rect(mercator::FromLatLon(minLat, minLon), mercator::FromLatLon(maxLat, maxLon));
  frm()->ShowRect(rect, -1 /* maxScale */, animate, true /* useVisibleViewport */);
}

// TrackZero: draws a lat/lon polyline as a route line (not a bookmark, nothing is persisted), and a ring at its start
// (ring 1), its end (ring 2: a destination) or none (0: a leg joining another line). Returns the drape subroute ids for
// nativeRemoveTrackLine, or null when the engine isn't running.
// Styles (FEAT-005, plan v2 Phase 20; the losing ones go once the owner has compared them outdoors):
//   0 (B, default): an 8dp line on a 12dp black casing; 1 (A, as built before): 5.5dp line on a 16dp glow;
//   2 (C): as B with a brighter line; 3 (D): a 10dp line on a 14dp casing.
// Dimmed (off route, R3) is the same in every style: no casing, the line dashed at 5.5dp in 45 % mint.
// chevronSpacingM > 0: direction chevrons inside the line every that many metres (never on a dimmed line).
// look: 0 the line, 1 dimmed (off route), 2 on foot (Phase 27: 6dp mint dots 13dp apart, no casing, no chevrons).
// ring: 0 none, 1 at the start, 2 at the end, 3 a hollow ring at the start (where the bike is left, Phase 27), 4 both
// the hollow ring at the start and the ring at the end (the walked part of a path to a place).
JNIEXPORT jintArray JNICALL Java_app_organicmaps_sdk_Framework_nativeShowTrackLine(JNIEnv * env, jclass,
                                                                                   jdoubleArray lats, jdoubleArray lons,
                                                                                   jint look, jint ring, jint style,
                                                                                   jdouble chevronSpacingM)
{
  bool const dimmed = look == 1;
  bool const walk = look == 2;
  auto engine = frm()->GetDrapeEngine();
  jsize const n = env->GetArrayLength(lats);
  if (engine == nullptr || n < 2)
    return nullptr;

  m2::PolylineD polyline;
  jdouble * lat = env->GetDoubleArrayElements(lats, nullptr);
  jdouble * lon = env->GetDoubleArrayElements(lons, nullptr);
  for (jsize i = 0; i < n; ++i)
    polyline.Add(mercator::FromLatLon(lat[i], lon[i]));
  env->ReleaseDoubleArrayElements(lats, lat, JNI_ABORT);
  env->ReleaseDoubleArrayElements(lons, lon, JNI_ABORT);

  // RouteType::Track is 8dp wide on each side at every zoom; each subroute is capped to its own width.
  float const vs = static_cast<float>(df::VisualParams::Instance().GetVisualScale());
  bool const glowStyle = style == 1;
  float const lineDp = walk ? 6.0f : dimmed || glowStyle ? 5.5f : style == 3 ? 10.0f : 8.0f;
  float const underDp = glowStyle ? 16.0f : style == 3 ? 14.0f : 12.0f;
  auto const makeSubroute = [&polyline](df::ColorConstant const & color, double depthIndex,
                                        df::RoutePattern const & pattern, float widthPx)
  {
    auto subroute = std::make_unique<df::Subroute>();
    subroute->m_routeType = df::RouteType::Track;
    subroute->m_polyline = polyline;
    subroute->m_baseDepthIndex = depthIndex;
    // Same colour inside and out: the shader's outline band would otherwise show as a stripe.
    subroute->AddStyle(df::SubrouteStyle(color, color, pattern));
    // The whole line is real route: no "fake" (grey) head or tail. Distances are in mercator units.
    subroute->m_headFakeDistance = -1.0;
    subroute->m_tailFakeDistance = polyline.GetLength() + 1.0;
    subroute->m_maxPixelWidth = widthPx;
    return subroute;
  };

  // Under the line: a hard black casing (it keeps an edge where the line crosses lighter map features in sun), or the
  // old glow. None when dimmed.
  auto under = dimmed || walk
                   ? nullptr
                   : makeSubroute(glowStyle ? "TrackGlow" : "TrackCasing", 0.0, df::RoutePattern(), underDp * vs);
  // Dash and gap are in half-widths: 6dp dots (2 × 3dp) every 13dp (gap 7dp).
  auto line = makeSubroute(dimmed ? "TrackLineOff" : style == 2 && !walk ? "TrackLineBright" : "TrackLine", 1.0,
                           dimmed ? df::RoutePattern(2.0, 3.0)
                           : walk ? df::RoutePattern(2.0, 7.0 / 3.0)
                                  : df::RoutePattern(),
                           lineDp * vs);

  // Direction chevrons (BUG-002): every chevronSpacingM metres, none within 25 m of either end (the ring).
  if (!dimmed && !walk && chevronSpacingM > 0)
  {
    double constexpr kEndClearM = 25.0;
    auto const & pts = polyline.GetPoints();
    double totalM = 0.0;
    for (size_t i = 1; i < pts.size(); ++i)
      totalM += mercator::DistanceOnEarth(pts[i - 1], pts[i]);
    double nextM = std::max(kEndClearM, chevronSpacingM / 2);
    double doneM = 0.0;
    double doneMerc = 0.0;
    for (size_t i = 1; i < pts.size() && nextM <= totalM - kEndClearM; ++i)
    {
      double const segM = mercator::DistanceOnEarth(pts[i - 1], pts[i]);
      double const segMerc = pts[i - 1].Length(pts[i]);
      while (segM > 0 && nextM <= doneM + segM && nextM <= totalM - kEndClearM)
      {
        line->m_chevrons.push_back(doneMerc + segMerc * (nextM - doneM) / segM);
        nextM += chevronSpacingM;
      }
      doneM += segM;
      doneMerc += segMerc;
    }
  }

  // Ring: 13dp black disc with a 3dp mint ring and a 4.5dp mint dot, whatever the line's width; the hollow ring (3) has
  // no dot. Markers stack in list order and are scaled by the line's half width.
  auto const addCircle = [&line, lineDp](m2::PointD const & at, df::ColorConstant const & color, float radiusDp)
  {
    df::SubrouteMarker m;
    m.m_position = at;
    m.m_colors = {color};
    m.m_innerColor = color;
    m.m_scale = radiusDp / (lineDp / 2);
    line->m_markers.push_back(std::move(m));
  };
  auto const addRing = [&addCircle](m2::PointD const & at, bool dot)
  {
    addCircle(at, "TrackLine", 14.5f);
    addCircle(at, "TrackMarkerCenter", 11.5f);
    if (dot)
      addCircle(at, "TrackLine", 4.5f);
  };
  if (ring == 1)
    addRing(polyline.Front(), true);
  else if (ring == 2)
    addRing(polyline.Back(), true);
  else if (ring == 3 || ring == 4)
    addRing(polyline.Front(), false);
  if (ring == 4)
    addRing(polyline.Back(), true);

  std::vector<jint> ids;
  if (under)
    ids.push_back(static_cast<jint>(engine->AddSubroute(df::SubrouteConstPtr(under.release()))));
  ids.push_back(static_cast<jint>(engine->AddSubroute(df::SubrouteConstPtr(line.release()))));
  jintArray result = env->NewIntArray(static_cast<jsize>(ids.size()));
  env->SetIntArrayRegion(result, 0, static_cast<jsize>(ids.size()), ids.data());
  return result;
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeRemoveTrackLine(JNIEnv * env, jclass, jintArray ids)
{
  auto engine = frm()->GetDrapeEngine();
  if (engine == nullptr)
    return;
  jsize const n = env->GetArrayLength(ids);
  jint * id = env->GetIntArrayElements(ids, nullptr);
  for (jsize i = 0; i < n; ++i)
    engine->RemoveSubroute(static_cast<dp::DrapeID>(id[i]), false /* deactivateFollowing */);
  env->ReleaseIntArrayElements(ids, id, JNI_ABORT);
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeIsRouteFinished(JNIEnv * env, jclass)
{
  return frm()->GetRoutingManager().IsRouteFinished();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeRunFirstLaunchAnimation(JNIEnv * env, jclass)
{
  frm()->RunFirstLaunchAnimation();
}

JNIEXPORT jint JNICALL Java_app_organicmaps_sdk_Framework_nativeOpenRoutePointsTransaction(JNIEnv * env, jclass)
{
  return frm()->GetRoutingManager().OpenRoutePointsTransaction();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeApplyRoutePointsTransaction(JNIEnv * env, jclass,
                                                                                            jint transactionId)
{
  frm()->GetRoutingManager().ApplyRoutePointsTransaction(transactionId);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeCancelRoutePointsTransaction(JNIEnv * env, jclass,
                                                                                             jint transactionId)
{
  frm()->GetRoutingManager().CancelRoutePointsTransaction(transactionId);
}

JNIEXPORT jint JNICALL Java_app_organicmaps_sdk_Framework_nativeInvalidRoutePointsTransactionId(JNIEnv * env, jclass)
{
  return frm()->GetRoutingManager().InvalidRoutePointsTransactionId();
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeHasSavedRoutePoints(JNIEnv *, jclass)
{
  return frm()->GetRoutingManager().HasSavedRoutePoints();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeLoadRoutePoints(JNIEnv *, jclass)
{
  frm()->GetRoutingManager().LoadRoutePoints(g_loadRouteHandler);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSaveRoutePoints(JNIEnv *, jclass)
{
  frm()->GetRoutingManager().SaveRoutePoints();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeDeleteSavedRoutePoints(JNIEnv *, jclass)
{
  frm()->GetRoutingManager().DeleteSavedRoutePoints();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeShowFeature(JNIEnv * env, jclass, jobject featureId)
{
  auto const f = g_framework->BuildFeatureId(env, featureId);

  if (f.IsValid())
    frm()->ShowFeature(f);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeMakeCrash(JNIEnv * env, jclass type)
{
  CHECK(false, ("Diagnostic native crash!"));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetPowerManagerFacility(JNIEnv *, jclass,
                                                                                        jint facilityType,
                                                                                        jboolean state)
{
  frm()->GetPowerManager().SetFacility(static_cast<power_management::Facility>(facilityType), static_cast<bool>(state));
}

JNIEXPORT jint JNICALL Java_app_organicmaps_sdk_Framework_nativeGetPowerManagerScheme(JNIEnv *, jclass)
{
  return static_cast<jint>(frm()->GetPowerManager().GetScheme());
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetPowerManagerScheme(JNIEnv *, jclass, jint schemeType)
{
  frm()->GetPowerManager().SetScheme(static_cast<power_management::Scheme>(schemeType));
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetViewportCenter(JNIEnv *, jclass, jdouble lat,
                                                                                  jdouble lon, jint zoom)
{
  // isAnim = true because of previous nativeSetChoosePositionMode animations.
  frm()->SetViewportCenter(mercator::FromLatLon(lat, lon), static_cast<int>(zoom), true /* isAnim */);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeStopLocationFollow(JNIEnv *, jclass)
{
  frm()->StopLocationFollow();
}

// TrackZero: follow the rider in one step (not a mode toggle, so it can be repeated), zoom -1 keeps the zoom.
JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeFollowMyPosition(JNIEnv *, jclass, jboolean rotate,
                                                                                 jint zoom, jboolean animateZoom)
{
  frm()->FollowMyPosition(rotate, static_cast<int>(zoom), animateZoom);
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetSearchViewport(JNIEnv *, jclass, jdouble lat,
                                                                                  jdouble lon, jint zoom)
{
  auto const center = mercator::FromLatLon(static_cast<double>(lat), static_cast<double>(lon));
  auto const rect = df::GetRectForDrawScale(static_cast<int>(zoom), center);
  frm()->GetSearchAPI().OnViewportChanged(rect);
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeHasPlacePageInfo(JNIEnv *, jclass)
{
  return static_cast<jboolean>(frm()->HasPlacePageInfo());
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeMemoryWarning(JNIEnv *, jclass)
{
  return frm()->MemoryWarning();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeSetShowBookmarkLabels(JNIEnv *, jclass,
                                                                                       jboolean show)
{
  frm()->SetShowBookmarkLabels(show);
}

JNIEXPORT jboolean JNICALL Java_app_organicmaps_sdk_Framework_nativeGetShowBookmarkLabels(JNIEnv *, jclass)
{
  return Framework::GetShowBookmarkLabels();
}
}  // extern "C"
