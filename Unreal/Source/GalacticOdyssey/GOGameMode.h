#pragma once

#include "CoreMinimal.h"
#include "GameFramework/GameModeBase.h"
#include "GOGameMode.generated.h"

class AGOAcademyHubActor;
class AGOPlanetActor;
class APlayerStart;
struct FGOStartScenario;

/**
 * New-game flow: asks UGOUniverseSubsystem for the scenario (seed from Project Settings or a
 * ?Seed=... URL option), spawns the home planet and the orbiting academy, and starts the player
 * either on graduation day in the academy hall or at the landing site (?Start=Surface).
 *
 * Everything is placed in one world: the start point sits at the origin, the planet centre
 * thousands of km below it. Large World Coordinates make that precise without level streaming.
 */
UCLASS()
class GALACTICODYSSEY_API AGOGameMode : public AGameModeBase
{
	GENERATED_BODY()

public:
	AGOGameMode();

	virtual void InitGame(const FString& MapName, const FString& Options, FString& ErrorMessage) override;
	virtual AActor* ChoosePlayerStart_Implementation(AController* Player) override;

	UFUNCTION(BlueprintPure, Category = "Galactic")
	AGOPlanetActor* GetPlanet() const { return Planet; }

	UFUNCTION(BlueprintPure, Category = "Galactic")
	AGOAcademyHubActor* GetAcademy() const { return Academy; }

protected:
	/** Override to use Blueprint subclasses (e.g. with custom materials or behaviour). */
	UPROPERTY(EditDefaultsOnly, Category = "Galactic")
	TSubclassOf<AGOPlanetActor> PlanetClass;

	UPROPERTY(EditDefaultsOnly, Category = "Galactic")
	TSubclassOf<AGOAcademyHubActor> AcademyClass;

private:
	void BuildUniverse(const FGOStartScenario& Scenario, bool bStartOnSurface);

	UPROPERTY(Transient)
	TObjectPtr<AGOPlanetActor> Planet;

	UPROPERTY(Transient)
	TObjectPtr<AGOAcademyHubActor> Academy;

	UPROPERTY(Transient)
	TObjectPtr<APlayerStart> StartSpot;
};
