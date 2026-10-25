#include "Planet/GOTerrainMesh.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"

namespace GOTerrainMeshPrivate
{
	constexpr double KmToCm = 100000.0;

	float LandingPreference(EGOBiome Biome)
	{
		switch (Biome)
		{
		case EGOBiome::Grassland:		return 3.f;
		case EGOBiome::TemperateForest:	return 2.5f;
		case EGOBiome::Savanna:			return 2.5f;
		case EGOBiome::Steppe:			return 2.f;
		case EGOBiome::AridShrub:		return 1.5f;
		case EGOBiome::BorealForest:	return 1.5f;
		case EGOBiome::TropicalForest:	return 1.5f;
		case EGOBiome::Tundra:			return 1.f;
		case EGOBiome::Desert:			return 1.f;
		default:						return 0.5f;
		}
	}
}

FVector GOTerrainMesh::ChunkOriginCm(const FGOPlanetTerrain& Terrain, const FGOTerrainChunkId& Id)
{
	const FVector Center = GOCubeSphere::FaceUVToDirection(Id.Face, (Id.U0() + Id.U1()) * 0.5, (Id.V0() + Id.V1()) * 0.5);
	return Center * (Terrain.GetRadiusKm() * GOTerrainMeshPrivate::KmToCm);
}

void GOTerrainMesh::BuildChunk(const FGOPlanetTerrain& Terrain, const FGOContentDefs& Content,
	const FGOTerrainChunkId& Id, const FGOTerrainMeshSettings& Settings, FGOChunkMeshData& Out)
{
	using namespace GOTerrainMeshPrivate;

	const int32 G = FMath::Max(Settings.GridSize, 3);
	const int32 B = G + 2;	// grid plus a one-vertex border used only for seamless normals

	Out.Id = Id;
	Out.OriginCm = ChunkOriginCm(Terrain, Id);
	Out.Positions.Reset();
	Out.Normals.Reset();
	Out.UVs.Reset();
	Out.Colors.Reset();
	Out.Triangles.Reset();
	Out.Scatter.Reset();

	const double U0 = Id.U0();
	const double V0 = Id.V0();
	const double Step = Id.CellSize() / (G - 1);

	TArray<FVector> Grid;
	Grid.SetNumUninitialized(B * B);
	TArray<EGOBiome> Biomes;
	Biomes.SetNumUninitialized(G * G);

	for (int32 J = -1; J <= G; ++J)
	{
		for (int32 I = -1; I <= G; ++I)
		{
			const FVector Dir = GOCubeSphere::FaceUVToDirection(Id.Face, U0 + I * Step, V0 + J * Step);
			const FGOTerrainSample Sample = Terrain.Sample(Dir, Id.Depth);
			const double RadiusCm = (Terrain.GetRadiusKm() + Terrain.HeightToKm(Sample.Height)) * KmToCm;
			Grid[(J + 1) * B + (I + 1)] = Dir * RadiusCm - Out.OriginCm;
			if (I >= 0 && J >= 0 && I < G && J < G)
			{
				Biomes[J * G + I] = Sample.Biome;
			}
		}
	}

	const int32 SurfaceCount = G * G;
	Out.Positions.Reserve(SurfaceCount + 4 * G);
	Out.Normals.Reserve(SurfaceCount + 4 * G);
	Out.UVs.Reserve(SurfaceCount + 4 * G);
	Out.Colors.Reserve(SurfaceCount + 4 * G);

	for (int32 J = 0; J < G; ++J)
	{
		for (int32 I = 0; I < G; ++I)
		{
			const FVector& P = Grid[(J + 1) * B + (I + 1)];
			const FVector DX = Grid[(J + 1) * B + (I + 2)] - Grid[(J + 1) * B + I];
			const FVector DY = Grid[(J + 2) * B + (I + 1)] - Grid[J * B + (I + 1)];
			const FVector Radial = (P + Out.OriginCm).GetSafeNormal();
			FVector Normal = FVector::CrossProduct(DX, DY).GetSafeNormal();
			if (FVector::DotProduct(Normal, Radial) < 0.0)
			{
				Normal = -Normal;
			}

			Out.Positions.Add(P);
			Out.Normals.Add(Normal);
			Out.UVs.Add(FVector2D(static_cast<double>(I) / (G - 1), static_cast<double>(J) / (G - 1)));
			Out.Colors.Add(Terrain.BiomeColor(Biomes[J * G + I]));
		}
	}
	Out.SurfaceVertexCount = SurfaceCount;

	auto Index = [G](int32 I, int32 J) { return J * G + I; };

	// Face orientation differs per cube face, so pick the winding from the geometry itself.
	const FVector RadialAtCorner = (Out.Positions[Index(0, 0)] + Out.OriginCm).GetSafeNormal();
	const bool bFlip = !GOMeshWinding::IsFrontFacing(
		Out.Positions[Index(0, 0)], Out.Positions[Index(1, 0)], Out.Positions[Index(1, 1)], RadialAtCorner);

	Out.Triangles.Reserve((G - 1) * (G - 1) * 6 + 4 * (G - 1) * 6);
	auto AddTriangle = [&Out](int32 A, int32 Bv, int32 C, bool bReverse)
	{
		Out.Triangles.Add(A);
		Out.Triangles.Add(bReverse ? C : Bv);
		Out.Triangles.Add(bReverse ? Bv : C);
	};

	for (int32 J = 0; J < G - 1; ++J)
	{
		for (int32 I = 0; I < G - 1; ++I)
		{
			const int32 V00 = Index(I, J);
			const int32 V10 = Index(I + 1, J);
			const int32 V01 = Index(I, J + 1);
			const int32 V11 = Index(I + 1, J + 1);
			AddTriangle(V00, V10, V11, bFlip);
			AddTriangle(V00, V11, V01, bFlip);
		}
	}

	// Skirts: a curtain hanging below the chunk edge hides cracks against coarser neighbours.
	TArray<int32> Boundary;
	Boundary.Reserve(4 * (G - 1));
	for (int32 I = 0; I < G; ++I)			{ Boundary.Add(Index(I, 0)); }
	for (int32 J = 1; J < G; ++J)			{ Boundary.Add(Index(G - 1, J)); }
	for (int32 I = G - 2; I >= 0; --I)		{ Boundary.Add(Index(I, G - 1)); }
	for (int32 J = G - 2; J >= 1; --J)		{ Boundary.Add(Index(0, J)); }

	const double ArcCm = Terrain.GetRadiusKm() * Id.CellSize() * (UE_DOUBLE_PI * 0.5) * KmToCm;
	const double SkirtDepthCm = FMath::Max(ArcCm * Settings.SkirtFraction, 100.0);

	const int32 SkirtStart = Out.Positions.Num();
	for (const int32 Edge : Boundary)
	{
		const FVector P = Out.Positions[Edge];
		const FVector Radial = (P + Out.OriginCm).GetSafeNormal();
		Out.Positions.Add(P - Radial * SkirtDepthCm);
		Out.Normals.Add(Out.Normals[Edge]);
		Out.UVs.Add(Out.UVs[Edge]);
		Out.Colors.Add(Out.Colors[Edge]);
	}

	for (int32 K = 0; K < Boundary.Num(); ++K)
	{
		const int32 Next = (K + 1) % Boundary.Num();
		const int32 E0 = Boundary[K];
		const int32 E1 = Boundary[Next];
		const int32 S0 = SkirtStart + K;
		const int32 S1 = SkirtStart + Next;

		const FVector Mid = (Out.Positions[E0] + Out.Positions[E1]) * 0.5;
		const FVector Radial = (Mid + Out.OriginCm).GetSafeNormal();
		const FVector Outward = Mid - Radial * FVector::DotProduct(Mid, Radial);
		const bool bReverse = !GOMeshWinding::IsFrontFacing(Out.Positions[E0], Out.Positions[E1], Out.Positions[S1], Outward);
		AddTriangle(E0, E1, S1, bReverse);
		AddTriangle(E0, S1, S0, bReverse);
	}

	if (Id.Depth >= Settings.ScatterMinDepth)
	{
		BuildScatter(Terrain, Content, Id, Settings, Out.OriginCm, Out.Scatter);
	}
}

