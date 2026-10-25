#pragma once

#include "CoreMinimal.h"
#include "GOUniverseTypes.generated.h"

UENUM(BlueprintType)
enum class EGOGalaxyType : uint8
{
	Spiral,
	BarredSpiral,
	Elliptical,
	Irregular
};

UENUM(BlueprintType)
enum class EGOGalaxySize : uint8
{
	Small	UMETA(DisplayName = "Small (500 systems)"),
	Medium	UMETA(DisplayName = "Medium (2000 systems)"),
	Large	UMETA(DisplayName = "Large (10000 systems)")
};

UENUM(BlueprintType)
enum class EGOGalaxyRegion : uint8
{
	Core,
	InnerRim,
	OuterRim,
	Void
};

UENUM(BlueprintType)
enum class EGOStartingRegion : uint8
{
	Core		UMETA(DisplayName = "Core (dense, dangerous)"),
	InnerRim	UMETA(DisplayName = "Inner Rim (balanced)"),
	Frontier	UMETA(DisplayName = "Frontier (sparse, quiet)")
};

UENUM(BlueprintType)
enum class EGOSpectralClass : uint8
{
	O, B, A, F, G, K, M
};

UENUM(BlueprintType)
enum class EGOLuminosityClass : uint8
{
	MainSequence,
	Giant,
	Supergiant,
	WhiteDwarf
};

UENUM(BlueprintType)
enum class EGOOrbitalZone : uint8
{
	Inner,
	Habitable,
	Outer,
	Deep
};

UENUM(BlueprintType)
enum class EGOPlanetType : uint8
{
	Molten,
	Barren,
	Arid,
	Terran,
	Ocean,
	Toxic,
	GasGiant,
	IceGiant,
	IceWorld,
	Dwarf
};

UENUM(BlueprintType)
enum class EGOBiome : uint8
{
	IceSheet,
	Tundra,
	PolarDesert,
	IceField,
	BorealForest,
	TemperateForest,
	Steppe,
	RockyWaste,
	TropicalForest,
	Grassland,
	AridShrub,
	Desert,
	Swamp,
	Savanna,
	Badlands,
	Volcanic,
	Ocean,
	Lake,
	River
};

UENUM(BlueprintType)
enum class EGOGas : uint8
{
	N2, O2, CO2, H2O, SO2, HCl, Ar, CH4, H2, He, NH3
};

UENUM(BlueprintType)
enum class EGOAtmoHazard : uint8
{
	Vacuum,
	Crushing,
	Toxic,
	Corrosive,
	ExtremeHeat,
	ExtremeCold
};

/** Shape and scale of the galaxy. Built from size + type with FGOGalaxyConfig::Make. */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOGalaxyConfig
{
	GENERATED_BODY()

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galaxy")
	EGOGalaxyType Type = EGOGalaxyType::Spiral;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galaxy", meta = (ClampMin = "1"))
	int32 TargetStarCount = 2000;

	/** Disc radius in light years. */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galaxy")
	double RadiusLY = 2764.0;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galaxy|Arms", meta = (ClampMin = "1"))
	int32 ArmCount = 4;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galaxy|Arms")
	float ArmWindingAngle = 4.f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galaxy|Arms")
	float ArmWidth = 0.15f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galaxy")
	float CoreDensityFactor = 3.f;

	/** Edge length of the square streaming chunks, light years. */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galaxy|Streaming", meta = (ClampMin = "1"))
	double ChunkSizeLY = 100.0;

	static int32 StarCountForSize(EGOGalaxySize Size);

	/** Radius is chosen so a chunk at average arm density holds about one star or more. */
	static FGOGalaxyConfig Make(EGOGalaxySize Size, EGOGalaxyType Type);
};

/** A star's position in the galaxy. Everything else about it is derived from StarId. */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOStarRecord
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Galaxy")
	int64 StarId = 0;

	/** Galaxy-space position in light years (disc in XY, thickness in Z). */
	UPROPERTY(BlueprintReadOnly, Category = "Galaxy")
	FVector PositionLY = FVector::ZeroVector;

	UPROPERTY(BlueprintReadOnly, Category = "Galaxy")
	float LocalDensity = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Galaxy")
	FIntPoint Chunk = FIntPoint(0, 0);
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOCompanionStar
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	EGOSpectralClass SpectralClass = EGOSpectralClass::M;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float MassSolar = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float Luminosity = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float TemperatureK = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float SeparationAU = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	FLinearColor Color = FLinearColor::White;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOOrbitalSlot
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Orbit")
	int32 Index = 0;

	/** Semi-major axis, astronomical units. */
	UPROPERTY(BlueprintReadOnly, Category = "Orbit")
	float RadiusAU = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Orbit")
	float Eccentricity = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Orbit")
	float PeriodYears = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Orbit")
	EGOOrbitalZone Zone = EGOOrbitalZone::Inner;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOStarSystem
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	int64 StarId = 0;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	int64 Seed = 0;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	FString Name;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	FVector PositionLY = FVector::ZeroVector;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	EGOGalaxyRegion Region = EGOGalaxyRegion::InnerRim;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	EGOSpectralClass SpectralClass = EGOSpectralClass::G;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	EGOLuminosityClass LuminosityClass = EGOLuminosityClass::MainSequence;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float TemperatureK = 5778.f;

	/** Solar luminosities. */
	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float Luminosity = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float MassSolar = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float RadiusSolar = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	float AgeGyr = 4.6f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	FLinearColor Color = FLinearColor::White;

	UPROPERTY(BlueprintReadOnly, Category = "Star|Zones")
	float HabZoneInnerAU = 0.75f;

	UPROPERTY(BlueprintReadOnly, Category = "Star|Zones")
	float HabZoneOuterAU = 1.77f;

	UPROPERTY(BlueprintReadOnly, Category = "Star|Zones")
	float FrostLineAU = 4.85f;

	UPROPERTY(BlueprintReadOnly, Category = "Star|Zones")
	float SystemEdgeAU = 40.f;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	bool bHasCompanion = false;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	FGOCompanionStar Companion;

	UPROPERTY(BlueprintReadOnly, Category = "Star")
	TArray<FGOOrbitalSlot> Orbits;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOGasFraction
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	EGOGas Gas = EGOGas::N2;

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	float Fraction = 0.f;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOAtmosphere
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	bool bPresent = false;

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	TArray<FGOGasFraction> Composition;

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	float SurfacePressureAtm = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	float GreenhouseMultiplier = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	float EquilibriumTempK = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	float SurfaceTempK = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	bool bBreathable = false;

	UPROPERTY(BlueprintReadOnly, Category = "Atmosphere")
	TArray<EGOAtmoHazard> Hazards;

	float GetFraction(EGOGas Gas) const;
	bool HasHazard(EGOAtmoHazard Hazard) const { return Hazards.Contains(Hazard); }
};

