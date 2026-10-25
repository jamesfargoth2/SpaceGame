#include "Universe/GOStarSystemGenerator.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"
#include "Universe/GONameGenerator.h"

namespace GOStarSystemGeneratorPrivate
{
	constexpr float UniverseAgeGyr = 13.f;
	constexpr float BinaryChance = 0.3f;
}

float GOStellar::MainSequenceLifetimeGyr(float MassSolar)
{
	return MassSolar > 0.f ? 10.f * FMath::Pow(MassSolar, -2.5f) : TNumericLimits<float>::Max();
}

float GOStellar::MainSequenceLuminosity(float MassSolar)
{
	if (MassSolar < 0.43f)
	{
		return 0.23f * FMath::Pow(MassSolar, 2.3f);
	}
	if (MassSolar < 2.f)
	{
		return FMath::Pow(MassSolar, 4.f);
	}
	return 1.4f * FMath::Pow(MassSolar, 3.5f);
}

float GOStellar::MainSequenceRadius(float MassSolar)
{
	return MassSolar < 1.f ? FMath::Pow(MassSolar, 0.8f) : FMath::Pow(MassSolar, 0.57f);
}

EGOSpectralClass GOStellar::SpectralClassFromMass(float MassSolar)
{
	if (MassSolar >= 16.f)	return EGOSpectralClass::O;
	if (MassSolar >= 2.1f)	return EGOSpectralClass::B;
	if (MassSolar >= 1.4f)	return EGOSpectralClass::A;
	if (MassSolar >= 1.04f)	return EGOSpectralClass::F;
	if (MassSolar >= 0.8f)	return EGOSpectralClass::G;
	if (MassSolar >= 0.45f)	return EGOSpectralClass::K;
	return EGOSpectralClass::M;
}

FLinearColor GOStellar::ColorFromTemperature(float TemperatureK)
{
	const float Temp = TemperatureK / 100.f;
	float R, G, B;

	if (Temp <= 66.f)
	{
		R = 255.f;
		G = 99.4708025861f * FMath::Loge(Temp) - 161.1195681661f;
	}
	else
	{
		R = 329.698727446f * FMath::Pow(Temp - 60.f, -0.1332047592f);
		G = 288.1221695283f * FMath::Pow(Temp - 60.f, -0.0755148492f);
	}

	if (Temp >= 66.f)
	{
		B = 255.f;
	}
	else if (Temp <= 19.f)
	{
		B = 0.f;
	}
	else
	{
		B = 138.5177312231f * FMath::Loge(Temp - 10.f) - 305.0447927307f;
	}

	return FLinearColor(
		FMath::Clamp(R, 0.f, 255.f) / 255.f,
		FMath::Clamp(G, 0.f, 255.f) / 255.f,
		FMath::Clamp(B, 0.f, 255.f) / 255.f,
		1.f);
}

FGOStarSystemGenerator::FGOStarSystemGenerator(uint64 InGalaxySeed, const FGOContentDefs& InContent)
	: StarDomainSeed(GOSeed::Domain(InGalaxySeed, GOSeed::StarDomain))
	, Content(InContent)
{
}

EGOSpectralClass FGOStarSystemGenerator::RollSpectralClass(FGORandom& Rng, EGOGalaxyRegion Region) const
{
	// Dense cores skew hot and young, the rim skews toward cool red dwarfs.
	float Modifier = 0.f;
	switch (Region)
	{
	case EGOGalaxyRegion::Core:		Modifier = -0.08f; break;
	case EGOGalaxyRegion::OuterRim:	Modifier = 0.05f; break;
	case EGOGalaxyRegion::Void:		Modifier = 0.10f; break;
	default: break;
	}

	float Total = 0.f;
	for (const FGOSpectralClassDef& Def : Content.SpectralClasses)
	{
		Total += FMath::Max(Def.Frequency, 0.f);
	}
	if (Total <= 0.f)
	{
		Rng.NextU64();
		return EGOSpectralClass::G;
	}

	// Classes are listed hot to cool; shifting the roll moves probability mass along that order.
	const float Roll = FMath::Clamp(Rng.FRand() + Modifier, 0.f, 0.9999f) * Total;
	float Cumulative = 0.f;
	for (const FGOSpectralClassDef& Def : Content.SpectralClasses)
	{
		Cumulative += FMath::Max(Def.Frequency, 0.f);
		if (Roll < Cumulative)
		{
			return Def.Class;
		}
	}
	return Content.SpectralClasses.Last().Class;
}

