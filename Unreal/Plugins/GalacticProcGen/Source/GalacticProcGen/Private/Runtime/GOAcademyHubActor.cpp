#include "Runtime/GOAcademyHubActor.h"

#include "Assets/GOKitAssets.h"
#include "Components/InstancedStaticMeshComponent.h"
#include "Components/PointLightComponent.h"
#include "Components/TextRenderComponent.h"
#include "Core/GOContentDefs.h"
#include "Engine/CollisionProfile.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"
#include "GalacticProcGenModule.h"
#include "Materials/MaterialInterface.h"
#include "Misc/PackageName.h"

namespace GOAcademyHubActorPrivate
{
	struct FBatch
	{
		UStaticMesh* Mesh = nullptr;
		UMaterialInterface* Material = nullptr;
		bool bCollision = true;
		bool bVisible = true;
		float CullDistanceCm = 0.f;
		TArray<FTransform> Transforms;
	};
}

AGOAcademyHubActor::AGOAcademyHubActor()
{
	PrimaryActorTick.bCanEverTick = false;
	Root = CreateDefaultSubobject<USceneComponent>(TEXT("Root"));
	Root->SetMobility(EComponentMobility::Movable);
	RootComponent = Root;
}

void AGOAcademyHubActor::EndPlay(const EEndPlayReason::Type EndPlayReason)
{
	Clear();
	Super::EndPlay(EndPlayReason);
}

void AGOAcademyHubActor::Clear()
{
	for (UActorComponent* Component : BuiltComponents)
	{
		if (Component)
		{
			Component->DestroyComponent();
		}
	}
	for (AActor* Actor : SpawnedActors)
	{
		if (IsValid(Actor))
		{
			Actor->Destroy();
		}
	}
	BuiltComponents.Reset();
	SpawnedActors.Reset();
	ReferencedAssets.Reset();
}

UInstancedStaticMeshComponent* AGOAcademyHubActor::MakeInstanceComponent(UStaticMesh* Mesh, UMaterialInterface* Material,
	bool bCollision, bool bVisible, float CullDistanceCm)
{
	UInstancedStaticMeshComponent* Component = NewObject<UInstancedStaticMeshComponent>(this, NAME_None, RF_Transient);
	Component->SetupAttachment(Root);
	Component->SetMobility(EComponentMobility::Movable);
	Component->SetStaticMesh(Mesh);
	if (Material)
	{
		Component->SetMaterial(0, Material);
	}
	Component->SetCollisionProfileName(bCollision ? UCollisionProfile::BlockAll_ProfileName : UCollisionProfile::NoCollision_ProfileName);
	Component->SetVisibility(bVisible);
	if (CullDistanceCm > 0.f)
	{
		Component->SetCullDistances(FMath::RoundToInt32(CullDistanceCm * 0.8f), FMath::RoundToInt32(CullDistanceCm));
	}
	Component->RegisterComponent();
	AddInstanceComponent(Component);
	BuiltComponents.Add(Component);
	return Component;
}

AActor* AGOAcademyHubActor::SpawnKitActor(UClass* Class, const FTransform& HubSlot, const FVector& SlotSize)
{
	UWorld* World = GetWorld();
	if (!World || !Class)
	{
		return nullptr;
	}

	// Kit actors are placed standing on the slot's floor, facing the slot's +X.
	const FVector FloorLocal = HubSlot.GetLocation() - FVector(0.0, 0.0, SlotSize.Z * 0.5);
	const FTransform WorldTransform(GetActorQuat() * HubSlot.GetRotation(), GetActorTransform().TransformPosition(FloorLocal));

	FActorSpawnParameters Params;
	Params.Owner = this;
	Params.SpawnCollisionHandlingOverride = ESpawnActorCollisionHandlingMethod::AlwaysSpawn;
	AActor* Spawned = World->SpawnActor<AActor>(Class, WorldTransform, Params);
	if (!Spawned)
	{
		return nullptr;
	}

	// Rest the actor's bounds on the floor whatever its pivot.
	FVector Origin, Extent;
	Spawned->GetActorBounds(true, Origin, Extent);
	const double FloorZ = WorldTransform.GetLocation().Z;
	if (Extent.Z > 0.0)
	{
		Spawned->AddActorWorldOffset(FVector(0.0, 0.0, FloorZ - (Origin.Z - Extent.Z)));
	}
	SpawnedActors.Add(Spawned);
	return Spawned;
}

