#include "Universe/GOGalaxyGenerator.h"
#include "Core/GORandom.h"

FGOGalaxyGenerator::FGOGalaxyGenerator(uint64 InGalaxySeed, const FGOGalaxyConfig& InConfig)
	: GalaxySeed(InGalaxySeed)
	, StarDomainSeed(GOSeed::Domain(InGalaxySeed, GOSeed::StarDomain))
	, Config(InConfig)
	, ShapeNoise(GOSeed::Domain(InGalaxySeed, GOSeed::GalaxyShapeDomain))
{
	Config.ChunkSizeLY = FMath::Max(Config.ChunkSizeLY, 1.0);
	Config.RadiusLY = FMath::Max(Config.RadiusLY, Config.ChunkSizeLY);
	Config.ArmCount = FMath::Max(Config.ArmCount, 1);
	Config.ArmWidth = FMath::Max(Config.ArmWidth, 0.01f);
	Config.TargetStarCount = FMath::Max(Config.TargetStarCount, 1);

	// Normalise chunk star counts by the mean density of this particular galaxy shape so the
	// whole disc holds ~TargetStarCount stars whatever the galaxy type.
	constexpr int32 Samples = 64;
	double Sum = 0.0;
	int32 Count = 0;
	for (int32 Y = 0; Y < Samples; ++Y)
	{
		for (int32 X = 0; X < Samples; ++X)
		{
			const float NX = ((X + 0.5f) / Samples) * 2.f - 1.f;
			const float NY = ((Y + 0.5f) / Samples) * 2.f - 1.f;
			if (NX * NX + NY * NY <= 1.f)
			{
				Sum += DensityAt(NX, NY);
				++Count;
			}
		}
	}
	MeanDensity = FMath::Max(Count > 0 ? static_cast<float>(Sum / Count) : 0.25f, 0.01f);
}

float FGOGalaxyGenerator::ArmDensity(float R, float Angle, int32 ArmCount, float MinRadius) const
{
	// Phase shift so arm 0 crosses angle 0 at the reference radius 0.5.
	const float PhaseRef = Config.ArmWindingAngle * FMath::Loge(0.5f + 0.1f);
	const float TwoSigmaSq = 2.f * Config.ArmWidth * Config.ArmWidth;

	float MaxDensity = 0.f;
	for (int32 Arm = 0; Arm < ArmCount; ++Arm)
	{
		const float ArmOffset = Arm * (UE_TWO_PI / ArmCount);
		const float SpiralAngle = Config.ArmWindingAngle * FMath::Loge(R + 0.1f) - PhaseRef + ArmOffset;
		const float AngleDiff = FMath::Abs(FMath::UnwindRadians(Angle - SpiralAngle));
		const float Density = FMath::Exp(-AngleDiff * AngleDiff / TwoSigmaSq) * (1.f - R * 0.5f);
		MaxDensity = FMath::Max(MaxDensity, Density);
	}

	if (MinRadius > 0.f)
	{
		// Arms of a barred spiral start at the bar tips.
		MaxDensity *= FMath::SmoothStep(MinRadius * 0.6f, MinRadius, R);
	}
	return MaxDensity;
}

float FGOGalaxyGenerator::DensityAt(float NX, float NY) const
{
	const float R = FMath::Sqrt(NX * NX + NY * NY);
	if (R > 1.f)
	{
		return 0.f;
	}

	const float Angle = FMath::Atan2(NY, NX);
	// Compact central bulge; a wider one saturates the inner third of the disc and hides the arms.
	const float Bulge = Config.CoreDensityFactor * 0.5f * FMath::Exp(-R * 9.f);

	float Density = 0.f;
	switch (Config.Type)
	{
	case EGOGalaxyType::BarredSpiral:
	{
		constexpr float BarHalfLength = 0.3f;
		constexpr float BarWidth = 0.06f;
		const float Along = FMath::Abs(NX);
		const float Across = FMath::Abs(NY);
		const float Overshoot = FMath::Max(Along - BarHalfLength, 0.f);
		const float Bar = FMath::Exp(-(Across * Across + Overshoot * Overshoot) / (2.f * BarWidth * BarWidth));
		Density = FMath::Max(ArmDensity(R, Angle, Config.ArmCount, BarHalfLength), 0.9f * Bar) + 0.6f * Bulge;
		break;
	}
	case EGOGalaxyType::Elliptical:
		Density = 0.9f * FMath::Exp(-R * 2.5f) + 0.1f * (1.f - R) + 0.3f * Bulge;
		break;
	case EGOGalaxyType::Irregular:
	{
		const float Clumps = ShapeNoise.Fbm2(NX * 2.f + 5.3f, NY * 2.f - 1.7f, 4);
		Density = FMath::Clamp(0.35f + 0.9f * Clumps, 0.f, 1.f) * (1.f - R * R) + 0.25f * FMath::Exp(-R * 6.f);
		break;
	}
	case EGOGalaxyType::Spiral:
	default:
		Density = ArmDensity(R, Angle, Config.ArmCount, 0.f) + Bulge;
		break;
	}

	Density *= 0.85f + 0.3f * ShapeNoise.Fbm2(NX * 3.f, NY * 3.f, 3);
	return FMath::Clamp(Density, 0.f, 1.f);
}

