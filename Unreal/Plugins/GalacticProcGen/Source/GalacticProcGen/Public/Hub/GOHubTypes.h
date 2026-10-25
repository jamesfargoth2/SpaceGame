#pragma once

#include "CoreMinimal.h"
#include "Engine/DataTable.h"
#include "GOHubTypes.generated.h"

/** Where a room template may be placed in the academy's hub-and-spoke plan. */
UENUM(BlueprintType)
enum class EGOHubRoomPlacement : uint8
{
	/** The central room every spoke radiates from (the graduation hall). */
	Atrium,
	/** Along either side of a spoke corridor. */
	Side,
	/** Capping the far end of a spoke (hangar, observation deck, ...). */
	Terminal
};

/** How a prop rule distributes its asset inside a room. */
UENUM(BlueprintType)
enum class EGOPropPattern : uint8
{
	/** Grid of rows facing the front wall (audience seating, classroom desks). */
	Rows,
	/** Backs against the walls, facing into the room; door openings are kept clear. */
	AlongWalls,
	/** Circle around the room centre, facing inward. */
	Ring,
	/** A single instance at the room centre. */
	Center,
	/** One instance in each corner. */
	Corners,
	/** Random non-overlapping positions. */
	Scatter,
	/** Centred against the front wall, facing the room (podiums, lecterns). */
	Stage
};

