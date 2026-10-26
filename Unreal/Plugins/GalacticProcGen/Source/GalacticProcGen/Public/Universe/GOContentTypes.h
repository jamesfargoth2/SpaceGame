#pragma once

#include "CoreMinimal.h"
#include "Engine/DataTable.h"
#include "Universe/GOUniverseTypes.h"
#include "GOContentTypes.generated.h"

/**
 * Data-table rows for universe content. The plugin ships defaults as JSON in
 * Plugins/GalacticProcGen/Data (importable as DataTables with these row structs);
 * a DataTable assigned in Project Settings > Galactic Universe takes precedence.
 */

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOSpectralClassDef : public FTableRowBase
{
	GENERATED_BODY()

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Star")
	EGOSpectralClass Class = EGOSpectralClass::G;

	/** Relative frequency among all stars (rows should sum to ~1). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Star")
	float Frequency = 0.1f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Star")
	float TempMinK = 5200.f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Star")
	float TempMaxK = 6000.f;

	/** Solar masses. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Star")
	float MassMin = 0.8f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Star")
	float MassMax = 1.04f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Star")
	int32 PlanetCountMin = 3;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Star")
	int32 PlanetCountMax = 6;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOPlanetTypeDef : public FTableRowBase
{
	GENERATED_BODY()

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	EGOPlanetType Type = EGOPlanetType::Terran;

	/** Earth radii. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	float RadiusMin = 0.8f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	float RadiusMax = 1.8f;

	/** Bulk density, g/cm^3 (Earth is 5.51). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	float DensityMin = 4.5f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	float DensityMax = 6.f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	int32 MoonMin = 0;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	int32 MoonMax = 3;

	/** Orbital zones this type can form in. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	TArray<EGOOrbitalZone> Zones;

	/** Relative chance among the types valid for a zone. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Planet")
	float Weight = 1.f;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOBiomeDef : public FTableRowBase
{
	GENERATED_BODY()

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Biome")
	EGOBiome Biome = EGOBiome::Grassland;

	/** Terrain height multiplier for this biome. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Biome")
	float Amplitude = 0.2f;

	/** 0 = rolling continental noise, 1 = sharp ridged mountains. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Biome")
	float RidgeMix = 0.1f;

	/** Vertex colour written to terrain chunks (read by the planet surface material). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Biome")
	FLinearColor GroundColor = FLinearColor(0.4f, 0.7f, 0.3f, 1.f);
};

/** A named list of name fragments. The row name is the list key, e.g. "starOnsets". */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGONameListDef : public FTableRowBase
{
	GENERATED_BODY()

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Names")
	TArray<FString> Values;
};
