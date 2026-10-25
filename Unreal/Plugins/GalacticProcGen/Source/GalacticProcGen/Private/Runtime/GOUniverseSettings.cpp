#include "Runtime/GOUniverseSettings.h"

FGOStartScenarioParams UGOUniverseSettings::MakeScenarioParams(uint64 Seed) const
{
	FGOStartScenarioParams Params;
	Params.Universe.Seed = Seed;
	Params.Universe.GalaxyType = GalaxyType;
	Params.Universe.GalaxySize = GalaxySize;
	Params.Universe.StartingRegion = StartingRegion;
	Params.Hub = HubParams;
	Params.TerrainMaxDepth = TerrainMaxDepth;
	Params.TerrainHeightScale = TerrainHeightScale;
	Params.HubAltitudeKm = HubAltitudeKm;
	return Params;
}
