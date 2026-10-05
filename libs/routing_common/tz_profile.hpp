#pragma once

// TrackZero: the navigation profile the next routing models are built for (plan v2 Phases 22-26). Set before a
// router is constructed; the model factories read it while they build their models, which never change afterwards.

#include <cstdint>

namespace routing
{
enum class TzProfile : uint8_t
{
  Road,
  Gravel,
  Walk,
  Hike
};

void SetTzProfile(TzProfile profile);
TzProfile GetTzProfile();
}  // namespace routing
