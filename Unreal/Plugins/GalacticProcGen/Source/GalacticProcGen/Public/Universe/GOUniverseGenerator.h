#pragma once

#include "CoreMinimal.h"
#include "Universe/GOUniverseTypes.h"

struct FGOContentDefs;

struct GALACTICPROCGEN_API FGOUniverseParams
{
	uint64 Seed = 0;
	EGOGalaxyType GalaxyType = EGOGalaxyType::Spiral;
	EGOGalaxySize GalaxySize = EGOGalaxySize::Medium;
	EGOStartingRegion StartingRegion = EGOStartingRegion::InnerRim;

	/** The search area grows until it holds this many stars in the starting region. */
	int32 MinCandidatePool = 256;

	/** Upper bound on star systems fully generated while looking for a home world. */
	int32 MaxCandidateSystems = 256;

	/** A candidate home world at or above this score is accepted immediately (max is 9.5). */
	float AcceptScore = 8.6f;
};

/**
 * New-game pipeline (port of the Java GalaxyGenerationPipeline):
 *   1. lay out the galaxy for the chosen size/type,
 *   2. look around a seeded point in the chosen starting region,
 *   3. walk main-sequence G/K stars there in seeded order, generating each system,
 *   4. keep the system whose best planet scores highest as a home world (breathable Terran
 *      preferred), stopping early once a candidate clears AcceptScore.
 * Unlike the Java version, the home world's atmosphere is actually generated before it is
 * tested, and several stars are tried instead of settling for the first one.
 */
class GALACTICPROCGEN_API FGOUniverseGenerator
{
public:
	explicit FGOUniverseGenerator(const FGOContentDefs& InContent) : Content(InContent) {}

	FGOUniverseStart GenerateStart(const FGOUniverseParams& Params, TArray<FString>* OutLog = nullptr) const;

	/** Fraction of the galaxy radius the starting search is centred at. */
	static double StartingRadiusFraction(EGOStartingRegion Region);
	static EGOGalaxyRegion GalaxyRegionFor(EGOStartingRegion Region);

private:
	const FGOContentDefs& Content;
};
