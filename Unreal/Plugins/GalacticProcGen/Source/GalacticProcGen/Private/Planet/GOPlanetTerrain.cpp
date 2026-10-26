#include "Planet/GOPlanetTerrain.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"

namespace GOPlanetTerrainPrivate
{
	// Whittaker grid [moisture row][temperature column]: Freezing, Cool, Warm, Hot.
	const EGOBiome WhittakerGrid[4][4] = {
		{ EGOBiome::IceField,    EGOBiome::RockyWaste,      EGOBiome::Desert,         EGOBiome::Volcanic },
		{ EGOBiome::PolarDesert, EGOBiome::Steppe,          EGOBiome::AridShrub,      EGOBiome::Badlands },
		{ EGOBiome::Tundra,      EGOBiome::TemperateForest, EGOBiome::Grassland,      EGOBiome::Savanna },
		{ EGOBiome::IceSheet,    EGOBiome::BorealForest,    EGOBiome::TropicalForest, EGOBiome::Swamp },
	};

	// Cell centres used to blend terrain shape smoothly across the grid.
	const float TemperatureCenters[4] = { 243.f, 263.f, 288.f, 318.f };
	const float MoistureCenters[4] = { 0.125f, 0.375f, 0.625f, 0.875f };

	int32 TemperatureBand(float TemperatureK)
	{
		if (TemperatureK < 253.f) return 0;
		if (TemperatureK < 273.f) return 1;
		if (TemperatureK < 303.f) return 2;
		return 3;
	}

	int32 MoistureBand(float Moisture)
	{
		if (Moisture < 0.25f) return 0;
		if (Moisture < 0.5f) return 1;
		if (Moisture < 0.75f) return 2;
		return 3;
	}

	/** Whittaker coordinates (temperature column, moisture row) of every biome, for fallbacks. */
	FIntPoint GridCoord(EGOBiome Biome)
	{
		for (int32 M = 0; M < 4; ++M)
		{
			for (int32 T = 0; T < 4; ++T)
			{
				if (WhittakerGrid[M][T] == Biome)
				{
					return FIntPoint(T, M);
				}
			}
		}
		// Water biomes sit with the warm, wet end of the grid.
		return FIntPoint(2, 3);
	}

	/** Bilinear position between two neighbouring cell centres. */
	void Bracket(const float* Centers, float Value, int32& OutLow, int32& OutHigh, float& OutAlpha)
	{
		if (Value <= Centers[0])
		{
			OutLow = OutHigh = 0;
			OutAlpha = 0.f;
			return;
		}
		if (Value >= Centers[3])
		{
			OutLow = OutHigh = 3;
			OutAlpha = 0.f;
			return;
		}
		for (int32 I = 0; I < 3; ++I)
		{
			if (Value < Centers[I + 1])
			{
				OutLow = I;
				OutHigh = I + 1;
				OutAlpha = (Value - Centers[I]) / (Centers[I + 1] - Centers[I]);
				return;
			}
		}
		OutLow = OutHigh = 3;
		OutAlpha = 0.f;
	}

	struct FDetailOctave
	{
		int32 MinLod;
		float Frequency;
		int32 Octaves;
		float Weight;
		bool bBillow;
	};

	// Geometric series: each step doubles frequency and halves weight, so every octave adds the
	// same slope and deep chunks gain walkable metre-scale relief without cliffs.
	const FDetailOctave DetailOctaves[] = {
		{ 3,    64.f, 4, 0.1f,       true },
		{ 5,   256.f, 3, 0.02f,      false },
		{ 7,   512.f, 3, 0.01f,      false },
		{ 9,  1024.f, 3, 0.005f,     false },
		{ 11, 2048.f, 2, 0.0025f,    false },
		{ 12, 4096.f, 2, 0.00125f,   false },
		{ 12, 8192.f, 2, 0.000625f,  false },
		{ 13, 16384.f, 2, 0.0003125f, false },
		{ 13, 32768.f, 2, 0.00015625f, false },
		{ 14, 65536.f, 2, 0.000078f, false },
	};
}

FGOTerrainChunkId FGOTerrainChunkId::AncestorAt(int32 AncestorDepth) const
{
	const int32 Target = FMath::Clamp(AncestorDepth, 0, Depth);
	const int32 Shift = Depth - Target;
	return FGOTerrainChunkId(Face, Target, X >> Shift, Y >> Shift);
}