/** Inputs for biome classification on a planet surface (port of the Java BiomeMap). */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOBiomeParams
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Biome")
	int64 Seed = 0;

	/** Terrain elevation (normalised noise units) below which the surface is sea. */
	UPROPERTY(BlueprintReadOnly, Category = "Biome")
	float SeaLevel = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Biome")
	float SnowLine = 0.75f;

	UPROPERTY(BlueprintReadOnly, Category = "Biome")
	float BaseMoisture = 0.5f;

	UPROPERTY(BlueprintReadOnly, Category = "Biome")
	float SurfaceTempK = 288.f;

	UPROPERTY(BlueprintReadOnly, Category = "Biome")
	TArray<EGOBiome> AllowedBiomes;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOMoon
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Moon")
	int64 Seed = 0;

	UPROPERTY(BlueprintReadOnly, Category = "Moon")
	FString Name;

	UPROPERTY(BlueprintReadOnly, Category = "Moon")
	EGOPlanetType Type = EGOPlanetType::Barren;

	UPROPERTY(BlueprintReadOnly, Category = "Moon")
	double RadiusKm = 1000.0;

	UPROPERTY(BlueprintReadOnly, Category = "Moon")
	double OrbitRadiusKm = 384400.0;

	UPROPERTY(BlueprintReadOnly, Category = "Moon")
	float Eccentricity = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Moon")
	float InclinationRad = 0.f;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOPlanet
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	int64 Seed = 0;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	FString Name;

	UPROPERTY(BlueprintReadOnly, Category = "Planet|Orbit")
	int32 OrbitIndex = 0;

	UPROPERTY(BlueprintReadOnly, Category = "Planet|Orbit")
	float OrbitRadiusAU = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Planet|Orbit")
	float Eccentricity = 0.f;

	UPROPERTY(BlueprintReadOnly, Category = "Planet|Orbit")
	EGOOrbitalZone Zone = EGOOrbitalZone::Habitable;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	EGOPlanetType Type = EGOPlanetType::Terran;

	/** Earth radii. */
	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	float RadiusEarth = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	double RadiusKm = 6371.0;

	/** Earth masses. */
	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	float MassEarth = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	float DensityGcc = 5.51f;

	/** Multiples of Earth gravity. */
	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	float SurfaceGravityG = 1.f;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	float EscapeVelocityKmS = 11.2f;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	float DayLengthHours = 24.f;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	float AxialTiltDeg = 23.f;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	bool bTidallyLocked = false;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	FGOAtmosphere Atmosphere;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	FGOBiomeParams Biomes;

	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	TArray<FGOMoon> Moons;

	/** How pleasant a home world this would make (higher is better). Used to pick the start. */
	UPROPERTY(BlueprintReadOnly, Category = "Planet")
	float HabitabilityScore = 0.f;

	bool HasSurface() const { return Type != EGOPlanetType::GasGiant && Type != EGOPlanetType::IceGiant; }
	uint64 GetTerrainSeed() const;
	float GetSurfaceTempK() const { return Atmosphere.bPresent ? Atmosphere.SurfaceTempK : Atmosphere.EquilibriumTempK; }
};

/** The generated starting situation: galaxy, home system and the home planet within it. */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOUniverseStart
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	int64 GalaxySeed = 0;

	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	FString GalaxyName;

	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	FGOGalaxyConfig GalaxyConfig;

	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	EGOStartingRegion StartingRegion = EGOStartingRegion::InnerRim;

	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	FGOStarRecord HomeStar;

	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	FGOStarSystem HomeSystem;

	/** One entry per orbital slot of the home system, in orbit order. */
	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	TArray<FGOPlanet> HomePlanets;

	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	int32 HomePlanetIndex = INDEX_NONE;

	/** Number of star systems examined while choosing the start (diagnostics). */
	UPROPERTY(BlueprintReadOnly, Category = "Universe")
	int32 CandidatesExamined = 0;

	bool IsValid() const { return HomePlanets.IsValidIndex(HomePlanetIndex); }
	const FGOPlanet& GetHomePlanet() const { return HomePlanets[HomePlanetIndex]; }
};

namespace GOUniverseText
{
	GALACTICPROCGEN_API FString SpectralClassName(EGOSpectralClass Class);
	GALACTICPROCGEN_API FString PlanetTypeName(EGOPlanetType Type);
	GALACTICPROCGEN_API FString BiomeName(EGOBiome Biome);
}
