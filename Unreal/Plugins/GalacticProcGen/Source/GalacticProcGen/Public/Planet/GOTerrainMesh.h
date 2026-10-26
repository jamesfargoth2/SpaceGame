#pragma once

#include "CoreMinimal.h"
#include "Planet/GOPlanetTerrain.h"

struct FGOContentDefs;

/** One scattered asset instance: a slot (centre + facing, Z along the ground normal) relative to
 *  its chunk origin, in centimetres. The runtime fits the kit mesh into SlotSizeCm. */
struct GALACTICPROCGEN_API FGOScatterInstance
{
	FName AssetId;
	FTransform Transform;
	FVector SlotSizeCm = FVector(100.0, 100.0, 100.0);
};

/**
 * Renderable geometry for one terrain chunk. Positions are relative to Origin so they stay
 * small enough for float precision on the GPU; Origin itself is planet-local, centimetres.
 */
struct GALACTICPROCGEN_API FGOChunkMeshData
{
	FGOTerrainChunkId Id;
	FVector OriginCm = FVector::ZeroVector;
	TArray<FVector> Positions;
	TArray<FVector> Normals;
	TArray<FVector2D> UVs;
	TArray<FLinearColor> Colors;
	TArray<int32> Triangles;
	int32 SurfaceVertexCount = 0;
	TArray<FGOScatterInstance> Scatter;
};

namespace GOMeshWinding
{
	/**
	 * True when triangle (A, B, C) is front-facing for a viewer on the side of DesiredNormal.
	 * Unreal treats (C - A) x (B - A) as the front side, matching the engine's own procedural
	 * box (UKismetProceduralMeshLibrary::GenerateBoxMesh). Kept in one place on purpose.
	 */
	FORCEINLINE bool IsFrontFacing(const FVector& A, const FVector& B, const FVector& C, const FVector& DesiredNormal)
	{
		return FVector::DotProduct(FVector::CrossProduct(C - A, B - A), DesiredNormal) > 0.0;
	}
}

struct GALACTICPROCGEN_API FGOTerrainMeshSettings
{
	/** Vertices per chunk edge. */
	int32 GridSize = 33;

	/** Skirt depth as a fraction of the chunk arc; hides cracks between LOD levels. */
	float SkirtFraction = 0.04f;

	/** Chunks at or below this depth carry scatter (rocks, flora). */
	int32 ScatterMinDepth = 12;

	/** Deepest quadtree level; scatter reaches full density there. */
	int32 MaxDepth = 14;

	/** Fraction of scatter kept per level above MaxDepth. Coarser chunks show a stable subset
	 *  (an instance visible at one level stays visible when the chunk splits). */
	float ScatterDensityFalloff = 0.35f;

	/** Upper bound on scatter instances per chunk; excess is thinned evenly across assets. */
	int32 MaxScatterPerChunk = 3000;
};

namespace GOTerrainMesh
{
	/** Builds the chunk mesh (and its scatter when deep enough). Thread-safe. */
	GALACTICPROCGEN_API void BuildChunk(const FGOPlanetTerrain& Terrain, const FGOContentDefs& Content,
		const FGOTerrainChunkId& Id, const FGOTerrainMeshSettings& Settings, FGOChunkMeshData& Out);

	/** Deterministic scatter for a chunk; instances are stable regardless of the chunk's depth. */
	GALACTICPROCGEN_API void BuildScatter(const FGOPlanetTerrain& Terrain, const FGOContentDefs& Content,
		const FGOTerrainChunkId& Id, const FGOTerrainMeshSettings& Settings, const FVector& OriginCm,
		TArray<FGOScatterInstance>& Out);

	/** Planet-local origin of a chunk (sea-level point under its centre), centimetres. */
	GALACTICPROCGEN_API FVector ChunkOriginCm(const FGOPlanetTerrain& Terrain, const FGOTerrainChunkId& Id);
}

struct GALACTICPROCGEN_API FGOLandingSite
{
	FVector Direction = FVector(0.0, 0.0, 1.0);
	double SurfaceRadiusKm = 0.0;
	EGOBiome Biome = EGOBiome::Grassland;
	float SlopeDeg = 0.f;
};

namespace GOLandingSite
{
	/** Picks a dry, gentle, temperate spot (|latitude| < 40 deg) for the player's first landing. */
	GALACTICPROCGEN_API FGOLandingSite Find(const FGOPlanetTerrain& Terrain, uint64 Seed, int32 Lod, int32 Candidates = 256);
}
