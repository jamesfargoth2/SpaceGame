#include "Runtime/GOUniverseSubsystem.h"

#include "Async/Async.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"
#include "GalacticProcGenModule.h"
#include "HAL/PlatformTime.h"
#include "Misc/DateTime.h"
#include "Runtime/GOContentLoader.h"
#include "Runtime/GOUniverseSettings.h"
#include "Universe/GOGalaxyGenerator.h"
#include "Universe/GOPlanetGenerator.h"
#include "Universe/GOStarSystemGenerator.h"

void UGOUniverseSubsystem::Initialize(FSubsystemCollectionBase& Collection)
{
	Super::Initialize(Collection);

	const UGOUniverseSettings* Settings = GetDefault<UGOUniverseSettings>();
	TSharedPtr<FGOContentDefs, ESPMode::ThreadSafe> Loaded = MakeShared<FGOContentDefs, ESPMode::ThreadSafe>();
	TArray<FString> Messages;
	const bool bValid = GOContentLoader::Load(*Settings, *Loaded, Messages);
	for (const FString& Message : Messages)
	{
		if (bValid)
		{
			UE_LOG(LogGalacticProcGen, Warning, TEXT("%s"), *Message);
		}
		else
		{
			UE_LOG(LogGalacticProcGen, Error, TEXT("%s"), *Message);
		}
	}
	Content = Loaded;

	Seed = Settings->bRandomSeedEachSession
		? GOSeed::Mix(FPlatformTime::Cycles64() ^ static_cast<uint64>(FDateTime::Now().GetTicks()))
		: GOSeed::FromText(Settings->SeedText);

	UE_LOG(LogGalacticProcGen, Log, TEXT("Universe content: %d spectral classes, %d planet types, %d hub rooms, %d kit assets."),
		Content->SpectralClasses.Num(), Content->PlanetTypes.Num(), Content->HubRooms.Num(), Content->AssetKit.Num());
}

void UGOUniverseSubsystem::Deinitialize()
{
	Galaxy.Reset();
	ChunkCache.Reset();
	Super::Deinitialize();
}

void UGOUniverseSubsystem::SetSeedFromText(const FString& SeedText)
{
	SetSeed(GOSeed::ToBlueprint(GOSeed::FromText(SeedText)));
}

void UGOUniverseSubsystem::SetSeed(int64 NewSeed)
{
	Seed = GOSeed::FromBlueprint(NewSeed);
}

FGOStartScenarioParams UGOUniverseSubsystem::MakeParams() const
{
	return GetDefault<UGOUniverseSettings>()->MakeScenarioParams(Seed);
}

const FGOStartScenario& UGOUniverseSubsystem::GenerateScenario()
{
	if (bHasScenario && ScenarioSeed == Seed)
	{
		return Scenario;
	}

	const double StartTime = FPlatformTime::Seconds();
	Scenario = GOStartScenario::Generate(*Content, MakeParams());
	ScenarioSeed = Seed;
	bHasScenario = true;

	for (const FString& Line : Scenario.Log)
	{
		UE_LOG(LogGalacticProcGen, Display, TEXT("%s"), *Line);
	}
	UE_LOG(LogGalacticProcGen, Log, TEXT("Scenario generated in %.1f ms."), (FPlatformTime::Seconds() - StartTime) * 1000.0);
	return Scenario;
}

void UGOUniverseSubsystem::GenerateScenarioAsync()
{
	if (bAsyncInFlight)
	{
		return;
	}
	if (bHasScenario && ScenarioSeed == Seed)
	{
		OnScenarioReady.Broadcast(Scenario);
		return;
	}

	bAsyncInFlight = true;
	const FGOStartScenarioParams Params = MakeParams();
	const uint64 RequestedSeed = Seed;
	TSharedPtr<const FGOContentDefs, ESPMode::ThreadSafe> SharedContent = Content;
	TWeakObjectPtr<UGOUniverseSubsystem> WeakThis(this);

	Async(EAsyncExecution::ThreadPool, [Params, RequestedSeed, SharedContent, WeakThis]()
	{
		FGOStartScenario Result = GOStartScenario::Generate(*SharedContent, Params);
		AsyncTask(ENamedThreads::GameThread, [WeakThis, RequestedSeed, Result = MoveTemp(Result)]() mutable
		{
			UGOUniverseSubsystem* Self = WeakThis.Get();
			if (!Self)
			{
				return;
			}
			Self->bAsyncInFlight = false;
			Self->Scenario = MoveTemp(Result);
			Self->ScenarioSeed = RequestedSeed;
			Self->bHasScenario = true;
			Self->OnScenarioReady.Broadcast(Self->Scenario);
		});
	});
}

void UGOUniverseSubsystem::EnsureGalaxy()
{
	if (Galaxy.IsValid() && GalaxySeed == Seed)
	{
		return;
	}
	const UGOUniverseSettings* Settings = GetDefault<UGOUniverseSettings>();
	Galaxy = MakeShared<FGOGalaxyGenerator, ESPMode::ThreadSafe>(Seed, FGOGalaxyConfig::Make(Settings->GalaxySize, Settings->GalaxyType));
	GalaxySeed = Seed;
	ChunkCache.Reset();
}

TArray<FGOStarRecord> UGOUniverseSubsystem::GetStarsNear(FVector PositionLY, double RadiusLY)
{
	EnsureGalaxy();

	constexpr int32 MaxCachedChunks = 1024;
	if (ChunkCache.Num() > MaxCachedChunks)
	{
		ChunkCache.Reset();
	}

	const double ChunkSize = Galaxy->GetConfig().ChunkSizeLY;
	const int32 MinX = FMath::FloorToInt((PositionLY.X - RadiusLY) / ChunkSize);
	const int32 MaxX = FMath::FloorToInt((PositionLY.X + RadiusLY) / ChunkSize);
	const int32 MinY = FMath::FloorToInt((PositionLY.Y - RadiusLY) / ChunkSize);
	const int32 MaxY = FMath::FloorToInt((PositionLY.Y + RadiusLY) / ChunkSize);

	TArray<FGOStarRecord> Result;
	for (int32 CY = MinY; CY <= MaxY; ++CY)
	{
		for (int32 CX = MinX; CX <= MaxX; ++CX)
		{
			const FIntPoint Key(CX, CY);
			TArray<FGOStarRecord>* Stars = ChunkCache.Find(Key);
			if (!Stars)
			{
				Stars = &ChunkCache.Add(Key);
				Galaxy->GenerateChunk(Key, *Stars);
			}
			for (const FGOStarRecord& Star : *Stars)
			{
				const double DX = Star.PositionLY.X - PositionLY.X;
				const double DY = Star.PositionLY.Y - PositionLY.Y;
				if (DX * DX + DY * DY <= RadiusLY * RadiusLY)
				{
					Result.Add(Star);
				}
			}
		}
	}
	return Result;
}

FGOStarSystem UGOUniverseSubsystem::GetStarSystem(const FGOStarRecord& Star)
{
	EnsureGalaxy();
	return FGOStarSystemGenerator(Seed, *Content).Generate(Star, Galaxy->ClassifyRegion(Star.PositionLY));
}

TArray<FGOPlanet> UGOUniverseSubsystem::GetPlanets(const FGOStarSystem& System) const
{
	return FGOPlanetGenerator(*Content).GenerateAll(System);
}
