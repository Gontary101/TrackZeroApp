#include "routing_common/tz_profile.hpp"

#include <atomic>

namespace routing
{
namespace
{
std::atomic<TzProfile> g_profile{TzProfile::Road};
}  // namespace

void SetTzProfile(TzProfile profile)
{
  g_profile.store(profile);
}

TzProfile GetTzProfile()
{
  return g_profile.load();
}
}  // namespace routing
