#pragma once

#include "CoreMinimal.h"
#include "Subsystems/GameInstanceSubsystem.h"
#include "Universe/GOStartScenario.h"
#include "Universe/GOUniverseTypes.h"
#include "GOUniverseSubsystem.generated.h"

struct FGOContentDefs;
class FGOGalaxyGenerator;

DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FGOOnScenarioReady, const FGOStartScenario&, Scenario);

/**
 * Owns the universe for the lifetime of the game instance: loads content once, turns a seed
 * into the new-game scenario (galaxy, home system, home world, academy hub) and answers galaxy
 * queries with a small chunk cache. Generation is pure data and runs on any thread; actors in
 * the world (AGOPlanetActor, AGOAcademyHubActor) are built from the results.
 */
UCLASS()
class GALACTICPROCGEN_API UGOUniverseSubsystem : public UGameInstanceSubsystem
{
	GENERATED_BODY()

public:
	virtual void Initialize(FSubsystemCollectionBase& Collection) override;
	virtual void Deinitialize() override;

	/** Hashes any text into the universe seed (the same text always gives the same universe). */
	UFUNCTION(BlueprintCallable, Category = "Galactic|Universe")
	void SetSeedFromText(const FString& SeedText);

	UFUNCTION(BlueprintCallable, Category = "Galactic|Universe")
	void SetSeed(int64 NewSeed);

	UFUNCTION(BlueprintPure, Category = "Galactic|Universe")
	int64 GetSeed() const { return static_cast<int64>(Seed); }

	/** Generates (or returns the cached) scenario for the current seed. Takes a few milliseconds. */
	const FGOStartScenario& GenerateScenario();

	/** Blueprint-friendly synchronous generation. */
	UFUNCTION(BlueprintCallable, Category = "Galactic|Universe", meta = (DisplayName = "Generate Scenario"))
	FGOStartScenario K2_GenerateScenario() { return GenerateScenario(); }

	/** Generates on a worker thread and broadcasts OnScenarioReady on the game thread. */
	UFUNCTION(BlueprintCallable, Category = "Galactic|Universe")
	void GenerateScenarioAsync();

	UPROPERTY(BlueprintAssignable, Category = "Galactic|Universe")
	FGOOnScenarioReady OnScenarioReady;

	UFUNCTION(BlueprintPure, Category = "Galactic|Universe")
	bool HasScenario() const { return bHasScenario; }

	const FGOStartScenario& GetScenario() const { return Scenario; }

	/** Content shared with worker threads; never null after Initialize. */
	TSharedPtr<const FGOContentDefs, ESPMode::ThreadSafe> GetContent() const { return Content; }

	/** Stars within RadiusLY (disc plane) of a galaxy position, generated on demand. */
	UFUNCTION(BlueprintCallable, Category = "Galactic|Galaxy")
	TArray<FGOStarRecord> GetStarsNear(FVector PositionLY, double RadiusLY);

	UFUNCTION(BlueprintCallable, Category = "Galactic|Galaxy")
	FGOStarSystem GetStarSystem(const FGOStarRecord& Star);

	UFUNCTION(BlueprintCallable, Category = "Galactic|Galaxy")
	TArray<FGOPlanet> GetPlanets(const FGOStarSystem& System) const;

private:
	void EnsureGalaxy();
	FGOStartScenarioParams MakeParams() const;

	TSharedPtr<const FGOContentDefs, ESPMode::ThreadSafe> Content;
	TSharedPtr<FGOGalaxyGenerator, ESPMode::ThreadSafe> Galaxy;
	TMap<FIntPoint, TArray<FGOStarRecord>> ChunkCache;

	FGOStartScenario Scenario;
	uint64 Seed = 0;
	uint64 ScenarioSeed = 0;
	uint64 GalaxySeed = 0;
	bool bHasScenario = false;
	bool bAsyncInFlight = false;
};
