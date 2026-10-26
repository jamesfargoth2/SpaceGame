#pragma once

#include "CoreMinimal.h"
#include "Engine/DeveloperSettings.h"
#include "Hub/GOHubTypes.h"
#include "Universe/GOStartScenario.h"
#include "Universe/GOUniverseTypes.h"
#include "GOUniverseSettings.generated.h"

class UDataTable;
class UMaterialInterface;

/**
 * Project Settings > Game > Galactic Universe. Defaults for a new game, terrain streaming and
 * where content comes from. Every content table is optional: when a table is not assigned (or
 * the asset does not exist yet) the matching JSON file in Plugins/GalacticProcGen/Data is used.
 */
UCLASS(Config = Game, DefaultConfig, meta = (DisplayName = "Galactic Universe"))
class GALACTICPROCGEN_API UGOUniverseSettings : public UDeveloperSettings
{
	GENERATED_BODY()

public:
	virtual FName GetCategoryName() const override { return TEXT("Game"); }

	/** Free-form seed text; the same text always produces the same universe. */
	UPROPERTY(Config, EditAnywhere, Category = "New Game")
	FString SeedText = TEXT("Galactic Odyssey");

	/** Ignore SeedText and roll a new universe every session (a ?Seed= URL option still wins). */
	UPROPERTY(Config, EditAnywhere, Category = "New Game")
	bool bRandomSeedEachSession = false;

	UPROPERTY(Config, EditAnywhere, Category = "New Game")
	EGOGalaxyType GalaxyType = EGOGalaxyType::Spiral;

	UPROPERTY(Config, EditAnywhere, Category = "New Game")
	EGOGalaxySize GalaxySize = EGOGalaxySize::Medium;

	UPROPERTY(Config, EditAnywhere, Category = "New Game")
	EGOStartingRegion StartingRegion = EGOStartingRegion::InnerRim;

	UPROPERTY(Config, EditAnywhere, Category = "New Game")
	EGOStartLocation StartLocation = EGOStartLocation::AcademyHub;

	UPROPERTY(Config, EditAnywhere, Category = "Academy")
	FGOHubGenParams HubParams;

	UPROPERTY(Config, EditAnywhere, Category = "Academy", meta = (ClampMin = "100", Units = "km"))
	double HubAltitudeKm = 420.0;

	/** Spawn placeholder NPCs (kit asset human_npc_generic, or its ActorClass) at cadet/instructor/staff spawns. */
	UPROPERTY(Config, EditAnywhere, Category = "Academy")
	bool bSpawnNpcStandIns = true;

	UPROPERTY(Config, EditAnywhere, Category = "Academy", meta = (ClampMin = "0"))
	int32 MaxHubLights = 48;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Terrain", meta = (ClampMin = "4", ClampMax = "18"))
	int32 TerrainMaxDepth = 14;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Terrain", meta = (ClampMin = "5", ClampMax = "129"))
	int32 TerrainGridSize = 33;

	/** Terrain relief: height units are multiplied by planet radius times this. */
	UPROPERTY(Config, EditAnywhere, Category = "Planet|Terrain")
	float TerrainHeightScale = 0.002f;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Terrain")
	float SplitThreshold = 1.5f;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Terrain")
	float MergeThreshold = 0.75f;

	/** Chunks this deep or deeper get collision (they only exist close to the camera). */
	UPROPERTY(Config, EditAnywhere, Category = "Planet|Terrain")
	int32 CollisionMinDepth = 12;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Terrain", meta = (ClampMin = "1"))
	int32 MaxChunkBuildsInFlight = 8;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Terrain", meta = (ClampMin = "1"))
	int32 MaxChunkUploadsPerFrame = 4;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Scatter")
	int32 ScatterMinDepth = 12;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Scatter", meta = (ClampMin = "0", ClampMax = "1"))
	float ScatterDensityFalloff = 0.35f;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Scatter", meta = (ClampMin = "0"))
	int32 MaxScatterPerChunk = 3000;

	UPROPERTY(Config, EditAnywhere, Category = "Planet|Sky")
	bool bSpawnAtmosphere = true;

	/**
	 * Sunlight at 1 AU from a 1 L-sun star, lux; scaled by the home star's luminosity and distance.
	 * The default matches UE's directional light so the emissive fallback terrain material and text
	 * stay readable; use ~100000 for physically based values once lit materials are in place.
	 */
	UPROPERTY(Config, EditAnywhere, Category = "Planet|Sky")
	float SunIlluminanceLux = 10.f;

	/** Game seconds per real second for the day/night cycle (0 freezes the sun). */
	UPROPERTY(Config, EditAnywhere, Category = "Planet|Sky", meta = (ClampMin = "0"))
	float DayNightTimeScale = 60.f;

	UPROPERTY(Config, EditAnywhere, Category = "Content", meta = (RequiredAssetDataTags = "RowStructure=/Script/GalacticProcGen.GOAssetKitRow"))
	TSoftObjectPtr<UDataTable> AssetKitTable;

	UPROPERTY(Config, EditAnywhere, Category = "Content", meta = (RequiredAssetDataTags = "RowStructure=/Script/GalacticProcGen.GOHubRoomTemplate"))
	TSoftObjectPtr<UDataTable> HubRoomTable;

	UPROPERTY(Config, EditAnywhere, Category = "Content", meta = (RequiredAssetDataTags = "RowStructure=/Script/GalacticProcGen.GOPlanetTypeDef"))
	TSoftObjectPtr<UDataTable> PlanetTypeTable;

	UPROPERTY(Config, EditAnywhere, Category = "Content", meta = (RequiredAssetDataTags = "RowStructure=/Script/GalacticProcGen.GOSpectralClassDef"))
	TSoftObjectPtr<UDataTable> SpectralClassTable;

	UPROPERTY(Config, EditAnywhere, Category = "Content", meta = (RequiredAssetDataTags = "RowStructure=/Script/GalacticProcGen.GOBiomeDef"))
	TSoftObjectPtr<UDataTable> BiomeTable;

	UPROPERTY(Config, EditAnywhere, Category = "Content", meta = (RequiredAssetDataTags = "RowStructure=/Script/GalacticProcGen.GONameListDef"))
	TSoftObjectPtr<UDataTable> NameListTable;

	/** Override for the JSON fallback directory; empty means Plugins/GalacticProcGen/Data. */
	UPROPERTY(Config, EditAnywhere, Category = "Content")
	FString FallbackDataDirectory;

	/** Terrain material; should output Vertex Color as Base Color. Falls back to an engine vertex-colour material. */
	UPROPERTY(Config, EditAnywhere, Category = "Content")
	TSoftObjectPtr<UMaterialInterface> PlanetSurfaceMaterial;

	/** Material for placeholder geometry; a "Color" vector parameter receives the kit/room tint. */
	UPROPERTY(Config, EditAnywhere, Category = "Content")
	TSoftObjectPtr<UMaterialInterface> GreyboxMaterial;

	FGOStartScenarioParams MakeScenarioParams(uint64 Seed) const;
};