uint64 FGOTerrainChunkId::Key() const
{
	return (static_cast<uint64>(Face) << 61)
		| (static_cast<uint64>(Depth & 31) << 56)
		| (static_cast<uint64>(X & 0x0FFFFFFF) << 28)
		| static_cast<uint64>(Y & 0x0FFFFFFF);
}

FVector GOCubeSphere::FaceUVToDirection(EGOCubeFace Face, double U, double V)
{
	const double CU = U * 2.0 - 1.0;
	const double CV = V * 2.0 - 1.0;
	FVector P;
	switch (Face)
	{
	case EGOCubeFace::PosX: P = FVector(1.0, CU, CV); break;
	case EGOCubeFace::NegX: P = FVector(-1.0, -CU, CV); break;
	case EGOCubeFace::PosY: P = FVector(-CU, 1.0, CV); break;
	case EGOCubeFace::NegY: P = FVector(CU, -1.0, CV); break;
	case EGOCubeFace::PosZ: P = FVector(-CV, CU, 1.0); break;
	case EGOCubeFace::NegZ:
	default:                P = FVector(CV, CU, -1.0); break;
	}
	return P.GetSafeNormal();
}

void GOCubeSphere::DirectionToFaceUV(const FVector& Direction, EGOCubeFace& OutFace, double& OutU, double& OutV)
{
	const double AX = FMath::Abs(Direction.X);
	const double AY = FMath::Abs(Direction.Y);
	const double AZ = FMath::Abs(Direction.Z);

	double CU = 0.0;
	double CV = 0.0;
	if (AX >= AY && AX >= AZ)
	{
		if (Direction.X > 0.0) { OutFace = EGOCubeFace::PosX; CU = Direction.Y / AX; CV = Direction.Z / AX; }
		else                   { OutFace = EGOCubeFace::NegX; CU = -Direction.Y / AX; CV = Direction.Z / AX; }
	}
	else if (AY >= AX && AY >= AZ)
	{
		if (Direction.Y > 0.0) { OutFace = EGOCubeFace::PosY; CU = -Direction.X / AY; CV = Direction.Z / AY; }
		else                   { OutFace = EGOCubeFace::NegY; CU = Direction.X / AY; CV = Direction.Z / AY; }
	}
	else
	{
		if (Direction.Z > 0.0) { OutFace = EGOCubeFace::PosZ; CU = Direction.Y / AZ; CV = -Direction.X / AZ; }
		else                   { OutFace = EGOCubeFace::NegZ; CU = Direction.Y / AZ; CV = Direction.X / AZ; }
	}
	OutU = FMath::Clamp((CU + 1.0) * 0.5, 0.0, 1.0);
	OutV = FMath::Clamp((CV + 1.0) * 0.5, 0.0, 1.0);
}

FVector GOCubeSphere::FaceNormal(EGOCubeFace Face)
{
	return FaceUVToDirection(Face, 0.5, 0.5);
}

double GOCubeSphere::Latitude(const FVector& Direction)
{
	return FMath::Asin(FMath::Clamp(Direction.Z, -1.0, 1.0));
}

double GOCubeSphere::Longitude(const FVector& Direction)
{
	return FMath::Atan2(Direction.Y, Direction.X);
}

FGOPlanetTerrain::FGOPlanetTerrain(const FGOPlanet& InPlanet, const FGOContentDefs& Content, float InHeightScale)
	: Planet(InPlanet)
	, RadiusKm(FMath::Max(InPlanet.RadiusKm, 1.0))
	, HeightScale(InHeightScale)
	, ContinentNoise(GOSeed::Domain(InPlanet.GetTerrainSeed(), GOSeed::ContinentNoiseDomain))
	, RidgeNoise(GOSeed::Domain(InPlanet.GetTerrainSeed(), GOSeed::RidgeNoiseDomain))
	, DetailNoise(GOSeed::Domain(InPlanet.GetTerrainSeed(), GOSeed::DetailNoiseDomain))
	, MoistureNoise(GOSeed::FromBlueprint(InPlanet.Biomes.Seed))
{
	using namespace GOPlanetTerrainPrivate;

	bool bAllowed[BiomeCount];
	bool bAnyAllowed = false;
	for (int32 I = 0; I < BiomeCount; ++I)
	{
		const EGOBiome Biome = static_cast<EGOBiome>(I);
		if (const FGOBiomeDef* Def = Content.FindBiome(Biome))
		{
			Looks[I].Amplitude = Def->Amplitude;
			Looks[I].RidgeMix = Def->RidgeMix;
			Looks[I].Color = Def->GroundColor;
		}
		bAllowed[I] = Planet.Biomes.AllowedBiomes.Num() == 0 || Planet.Biomes.AllowedBiomes.Contains(Biome);
		bAnyAllowed |= bAllowed[I];
	}

	for (int32 I = 0; I < BiomeCount; ++I)
	{
		if (bAllowed[I] || !bAnyAllowed)
		{
			Fallback[I] = bAnyAllowed ? static_cast<EGOBiome>(I) : EGOBiome::RockyWaste;
			continue;
		}
		const FIntPoint From = GridCoord(static_cast<EGOBiome>(I));
		int32 Best = INDEX_NONE;
		int32 BestDistance = MAX_int32;
		for (int32 J = 0; J < BiomeCount; ++J)
		{
			if (!bAllowed[J])
			{
				continue;
			}
			const FIntPoint To = GridCoord(static_cast<EGOBiome>(J));
			const int32 Distance = FMath::Square(From.X - To.X) + FMath::Square(From.Y - To.Y);
			if (Distance < BestDistance)
			{
				BestDistance = Distance;
				Best = J;
			}
		}
		Fallback[I] = static_cast<EGOBiome>(Best);
	}
}

