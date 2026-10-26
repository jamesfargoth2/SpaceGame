#pragma once

#include "CoreMinimal.h"
#include "Core/GONoise.h"
#include "Universe/GOUniverseTypes.h"

struct FGOContentDefs;

/** Faces of the cube that is inflated into the planet sphere (planet-local frame, Z up). */
enum class EGOCubeFace : uint8
{
	PosX, NegX, PosY, NegY, PosZ, NegZ
};

/** A node of a face quadtree: X/Y index the 2^Depth x 2^Depth grid of cells on the face. */
struct GALACTICPROCGEN_API FGOTerrainChunkId
{
	EGOCubeFace Face = EGOCubeFace::PosX;
	int32 Depth = 0;
	int32 X = 0;
	int32 Y = 0;

	FGOTerrainChunkId() = default;
	FGOTerrainChunkId(EGOCubeFace InFace, int32 InDepth, int32 InX, int32 InY)
		: Face(InFace), Depth(InDepth), X(InX), Y(InY) {}

	double CellSize() const { return 1.0 / static_cast<double>(1 << Depth); }
	double U0() const { return X * CellSize(); }
	double V0() const { return Y * CellSize(); }
	double U1() const { return (X + 1) * CellSize(); }
	double V1() const { return (Y + 1) * CellSize(); }

	FGOTerrainChunkId Child(int32 DX, int32 DY) const { return FGOTerrainChunkId(Face, Depth + 1, X * 2 + DX, Y * 2 + DY); }

	/** Ancestor at a shallower depth (or itself). */
	FGOTerrainChunkId AncestorAt(int32 AncestorDepth) const;

	/** Unique 64-bit key (face:3 | depth:5 | x:28 | y:28). */
	uint64 Key() const;

	bool operator==(const FGOTerrainChunkId& Other) const
	{
		return Face == Other.Face && Depth == Other.Depth && X == Other.X && Y == Other.Y;
	}
	bool operator!=(const FGOTerrainChunkId& Other) const { return !(*this == Other); }
};

FORCEINLINE uint32 GetTypeHash(const FGOTerrainChunkId& Id)
{
	const uint64 K = Id.Key();
	return static_cast<uint32>(K ^ (K >> 32));
}

namespace GOCubeSphere
{
	/** Unit direction for face coordinates U, V in [0, 1]. Double precision: on a 6000 km sphere a
	 *  float direction is only good to ~0.4 m, which shows up as bumpy terrain normals. */
	GALACTICPROCGEN_API FVector FaceUVToDirection(EGOCubeFace Face, double U, double V);
	GALACTICPROCGEN_API void DirectionToFaceUV(const FVector& Direction, EGOCubeFace& OutFace, double& OutU, double& OutV);
	GALACTICPROCGEN_API FVector FaceNormal(EGOCubeFace Face);
	GALACTICPROCGEN_API double Latitude(const FVector& Direction);
	GALACTICPROCGEN_API double Longitude(const FVector& Direction);
}

struct FGOTerrainSample
{
	/** Height above sea level in normalised units (multiply by RadiusKm * HeightScale for km). */
	float Height = 0.f;
	/** Raw continent value the biome was classified with. */
	float Elevation = 0.f;
	EGOBiome Biome = EGOBiome::Grassland;
	bool bWater = false;
};

/**
 * Height and biome function of one planet (port of the Java TerrainNoiseStack). Immutable after
 * construction, so chunk builders on worker threads can share one instance.
 *
 * Differences from the Java stack: terrain amplitude and ridge mix are blended smoothly across
 * the Whittaker climate grid and the coastline instead of switching per biome (the switch left
 * kilometre-high steps at biome borders); oceans are flattened to a walkable water surface at
 * sea level; and latitude is used unscaled because the player no longer spawns on a pole.
 */
class GALACTICPROCGEN_API FGOPlanetTerrain
{
public:
	FGOPlanetTerrain(const FGOPlanet& InPlanet, const FGOContentDefs& Content, float InHeightScale = 0.002f);

	/** Lod is the quadtree depth of the chunk being built; deeper chunks get finer octaves. */
	FGOTerrainSample Sample(const FVector& UnitDirection, int32 Lod) const;

	double SurfaceRadiusKm(const FVector& UnitDirection, int32 Lod) const;
	double HeightToKm(float Height) const { return Height * RadiusKm * HeightScale; }

	double GetRadiusKm() const { return RadiusKm; }
	float GetHeightScale() const { return HeightScale; }
	const FGOPlanet& GetPlanet() const { return Planet; }
	const FGOBiomeParams& GetBiomeParams() const { return Planet.Biomes; }

	/** Climate inputs (Kelvin, 0-1) at a direction for a given continent elevation. */
	float TemperatureAt(const FVector& UnitDirection, float Elevation) const;
	float MoistureAt(const FVector& UnitDirection, float Elevation) const;

	EGOBiome ClassifyBiome(float TemperatureK, float Moisture, float Elevation) const;
	FLinearColor BiomeColor(EGOBiome Biome) const;

	/** Unit surface normal (finite differences, StepKm apart). */
	FVector SurfaceNormal(const FVector& UnitDirection, int32 Lod, double StepKm) const;

private:
	struct FBiomeLook
	{
		float Amplitude = 0.2f;
		float RidgeMix = 0.1f;
		FLinearColor Color = FLinearColor::Gray;
	};

	EGOBiome FilterBiome(EGOBiome Biome) const { return Fallback[static_cast<int32>(Biome)]; }
	void BlendedLandShape(float TemperatureK, float Moisture, float& OutAmplitude, float& OutRidgeMix) const;

	FGOPlanet Planet;
	double RadiusKm;
	float HeightScale;
	FGONoise ContinentNoise;
	FGONoise RidgeNoise;
	FGONoise DetailNoise;
	FGONoise MoistureNoise;
	static constexpr int32 BiomeCount = static_cast<int32>(EGOBiome::River) + 1;
	FBiomeLook Looks[BiomeCount];
	/** Nearest allowed biome (in Whittaker space) for every biome. */
	EGOBiome Fallback[BiomeCount];
};

/** Distance-driven quadtree over the six cube faces (port of the Java TerrainQuadtree LOD logic). */
class GALACTICPROCGEN_API FGOTerrainQuadtree
{
public:
	FGOTerrainQuadtree(double InRadiusKm, int32 InMaxDepth, float InSplitThreshold = 1.5f, float InMergeThreshold = 0.75f);

	/** Recomputes the leaf set for a camera in planet-local km. Returns true when it changed. */
	bool Update(const FVector& CameraPlanetKm);

	const TArray<FGOTerrainChunkId>& GetLeaves() const { return Leaves; }
	int32 GetMaxDepth() const { return MaxDepth; }

	/** Arc length spanned by a chunk, km. */
	double ChunkArcKm(const FGOTerrainChunkId& Id) const;

	/** Distance from the camera to the nearest corner/centre of a chunk on the sphere, km. */
	double NearestDistanceKm(const FGOTerrainChunkId& Id, const FVector& CameraPlanetKm) const;

private:
	void Visit(const FGOTerrainChunkId& Id, const FVector& CameraPlanetKm, TSet<uint64>& OutSplit, TArray<FGOTerrainChunkId>& OutLeaves) const;

	double RadiusKm;
	int32 MaxDepth;
	float SplitThreshold;
	float MergeThreshold;
	TSet<uint64> SplitNodes;
	TArray<FGOTerrainChunkId> Leaves;
};
