#pragma once

#include "CoreMinimal.h"
#include "Universe/GOUniverseTypes.h"

struct FGOContentDefs;
struct FGORandom;

/** Physical relations used by star generation (port of the Java StellarEvolution). */
namespace GOStellar
{
	/** t_MS = 10 * M^-2.5 Gyr. */
	GALACTICPROCGEN_API float MainSequenceLifetimeGyr(float MassSolar);
	GALACTICPROCGEN_API float MainSequenceLuminosity(float MassSolar);
	GALACTICPROCGEN_API float MainSequenceRadius(float MassSolar);
	GALACTICPROCGEN_API EGOSpectralClass SpectralClassFromMass(float MassSolar);

	/** Blackbody tint for a colour temperature (Tanner Helland fit, ~1000K-40000K). */
	GALACTICPROCGEN_API FLinearColor ColorFromTemperature(float TemperatureK);
}

/**
 * Derives a full star system (star, optional companion, orbital slots, name) from a star
 * record. The result depends only on the galaxy seed and the record, never on how the star
 * was found, so the same star always comes out the same.
 */
class GALACTICPROCGEN_API FGOStarSystemGenerator
{
public:
	FGOStarSystemGenerator(uint64 InGalaxySeed, const FGOContentDefs& InContent);

	FGOStarSystem Generate(const FGOStarRecord& Star, EGOGalaxyRegion Region) const;

private:
	EGOSpectralClass RollSpectralClass(FGORandom& Rng, EGOGalaxyRegion Region) const;
	void GenerateOrbits(FGOStarSystem& System) const;

	uint64 StarDomainSeed;
	const FGOContentDefs& Content;
};
