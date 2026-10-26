#pragma once

#include "CoreMinimal.h"
#include "Assets/GOAssetKitTypes.h"
#include "Templates/SubclassOf.h"

class AActor;
class UMaterialInstanceDynamic;
class UMaterialInterface;
class UObject;
class UStaticMesh;
struct FGOContentDefs;
struct FStreamableHandle;

/** A kit asset resolved to something renderable. */
struct GALACTICPROCGEN_API FGOResolvedAsset
{
	FName AssetId;
	const FGOAssetKitRow* Row = nullptr;

	/** Imported mesh, or the engine primitive standing in for it. */
	UStaticMesh* Mesh = nullptr;
	TSubclassOf<AActor> ActorClass;
	FBox LocalBounds = FBox(ForceInit);

	/** True while the kit row has no usable mesh/class and an engine primitive is used. */
	bool bFallback = true;
	EGOFallbackShape FallbackShape = EGOFallbackShape::Cube;
};

/** One box of a composite fallback (door frame, window frame), in slot space. */
struct GALACTICPROCGEN_API FGOFallbackPart
{
	FTransform LocalTransform;	// scale is relative to the 100 cm engine cube
	bool bInvisibleBlocker = false;
};

/**
 * Resolves asset kit ids to meshes/actor classes and computes how an asset is fitted into the
 * slot a generator reserved for it. Fitting uses the mesh bounds, so assets imported from the
 * legacy libGDX .glb files work regardless of their units, pivot or forward axis (YawOffsetDeg).
 * Rows without a mesh fall back to /Engine/BasicShapes primitives so layouts are always playable.
 */
class GALACTICPROCGEN_API FGOKitAssets
{
public:
	explicit FGOKitAssets(const FGOContentDefs& InContent) : Content(InContent) {}

	/** Synchronously loads the meshes/classes of the given ids (use during level start). */
	void LoadSync(const TArray<FName>& AssetIds);

	/** Streams the given ids in the background; OnLoaded runs on the game thread. */
	TSharedPtr<FStreamableHandle> LoadAsync(const TArray<FName>& AssetIds, TFunction<void()> OnLoaded);

	/** Resolves an id (loading synchronously if needed). Unknown ids resolve to a fallback cube.
	 *  Returned by value: the cache may grow while callers still hold an earlier result. */
	FGOResolvedAsset Resolve(FName AssetId);

	/** Transform that places Asset inside a slot (slot centre/rotation + slot size, centimetres). */
	static FTransform FitToSlot(const FGOResolvedAsset& Asset, const FTransform& Slot, const FVector& SlotSize);

	/** Boxes making up a composite fallback shape inside a slot of the given size. */
	static void MakeFallbackParts(EGOFallbackShape Shape, const FVector& SlotSize, TArray<FGOFallbackPart>& OutParts);

	static bool IsComposite(EGOFallbackShape Shape) { return Shape == EGOFallbackShape::DoorFrame || Shape == EGOFallbackShape::WindowFrame; }

	static UStaticMesh* GetFallbackMesh(EGOFallbackShape Shape);

	/** Material tinted with a colour (one dynamic instance per colour, cached). Parent defaults to the engine's basic shape material. */
	UMaterialInterface* GetTintedMaterial(UMaterialInterface* Parent, const FLinearColor& Color, UObject* Outer);

	/** Objects that must stay referenced while instances use them (add to a UPROPERTY array). */
	void CollectReferencedObjects(TArray<UObject*>& Out) const;

private:
	void CollectPaths(const TArray<FName>& AssetIds, TArray<FSoftObjectPath>& OutPaths) const;

	const FGOContentDefs& Content;
	TMap<FName, FGOResolvedAsset> Resolved;
	TMap<FString, UMaterialInstanceDynamic*> TintedMaterials;
};