float FGOPlanetTerrain::TemperatureAt(const FVector& UnitDirection, float Elevation) const
{
	// SurfaceTempK is the planet-wide mean. The mean of sin^2(lat) over a sphere is 1/3, so this
	// keeps the mean while making the equator ~20 K warmer and the poles ~40 K colder (Earth-like).
	// (The Java model used the mean as the equator temperature, which froze every sea past ~17 deg.)
	const float SinLat = static_cast<float>(FMath::Clamp(UnitDirection.Z, -1.0, 1.0));
	const float Base = Planet.Biomes.SurfaceTempK + 60.f * (1.f / 3.f - SinLat * SinLat);
	// Highlands are colder: ~40 K across the full continent range.
	return Base - FMath::Max(Elevation - Planet.Biomes.SeaLevel, 0.f) * 40.f;
}

float FGOPlanetTerrain::MoistureAt(const FVector& UnitDirection, float Elevation) const
{
	// 3D noise on the direction: the Java version used (lon, lat) and had a seam at the antimeridian.
	const float Noise = MoistureNoise.Fbm3(
		static_cast<float>(UnitDirection.X) * 2.f,
		static_cast<float>(UnitDirection.Y) * 2.f,
		static_cast<float>(UnitDirection.Z) * 2.f, 4);
	// Coasts (low elevation) keep the base moisture; inland highlands dry out.
	const float Moisture = Planet.Biomes.BaseMoisture + Noise * 0.35f - FMath::Max(Elevation - Planet.Biomes.SeaLevel, 0.f) * 0.6f;
	return FMath::Clamp(Moisture, 0.f, 1.f);
}

EGOBiome FGOPlanetTerrain::ClassifyBiome(float TemperatureK, float Moisture, float Elevation) const
{
	using namespace GOPlanetTerrainPrivate;

	const FGOBiomeParams& Params = Planet.Biomes;
	if (Elevation < Params.SeaLevel)
	{
		return FilterBiome(TemperatureK > 273.f ? EGOBiome::Ocean : EGOBiome::IceSheet);
	}
	if (Elevation > Params.SnowLine)
	{
		return FilterBiome(EGOBiome::IceField);
	}
	return FilterBiome(WhittakerGrid[MoistureBand(Moisture)][TemperatureBand(TemperatureK)]);
}

FLinearColor FGOPlanetTerrain::BiomeColor(EGOBiome Biome) const
{
	return Looks[static_cast<int32>(Biome)].Color;
}

void FGOPlanetTerrain::BlendedLandShape(float TemperatureK, float Moisture, float& OutAmplitude, float& OutRidgeMix) const
{
	using namespace GOPlanetTerrainPrivate;

	int32 T0, T1, M0, M1;
	float TA, MA;
	Bracket(TemperatureCenters, TemperatureK, T0, T1, TA);
	Bracket(MoistureCenters, Moisture, M0, M1, MA);

	auto LookAt = [this](int32 M, int32 T) -> const FBiomeLook&
	{
		return Looks[static_cast<int32>(FilterBiome(WhittakerGrid[M][T]))];
	};

	const FBiomeLook& L00 = LookAt(M0, T0);
	const FBiomeLook& L01 = LookAt(M0, T1);
	const FBiomeLook& L10 = LookAt(M1, T0);
	const FBiomeLook& L11 = LookAt(M1, T1);

	const float AmpLow = FMath::Lerp(L00.Amplitude, L01.Amplitude, TA);
	const float AmpHigh = FMath::Lerp(L10.Amplitude, L11.Amplitude, TA);
	const float RidgeLow = FMath::Lerp(L00.RidgeMix, L01.RidgeMix, TA);
	const float RidgeHigh = FMath::Lerp(L10.RidgeMix, L11.RidgeMix, TA);
	OutAmplitude = FMath::Lerp(AmpLow, AmpHigh, MA);
	OutRidgeMix = FMath::Lerp(RidgeLow, RidgeHigh, MA);
}

