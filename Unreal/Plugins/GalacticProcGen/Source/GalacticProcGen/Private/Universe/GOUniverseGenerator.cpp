#include "Universe/GOUniverseGenerator.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"
#include "Universe/GOGalaxyGenerator.h"
#include "Universe/GONameGenerator.h"
#include "Universe/GOPlanetGenerator.h"
#include "Universe/GOStarSystemGenerator.h"

namespace GOUniverseGeneratorPrivate
{
	struct FCandidate
	{
		FGOStarRecord Star;
		FGOStarSystem System;
		int32 Preference = 0;	// lower is better
	};

	int32 PreferenceFor(const FGOStarSystem& System)
	{
		const bool bMainSequence = System.LuminosityClass == EGOLuminosityClass::MainSequence;
		const bool bSunLike = System.SpectralClass == EGOSpectralClass::G || System.SpectralClass == EGOSpectralClass::K;
		if (bMainSequence && bSunLike)
		{
			return 0;
		}
		return bMainSequence ? 1 : 2;
	}
}

double FGOUniverseGenerator::StartingRadiusFraction(EGOStartingRegion Region)
{
	switch (Region)
	{
	case EGOStartingRegion::Core:		return 0.07;
	case EGOStartingRegion::Frontier:	return 0.65;
	case EGOStartingRegion::InnerRim:
	default:							return 0.275;
	}
}

EGOGalaxyRegion FGOUniverseGenerator::GalaxyRegionFor(EGOStartingRegion Region)
{
	switch (Region)
	{
	case EGOStartingRegion::Core:		return EGOGalaxyRegion::Core;
	case EGOStartingRegion::Frontier:	return EGOGalaxyRegion::OuterRim;
	case EGOStartingRegion::InnerRim:
	default:							return EGOGalaxyRegion::InnerRim;
	}
}