void GOTerrainMesh::BuildScatter(const FGOPlanetTerrain& Terrain, const FGOContentDefs& Content,
	const FGOTerrainChunkId& Id, const FGOTerrainMeshSettings& Settings, const FVector& OriginCm,
	TArray<FGOScatterInstance>& Out)
{
	using namespace GOTerrainMeshPrivate;

	Out.Reset();
	if (Id.Depth < Settings.ScatterMinDepth)
	{
		return;
	}

	// Instances live on a fixed-depth grid of scatter cells so a rock stays put whatever depth
	// the chunk displaying it has; each chunk keeps the subset inside its own bounds.
	const FGOTerrainChunkId Cell = Id.AncestorAt(Settings.ScatterMinDepth);
	const uint64 CellSeed = GOSeed::ForId(GOSeed::Domain(Terrain.GetPlanet().GetTerrainSeed(), GOSeed::ScatterDomain), Cell.Key());
	const double CellArcKm = Terrain.GetRadiusKm() * Cell.CellSize() * (UE_DOUBLE_PI * 0.5);
	const double CellAreaKm2 = CellArcKm * CellArcKm;

	// Stable order regardless of how the kit was loaded.
	TArray<const FGOAssetKitRow*> Rows;
	for (const TPair<FName, FGOAssetKitRow>& Pair : Content.AssetKit)
	{
		if (Pair.Value.Category == EGOKitCategory::Scatter && Pair.Value.DensityPerKm2 > 0.f)
		{
			Rows.Add(&Pair.Value);
		}
	}
	Rows.Sort([](const FGOAssetKitRow& A, const FGOAssetKitRow& B) { return A.AssetId.ToString() < B.AssetId.ToString(); });

	const double U0 = Id.U0();
	const double U1 = Id.U1();
	const double V0 = Id.V0();
	const double V1 = Id.V1();
	const double NormalStepKm = 0.01;
	const double KeepFraction = FMath::Pow(static_cast<double>(FMath::Clamp(Settings.ScatterDensityFalloff, 0.f, 1.f)),
		static_cast<double>(FMath::Max(Settings.MaxDepth - Id.Depth, 0)));

	struct FCandidate
	{
		FGOScatterInstance Instance;
		float Rank;
	};
	TArray<FCandidate> Accepted;

	for (const FGOAssetKitRow* Row : Rows)
	{
		FGORandom Rng(GOSeed::ForId(CellSeed, GOSeed::FromText(Row->AssetId.ToString())));
		const double Expected = Row->DensityPerKm2 * CellAreaKm2;
		const int32 Whole = FMath::FloorToInt(Expected);
		const int32 Count = Whole + (Rng.DRand() < Expected - Whole ? 1 : 0);

		for (int32 K = 0; K < Count; ++K)
		{
			// Draw every random value up front so filtering never shifts the stream.
			const double U = Cell.U0() + Rng.DRand() * Cell.CellSize();
			const double V = Cell.V0() + Rng.DRand() * Cell.CellSize();
			const float YawDeg = Rng.FRand() * 360.f;
			const float ScaleAlpha = Rng.FRand();
			const float Rank = Rng.FRand();

			if (Rank >= KeepFraction || U < U0 || U >= U1 || V < V0 || V >= V1)
			{
				continue;
			}

			const FVector Dir = GOCubeSphere::FaceUVToDirection(Id.Face, U, V);
			const FGOTerrainSample Sample = Terrain.Sample(Dir, Id.Depth);
			if (Sample.bWater || (Row->Biomes.Num() > 0 && !Row->Biomes.Contains(Sample.Biome)))
			{
				continue;
			}

			const FVector Normal = Terrain.SurfaceNormal(Dir, Id.Depth, NormalStepKm);
			const double SlopeDeg = FMath::RadiansToDegrees(FMath::Acos(FMath::Clamp(FVector::DotProduct(Normal, Dir), -1.0, 1.0)));
			if (SlopeDeg > Row->MaxSlopeDeg)
			{
				continue;
			}

			const float Scale = FMath::Lerp(static_cast<float>(Row->ScaleRange.X), static_cast<float>(Row->ScaleRange.Y), ScaleAlpha);
			const FVector SlotSize = Row->FootprintCm * Scale;
			const FVector Up = Row->bAlignToSurface ? Normal : Dir;
			const FQuat Rotation = FQuat::FindBetweenNormals(FVector::UpVector, Up) * FQuat(FVector::UpVector, FMath::DegreesToRadians(YawDeg));

			const double SurfaceCm = (Terrain.GetRadiusKm() + Terrain.HeightToKm(Sample.Height)) * KmToCm;
			const FVector Ground = Dir * SurfaceCm - OriginCm;
			const FVector Center = Ground + Up * (SlotSize.Z * 0.5 - Row->SinkCm);

			FCandidate& Candidate = Accepted.AddDefaulted_GetRef();
			Candidate.Instance.AssetId = Row->AssetId;
			Candidate.Instance.Transform = FTransform(Rotation, Center);
			Candidate.Instance.SlotSizeCm = SlotSize;
			Candidate.Rank = Rank;
		}
	}

	// Over budget: keep the lowest ranks, which thins every asset evenly.
	if (Accepted.Num() > Settings.MaxScatterPerChunk)
	{
		Accepted.StableSort([](const FCandidate& A, const FCandidate& B) { return A.Rank < B.Rank; });
		Accepted.SetNum(FMath::Max(Settings.MaxScatterPerChunk, 0));
	}

	Out.Reserve(Accepted.Num());
	for (const FCandidate& Candidate : Accepted)
	{
		Out.Add(Candidate.Instance);
	}
}

