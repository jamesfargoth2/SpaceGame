#pragma once

#include "CoreMinimal.h"
#include "Hub/GOHubTypes.h"
#include "Universe/GOUniverseGenerator.h"
#include "Universe/GOUniverseTypes.h"
#include "GOStartScenario.generated.h"

struct FGOContentDefs;

UENUM(BlueprintType)
enum class EGOStartLocation : uint8
{
	/** Graduation day aboard the orbital academy above the home world. */
	AcademyHub,
	/** On the home world's surface at the generated landing site. */
	PlanetSurface
};

struct GALACTICPROCGEN_API FGOStartScenarioParams
{
	FGOUniverseParams Universe;
	FGOHubGenParams Hub;
	int32 TerrainMaxDepth = 14;
	float TerrainHeightScale = 0.002f;
	double HubAltitudeKm = 420.0;
};

/**
 * Everything a new game needs: the universe start, the academy hub layout and where the hub and
 * the landing site sit on the home world. Positions are planet-local (planet centre at origin,
 * Z up through the north pole). The academy orbits directly above the landing site, so its
 * "down" lines up with the planet's gravity.
 */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOStartScenario
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Start")
	FGOUniverseStart Universe;

	UPROPERTY(BlueprintReadOnly, Category = "Start")
	FGOHubLayout Academy;

	/** Unit direction from the planet centre to the landing site. */
	UPROPERTY(BlueprintReadOnly, Category = "Start")
	FVector LandingDirection = FVector(0.0, 0.0, 1.0);

	UPROPERTY(BlueprintReadOnly, Category = "Start")
	double LandingRadiusKm = 6371.0;

	UPROPERTY(BlueprintReadOnly, Category = "Start")
	EGOBiome LandingBiome = EGOBiome::Grassland;

	UPROPERTY(BlueprintReadOnly, Category = "Start")
	double HubOrbitRadiusKm = 6791.0;

	UPROPERTY(BlueprintReadOnly, Category = "Start")
	int32 TerrainMaxDepth = 14;

	UPROPERTY(BlueprintReadOnly, Category = "Start")
	float TerrainHeightScale = 0.002f;

	/** Flavour lines for a loading screen. */
	UPROPERTY(BlueprintReadOnly, Category = "Start")
	TArray<FString> Log;

	bool IsValid() const { return Universe.IsValid(); }
};

namespace GOStartScenario
{
	/** Runs the full deterministic new-game pipeline. Thread-safe; touches no UObjects. */
	GALACTICPROCGEN_API FGOStartScenario Generate(const FGOContentDefs& Content, const FGOStartScenarioParams& Params);
}
