#pragma once

#include "CoreMinimal.h"
#include "Engine/DataTable.h"
#include "UObject/SoftObjectPtr.h"
#include "Universe/GOUniverseTypes.h"
#include "GOAssetKitTypes.generated.h"

class UStaticMesh;
class AActor;

UENUM(BlueprintType)
enum class EGOKitCategory : uint8
{
	Structure,	// hub floors, walls, ceilings, door frames, windows
	Prop,		// furniture and equipment placed by room prop rules
	Character,	// NPC / player stand-ins at spawn points
	Vehicle,	// ships parked in the hangar
	Scatter		// planet-surface rocks and flora
};

/** How a mesh is fitted into the slot (footprint box) the generator reserved for it. */
UENUM(BlueprintType)
enum class EGOFitMode : uint8
{
	/** Authored size, centred on the slot. Use for assets already modelled in centimetres. */
	None,
	/** Uniform scale so the mesh bounds fit inside the footprint. Safe for assets of unknown units. */
	Uniform,
	/** Per-axis scale so the bounds fill the footprint exactly. Use for modular structure pieces. */
	Stretch
};

/** Engine primitive used until a real mesh is assigned, so every layout is playable out of the box. */
UENUM(BlueprintType)
enum class EGOFallbackShape : uint8
{
	Cube,
	Cylinder,
	Sphere,
	Cone,
	Plane,
	/** Composite: two jambs and a header around a walkable opening. */
	DoorFrame,
	/** Composite: sill and header around a glazed opening (invisible blocker in the gap). */
	WindowFrame
};

/**
 * One reusable 3D asset known to the procedural generators, keyed by row name (AssetId).
 * Generators only reason about ids, footprints and tags; the runtime actors resolve the mesh
 * (or actor class) and fit it to the slot. Ids from the old libGDX manifests
 * (chair_crew, console_nav, desk_standard, human_player, human_npc_generic) are kept, with
 * LegacySourcePath pointing at the original .glb so the editor import script can wire them in.
 */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOAssetKitRow : public FTableRowBase
{
	GENERATED_BODY()

	/** Filled from the row name by the content loader. */
	UPROPERTY(Transient, VisibleAnywhere, BlueprintReadOnly, Category = "Kit")
	FName AssetId;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit")
	EGOKitCategory Category = EGOKitCategory::Prop;

	/** Free-form tags (SEATING, CONSOLE, TECH, ...) carried over from the legacy manifests. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit")
	TArray<FName> Tags;

	/** Instanced with ISM/HISM when set. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Visual")
	TSoftObjectPtr<UStaticMesh> Mesh;

	/** Spawned as an actor instead of an instance when set (characters, interactive props, ships). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Visual")
	TSoftClassPtr<AActor> ActorClass;

	/** Original asset path from the libGDX content manifests, relative to core/src/main/resources. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Visual")
	FString LegacySourcePath;

	/** Slot size reserved by the generators (X forward, Y right, Z up), centimetres. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Fit")
	FVector FootprintCm = FVector(100.0, 100.0, 100.0);

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Fit")
	EGOFitMode FitMode = EGOFitMode::Uniform;

	/** Corrects assets whose "front" is not +X (e.g. glTF content authored facing +Y). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Fit")
	float YawOffsetDeg = 0.f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Fit")
	bool bCollision = true;

	/** 0 disables distance culling. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Fit")
	float CullDistanceCm = 0.f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Fallback")
	EGOFallbackShape FallbackShape = EGOFallbackShape::Cube;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Fallback")
	FLinearColor FallbackColor = FLinearColor(0.6f, 0.6f, 0.62f, 1.f);

	/** Biomes this asset may be scattered in (Scatter category only). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Scatter")
	TArray<EGOBiome> Biomes;

	/** Instances per square kilometre on matching ground. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Scatter")
	float DensityPerKm2 = 0.f;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Scatter")
	FVector2D ScaleRange = FVector2D(0.8, 1.2);

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Scatter")
	float MaxSlopeDeg = 30.f;

	/** Tilt instances to the ground normal instead of the local vertical. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Scatter")
	bool bAlignToSurface = false;

	/** Push instances into the ground to hide their base, centimetres. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Kit|Scatter")
	float SinkCm = 0.f;

	bool HasTag(FName Tag) const { return Tags.Contains(Tag); }
};
