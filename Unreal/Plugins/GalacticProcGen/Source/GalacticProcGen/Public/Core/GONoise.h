#pragma once

#include "CoreMinimal.h"

/**
 * Seeded simplex noise (2D/3D) with a permutation table shuffled by FGORandom.
 * Thread-safe once constructed: all sampling functions are const.
 */
class GALACTICPROCGEN_API FGOSimplexNoise
{
public:
	explicit FGOSimplexNoise(uint64 Seed);

	/** Roughly in [-1, 1]. */
	float Noise2(float X, float Y) const;

	/** Roughly in [-1, 1]. */
	float Noise3(float X, float Y, float Z) const;

private:
	uint8 Perm[512];
};

/** Fractal layers over FGOSimplexNoise (ported from the Java GalaxyNoise). */
class GALACTICPROCGEN_API FGONoise
{
public:
	explicit FGONoise(uint64 Seed) : Simplex(Seed) {}

	float Fbm2(float X, float Y, int32 Octaves, float Persistence = 0.5f, float Lacunarity = 2.f) const;
	float Fbm3(float X, float Y, float Z, int32 Octaves, float Persistence = 0.5f, float Lacunarity = 2.f) const;

	/** Ridged multifractal; output is >= 0 (roughly [0, 2]). */
	float RidgedFbm3(float X, float Y, float Z, int32 Octaves, float Lacunarity = 2.f, float Gain = 2.f) const;

	/** Billowy turbulence remapped to roughly [-1, 1]. */
	float BillowFbm3(float X, float Y, float Z, int32 Octaves, float Persistence = 0.5f, float Lacunarity = 2.f) const;

	/** fbm sampled through a warped domain; organic continent shapes. */
	float DomainWarp3(float X, float Y, float Z, float WarpStrength, int32 WarpOctaves, int32 MainOctaves) const;

	const FGOSimplexNoise& GetSimplex() const { return Simplex; }

private:
	FGOSimplexNoise Simplex;
};
