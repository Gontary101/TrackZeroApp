// TrackZero: a bicycle path between two points from CoMaps' router, for the app to follow with its own engine.
// Unlike RoutingManager's session nothing is drawn, no route marks are added and the map's follow mode is left
// alone, so it can run in the middle of a ride (to the start of a track, back onto it, or to a place).

#include "app/organicmaps/sdk/Framework.hpp"
#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "routing/checkpoints.hpp"
#include "routing/index_router.hpp"
#include "routing/route.hpp"
#include "routing/router_delegate.hpp"
#include "routing/routing_callbacks.hpp"
#include "routing_common/num_mwm_id.hpp"

#include "storage/country_info_getter.hpp"
#include "storage/routing_helpers.hpp"
#include "storage/storage.hpp"

#include "traffic/traffic_cache.hpp"

#include "geometry/mercator.hpp"

#include "base/logging.hpp"

#include <algorithm>
#include <cmath>
#include <memory>
#include <mutex>
#include <vector>

namespace
{
// The delegate of the path being built, for nativeCancel.
std::mutex g_delegateMutex;
routing::RouterDelegate * g_delegate = nullptr;

uint32_t constexpr kTimeoutSec = 30;

// One router for all the paths of a ride: its road graphs stay loaded between paths (loading them is most of the
// work). Used on one thread only; freed by nativeRelease.
std::unique_ptr<routing::IndexRouter> g_router;

routing::IndexRouter & Router(Framework & f)
{
  if (g_router)
    return *g_router;
  auto const & infoGetter = f.GetCountryInfoGetter();
  auto const & storage = f.GetStorage();
  // The router locks maps while it reads them; RoutingManager is given this same data source, non-const.
  auto & dataSource = const_cast<DataSource &>(f.GetDataSource());

  auto numMwmIds = std::make_shared<routing::NumMwmIds>();
  storage.ForEachCountry([&numMwmIds](storage::Country const & country)
  { numMwmIds->RegisterFile(country.GetFile()); });

  static traffic::TrafficCache const kNoTraffic;
  g_router = std::make_unique<routing::IndexRouter>(
      routing::VehicleType::Bicycle, true /* loadAltitudes */,
      [&storage](std::string const & id) { return storage.GetParentIdFor(id); },
      [&infoGetter](m2::PointD const & p) { return infoGetter.GetRegionCountryId(p); },
      [&infoGetter](std::string const & id) { return infoGetter.GetLimitRectForLeaf(id); }, numMwmIds,
      routing::MakeNumMwmTree(*numMwmIds, infoGetter), kNoTraffic, dataSource);
  return *g_router;
}

jobject MakePath(JNIEnv * env, int code, std::vector<double> const & lat, std::vector<double> const & lon,
                 std::vector<double> const & ele, std::vector<int> const & turnPoint, std::vector<int> const & turnType,
                 std::vector<int> const & turnExit, std::vector<std::string> const & turnStreet)
{
  static jclass const pathClass = jni::GetGlobalClassRef(env, "app/organicmaps/sdk/routing/BicyclePath");
  static jmethodID const ctor =
      jni::GetConstructorID(env, pathClass, "(I[D[D[D[I[I[I[Ljava/lang/String;)V");

  auto const doubles = [env](std::vector<double> const & v)
  {
    jdoubleArray a = env->NewDoubleArray(static_cast<jsize>(v.size()));
    env->SetDoubleArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
    return a;
  };
  auto const ints = [env](std::vector<int> const & v)
  {
    std::vector<jint> j(v.begin(), v.end());
    jintArray a = env->NewIntArray(static_cast<jsize>(j.size()));
    env->SetIntArrayRegion(a, 0, static_cast<jsize>(j.size()), j.data());
    return a;
  };

  jni::TScopedLocalRef const jLat(env, doubles(lat));
  jni::TScopedLocalRef const jLon(env, doubles(lon));
  jni::TScopedLocalRef const jEle(env, ele.empty() ? nullptr : doubles(ele));
  jni::TScopedLocalRef const jPoint(env, ints(turnPoint));
  jni::TScopedLocalRef const jType(env, ints(turnType));
  jni::TScopedLocalRef const jExit(env, ints(turnExit));
  jni::TScopedLocalRef const jStreet(env, jni::ToJavaStringArray(env, turnStreet));
  return env->NewObject(pathClass, ctor, static_cast<jint>(code), jLat.get(), jLon.get(), jEle.get(), jPoint.get(),
                        jType.get(), jExit.get(), jStreet.get());
}
}  // namespace