FGOTerrainSample FGOPlanetTerrain::Sample(const FVector& UnitDirection, int32 Lod) const
{
	using namespace GOPlanetTerrainPrivate;

	const float X = static_cast<float>(UnitDirection.X);
	const float Y = static_cast<float>(UnitDirection.Y);
	const float Z = static_cast<float>(UnitDirection.Z);
	const FGOBiomeParams& Params = Planet.Biomes;

	const float Continent = ContinentNoise.DomainWarp3(X * 2.f, Y * 2.f, Z * 2.f, 0.7f, 3, 6);
	const float Ridge = RidgeNoise.RidgedFbm3(X * 8.f, Y * 8.f, Z * 8.f, 6, 2.f, 2.f);

	// Clamp so extreme continent values don't put whole ranges above the snow line.
	const float ClimateElevation = FMath::Clamp(Continent, -0.5f, 0.75f);
	const float Temperature = TemperatureAt(UnitDirection, ClimateElevation);
	const float Moisture = MoistureAt(UnitDirection, ClimateElevation);

	FGOTerrainSample Out;
	Out.Elevation = Continent;
	Out.Biome = ClassifyBiome(Temperature, Moisture, ClimateElevation);

	float LandAmplitude, LandRidgeMix;
	BlendedLandShape(Temperature, Moisture, LandAmplitude, LandRidgeMix);
	const FBiomeLook& Sea = Looks[static_cast<int32>(EGOBiome::Ocean)];

	const float Coast = FMath::SmoothStep(Params.SeaLevel - 0.02f, Params.SeaLevel + 0.02f, Continent);
	const float Amplitude = FMath::Lerp(Sea.Amplitude, LandAmplitude, Coast);
	const float RidgeMix = FMath::Lerp(Sea.RidgeMix, LandRidgeMix, Coast);
	// Mountains grow inland instead of rising straight out of the surf.
	const float Inland = FMath::SmoothStep(Params.SeaLevel, Params.SeaLevel + 0.25f, Continent);

	float Height = (Continent - Params.SeaLevel) * (1.f - RidgeMix) * Amplitude
		+ Ridge * RidgeMix * Amplitude * Inland;

	for (const FDetailOctave& Octave : DetailOctaves)
	{
		if (Lod < Octave.MinLod)
		{
			continue;
		}
		const float F = Octave.Frequency;
		const float N = Octave.bBillow
			? DetailNoise.BillowFbm3(X * F, Y * F, Z * F, Octave.Octaves)
			: DetailNoise.Fbm3(X * F, Y * F, Z * F, Octave.Octaves);
		Height += N * Amplitude * Octave.Weight;
	}

	// Worlds with seas get a flat, walkable water (or pack ice) surface at sea level.
	const bool bHasSeas = Params.SeaLevel > -0.99f;
	if (bHasSeas && Height < 0.f)
	{
		Height = 0.f;
		Out.bWater = Temperature > 273.f;
		if (Continent < Params.SeaLevel)
		{
			Out.Biome = FilterBiome(Out.bWater ? EGOBiome::Ocean : EGOBiome::IceSheet);
		}
	}

	Out.Height = Height;
	return Out;
}

double FGOPlanetTerrain::SurfaceRadiusKm(const FVector& UnitDirection, int32 Lod) const
{
	return RadiusKm + HeightToKm(Sample(UnitDirection, Lod).Height);
}

