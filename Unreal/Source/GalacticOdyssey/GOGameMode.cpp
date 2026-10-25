#include "GOGameMode.h"

#include "Core/GOContentDefs.h"
#include "Engine/GameInstance.h"
#include "Engine/World.h"
#include "GameFramework/PlayerStart.h"
#include "GameFramework/WorldSettings.h"
#include "GODebugHUD.h"
#include "GOExplorerCharacter.h"
#include "Kismet/GameplayStatics.h"
#include "Runtime/GOAcademyHubActor.h"
#include "Runtime/GOPlanetActor.h"
#include "Runtime/GOUniverseSettings.h"
#include "Runtime/GOUniverseSubsystem.h"

DEFINE_LOG_CATEGORY_STATIC(LogGOGameMode, Log, All);

namespace GOGameModePrivate
{
	constexpr double CmPerKm = 100000.0;
}

AGOGameMode::AGOGameMode()
{
	DefaultPawnClass = AGOExplorerCharacter::StaticClass();
	HUDClass = AGODebugHUD::StaticClass();
}

void AGOGameMode::InitGame(const FString& MapName, const FString& Options, FString& ErrorMessage)
{
	Super::InitGame(MapName, Options, ErrorMessage);

	UGOUniverseSubsystem* Universe = GetGameInstance() ? GetGameInstance()->GetSubsystem<UGOUniverseSubsystem>() : nullptr;
	if (!Universe)
	{
		ErrorMessage = TEXT("UGOUniverseSubsystem is unavailable.");
		return;
	}

	const FString SeedOption = UGameplayStatics::ParseOption(Options, TEXT("Seed"));
	if (!SeedOption.IsEmpty())
	{
		Universe->SetSeedFromText(SeedOption);
	}

	const UGOUniverseSettings* Settings = GetDefault<UGOUniverseSettings>();
	const FString StartOption = UGameplayStatics::ParseOption(Options, TEXT("Start"));
	const bool bStartOnSurface = StartOption.IsEmpty()
		? Settings->StartLocation == EGOStartLocation::PlanetSurface
		: StartOption.Equals(TEXT("Surface"), ESearchCase::IgnoreCase);

	// The start point is near the origin and the planet centre is thousands of km away: turn off
	// the legacy world-bounds / kill-Z checks, which assume a ~20 km world.
	if (AWorldSettings* WorldSettings = GetWorldSettings())
	{
		WorldSettings->bEnableWorldBoundsChecks = false;
		WorldSettings->KillZ = -1.0e12f;
	}

	const FGOStartScenario& Scenario = Universe->GenerateScenario();
	if (!Scenario.IsValid())
	{
		ErrorMessage = TEXT("Universe generation failed; see LogGalacticProcGen.");
		return;
	}
	BuildUniverse(Scenario, bStartOnSurface);
}

void AGOGameMode::BuildUniverse(const FGOStartScenario& Scenario, bool bStartOnSurface)
{
	using namespace GOGameModePrivate;

	UWorld* World = GetWorld();
	const UGOUniverseSettings* Settings = GetDefault<UGOUniverseSettings>();
	UGOUniverseSubsystem* Universe = GetGameInstance()->GetSubsystem<UGOUniverseSubsystem>();
	const FGOPlanet& Home = Scenario.Universe.GetHomePlanet();

	// Rotate the planet so the landing site's "up" is world +Z, then shift it so the chosen start
	// (the orbiting academy or the landing site) sits at the world origin.
	const FVector Landing = Scenario.LandingDirection.GetSafeNormal();
	const double AnchorKm = bStartOnSurface ? Scenario.LandingRadiusKm : Scenario.HubOrbitRadiusKm;
	const FQuat PlanetRotation = FQuat::FindBetweenNormals(Landing, FVector::UpVector);
	const FVector PlanetLocation = -PlanetRotation.RotateVector(Landing * AnchorKm * CmPerKm);

	FActorSpawnParameters Params;
	Params.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AlwaysSpawn;

	UClass* PlanetType = PlanetClass ? PlanetClass.Get() : AGOPlanetActor::StaticClass();
	Planet = World->SpawnActor<AGOPlanetActor>(PlanetType, FTransform(PlanetRotation, PlanetLocation), Params);
	if (!Planet)
	{
		UE_LOG(LogGOGameMode, Error, TEXT("Could not spawn the planet actor (%s)."), *GetNameSafe(PlanetType));
		return;
	}
	Planet->SurfaceMaterial = Settings->PlanetSurfaceMaterial;
	Planet->GreyboxMaterial = Settings->GreyboxMaterial;
	Planet->bSpawnAtmosphere = Settings->bSpawnAtmosphere;
	Planet->SunIlluminanceLux = Settings->SunIlluminanceLux;
	Planet->DayNightTimeScale = Settings->DayNightTimeScale;
	Planet->SplitThreshold = Settings->SplitThreshold;
	Planet->MergeThreshold = Settings->MergeThreshold;
	Planet->CollisionMinDepth = Settings->CollisionMinDepth;
	Planet->MaxBuildsInFlight = Settings->MaxChunkBuildsInFlight;
	Planet->MaxUploadsPerFrame = Settings->MaxChunkUploadsPerFrame;
	Planet->MeshSettings.GridSize = Settings->TerrainGridSize;
	Planet->MeshSettings.ScatterMinDepth = Settings->ScatterMinDepth;
	Planet->MeshSettings.ScatterDensityFalloff = Settings->ScatterDensityFalloff;
	Planet->MeshSettings.MaxScatterPerChunk = Settings->MaxScatterPerChunk;
	Planet->InitializePlanet(Universe->GetContent(), Home, Scenario.Universe.HomeSystem, Landing, Scenario.TerrainMaxDepth,
		Scenario.TerrainHeightScale);

	// The academy orbits directly above the landing site, so its floor faces the planet.
	const FVector HubLocation = PlanetLocation + PlanetRotation.RotateVector(Landing * Scenario.HubOrbitRadiusKm * CmPerKm);
	UClass* AcademyType = AcademyClass ? AcademyClass.Get() : AGOAcademyHubActor::StaticClass();
	Academy = World->SpawnActor<AGOAcademyHubActor>(AcademyType, FTransform(FQuat::Identity, HubLocation), Params);
	if (!Academy)
	{
		UE_LOG(LogGOGameMode, Error, TEXT("Could not spawn the academy actor (%s)."), *GetNameSafe(AcademyType));
		return;
	}
	Academy->GreyboxMaterial = Settings->GreyboxMaterial;
	Academy->bSpawnNpcStandIns = Settings->bSpawnNpcStandIns;
	Academy->MaxLights = Settings->MaxHubLights;
	Academy->BuildFromLayout(Scenario.Academy, *Universe->GetContent());

	FTransform Start;
	if (bStartOnSurface)
	{
		Start = FTransform(FRotator::ZeroRotator, Planet->GetSurfacePointWorld(Landing) + FVector(0.0, 0.0, 200.0));
	}
	else
	{
		Start = Academy->GetSpawnWorldTransform(EGOHubSpawnRole::Player);
		Start.AddToTranslation(FVector(0.0, 0.0, 100.0));
	}

	// Build the terrain the first camera will see before anyone spawns on it.
	Planet->PrewarmForCamera(Start.GetLocation());
	StartSpot = World->SpawnActor<APlayerStart>(APlayerStart::StaticClass(), Start, Params);
}

AActor* AGOGameMode::ChoosePlayerStart_Implementation(AController* Player)
{
	return StartSpot ? StartSpot.Get() : Super::ChoosePlayerStart_Implementation(Player);
}
