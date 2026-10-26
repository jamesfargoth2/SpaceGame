#pragma once

#include "CoreMinimal.h"
#include "Core/GONoise.h"
#include "Universe/GOUniverseTypes.h"

/**
 * Places stars in a galaxy disc. The galaxy is split into square chunks; each chunk's stars are
 * a pure function of (galaxy seed, chunk coordinate), so chunks can be generated lazily, in any
 * order, on any thread. Coordinates are light years (double precision); the disc lies in XY.
 */
class GALACTICPROCGEN_API FGOGalaxyGenerator
{
public:
	FGOGalaxyGenerator(uint64 InGalaxySeed, const FGOGalaxyConfig& InConfig);

	const FGOGalaxyConfig& GetConfig() const { return Config; }
	uint64 GetSeed() const { return GalaxySeed; }

	/** Relative stellar density in [0, 1] at normalised coordinates (1 = disc rim). */
	float DensityAt(float NX, float NY) const;

	EGOGalaxyRegion ClassifyRegion(const FVector& PositionLY) const;

	FIntPoint ChunkOf(const FVector& PositionLY) const;

	/** Deterministic star list of one chunk. */
	void GenerateChunk(const FIntPoint& Chunk, TArray<FGOStarRecord>& OutStars) const;

	/** All stars within RadiusLY (measured in the disc plane) of CenterLY. */
	void GatherStars(const FVector& CenterLY, double RadiusLY, TArray<FGOStarRecord>& OutStars) const;

private:
	float ArmDensity(float R, float Angle, int32 ArmCount, float MinRadius) const;

	uint64 GalaxySeed;
	uint64 StarDomainSeed;
	FGOGalaxyConfig Config;
	FGONoise ShapeNoise;

	/** Mean of DensityAt over the disc, used to normalise per-chunk star counts. */
	float MeanDensity = 0.25f;
};
