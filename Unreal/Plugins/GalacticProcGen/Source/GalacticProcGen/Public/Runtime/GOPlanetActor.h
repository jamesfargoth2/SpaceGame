#pragma once

#include "CoreMinimal.h"
#include "Assets/GOKitAssets.h"
#include "Async/Future.h"
#include "GameFramework/Actor.h"
#include "Planet/GOPlanetTerrain.h"
#include "Planet/GOTerrainMesh.h"
#include "Universe/GOUniverseTypes.h"
#include "GOPlanetActor.generated.h"

class UDirectionalLightComponent;
class UHierarchicalInstancedStaticMeshComponent;
class UMaterialInterface;
class UProceduralMeshComponent;
class USkyAtmosphereComponent;
struct FGOContentDefs;

/** A terrain chunk currently shown by AGOPlanetActor. */
struct FGOPlanetChunkState
{
	FGOTerrainChunkId Id;
	UProceduralMeshComponent* Mesh = nullptr;
	TArray<UHierarchicalInstancedStaticMeshComponent*> Scatter;
	double RetiredAtSeconds = 0.0;
};

struct FGOPendingChunkBuild
{
	FGOTerrainChunkId Id;
	TFuture<TSharedPtr<FGOChunkMeshData, ESPMode::ThreadSafe>> Future;
};

/**
 * A whole planet at true scale. The actor sits at the planet centre; terrain is a cube-sphere
 * quadtree whose chunks are built on worker threads and shown as procedural mesh sections. Each
 * chunk component is placed at its own origin with chunk-relative vertices, so Large World
 * Coordinates keep centimetre precision on a 6000+ km planet without a floating origin.
 * Scatter (rocks, flora from the asset kit) is instanced per chunk with HISMs. A sky atmosphere
 * and a sun tinted by the home star complete the look.
 */
UCLASS()
class GALACTICPROCGEN_API AGOPlanetActor : public AActor
{
	GENERATED_BODY()

public:
	AGOPlanetActor();

	/**
	 * Sets the planet up. LandingDirection (planet-local unit vector) is where the sun rises
	 * first and which side is pre-built. Call once, right after spawning.
	 */
	void InitializePlanet(TSharedPtr<const FGOContentDefs, ESPMode::ThreadSafe> InContent, const FGOPlanet& InPlanet,
		const FGOStarSystem& InStar, const FVector& LandingDirection, int32 MaxDepth);

	/** Synchronously builds every chunk needed by a camera at the given world location (load time). */
	void PrewarmForCamera(const FVector& CameraWorldLocation);

	virtual void Tick(float DeltaSeconds) override;

	UFUNCTION(BlueprintPure, Category = "Galactic|Planet")
	double GetRadiusKm() const;

	/** Height above the terrain surface, km (negative below it). */
	UFUNCTION(BlueprintPure, Category = "Galactic|Planet")
	double GetAltitudeKm(FVector WorldLocation) const;

	/** World position of the surface along a planet-local direction. */
	UFUNCTION(BlueprintPure, Category = "Galactic|Planet")
	FVector GetSurfacePointWorld(FVector PlanetLocalDirection) const;

	UPROPERTY(VisibleInstanceOnly, BlueprintReadOnly, Category = "Galactic|Planet")
	FGOPlanet Planet;

	UPROPERTY(VisibleInstanceOnly, BlueprintReadOnly, Category = "Galactic|Planet")
	FGOStarSystem Star;

	/** Should output Vertex Color as Base Color; empty = engine vertex-colour material (unlit look). */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Planet")
	TSoftObjectPtr<UMaterialInterface> SurfaceMaterial;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Planet")
	TSoftObjectPtr<UMaterialInterface> GreyboxMaterial;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Planet")
	bool bSpawnAtmosphere = true;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Planet")
	float SunIlluminanceLux = 10.f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Planet")
	float DayNightTimeScale = 60.f;

	FGOTerrainMeshSettings MeshSettings;
	float SplitThreshold = 1.5f;
	float MergeThreshold = 0.75f;
	int32 CollisionMinDepth = 12;
	int32 MaxBuildsInFlight = 8;
	int32 MaxUploadsPerFrame = 4;

protected:
	virtual void EndPlay(const EEndPlayReason::Type EndPlayReason) override;

	UPROPERTY(VisibleAnywhere, Category = "Galactic|Planet")
	TObjectPtr<USceneComponent> Root;

	UPROPERTY(VisibleAnywhere, Category = "Galactic|Planet")
	TObjectPtr<USkyAtmosphereComponent> Atmosphere;

	UPROPERTY(VisibleAnywhere, Category = "Galactic|Planet")
	TObjectPtr<UDirectionalLightComponent> Sun;

private:
	FVector WorldToPlanetKm(const FVector& WorldLocation) const;
	bool CameraWorldLocation(FVector& OutLocation) const;
	void UpdateLeaves(const FVector& CameraKm);
	void UpdateTerrain();
	void Dispatch(const FGOTerrainChunkId& Id);
	void ApplyChunk(const FGOChunkMeshData& Data);
	void ReleaseChunk(FGOPlanetChunkState& Chunk);
	UProceduralMeshComponent* AcquireMeshComponent();
	void ConfigureAtmosphere();
	void UpdateSun(float DeltaSeconds);

	UPROPERTY(Transient)
	TArray<TObjectPtr<UProceduralMeshComponent>> FreeMeshes;

	/** Every component created at runtime (keeps them referenced). */
	UPROPERTY(Transient)
	TArray<TObjectPtr<UActorComponent>> RuntimeComponents;

	UPROPERTY(Transient)
	TArray<TObjectPtr<UObject>> ReferencedAssets;

	UPROPERTY(Transient)
	TObjectPtr<UMaterialInterface> SurfaceMaterialInstance;

	UPROPERTY(Transient)
	TObjectPtr<UMaterialInterface> GreyboxMaterialInstance;

	TSharedPtr<const FGOContentDefs, ESPMode::ThreadSafe> Content;
	TSharedPtr<const FGOPlanetTerrain, ESPMode::ThreadSafe> Terrain;
	TUniquePtr<FGOTerrainQuadtree> Quadtree;
	TUniquePtr<FGOKitAssets> Kit;

	TMap<uint64, FGOPlanetChunkState> Chunks;
	TMap<uint64, FGOPlanetChunkState> Retiring;
	TArray<FGOPendingChunkBuild> Pending;
	TSet<uint64> PendingKeys;
	TSet<uint64> DesiredKeys;

	FVector LastLodCameraKm = FVector(TNumericLimits<double>::Max());
	/** Pre-built chunks cook collision synchronously so the first pawn has ground under it. */
	bool bPrewarming = false;
	double SunHourAngle = 0.0;
	double SunDeclination = 0.0;
};
