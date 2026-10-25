#include "Assets/GOKitAssets.h"

#include "Core/GOContentDefs.h"
#include "Engine/AssetManager.h"
#include "Engine/StaticMesh.h"
#include "Engine/StreamableManager.h"
#include "GameFramework/Actor.h"
#include "Materials/MaterialInstanceDynamic.h"
#include "Materials/MaterialInterface.h"
#include "Misc/PackageName.h"

namespace GOKitAssetsPrivate
{
	const TCHAR* FallbackMeshPath(EGOFallbackShape Shape)
	{
		switch (Shape)
		{
		case EGOFallbackShape::Cylinder:	return TEXT("/Engine/BasicShapes/Cylinder.Cylinder");
		case EGOFallbackShape::Sphere:		return TEXT("/Engine/BasicShapes/Sphere.Sphere");
		case EGOFallbackShape::Cone:		return TEXT("/Engine/BasicShapes/Cone.Cone");
		case EGOFallbackShape::Plane:		return TEXT("/Engine/BasicShapes/Plane.Plane");
		default:							return TEXT("/Engine/BasicShapes/Cube.Cube");
		}
	}

	const TCHAR* const BasicShapeMaterialPath = TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial");

	/** Soft references that point at assets which do not exist yet are treated as unset. */
	bool Exists(const FSoftObjectPath& Path)
	{
		return !Path.IsNull() && FPackageName::DoesPackageExist(Path.GetLongPackageName());
	}
}

UStaticMesh* FGOKitAssets::GetFallbackMesh(EGOFallbackShape Shape)
{
	return LoadObject<UStaticMesh>(nullptr, GOKitAssetsPrivate::FallbackMeshPath(IsComposite(Shape) ? EGOFallbackShape::Cube : Shape));
}

void FGOKitAssets::CollectPaths(const TArray<FName>& AssetIds, TArray<FSoftObjectPath>& OutPaths) const
{
	for (const FName& Id : AssetIds)
	{
		const FGOAssetKitRow* Row = Content.FindAsset(Id);
		if (!Row)
		{
			continue;
		}
		if (GOKitAssetsPrivate::Exists(Row->Mesh.ToSoftObjectPath()))
		{
			OutPaths.AddUnique(Row->Mesh.ToSoftObjectPath());
		}
		if (GOKitAssetsPrivate::Exists(Row->ActorClass.ToSoftObjectPath()))
		{
			OutPaths.AddUnique(Row->ActorClass.ToSoftObjectPath());
		}
	}
}

void FGOKitAssets::LoadSync(const TArray<FName>& AssetIds)
{
	TArray<FSoftObjectPath> Paths;
	CollectPaths(AssetIds, Paths);
	if (Paths.Num() > 0)
	{
		UAssetManager::GetStreamableManager().RequestSyncLoad(Paths);
	}
	for (const FName& Id : AssetIds)
	{
		Resolve(Id);
	}
}

TSharedPtr<FStreamableHandle> FGOKitAssets::LoadAsync(const TArray<FName>& AssetIds, TFunction<void()> OnLoaded)
{
	TArray<FSoftObjectPath> Paths;
	CollectPaths(AssetIds, Paths);
	if (Paths.Num() == 0)
	{
		OnLoaded();
		return nullptr;
	}
	return UAssetManager::GetStreamableManager().RequestAsyncLoad(Paths,
		FStreamableDelegate::CreateLambda([Callback = MoveTemp(OnLoaded)]() { Callback(); }));
}

FGOResolvedAsset FGOKitAssets::Resolve(FName AssetId)
{
	if (const FGOResolvedAsset* Found = Resolved.Find(AssetId))
	{
		return *Found;
	}

	FGOResolvedAsset Asset;
	Asset.AssetId = AssetId;
	Asset.Row = Content.FindAsset(AssetId);
	if (const FGOAssetKitRow* Row = Asset.Row)
	{
		Asset.FallbackShape = Row->FallbackShape;
		if (GOKitAssetsPrivate::Exists(Row->ActorClass.ToSoftObjectPath()))
		{
			if (UClass* Class = Row->ActorClass.LoadSynchronous())
			{
				Asset.ActorClass = Class;
				Asset.bFallback = false;
			}
		}
		if (GOKitAssetsPrivate::Exists(Row->Mesh.ToSoftObjectPath()))
		{
			if (UStaticMesh* Mesh = Row->Mesh.LoadSynchronous())
			{
				Asset.Mesh = Mesh;
				Asset.bFallback = false;
			}
		}
	}

	if (!Asset.Mesh)
	{
		Asset.Mesh = GetFallbackMesh(Asset.FallbackShape);
	}
	if (Asset.Mesh)
	{
		Asset.LocalBounds = Asset.Mesh->GetBoundingBox();
	}
	return Resolved.Add(AssetId, Asset);
}