FGOStarSystem FGOStarSystemGenerator::Generate(const FGOStarRecord& Star, EGOGalaxyRegion Region) const
{
	using namespace GOStarSystemGeneratorPrivate;

	const uint64 StarSeed = GOSeed::ForId(StarDomainSeed, GOSeed::FromBlueprint(Star.StarId));
	FGORandom Rng(StarSeed);

	FGOStarSystem System;
	System.StarId = Star.StarId;
	System.Seed = GOSeed::ToBlueprint(StarSeed);
	System.PositionLY = Star.PositionLY;
	System.Region = Region;
	System.SpectralClass = RollSpectralClass(Rng, Region);

	const FGOSpectralClassDef* Def = Content.FindSpectralClass(System.SpectralClass);
	const float MassMin = Def ? Def->MassMin : 0.8f;
	const float MassMax = Def ? Def->MassMax : 1.04f;
	System.MassSolar = Rng.FRandRange(MassMin, MassMax);

	const float Lifetime = FMath::Min(GOStellar::MainSequenceLifetimeGyr(System.MassSolar), UniverseAgeGyr);
	float Age = Rng.FRandRange(0.1f, UniverseAgeGyr);

	if (Age <= GOStellar::MainSequenceLifetimeGyr(System.MassSolar))
	{
		System.LuminosityClass = EGOLuminosityClass::MainSequence;
	}
	else if (System.MassSolar > 8.f)
	{
		System.LuminosityClass = EGOLuminosityClass::Supergiant;
	}
	else if (System.MassSolar > 0.5f)
	{
		System.LuminosityClass = EGOLuminosityClass::Giant;
	}
	else
	{
		System.LuminosityClass = EGOLuminosityClass::WhiteDwarf;
	}

	if (System.LuminosityClass == EGOLuminosityClass::MainSequence)
	{
		Age = FMath::Min(Age, Lifetime);
	}
	System.AgeGyr = Age;

	const float TempMin = Def ? Def->TempMinK : 5200.f;
	const float TempMax = Def ? Def->TempMaxK : 6000.f;
	System.TemperatureK = Rng.FRandRange(TempMin, TempMax);

	const float MsLuminosity = GOStellar::MainSequenceLuminosity(System.MassSolar);
	const float MsRadius = GOStellar::MainSequenceRadius(System.MassSolar);
	switch (System.LuminosityClass)
	{
	case EGOLuminosityClass::Giant:
		System.Luminosity = MsLuminosity * Rng.FRandRange(50.f, 200.f);
		System.RadiusSolar = MsRadius * Rng.FRandRange(10.f, 100.f);
		break;
	case EGOLuminosityClass::Supergiant:
		System.Luminosity = MsLuminosity * Rng.FRandRange(1000.f, 10000.f);
		System.RadiusSolar = MsRadius * Rng.FRandRange(100.f, 1500.f);
		break;
	case EGOLuminosityClass::WhiteDwarf:
		System.Luminosity = 0.001f + System.MassSolar * 0.005f;
		System.RadiusSolar = Rng.FRandRange(0.008f, 0.02f);
		break;
	default:
		System.Luminosity = MsLuminosity;
		System.RadiusSolar = MsRadius;
		break;
	}

	System.Color = GOStellar::ColorFromTemperature(System.TemperatureK);

	const float SqrtLum = FMath::Sqrt(System.Luminosity);
	System.HabZoneInnerAU = SqrtLum * 0.75f;
	System.HabZoneOuterAU = SqrtLum * 1.77f;
	System.FrostLineAU = SqrtLum * 4.85f;
	System.SystemEdgeAU = SqrtLum * 40.f;

	if (Rng.FRand() < BinaryChance)
	{
		FGOCompanionStar& Companion = System.Companion;
		Companion.MassSolar = System.MassSolar * Rng.FRandRange(0.1f, 1.f);
		// Log-uniform separation between 0.1 and 100 AU.
		Companion.SeparationAU = 0.1f * FMath::Pow(1000.f, Rng.FRand());
		Companion.SpectralClass = GOStellar::SpectralClassFromMass(Companion.MassSolar);
		const FGOSpectralClassDef* CompanionDef = Content.FindSpectralClass(Companion.SpectralClass);
		Companion.TemperatureK = CompanionDef
			? Rng.FRandRange(CompanionDef->TempMinK, CompanionDef->TempMaxK)
			: Rng.FRandRange(2400.f, 3700.f);
		Companion.Luminosity = GOStellar::MainSequenceLuminosity(Companion.MassSolar);
		Companion.Color = GOStellar::ColorFromTemperature(Companion.TemperatureK);
		System.bHasCompanion = true;
	}

	System.Name = FGONameGenerator(Content).StarName(StarSeed);
	GenerateOrbits(System);
	return System;
}

void FGOStarSystemGenerator::GenerateOrbits(FGOStarSystem& System) const
{
	FGORandom Rng(GOSeed::Domain(GOSeed::FromBlueprint(System.Seed), GOSeed::OrbitDomain));

	const FGOSpectralClassDef* Def = Content.FindSpectralClass(System.SpectralClass);
	const int32 PlanetCount = Def ? Rng.RandRange(Def->PlanetCountMin, Def->PlanetCountMax) : Rng.RandRange(2, 6);

	// A close companion clears out the inner system.
	const float InnerLimitAU = System.bHasCompanion ? System.Companion.SeparationAU * 0.33f : 0.f;
	const float BaseAU = 0.2f + Rng.FRand() * 0.2f;

	System.Orbits.Reset();
	float PreviousRadius = 0.f;
	for (int32 Index = 0; Index < PlanetCount; ++Index)
	{
		const float Step = 1.4f + Rng.FRand() * 0.8f;
		const float Jitter = 1.f + Rng.FRand() * 0.3f;
		float Radius = Index == 0 ? BaseAU * Jitter : PreviousRadius * Step * Jitter;
		Radius = FMath::Max(Radius, InnerLimitAU);

		FGOOrbitalSlot& Slot = System.Orbits.AddDefaulted_GetRef();
		Slot.Index = Index;
		Slot.RadiusAU = Radius;
		Slot.Eccentricity = Rng.FRand() * 0.3f;
		Slot.PeriodYears = FMath::Sqrt(Radius * Radius * Radius / FMath::Max(System.MassSolar, 0.01f));

		if (Radius < System.HabZoneInnerAU)
		{
			Slot.Zone = EGOOrbitalZone::Inner;
		}
		else if (Radius <= System.HabZoneOuterAU)
		{
			Slot.Zone = EGOOrbitalZone::Habitable;
		}
		else if (Radius <= System.FrostLineAU * 3.f)
		{
			Slot.Zone = EGOOrbitalZone::Outer;
		}
		else
		{
			Slot.Zone = EGOOrbitalZone::Deep;
		}

		PreviousRadius = Radius;
	}
}
