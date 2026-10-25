#include "GODebugHUD.h"

#include "Engine/Engine.h"
#include "Engine/GameInstance.h"
#include "Engine/World.h"
#include "GameFramework/Pawn.h"
#include "GameFramework/PlayerController.h"
#include "GOGameMode.h"
#include "Runtime/GOPlanetActor.h"
#include "Runtime/GOUniverseSubsystem.h"

void AGODebugHUD::DrawHUD()
{
	Super::DrawHUD();

	const UWorld* World = GetWorld();
	const UGameInstance* GameInstance = World ? World->GetGameInstance() : nullptr;
	const UGOUniverseSubsystem* Universe = GameInstance ? GameInstance->GetSubsystem<UGOUniverseSubsystem>() : nullptr;
	if (!Universe || !Universe->HasScenario() || !Universe->GetScenario().IsValid())
	{
		return;
	}

	const FGOStartScenario& Scenario = Universe->GetScenario();
	const FGOStarSystem& Star = Scenario.Universe.HomeSystem;
	const FGOPlanet& Home = Scenario.Universe.GetHomePlanet();

	TArray<FString> Lines;
	Lines.Add(FString::Printf(TEXT("%s  (seed %lld)"), *Scenario.Universe.GalaxyName, Universe->GetSeed()));
	Lines.Add(FString::Printf(TEXT("Star %s  class %s, %.2f L-sun, %.0f K"), *Star.Name,
		*GOUniverseText::SpectralClassName(Star.SpectralClass), Star.Luminosity, Star.TemperatureK));
	Lines.Add(FString::Printf(TEXT("Home %s  %s, r %.0f km, %.2f g, %.0f K, %.2f atm%s"), *Home.Name,
		*GOUniverseText::PlanetTypeName(Home.Type), Home.RadiusKm, Home.SurfaceGravityG, Home.GetSurfaceTempK(),
		Home.Atmosphere.SurfacePressureAtm, Home.Atmosphere.bBreathable ? TEXT(", breathable") : TEXT("")));
	Lines.Add(FString::Printf(TEXT("%s  %d rooms, orbit %.0f km"), *Scenario.Academy.AcademyName,
		Scenario.Academy.Rooms.Num(), Scenario.HubOrbitRadiusKm - Home.RadiusKm));

	const AGOGameMode* GameMode = World->GetAuthGameMode<AGOGameMode>();
	const APawn* Pawn = PlayerOwner ? PlayerOwner->GetPawn() : nullptr;
	if (GameMode && GameMode->GetPlanet() && Pawn)
	{
		Lines.Add(FString::Printf(TEXT("Altitude %.3f km"), GameMode->GetPlanet()->GetAltitudeKm(Pawn->GetActorLocation())));
	}
	Lines.Add(TEXT("WASD move  Mouse look  Space jump  F fly/noclip  Shift boost  E/Q up/down"));

	float Y = 16.f;
	for (const FString& Line : Lines)
	{
		DrawText(Line, FLinearColor(0.85f, 0.95f, 1.f), 16.f, Y, GEngine ? GEngine->GetSmallFont() : nullptr, 1.f);
		Y += 18.f;
	}
}