FGOLandingSite GOLandingSite::Find(const FGOPlanetTerrain& Terrain, uint64 Seed, int32 Lod, int32 Candidates)
{
	using namespace GOTerrainMeshPrivate;

	FGORandom Rng(GOSeed::Domain(Seed, GOSeed::LandingSiteDomain));
	const double MaxSinLat = FMath::Sin(FMath::DegreesToRadians(40.0));

	FGOLandingSite Best;
	float BestScore = -TNumericLimits<float>::Max();
	for (int32 K = 0; K < FMath::Max(Candidates, 1); ++K)
	{
		const double Z = Rng.DRandRange(-MaxSinLat, MaxSinLat);
		const double Lon = Rng.DRand() * 2.0 * UE_DOUBLE_PI;
		const double Ring = FMath::Sqrt(FMath::Max(0.0, 1.0 - Z * Z));
		const FVector Dir(Ring * FMath::Cos(Lon), Ring * FMath::Sin(Lon), Z);

		const FGOTerrainSample Sample = Terrain.Sample(Dir, Lod);
		const FVector Normal = Terrain.SurfaceNormal(Dir, Lod, 0.05);
		const float SlopeDeg = static_cast<float>(FMath::RadiansToDegrees(
			FMath::Acos(FMath::Clamp(FVector::DotProduct(Normal, Dir), -1.0, 1.0))));

		float Score = LandingPreference(Sample.Biome) - SlopeDeg * 0.1f;
		// Prefer lowlands a little above the shoreline: flat, green and with a sea view.
		Score -= FMath::Abs(Sample.Height - 0.01f) * 20.f;
		if (Sample.bWater || Sample.Biome == EGOBiome::IceSheet || Sample.Biome == EGOBiome::Ocean)
		{
			Score -= 100.f;
		}

		if (Score > BestScore)
		{
			BestScore = Score;
			Best.Direction = Dir;
			Best.SurfaceRadiusKm = Terrain.GetRadiusKm() + Terrain.HeightToKm(Sample.Height);
			Best.Biome = Sample.Biome;
			Best.SlopeDeg = SlopeDeg;
		}
	}
	return Best;
}
