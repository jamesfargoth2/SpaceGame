#pragma once

#include "CoreMinimal.h"
#include "Universe/GOUniverseTypes.h"

struct FGOContentDefs;
struct FGORandom;

namespace GOPlanetConstants
{
	constexpr double EarthRadiusKm = 6371.0;
	constexpr float EarthDensityGcc = 5.51f;
	constexpr double AstronomicalUnitKm = 149597870.7;
}

/**
 * Generates the planets of a star system: bulk physics, moons, atmosphere and the parameters
 * of the surface biome map. Ported from the Java PlanetGenerator/AtmosphereGenerator/BiomeMapper
 * with two corrections: masses are normalised to Earth (so an Earth-like world has ~1 g instead
 * of ~5.5 g), and atmospheres are generated with the planet's real orbital distance.
 */
class GALACTICPROCGEN_API FGOPlanetGenerator
{
public:
	explicit FGOPlanetGenerator(const FGOContentDefs& InContent) : Content(InContent) {}

	FGOPlanet Generate(const FGOStarSystem& System, const FGOOrbitalSlot& Slot) const;

	/** One planet per orbital slot, in orbit order. */
	TArray<FGOPlanet> GenerateAll(const FGOStarSystem& System) const;

	/** Higher means a nicer home world (0..9.5): Terran, breathable air, ~288 K, ~1 g, no hazards. */
	static float ScoreHabitability(const FGOPlanet& Planet);

private:
	EGOPlanetType RollPlanetType(FGORandom& Rng, EGOOrbitalZone Zone) const;
	FGOAtmosphere GenerateAtmosphere(const FGOPlanet& Planet, const FGOStarSystem& System) const;
	FGOBiomeParams GenerateBiomeParams(const FGOPlanet& Planet) const;

	const FGOContentDefs& Content;
};