extern "C"
{
// Blocking (seconds on a long path): call it off the main thread. Returns null when the engine isn't running.
JNIEXPORT jobject JNICALL Java_app_organicmaps_sdk_routing_BicyclePath_nativeBuild(JNIEnv * env, jclass,
                                                                                  jdouble fromLat, jdouble fromLon,
                                                                                  jdouble toLat, jdouble toLon,
                                                                                  jboolean hasBearing,
                                                                                  jdouble bearingDeg)
{
  Framework * const f = frm();
  if (f == nullptr)
    return nullptr;
  routing::IndexRouter & router = Router(*f);

  m2::PointD const from = mercator::FromLatLon(fromLat, fromLon);
  m2::PointD const to = mercator::FromLatLon(toLat, toLon);
  // The rider's heading picks the road they are on at a junction; zero when unknown. (A flag, not NaN: this code
  // is built with finite math only.)
  m2::PointD direction = m2::PointD::Zero();
  if (hasBearing)
  {
    double const rad = bearingDeg * M_PI / 180.0;
    direction = m2::PointD(std::sin(rad), std::cos(rad));
  }

  routing::RouterDelegate delegate;
  delegate.SetTimeout(kTimeoutSec);
  {
    std::lock_guard lock(g_delegateMutex);
    g_delegate = &delegate;
  }
  routing::Route route(router.GetName(), 0 /* routeId */);
  routing::RouterResultCode code;
  try
  {
    code = router.CalculateRoute(routing::Checkpoints(from, to), direction, false /* adjustToPrevRoute */, delegate,
                                 route);
  }
  catch (RootException const & e)
  {
    LOG(LERROR, ("Bicycle path failed:", e.Msg()));
    code = routing::RouterResultCode::InternalError;
  }
  {
    std::lock_guard lock(g_delegateMutex);
    g_delegate = nullptr;
  }

  std::vector<double> lat, lon, ele;
  std::vector<int> turnPoint, turnType, turnExit;
  std::vector<std::string> turnStreet;
  if (code == routing::RouterResultCode::NoError && route.IsValid())
  {
    auto const & points = route.GetPoly().GetPoints();
    lat.reserve(points.size());
    lon.reserve(points.size());
    for (auto const & p : points)
    {
      auto const ll = mercator::ToLatLon(p);
      lat.push_back(ll.m_lat);
      lon.push_back(ll.m_lon);
    }
    // Altitudes only when every point has one.
    geometry::Altitudes altitudes;
    route.GetAltitudes(altitudes);
    if (altitudes.size() == points.size() &&
        std::find(altitudes.begin(), altitudes.end(), geometry::kInvalidAltitude) == altitudes.end())
      ele.assign(altitudes.begin(), altitudes.end());

    // The turns as CoMaps announces them: roundabout entries folded into their exits, street after each turn.
    auto const & segments = route.GetRouteSegments();
    for (auto const & step : route.GetTurnsForDisplay("en"))
    {
      auto const & turn = segments[step.m_index].GetTurn();
      turnPoint.push_back(static_cast<int>(turn.m_index));
      turnType.push_back(static_cast<int>(step.m_turn));
      turnExit.push_back(static_cast<int>(step.m_exitNum));
      turnStreet.push_back(step.m_toStreetName);
    }
  }
  else
  {
    LOG(LINFO, ("No bicycle path:", code));
  }
  return MakePath(env, static_cast<int>(code), lat, lon, ele, turnPoint, turnType, turnExit, turnStreet);
}

// Frees the router and its loaded road graphs; call on the thread that builds paths.
JNIEXPORT void JNICALL Java_app_organicmaps_sdk_routing_BicyclePath_nativeRelease(JNIEnv *, jclass)
{
  g_router.reset();
}

JNIEXPORT void JNICALL Java_app_organicmaps_sdk_routing_BicyclePath_nativeCancel(JNIEnv *, jclass)
{
  std::lock_guard lock(g_delegateMutex);
  if (g_delegate != nullptr)
    g_delegate->Cancel();
}
}  // extern "C"