UENUM(BlueprintType)
enum class EGOHubSpawnRole : uint8
{
	None,
	Player,
	Cadet,
	Instructor,
	Staff,
	Ship
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOPropRule
{
	GENERATED_BODY()

	/** Row name in the asset kit. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop")
	FName AssetId;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop")
	EGOPropPattern Pattern = EGOPropPattern::Scatter;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop", meta = (ClampMin = "0"))
	int32 MinCount = 1;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop", meta = (ClampMin = "0"))
	int32 MaxCount = 1;

	/** Gap left between neighbouring instances, centimetres. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop")
	float SpacingCm = 60.f;

	/** Keep-out distance from walls, centimetres. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop")
	float MarginCm = 60.f;

	/** Probability that the rule is applied at all. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop", meta = (ClampMin = "0", ClampMax = "1"))
	float Chance = 1.f;

	/** Emit a spawn point at instances of this rule (e.g. cadets in seats). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop|Spawn")
	EGOHubSpawnRole SpawnRole = EGOHubSpawnRole::None;

	/** Fraction of instances that receive a spawn point. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop|Spawn", meta = (ClampMin = "0", ClampMax = "1"))
	float SpawnFraction = 1.f;

	/** Spawned character sits on this prop rather than standing in front of it. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop|Spawn")
	bool bSeated = false;

	/** Standing spawns: distance from the prop centre along the prop's facing (negative = behind it). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop|Spawn")
	float SpawnOffsetCm = 70.f;

	/** Standing spawns turn to face the prop (consoles) instead of looking where it faces (podiums). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Prop|Spawn")
	bool bSpawnFacesProp = true;
};

/** A kind of room the academy can contain. Row name is the template id. */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOHubRoomTemplate : public FTableRowBase
{
	GENERATED_BODY()

	/** Filled from the row name by the content loader. */
	UPROPERTY(Transient, VisibleAnywhere, BlueprintReadOnly, Category = "Room")
	FName TemplateId;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room")
	FString DisplayName;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room")
	EGOHubRoomPlacement Placement = EGOHubRoomPlacement::Side;

	/** Rooms of this template that must exist (mandatory when > 0). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room", meta = (ClampMin = "0"))
	int32 MinCount = 0;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room", meta = (ClampMin = "0"))
	int32 MaxCount = 1;

	/** Relative chance when filling optional room slots. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room")
	float Weight = 1.f;

	/** Mandatory rooms are placed highest priority first (closest to the hall). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room")
	int32 Priority = 0;

	/** X = width along the corridor, Y = depth away from it, in grid cells. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Size")
	FIntPoint MinSizeCells = FIntPoint(3, 3);

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Size")
	FIntPoint MaxSizeCells = FIntPoint(4, 4);

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Look", meta = (ClampMin = "1"))
	int32 DoorWidthCells = 1;

	/** Kit pieces used to build the room shell (asset kit row names). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Kit")
	FName FloorAsset = TEXT("hub_floor");

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Kit")
	FName CeilingAsset = TEXT("hub_ceiling");

	/** Walls shared with other rooms or corridors. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Kit")
	FName WallAsset = TEXT("hub_wall");

	/** Walls facing space (use hub_wall_window for glazing). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Kit")
	FName ExteriorWallAsset = TEXT("hub_wall");

	/** Optional override for exterior walls on the room's front side (stage windows, hangar doors). */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Kit")
	FName FrontWallAsset;

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Kit")
	FName DoorAsset = TEXT("hub_doorway");

	/** Tint applied to fallback structure pieces in this room. */
	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Look")
	FLinearColor Tint = FLinearColor(0.75f, 0.77f, 0.8f, 1.f);

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Look")
	FLinearColor LightColor = FLinearColor(1.f, 0.96f, 0.9f, 1.f);

	UPROPERTY(EditAnywhere, BlueprintReadOnly, Category = "Room|Props")
	TArray<FGOPropRule> Props;
};

/** Knobs for the hub layout. Content (which rooms exist and what is in them) lives in data. */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOHubGenParams
{
	GENERATED_BODY()

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub")
	float CellSizeCm = 400.f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub")
	float WallHeightCm = 450.f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub")
	float WallThicknessCm = 20.f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub")
	float FloorThicknessCm = 20.f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub", meta = (ClampMin = "1", ClampMax = "3"))
	int32 CorridorWidthCells = 1;

	/** Spokes leave the graduation hall on the sides that do not face the planet. */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub", meta = (ClampMin = "1", ClampMax = "3"))
	int32 SpokeCount = 3;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub", meta = (ClampMin = "2"))
	int32 MinSpokeLengthCells = 6;

	/** Rooms added beyond the mandatory ones. */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub", meta = (ClampMin = "0"))
	int32 OptionalRoomCount = 4;

	/** Grid direction the graduation hall's stage wall faces; the windows behind the stage look at it. */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub")
	FIntPoint FrontDirection = FIntPoint(1, 0);

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub", meta = (ClampMin = "1"))
	int32 CorridorLightSpacingCells = 3;

	/** Every Nth exterior corridor wall cell gets a window. 0 disables corridor windows. */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub", meta = (ClampMin = "0"))
	int32 CorridorWindowEvery = 3;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Kit")
	FName CorridorFloorAsset = TEXT("hub_floor");

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Kit")
	FName CorridorCeilingAsset = TEXT("hub_ceiling");

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Kit")
	FName CorridorWallAsset = TEXT("hub_wall");

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Kit")
	FName CorridorWindowAsset = TEXT("hub_wall_window");

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Kit")
	FName LightFixtureAsset = TEXT("hub_light");

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Kit")
	FName CadetAsset = TEXT("human_npc_generic");

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Kit")
	FName PlayerAsset = TEXT("human_player");

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Look")
	FLinearColor CorridorTint = FLinearColor(0.55f, 0.6f, 0.68f, 1.f);

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Hub|Look")
	float LightIntensityLumens = 6000.f;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOHubDoor
{
	GENERATED_BODY()

	/** Room-side cell of the doorway. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FIntPoint Cell = FIntPoint(0, 0);

	/** Corridor-side cell of the doorway (edge-adjacent to Cell). */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FIntPoint OutsideCell = FIntPoint(0, 0);
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOHubRoom
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FName TemplateId;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FString DisplayName;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	EGOHubRoomPlacement Placement = EGOHubRoomPlacement::Side;

	/** -1 for the atrium. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	int32 SpokeIndex = INDEX_NONE;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FIntPoint MinCell = FIntPoint(0, 0);

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FIntPoint SizeCells = FIntPoint(1, 1);

	/** Unit grid direction from the room's main door towards its front wall. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FIntPoint FrontDirection = FIntPoint(1, 0);

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	TArray<FGOHubDoor> Doors;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FLinearColor Tint = FLinearColor::White;

	/** Interior extents in the room's local frame: X along FrontDirection, Y to its right, cm. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FVector2D InteriorSizeCm = FVector2D::ZeroVector;

	/** Interior centre at floor level, hub space, cm. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FVector CenterCm = FVector::ZeroVector;

	bool ContainsCell(const FIntPoint& Cell) const
	{
		return Cell.X >= MinCell.X && Cell.Y >= MinCell.Y
			&& Cell.X < MinCell.X + SizeCells.X && Cell.Y < MinCell.Y + SizeCells.Y;
	}
};

/** One asset instance: the generator reserves a box (slot); the runtime fits the mesh into it. */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOHubPiece
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FName AssetId;

	/** Slot centre and facing (+X forward) in hub space, centimetres. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FTransform Transform;

	/** Slot extents in the slot's local frame, centimetres. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FVector SlotSizeCm = FVector(100.0, 100.0, 100.0);

	/** -1 for corridor pieces. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	int32 RoomIndex = INDEX_NONE;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FLinearColor Tint = FLinearColor::White;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOHubSpawnPoint
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	EGOHubSpawnRole Role = EGOHubSpawnRole::None;

	/** Feet location and facing in hub space. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FTransform Transform;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	int32 RoomIndex = INDEX_NONE;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	bool bSeated = false;

	/** Generated name for NPCs (empty for the player and ships). */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FString DisplayName;

	/** Kit asset to visualise this spawn with when no gameplay class is provided. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FName AssetId;
};

USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOHubLight
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FVector LocationCm = FVector::ZeroVector;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	float AttenuationRadiusCm = 1000.f;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	float IntensityLumens = 3000.f;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FLinearColor Color = FLinearColor::White;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	int32 RoomIndex = INDEX_NONE;
};

/** Complete, engine-independent description of a generated academy hub. */
USTRUCT(BlueprintType)
struct GALACTICPROCGEN_API FGOHubLayout
{
	GENERATED_BODY()

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	int64 Seed = 0;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FString AcademyName;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FGOHubGenParams Params;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	TArray<FGOHubRoom> Rooms;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	TArray<FIntPoint> CorridorCells;

	/** Floors, ceilings, walls, door frames and windows. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	TArray<FGOHubPiece> Structure;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	TArray<FGOHubPiece> Props;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	TArray<FGOHubSpawnPoint> Spawns;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	TArray<FGOHubLight> Lights;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	FBox BoundsCm = FBox(ForceInit);

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	int32 GraduationHallIndex = INDEX_NONE;

	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	int32 HangarIndex = INDEX_NONE;

	/** Non-fatal problems, e.g. a mandatory room that could not be placed. */
	UPROPERTY(BlueprintReadOnly, Category = "Hub")
	TArray<FString> Warnings;

	const FGOHubSpawnPoint* FindSpawn(EGOHubSpawnRole Role) const;
	int32 CountRooms(FName TemplateId) const;
	FVector CellCenterCm(const FIntPoint& Cell) const;
};
