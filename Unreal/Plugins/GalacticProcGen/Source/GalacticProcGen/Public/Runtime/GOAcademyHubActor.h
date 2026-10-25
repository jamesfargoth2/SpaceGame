#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "Hub/GOHubTypes.h"
#include "GOAcademyHubActor.generated.h"

class UInstancedStaticMeshComponent;
class UMaterialInterface;
class UStaticMesh;
struct FGOContentDefs;

/**
 * The orbital academy where the player graduates, assembled at runtime from an FGOHubLayout.
 * Kit pieces are batched into instanced static mesh components (one per mesh/material), kit
 * rows with an ActorClass are spawned as actors, and placeholders are used for rows that have
 * no mesh yet. The hub's local +Z is "up" for its artificial gravity.
 */
UCLASS()
class GALACTICPROCGEN_API AGOAcademyHubActor : public AActor
{
	GENERATED_BODY()

public:
	AGOAcademyHubActor();

	/** Builds (or rebuilds) the hub. Loads the referenced kit assets synchronously. */
	void BuildFromLayout(const FGOHubLayout& InLayout, const FGOContentDefs& Content);

	/** World transform (feet, facing) of the first spawn point with the given role. */
	UFUNCTION(BlueprintPure, Category = "Galactic|Academy")
	FTransform GetSpawnWorldTransform(EGOHubSpawnRole Role) const;

	UFUNCTION(BlueprintPure, Category = "Galactic|Academy")
	bool IsInsideHub(FVector WorldLocation) const;

	UPROPERTY(VisibleInstanceOnly, BlueprintReadOnly, Category = "Galactic|Academy")
	FGOHubLayout Layout;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Academy")
	bool bSpawnNpcStandIns = true;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Academy", meta = (ClampMin = "0"))
	int32 MaxLights = 48;

	/** Parent material for placeholder geometry ("Color" vector parameter). Empty = engine basic shape material. */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Academy")
	TSoftObjectPtr<UMaterialInterface> GreyboxMaterial;

protected:
	virtual void EndPlay(const EEndPlayReason::Type EndPlayReason) override;

private:
	void Clear();
	UInstancedStaticMeshComponent* MakeInstanceComponent(UStaticMesh* Mesh, UMaterialInterface* Material, bool bCollision, bool bVisible, float CullDistanceCm);
	AActor* SpawnKitActor(UClass* Class, const FTransform& HubSlot, const FVector& SlotSize);
	void AddLabel(const FString& Text, const FTransform& HubTransform, float WorldSize, const FColor& Color);

	UPROPERTY(VisibleAnywhere, Category = "Galactic|Academy")
	TObjectPtr<USceneComponent> Root;

	UPROPERTY(Transient)
	TArray<TObjectPtr<UActorComponent>> BuiltComponents;

	UPROPERTY(Transient)
	TArray<TObjectPtr<AActor>> SpawnedActors;

	/** Meshes, classes and dynamic materials kept alive while instances use them. */
	UPROPERTY(Transient)
	TArray<TObjectPtr<UObject>> ReferencedAssets;
};