FTransform FGOKitAssets::FitToSlot(const FGOResolvedAsset& Asset, const FTransform& Slot, const FVector& SlotSize)
{
	const FGOAssetKitRow* Row = Asset.Row;
	// Placeholders always fill their slot so the layout reads clearly; real meshes follow the row.
	const EGOFitMode Mode = Asset.bFallback ? EGOFitMode::Stretch : (Row ? Row->FitMode : EGOFitMode::Uniform);
	const double YawOffset = (Row && !Asset.bFallback) ? Row->YawOffsetDeg : 0.0;
	const FQuat YawQuat(FVector::UpVector, FMath::DegreesToRadians(YawOffset));

	const FBox Bounds = Asset.LocalBounds.IsValid ? Asset.LocalBounds : FBox(FVector(-50.0), FVector(50.0));
	const FBox Turned = Bounds.TransformBy(FTransform(YawQuat));
	const FVector Size(FMath::Max(Turned.GetSize().X, 1.0), FMath::Max(Turned.GetSize().Y, 1.0), FMath::Max(Turned.GetSize().Z, 1.0));

	FVector Scale(1.0);
	switch (Mode)
	{
	case EGOFitMode::Stretch:
		Scale = FVector(
			SlotSize.X > 0.0 ? SlotSize.X / Size.X : 1.0,
			SlotSize.Y > 0.0 ? SlotSize.Y / Size.Y : 1.0,
			SlotSize.Z > 0.0 ? SlotSize.Z / Size.Z : 1.0);
		break;
	case EGOFitMode::Uniform:
	{
		double Uniform = TNumericLimits<double>::Max();
		for (int32 Axis = 0; Axis < 3; ++Axis)
		{
			if (SlotSize[Axis] > 0.0)
			{
				Uniform = FMath::Min(Uniform, SlotSize[Axis] / Size[Axis]);
			}
		}
		Scale = FVector(Uniform < TNumericLimits<double>::Max() ? Uniform : 1.0);
		break;
	}
	case EGOFitMode::None:
	default:
		break;
	}

	// Scale is applied in mesh space, before the yaw offset; a quarter turn swaps X and Y.
	const FVector MeshScale = FMath::Abs(FMath::Sin(FMath::DegreesToRadians(YawOffset))) > 0.5
		? FVector(Scale.Y, Scale.X, Scale.Z)
		: Scale;
	const FVector CenterOffset = Slot.GetRotation().RotateVector(Turned.GetCenter() * Scale);
	return FTransform(Slot.GetRotation() * YawQuat, Slot.GetLocation() - CenterOffset, MeshScale);
}

void FGOKitAssets::MakeFallbackParts(EGOFallbackShape Shape, const FVector& SlotSize, TArray<FGOFallbackPart>& OutParts)
{
	OutParts.Reset();
	const double W = SlotSize.X;	// thickness
	const double L = SlotSize.Y;	// length along the wall
	const double H = SlotSize.Z;	// height
	auto AddBox = [&OutParts](const FVector& Center, const FVector& Size, bool bBlocker)
	{
		FGOFallbackPart& Part = OutParts.AddDefaulted_GetRef();
		Part.LocalTransform = FTransform(FQuat::Identity, Center, Size / 100.0);
		Part.bInvisibleBlocker = bBlocker;
	};

	if (Shape == EGOFallbackShape::DoorFrame)
	{
		const double Opening = FMath::Min(L * 0.55, 240.0);
		const double OpeningHeight = FMath::Min(H * 0.65, 280.0);
		const double Jamb = (L - Opening) * 0.5;
		AddBox(FVector(0.0, -(Opening + Jamb) * 0.5, 0.0), FVector(W, Jamb, H), false);
		AddBox(FVector(0.0, (Opening + Jamb) * 0.5, 0.0), FVector(W, Jamb, H), false);
		AddBox(FVector(0.0, 0.0, -H * 0.5 + OpeningHeight + (H - OpeningHeight) * 0.5), FVector(W, Opening, H - OpeningHeight), false);
	}
	else if (Shape == EGOFallbackShape::WindowFrame)
	{
		const double Sill = FMath::Min(100.0, H * 0.25);
		const double Header = FMath::Min(60.0, H * 0.15);
		const double Glass = H - Sill - Header;
		AddBox(FVector(0.0, 0.0, -H * 0.5 + Sill * 0.5), FVector(W, L, Sill), false);
		AddBox(FVector(0.0, 0.0, H * 0.5 - Header * 0.5), FVector(W, L, Header), false);
		// Thin mullions at the slot edges keep neighbouring windows visually separate.
		AddBox(FVector(0.0, -L * 0.5 + 5.0, -H * 0.5 + Sill + Glass * 0.5), FVector(W, 10.0, Glass), false);
		AddBox(FVector(0.0, 0.0, -H * 0.5 + Sill + Glass * 0.5), FVector(W * 0.5, L, Glass), true);
	}
	else
	{
		AddBox(FVector::ZeroVector, SlotSize, false);
	}
}

UMaterialInterface* FGOKitAssets::GetTintedMaterial(UMaterialInterface* Parent, const FLinearColor& Color, UObject* Outer)
{
	UMaterialInterface* Base = Parent ? Parent : LoadObject<UMaterialInterface>(nullptr, GOKitAssetsPrivate::BasicShapeMaterialPath);
	if (!Base)
	{
		return nullptr;
	}

	const FString Key = FString::Printf(TEXT("%s|%.3f|%.3f|%.3f"), *Base->GetPathName(), Color.R, Color.G, Color.B);
	if (UMaterialInstanceDynamic** Found = TintedMaterials.Find(Key))
	{
		return *Found;
	}
	UMaterialInstanceDynamic* Instance = UMaterialInstanceDynamic::Create(Base, Outer);
	Instance->SetVectorParameterValue(TEXT("Color"), Color);
	TintedMaterials.Add(Key, Instance);
	return Instance;
}

void FGOKitAssets::CollectReferencedObjects(TArray<UObject*>& Out) const
{
	for (const TPair<FName, FGOResolvedAsset>& Pair : Resolved)
	{
		if (Pair.Value.Mesh)
		{
			Out.AddUnique(Pair.Value.Mesh);
		}
		if (Pair.Value.ActorClass)
		{
			Out.AddUnique(Pair.Value.ActorClass.Get());
		}
	}
	for (const TPair<FString, UMaterialInstanceDynamic*>& Pair : TintedMaterials)
	{
		Out.AddUnique(Pair.Value);
	}
}