void AGOAcademyHubActor::AddLabel(const FString& Text, const FTransform& HubTransform, float WorldSize, const FColor& Color)
{
	UTextRenderComponent* Label = NewObject<UTextRenderComponent>(this, NAME_None, RF_Transient);
	Label->SetupAttachment(Root);
	Label->SetMobility(EComponentMobility::Movable);
	Label->SetRelativeTransform(HubTransform);
	Label->SetText(FText::FromString(Text));
	Label->SetWorldSize(WorldSize);
	Label->SetHorizontalAlignment(EHTA_Center);
	Label->SetTextRenderColor(Color);
	Label->SetCollisionEnabled(ECollisionEnabled::NoCollision);
	Label->RegisterComponent();
	AddInstanceComponent(Label);
	BuiltComponents.Add(Label);
}

void AGOAcademyHubActor::BuildFromLayout(const FGOHubLayout& InLayout, const FGOContentDefs& Content)
{
	using namespace GOAcademyHubActorPrivate;

	Clear();
	Layout = InLayout;

	FGOKitAssets Kit(Content);
	TArray<FName> AssetIds;
	for (const FGOHubPiece& Piece : Layout.Structure) { AssetIds.AddUnique(Piece.AssetId); }
	for (const FGOHubPiece& Piece : Layout.Props) { AssetIds.AddUnique(Piece.AssetId); }
	for (const FGOHubSpawnPoint& Spawn : Layout.Spawns) { AssetIds.AddUnique(Spawn.AssetId); }
	Kit.LoadSync(AssetIds);

	UMaterialInterface* Greybox = nullptr;
	if (!GreyboxMaterial.IsNull() && FPackageName::DoesPackageExist(GreyboxMaterial.ToSoftObjectPath().GetLongPackageName()))
	{
		Greybox = GreyboxMaterial.LoadSynchronous();
	}

	TArray<FBatch> Batches;
	TMap<FString, int32> BatchIndex;
	auto AddInstance = [&Batches, &BatchIndex](UStaticMesh* Mesh, UMaterialInterface* Material, bool bCollision, bool bVisible,
		float CullDistanceCm, const FTransform& Transform)
	{
		if (!Mesh)
		{
			return;
		}
		const FString Key = FString::Printf(TEXT("%p|%p|%d|%d|%.0f"), Mesh, Material, bCollision ? 1 : 0, bVisible ? 1 : 0, CullDistanceCm);
		int32* Found = BatchIndex.Find(Key);
		if (!Found)
		{
			FBatch& Batch = Batches.AddDefaulted_GetRef();
			Batch.Mesh = Mesh;
			Batch.Material = Material;
			Batch.bCollision = bCollision;
			Batch.bVisible = bVisible;
			Batch.CullDistanceCm = CullDistanceCm;
			Found = &BatchIndex.Add(Key, Batches.Num() - 1);
		}
		Batches[*Found].Transforms.Add(Transform);
	};

	int32 ActorCount = 0;
	auto PlacePiece = [&](const FGOHubPiece& Piece)
	{
		const FGOResolvedAsset Asset = Kit.Resolve(Piece.AssetId);
		const bool bCollision = Asset.Row ? Asset.Row->bCollision : true;
		const float CullDistance = Asset.Row ? Asset.Row->CullDistanceCm : 0.f;

		if (Asset.ActorClass)
		{
			ActorCount += SpawnKitActor(Asset.ActorClass, Piece.Transform, Piece.SlotSizeCm) ? 1 : 0;
			return;
		}
		if (!Asset.bFallback)
		{
			AddInstance(Asset.Mesh, nullptr, bCollision, true, CullDistance, FGOKitAssets::FitToSlot(Asset, Piece.Transform, Piece.SlotSizeCm));
			return;
		}

		UMaterialInterface* Tinted = Kit.GetTintedMaterial(Greybox, Piece.Tint, this);
		if (FGOKitAssets::IsComposite(Asset.FallbackShape))
		{
			TArray<FGOFallbackPart> Parts;
			FGOKitAssets::MakeFallbackParts(Asset.FallbackShape, Piece.SlotSizeCm, Parts);
			UStaticMesh* Cube = FGOKitAssets::GetFallbackMesh(EGOFallbackShape::Cube);
			for (const FGOFallbackPart& Part : Parts)
			{
				AddInstance(Cube, Part.bInvisibleBlocker ? nullptr : Tinted, bCollision, !Part.bInvisibleBlocker, CullDistance,
					Part.LocalTransform * Piece.Transform);
			}
			return;
		}
		AddInstance(Asset.Mesh, Tinted, bCollision, true, CullDistance, FGOKitAssets::FitToSlot(Asset, Piece.Transform, Piece.SlotSizeCm));
	};

	for (const FGOHubPiece& Piece : Layout.Structure)
	{
		PlacePiece(Piece);
	}
	for (const FGOHubPiece& Piece : Layout.Props)
	{
		PlacePiece(Piece);
	}

	// Placeholder people at NPC spawn points (or the kit's NPC actor class once one is assigned).
	if (bSpawnNpcStandIns)
	{
		for (const FGOHubSpawnPoint& Spawn : Layout.Spawns)
		{
			if (Spawn.Role != EGOHubSpawnRole::Cadet && Spawn.Role != EGOHubSpawnRole::Instructor && Spawn.Role != EGOHubSpawnRole::Staff)
			{
				continue;
			}
			const FGOAssetKitRow* Row = Content.FindAsset(Spawn.AssetId);
			const FVector Footprint = Row ? Row->FootprintCm : FVector(50.0, 50.0, 180.0);
			FGOHubPiece Person;
			Person.AssetId = Spawn.AssetId;
			Person.Tint = Row ? Row->FallbackColor : FLinearColor::Gray;
			Person.SlotSizeCm = Spawn.bSeated ? FVector(Footprint.X, Footprint.Y, Footprint.Z * 0.72) : Footprint;
			Person.Transform = FTransform(Spawn.Transform.GetRotation(),
				Spawn.Transform.GetLocation() + FVector(0.0, 0.0, Person.SlotSizeCm.Z * 0.5));
			PlacePiece(Person);

			if (Spawn.Role == EGOHubSpawnRole::Instructor && !Spawn.DisplayName.IsEmpty())
			{
				AddLabel(Spawn.DisplayName,
					FTransform(Spawn.Transform.GetRotation(), Spawn.Transform.GetLocation() + FVector(0.0, 0.0, Person.SlotSizeCm.Z + 30.0)),
					14.f, FColor(255, 230, 160));
			}
		}
	}

	int32 InstanceCount = 0;
	for (FBatch& Batch : Batches)
	{
		UInstancedStaticMeshComponent* Component = MakeInstanceComponent(Batch.Mesh, Batch.Material, Batch.bCollision, Batch.bVisible, Batch.CullDistanceCm);
		Component->AddInstances(Batch.Transforms, /*bShouldReturnIndices*/ false);
		InstanceCount += Batch.Transforms.Num();
	}

	const int32 LightCount = FMath::Min(Layout.Lights.Num(), FMath::Max(MaxLights, 0));
	for (int32 Index = 0; Index < LightCount; ++Index)
	{
		const FGOHubLight& Light = Layout.Lights[Index];
		UPointLightComponent* Component = NewObject<UPointLightComponent>(this, NAME_None, RF_Transient);
		Component->SetupAttachment(Root);
		Component->SetMobility(EComponentMobility::Movable);
		Component->SetRelativeLocation(Light.LocationCm);
		Component->SetIntensityUnits(ELightUnits::Lumens);
		Component->SetIntensity(Light.IntensityLumens);
		Component->SetAttenuationRadius(Light.AttenuationRadiusCm);
		Component->SetLightColor(Light.Color);
		Component->SetCastShadows(false);
		Component->RegisterComponent();
		AddInstanceComponent(Component);
		BuiltComponents.Add(Component);
	}

	// The academy's name above the graduation stage, facing the audience.
	if (Layout.Rooms.IsValidIndex(Layout.GraduationHallIndex))
	{
		const FGOHubRoom& Hall = Layout.Rooms[Layout.GraduationHallIndex];
		const FVector Front(Hall.FrontDirection.X, Hall.FrontDirection.Y, 0.0);
		const FVector SignLocation = Hall.CenterCm + Front * (Hall.InteriorSizeCm.X * 0.5 - 40.0)
			+ FVector(0.0, 0.0, Layout.Params.WallHeightCm * 0.82);
		AddLabel(Layout.AcademyName + TEXT("\nGraduation Day"),
			FTransform((-Front).Rotation(), SignLocation), 55.f, FColor(255, 214, 120));
	}

	TArray<UObject*> Referenced;
	Kit.CollectReferencedObjects(Referenced);
	ReferencedAssets.Append(Referenced);

	UE_LOG(LogGalacticProcGen, Log, TEXT("Built %s: %d rooms, %d instances in %d batches, %d actors, %d lights."),
		*Layout.AcademyName, Layout.Rooms.Num(), InstanceCount, Batches.Num(), ActorCount, LightCount);
}

FTransform AGOAcademyHubActor::GetSpawnWorldTransform(EGOHubSpawnRole SpawnRole) const
{
	if (const FGOHubSpawnPoint* Spawn = Layout.FindSpawn(SpawnRole))
	{
		return Spawn->Transform * GetActorTransform();
	}
	return GetActorTransform();
}

bool AGOAcademyHubActor::IsInsideHub(FVector WorldLocation) const
{
	return Layout.BoundsCm.IsValid && Layout.BoundsCm.ExpandBy(100.0).IsInside(GetActorTransform().InverseTransformPosition(WorldLocation));
}
