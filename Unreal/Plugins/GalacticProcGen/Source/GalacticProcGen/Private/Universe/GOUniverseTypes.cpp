#include "Universe/GOUniverseTypes.h"
#include "Core/GORandom.h"

int32 FGOGalaxyConfig::StarCountForSize(EGOGalaxySize Size)
{
	switch (Size)
	{
	case EGOGalaxySize::Small:	return 500;
	case EGOGalaxySize::Large:	return 10000;
	case EGOGalaxySize::Medium:
	default:					return 2000;
	}
}

FGOGalaxyConfig FGOGalaxyConfig::Make(EGOGalaxySize Size, EGOGalaxyType Type)
{
	FGOGalaxyConfig Config;
	Config.Type = Type;
	Config.TargetStarCount = StarCountForSize(Size);
	// radius = chunk * sqrt(stars * 1.2 / pi): keeps galaxy scale proportional to star count.
	Config.RadiusLY = Config.ChunkSizeLY * FMath::Sqrt(static_cast<double>(Config.TargetStarCount) * 1.2 / UE_DOUBLE_PI);
	if (Type == EGOGalaxyType::BarredSpiral)
	{
		Config.ArmCount = 2;
	}
	return Config;
}

float FGOAtmosphere::GetFraction(EGOGas Gas) const
{
	for (const FGOGasFraction& Entry : Composition)
	{
		if (Entry.Gas == Gas)
		{
			return Entry.Fraction;
		}
	}
	return 0.f;
}

uint64 FGOPlanet::GetTerrainSeed() const
{
	return GOSeed::Domain(GOSeed::FromBlueprint(Seed), GOSeed::TerrainDomain);
}

FString GOUniverseText::SpectralClassName(EGOSpectralClass Class)
{
	static const TCHAR* Names[] = { TEXT("O"), TEXT("B"), TEXT("A"), TEXT("F"), TEXT("G"), TEXT("K"), TEXT("M") };
	const int32 Index = static_cast<int32>(Class);
	return Index >= 0 && Index < UE_ARRAY_COUNT(Names) ? FString(Names[Index]) : FString(TEXT("?"));
}

FString GOUniverseText::PlanetTypeName(EGOPlanetType Type)
{
	static const TCHAR* Names[] = {
		TEXT("Molten"), TEXT("Barren"), TEXT("Arid"), TEXT("Terran"), TEXT("Ocean"),
		TEXT("Toxic"), TEXT("Gas Giant"), TEXT("Ice Giant"), TEXT("Ice World"), TEXT("Dwarf")
	};
	const int32 Index = static_cast<int32>(Type);
	return Index >= 0 && Index < UE_ARRAY_COUNT(Names) ? FString(Names[Index]) : FString(TEXT("?"));
}

FString GOUniverseText::BiomeName(EGOBiome Biome)
{
	static const TCHAR* Names[] = {
		TEXT("Ice Sheet"), TEXT("Tundra"), TEXT("Polar Desert"), TEXT("Ice Field"), TEXT("Boreal Forest"),
		TEXT("Temperate Forest"), TEXT("Steppe"), TEXT("Rocky Waste"), TEXT("Tropical Forest"), TEXT("Grassland"),
		TEXT("Arid Shrub"), TEXT("Desert"), TEXT("Swamp"), TEXT("Savanna"), TEXT("Badlands"),
		TEXT("Volcanic"), TEXT("Ocean"), TEXT("Lake"), TEXT("River")
	};
	const int32 Index = static_cast<int32>(Biome);
	return Index >= 0 && Index < UE_ARRAY_COUNT(Names) ? FString(Names[Index]) : FString(TEXT("?"));
}
