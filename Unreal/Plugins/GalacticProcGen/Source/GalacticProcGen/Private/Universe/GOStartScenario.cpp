#include "Universe/GOStartScenario.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"
#include "Hub/GOHubGenerator.h"
#include "Planet/GOPlanetTerrain.h"
#include "Planet/GOTerrainMesh.h"
#include "Universe/GONameGenerator.h"

FGOStartScenario GOStartScenario::Generate(const FGOContentDefs& Content, const FGOStartScenarioParams& Params)
{
	FGOStartScenario Scenario;
	Scenario.TerrainMaxDepth = Params.TerrainMaxDepth;
	Scenario.TerrainHeightScale = Params.TerrainHeightScale;

	Scenario.Universe = FGOUniverseGenerator(Content).GenerateStart(Params.Universe, &Scenario.Log);
	if (!Scenario.Universe.IsValid())
	{
		Scenario.Log.Add(TEXT("Generation failed: no home world found."));
		return Scenario;
	}

	const FGOPlanet& Home = Scenario.Universe.GetHomePlanet();
	const uint64 HomeSeed = GOSeed::FromBlueprint(Home.Seed);

	const FGOPlanetTerrain Terrain(Home, Content, Params.TerrainHeightScale);
	const FGOLandingSite Site = GOLandingSite::Find(Terrain, HomeSeed, Params.TerrainMaxDepth);
	Scenario.LandingDirection = Site.Direction;
	Scenario.LandingRadiusKm = Site.SurfaceRadiusKm;
	Scenario.LandingBiome = Site.Biome;
	Scenario.HubOrbitRadiusKm = Terrain.GetRadiusKm() + Params.HubAltitudeKm;
	Scenario.Log.Add(FString::Printf(TEXT("Surveying surface of %s: landing site in %s terrain..."),
		*Home.Name, *GOUniverseText::BiomeName(Site.Biome)));

	const uint64 AcademySeed = GOSeed::Domain(HomeSeed, GOSeed::AcademyDomain);
	const FString AcademyName = FGONameGenerator(Content).AcademyName(AcademySeed);

	FGOHubGenParams HubParams = Params.Hub;
	FGORandom FacingRng(AcademySeed);
	static const FIntPoint Facings[4] = { FIntPoint(1, 0), FIntPoint(0, 1), FIntPoint(-1, 0), FIntPoint(0, -1) };
	HubParams.FrontDirection = Facings[FacingRng.RandHelper(4)];

	Scenario.Academy = FGOHubGenerator(Content).Generate(AcademySeed, HubParams, AcademyName);
	Scenario.Log.Add(FString::Printf(TEXT("%s in orbit at %.0f km. Graduation day."), *AcademyName, Params.HubAltitudeKm));
	for (const FString& Warning : Scenario.Academy.Warnings)
	{
		Scenario.Log.Add(TEXT("Warning: ") + Warning);
	}
	return Scenario;
}