FVector FGOPlanetTerrain::SurfaceNormal(const FVector& UnitDirection, int32 Lod, double StepKm) const
{
	const FVector Up = UnitDirection.GetSafeNormal();
	const FVector Helper = FMath::Abs(Up.Z) < 0.9 ? FVector(0.0, 0.0, 1.0) : FVector(1.0, 0.0, 0.0);
	const FVector TangentA = FVector::CrossProduct(Up, Helper).GetSafeNormal();
	const FVector TangentB = FVector::CrossProduct(Up, TangentA);
	const double Angle = StepKm / RadiusKm;

	const FVector DirA = (Up + TangentA * Angle).GetSafeNormal();
	const FVector DirB = (Up + TangentB * Angle).GetSafeNormal();
	const FVector P0 = Up * SurfaceRadiusKm(Up, Lod);
	const FVector PA = DirA * SurfaceRadiusKm(DirA, Lod);
	const FVector PB = DirB * SurfaceRadiusKm(DirB, Lod);

	FVector Normal = FVector::CrossProduct(PA - P0, PB - P0).GetSafeNormal();
	if (FVector::DotProduct(Normal, Up) < 0.0)
	{
		Normal = -Normal;
	}
	return Normal;
}

FGOTerrainQuadtree::FGOTerrainQuadtree(double InRadiusKm, int32 InMaxDepth, float InSplitThreshold, float InMergeThreshold)
	: RadiusKm(FMath::Max(InRadiusKm, 1.0))
	, MaxDepth(FMath::Clamp(InMaxDepth, 0, 20))
	, SplitThreshold(InSplitThreshold)
	, MergeThreshold(FMath::Min(InMergeThreshold, InSplitThreshold))
{
}

double FGOTerrainQuadtree::ChunkArcKm(const FGOTerrainChunkId& Id) const
{
	return RadiusKm * Id.CellSize() * (UE_DOUBLE_PI * 0.5);
}

double FGOTerrainQuadtree::NearestDistanceKm(const FGOTerrainChunkId& Id, const FVector& CameraPlanetKm) const
{
	// Nearest of centre and corners: a centroid-only metric stalls the split under the camera
	// because cube-sphere cell centroids project away from the point directly below it.
	const double Us[3] = { Id.U0(), (Id.U0() + Id.U1()) * 0.5, Id.U1() };
	const double Vs[3] = { Id.V0(), (Id.V0() + Id.V1()) * 0.5, Id.V1() };
	double Best = TNumericLimits<double>::Max();
	for (int32 I = 0; I < 3; I += 2)
	{
		for (int32 J = 0; J < 3; J += 2)
		{
			const FVector P = GOCubeSphere::FaceUVToDirection(Id.Face, Us[I], Vs[J]) * RadiusKm;
			Best = FMath::Min(Best, FVector::Dist(P, CameraPlanetKm));
		}
	}
	const FVector Center = GOCubeSphere::FaceUVToDirection(Id.Face, Us[1], Vs[1]) * RadiusKm;
	return FMath::Min(Best, FVector::Dist(Center, CameraPlanetKm));
}

void FGOTerrainQuadtree::Visit(const FGOTerrainChunkId& Id, const FVector& CameraPlanetKm,
	TSet<uint64>& OutSplit, TArray<FGOTerrainChunkId>& OutLeaves) const
{
	const bool bWasSplit = SplitNodes.Contains(Id.Key());
	const double ScreenSize = ChunkArcKm(Id) / FMath::Max(NearestDistanceKm(Id, CameraPlanetKm), 1e-6);
	// Hysteresis: once split, a node only merges when it drops well below the split threshold.
	const bool bSplit = Id.Depth < MaxDepth && (bWasSplit ? ScreenSize >= MergeThreshold : ScreenSize > SplitThreshold);

	if (!bSplit)
	{
		OutLeaves.Add(Id);
		return;
	}

	OutSplit.Add(Id.Key());
	Visit(Id.Child(0, 0), CameraPlanetKm, OutSplit, OutLeaves);
	Visit(Id.Child(1, 0), CameraPlanetKm, OutSplit, OutLeaves);
	Visit(Id.Child(0, 1), CameraPlanetKm, OutSplit, OutLeaves);
	Visit(Id.Child(1, 1), CameraPlanetKm, OutSplit, OutLeaves);
}

bool FGOTerrainQuadtree::Update(const FVector& CameraPlanetKm)
{
	TSet<uint64> NewSplit;
	TArray<FGOTerrainChunkId> NewLeaves;
	NewLeaves.Reserve(Leaves.Num() + 16);
	for (int32 Face = 0; Face < 6; ++Face)
	{
		Visit(FGOTerrainChunkId(static_cast<EGOCubeFace>(Face), 0, 0, 0), CameraPlanetKm, NewSplit, NewLeaves);
	}

	const bool bChanged = NewLeaves != Leaves;
	SplitNodes = MoveTemp(NewSplit);
	Leaves = MoveTemp(NewLeaves);
	return bChanged;
}
