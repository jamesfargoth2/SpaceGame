#pragma once

#include "CoreMinimal.h"

struct FGOContentDefs;
struct FGORandom;

/**
 * Seeded names built from the syllable lists in the content (NameLists.json).
 * Each call derives its own stream from the seed, so names never depend on call order.
 */
class GALACTICPROCGEN_API FGONameGenerator
{
public:
	explicit FGONameGenerator(const FGOContentDefs& InContent) : Content(InContent) {}

	FString GalaxyName(uint64 Seed) const;
	FString StarName(uint64 Seed) const;
	FString PlanetName(uint64 Seed) const;
	FString MoonName(const FString& PlanetName, int32 MoonIndex) const;
	FString StationName(uint64 Seed) const;
	FString AcademyName(uint64 Seed) const;
	FString PersonName(uint64 Seed) const;
	FString ShipName(uint64 Seed) const;

	static FString RomanNumeral(int32 Value);

private:
	FString Pick(FGORandom& Rng, const TCHAR* ListId, const TCHAR* Fallback) const;

	const FGOContentDefs& Content;
};