EGOGalaxyRegion FGOGalaxyGenerator::ClassifyRegion(const FVector& PositionLY) const
{
	const double R = FMath::Sqrt(PositionLY.X * PositionLY.X + PositionLY.Y * PositionLY.Y) / Config.RadiusLY;
	if (R > 1.0)
	{
		return EGOGalaxyRegion::Void;
	}
	if (R < 0.1)
	{
		return EGOGalaxyRegion::Core;
	}
	if (R < 0.4)
	{
		return EGOGalaxyRegion::InnerRim;
	}
	return EGOGalaxyRegion::OuterRim;
}

FIntPoint FGOGalaxyGenerator::ChunkOf(const FVector& PositionLY) const
{
	return FIntPoint(
		FMath::FloorToInt(PositionLY.X / Config.ChunkSizeLY),
		FMath::FloorToInt(PositionLY.Y / Config.ChunkSizeLY));
}

void FGOGalaxyGenerator::GenerateChunk(const FIntPoint& Chunk, TArray<FGOStarRecord>& OutStars) const
{
	OutStars.Reset();

	const double ChunkSize = Config.ChunkSizeLY;
	const double Radius = Config.RadiusLY;
	const double OriginX = Chunk.X * ChunkSize;
	const double OriginY = Chunk.Y * ChunkSize;

	// Sample the density field on a 3x3 lattice inside the chunk: the mean drives how many
	// stars the chunk holds, the max normalises per-point acceptance below.
	float DensitySum = 0.f;
	float DensityMax = 0.f;
	for (int32 SY = 0; SY < 3; ++SY)
	{
		for (int32 SX = 0; SX < 3; ++SX)
		{
			const float NX = static_cast<float>((OriginX + ChunkSize * (SX + 0.5) / 3.0) / Radius);
			const float NY = static_cast<float>((OriginY + ChunkSize * (SY + 0.5) / 3.0) / Radius);
			const float D = DensityAt(NX, NY);
			DensitySum += D;
			DensityMax = FMath::Max(DensityMax, D);
		}
	}
	if (DensityMax <= 0.f)
	{
		return;
	}

	const uint64 ChunkSeed = GOSeed::ForCell(StarDomainSeed, Chunk.X, Chunk.Y);
	FGORandom Rng(ChunkSeed);

	const double ChunkArea = ChunkSize * ChunkSize;
	const double GalaxyArea = UE_DOUBLE_PI * Radius * Radius;
	const double Expected = Config.TargetStarCount * (ChunkArea / GalaxyArea) * (DensitySum / 9.0) / MeanDensity;
	const int32 WholeStars = FMath::FloorToInt(Expected);
	const int32 TargetCount = WholeStars + (Rng.DRand() < Expected - WholeStars ? 1 : 0);
	if (TargetCount <= 0)
	{
		return;
	}

	OutStars.Reserve(TargetCount);
	const int32 MaxAttempts = TargetCount * 20;
	for (int32 Attempt = 0; Attempt < MaxAttempts && OutStars.Num() < TargetCount; ++Attempt)
	{
		const double WorldX = OriginX + Rng.DRand() * ChunkSize;
		const double WorldY = OriginY + Rng.DRand() * ChunkSize;
		const float NX = static_cast<float>(WorldX / Radius);
		const float NY = static_cast<float>(WorldY / Radius);
		const float R = FMath::Sqrt(NX * NX + NY * NY);
		if (R > 1.f)
		{
			continue;
		}

		const float D = DensityAt(NX, NY);
		if (Rng.FRand() * DensityMax > D)
		{
			continue;
		}

		// Thin disc that thickens toward the core.
		const float ZScale = 0.02f + 0.01f * (1.f - R);

		FGOStarRecord& Star = OutStars.AddDefaulted_GetRef();
		Star.StarId = GOSeed::ToBlueprint(GOSeed::ForId(ChunkSeed, static_cast<uint64>(OutStars.Num())));
		Star.PositionLY = FVector(WorldX, WorldY, Rng.Gaussian() * ZScale * Radius);
		Star.LocalDensity = D;
		Star.Chunk = Chunk;
	}
}

void FGOGalaxyGenerator::GatherStars(const FVector& CenterLY, double RadiusLY, TArray<FGOStarRecord>& OutStars) const
{
	OutStars.Reset();

	const double ChunkSize = Config.ChunkSizeLY;
	const int32 MinX = FMath::FloorToInt((CenterLY.X - RadiusLY) / ChunkSize);
	const int32 MaxX = FMath::FloorToInt((CenterLY.X + RadiusLY) / ChunkSize);
	const int32 MinY = FMath::FloorToInt((CenterLY.Y - RadiusLY) / ChunkSize);
	const int32 MaxY = FMath::FloorToInt((CenterLY.Y + RadiusLY) / ChunkSize);
	const double RadiusSq = RadiusLY * RadiusLY;

	TArray<FGOStarRecord> ChunkStars;
	for (int32 CY = MinY; CY <= MaxY; ++CY)
	{
		for (int32 CX = MinX; CX <= MaxX; ++CX)
		{
			GenerateChunk(FIntPoint(CX, CY), ChunkStars);
			for (const FGOStarRecord& Star : ChunkStars)
			{
				const double DX = Star.PositionLY.X - CenterLY.X;
				const double DY = Star.PositionLY.Y - CenterLY.Y;
				if (DX * DX + DY * DY <= RadiusSq)
				{
					OutStars.Add(Star);
				}
			}
		}
	}
}