FGOUniverseStart FGOUniverseGenerator::GenerateStart(const FGOUniverseParams& Params, TArray<FString>* OutLog) const
{
	using namespace GOUniverseGeneratorPrivate;

	auto Log = [OutLog](const FString& Line)
	{
		if (OutLog)
		{
			OutLog->Add(Line);
		}
	};

	FGOUniverseStart Start;
	Start.GalaxySeed = GOSeed::ToBlueprint(Params.Seed);
	Start.StartingRegion = Params.StartingRegion;
	Start.GalaxyConfig = FGOGalaxyConfig::Make(Params.GalaxySize, Params.GalaxyType);

	const FGONameGenerator Names(Content);
	Start.GalaxyName = Names.GalaxyName(Params.Seed);

	const FGOGalaxyGenerator Galaxy(Params.Seed, Start.GalaxyConfig);
	const FGOGalaxyConfig& Config = Galaxy.GetConfig();
	Log(FString::Printf(TEXT("Mapping %d star systems across the %s..."), Config.TargetStarCount, *Start.GalaxyName));

	// Seeded viewpoint inside the requested starting band.
	FGORandom Rng(GOSeed::Domain(Params.Seed, GOSeed::StartSelectDomain));
	const double Angle = Rng.DRand() * 2.0 * UE_DOUBLE_PI;
	const double ViewRadius = StartingRadiusFraction(Params.StartingRegion) * Config.RadiusLY;
	const FVector ViewLY(FMath::Cos(Angle) * ViewRadius, FMath::Sin(Angle) * ViewRadius, 0.0);
	const EGOGalaxyRegion TargetRegion = GalaxyRegionFor(Params.StartingRegion);

	TArray<FGOStarRecord> Nearby;
	TArray<FGOStarRecord> InRegion;
	double SearchRadius = FMath::Max(Config.ChunkSizeLY * 3.0, Config.RadiusLY * 0.05);
	const double MaxSearchRadius = Config.RadiusLY * 0.6;
	for (;;)
	{
		Galaxy.GatherStars(ViewLY, SearchRadius, Nearby);
		InRegion = Nearby.FilterByPredicate([&](const FGOStarRecord& Star)
		{
			return Galaxy.ClassifyRegion(Star.PositionLY) == TargetRegion;
		});
		if (InRegion.Num() >= Params.MinCandidatePool || SearchRadius >= MaxSearchRadius)
		{
			break;
		}
		SearchRadius *= 1.5;
	}

	TArray<FGOStarRecord> Pool = InRegion.Num() > 0 ? InRegion : Nearby;
	if (Pool.Num() == 0)
	{
		// Last resort: the core is always the densest part of the disc.
		Galaxy.GatherStars(FVector::ZeroVector, Config.RadiusLY * 0.2, Pool);
	}
	if (Pool.Num() == 0)
	{
		FGOStarRecord Synthetic;
		Synthetic.StarId = GOSeed::ToBlueprint(GOSeed::ForId(GOSeed::Domain(Params.Seed, GOSeed::StarDomain), 0));
		Synthetic.PositionLY = ViewLY;
		Synthetic.LocalDensity = 0.f;
		Synthetic.Chunk = Galaxy.ChunkOf(ViewLY);
		Pool.Add(Synthetic);
	}

	// Seeded order that does not depend on chunk iteration order.
	Pool.Sort([](const FGOStarRecord& A, const FGOStarRecord& B) { return A.StarId < B.StarId; });
	Rng.Shuffle(Pool);
	constexpr int32 MaxPool = 1024;
	if (Pool.Num() > MaxPool)
	{
		Pool.SetNum(MaxPool);
	}

	const FGOStarSystemGenerator StarGen(Params.Seed, Content);
	const FGOPlanetGenerator PlanetGen(Content);

	TArray<FCandidate> Candidates;
	Candidates.Reserve(Pool.Num());
	for (const FGOStarRecord& Star : Pool)
	{
		FCandidate& Candidate = Candidates.AddDefaulted_GetRef();
		Candidate.Star = Star;
		Candidate.System = StarGen.Generate(Star, Galaxy.ClassifyRegion(Star.PositionLY));
		Candidate.Preference = PreferenceFor(Candidate.System);
	}
	Candidates.StableSort([](const FCandidate& A, const FCandidate& B) { return A.Preference < B.Preference; });

	float BestScore = -1.f;
	int32 Examined = 0;
	for (const FCandidate& Candidate : Candidates)
	{
		if (Examined >= FMath::Max(Params.MaxCandidateSystems, 1))
		{
			break;
		}
		++Examined;

		TArray<FGOPlanet> Planets = PlanetGen.GenerateAll(Candidate.System);
		int32 BestIndex = INDEX_NONE;
		float SystemBest = -1.f;
		for (int32 Index = 0; Index < Planets.Num(); ++Index)
		{
			if (Planets[Index].HasSurface() && Planets[Index].HabitabilityScore > SystemBest)
			{
				SystemBest = Planets[Index].HabitabilityScore;
				BestIndex = Index;
			}
		}

		if (BestIndex != INDEX_NONE && SystemBest > BestScore)
		{
			BestScore = SystemBest;
			Start.HomeStar = Candidate.Star;
			Start.HomeSystem = Candidate.System;
			Start.HomePlanets = MoveTemp(Planets);
			Start.HomePlanetIndex = BestIndex;
			if (BestScore >= Params.AcceptScore)
			{
				break;
			}
		}
	}
	Start.CandidatesExamined = Examined;

	if (!Start.IsValid())
	{
		// No rocky world anywhere we looked: give the first candidate a single planet slot so the
		// game can still start. Content validation makes this practically unreachable.
		const FCandidate& Fallback = Candidates[0];
		Start.HomeStar = Fallback.Star;
		Start.HomeSystem = Fallback.System;
		if (Start.HomeSystem.Orbits.Num() == 0)
		{
			FGOOrbitalSlot& Slot = Start.HomeSystem.Orbits.AddDefaulted_GetRef();
			Slot.RadiusAU = FMath::Max(1.f, Start.HomeSystem.HabZoneInnerAU);
			Slot.Zone = EGOOrbitalZone::Habitable;
		}
		Start.HomePlanets = PlanetGen.GenerateAll(Start.HomeSystem);
		Start.HomePlanetIndex = 0;
	}

	const FGOStarSystem& Home = Start.HomeSystem;
	const FGOPlanet& HomePlanet = Start.GetHomePlanet();
	Log(FString::Printf(TEXT("Habitable zone identified around %s (%s)..."),
		*Home.Name, *GOUniverseText::SpectralClassName(Home.SpectralClass)));
	Log(FString::Printf(TEXT("%s world %s selected as origin point (%.2f g, %.0f K%s)..."),
		*GOUniverseText::PlanetTypeName(HomePlanet.Type), *HomePlanet.Name, HomePlanet.SurfaceGravityG,
		HomePlanet.GetSurfaceTempK(), HomePlanet.Atmosphere.bBreathable ? TEXT(", breathable") : TEXT("")));
	return Start;
}
