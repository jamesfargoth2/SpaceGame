#include "Runtime/GOPlanetActor.h"

#include "Async/Async.h"
#include "Async/ParallelFor.h"
#include "Camera/PlayerCameraManager.h"
#include "Components/DirectionalLightComponent.h"
#include "Components/HierarchicalInstancedStaticMeshComponent.h"
#include "Components/SkyAtmosphereComponent.h"
#include "Core/GOContentDefs.h"
#include "Engine/CollisionProfile.h"
#include "Engine/StaticMesh.h"
#include "Engine/World.h"
#include "GalacticProcGenModule.h"
#include "GameFramework/PlayerController.h"
#include "HAL/PlatformTime.h"
#include "Materials/MaterialInterface.h"
#include "Misc/PackageName.h"
#include "ProceduralMeshComponent.h"

namespace GOPlanetActorPrivate
{
	constexpr double CmPerKm = 100000.0;
	constexpr double RetireGraceSeconds = 1.5;
	const TCHAR* const VertexColorMaterialPath = TEXT("/Engine/EngineDebugMaterials/VertexColorMaterial.VertexColorMaterial");

	template <typename T>
	T* LoadIfExists(const TSoftObjectPtr<T>& Ref)
	{
		if (Ref.IsNull() || !FPackageName::DoesPackageExist(Ref.ToSoftObjectPath().GetLongPackageName()))
		{
			return nullptr;
		}
		return Ref.LoadSynchronous();
	}
}

AGOPlanetActor::AGOPlanetActor()
{
	PrimaryActorTick.bCanEverTick = true;
	PrimaryActorTick.TickGroup = TG_PostUpdateWork;

	Root = CreateDefaultSubobject<USceneComponent>(TEXT("PlanetCenter"));
	Root->SetMobility(EComponentMobility::Movable);
	RootComponent = Root;

	Atmosphere = CreateDefaultSubobject<USkyAtmosphereComponent>(TEXT("Atmosphere"));
	Atmosphere->SetupAttachment(Root);

	Sun = CreateDefaultSubobject<UDirectionalLightComponent>(TEXT("Sun"));
	Sun->SetupAttachment(Root);
	Sun->SetMobility(EComponentMobility::Movable);
	Sun->SetAtmosphereSunLight(true);
}

void AGOPlanetActor::InitializePlanet(TSharedPtr<const FGOContentDefs, ESPMode::ThreadSafe> InContent, const FGOPlanet& InPlanet,
	const FGOStarSystem& InStar, const FVector& LandingDirection, int32 MaxDepth)
{
	using namespace GOPlanetActorPrivate;

	Content = InContent;
	Planet = InPlanet;
	Star = InStar;
	MeshSettings.MaxDepth = MaxDepth;
	Terrain = MakeShared<FGOPlanetTerrain, ESPMode::ThreadSafe>(Planet, *Content);
	Quadtree = MakeUnique<FGOTerrainQuadtree>(Terrain->GetRadiusKm(), MaxDepth, SplitThreshold, MergeThreshold);

	SurfaceMaterialInstance = LoadIfExists(SurfaceMaterial);
	if (!SurfaceMaterialInstance)
	{
		SurfaceMaterialInstance = LoadObject<UMaterialInterface>(nullptr, VertexColorMaterialPath);
		UE_LOG(LogGalacticProcGen, Log, TEXT("No planet surface material found; using the engine vertex-colour material. Run Tools/SetupGalacticOdyssey.py in the editor for a lit terrain material."));
	}
	GreyboxMaterialInstance = LoadIfExists(GreyboxMaterial);

	// Scatter meshes are small and few: load them up front so chunk uploads never hitch.
	Kit = MakeUnique<FGOKitAssets>(*Content);
	TArray<FName> ScatterIds;
	for (const TPair<FName, FGOAssetKitRow>& Pair : Content->AssetKit)
	{
		if (Pair.Value.Category == EGOKitCategory::Scatter)
		{
			ScatterIds.Add(Pair.Key);
		}
	}
	Kit->LoadSync(ScatterIds);
	TArray<UObject*> Referenced;
	Kit->CollectReferencedObjects(Referenced);
	ReferencedAssets.Append(Referenced);

	// Start the day with the sun mid-morning over the landing site.
	const FVector Landing = LandingDirection.GetSafeNormal();
	SunHourAngle = FMath::Atan2(Landing.Y, Landing.X) - 0.6;
	SunDeclination = FMath::DegreesToRadians(Planet.AxialTiltDeg * 0.5);

	const float StarFlux = Star.Luminosity / FMath::Max(Planet.OrbitRadiusAU * Planet.OrbitRadiusAU, 1e-4f);
	Sun->SetIntensity(SunIlluminanceLux * FMath::Clamp(StarFlux, 0.05f, 20.f));
	Sun->SetUseTemperature(true);
	Sun->SetTemperature(FMath::Clamp(Star.TemperatureK, 1700.f, 12000.f));
	UpdateSun(0.f);

	ConfigureAtmosphere();

	UE_LOG(LogGalacticProcGen, Log, TEXT("Planet %s: radius %.0f km, %s, %.2f g."),
		*Planet.Name, Terrain->GetRadiusKm(), *GOUniverseText::PlanetTypeName(Planet.Type), Planet.SurfaceGravityG);
}

void AGOPlanetActor::ConfigureAtmosphere()
{
	const FGOAtmosphere& Atmo = Planet.Atmosphere;
	if (!bSpawnAtmosphere || !Atmo.bPresent || Atmo.SurfacePressureAtm < 0.01f)
	{
		Atmosphere->SetVisibility(false);
		return;
	}

	const float Gravity = FMath::Max(Planet.SurfaceGravityG, 0.1f);
	const float TempRatio = FMath::Clamp(Atmo.SurfaceTempK / 288.f, 0.3f, 3.f);
	const float Pressure = FMath::Clamp(Atmo.SurfacePressureAtm, 0.05f, 10.f);
	const float SO2 = Atmo.GetFraction(EGOGas::SO2);
	const float H2O = Atmo.GetFraction(EGOGas::H2O);
	const float CO2 = Atmo.GetFraction(EGOGas::CO2);

	// Planet-scale sky: the component's transform is the planet centre. The engine UI clamps the
	// ground radius to 10000 km; larger worlds get a slightly smaller sky shell.
	Atmosphere->TransformMode = ESkyAtmosphereTransformMode::PlanetCenterAtComponentTransform;
	Atmosphere->BottomRadius = static_cast<float>(FMath::Min(Terrain->GetRadiusKm(), 9999.0));
	// Scale height ~ T / g: thin, cold or heavy worlds hug the ground.
	Atmosphere->AtmosphereHeight = FMath::Clamp(60.f * TempRatio / Gravity, 20.f, 200.f);
	Atmosphere->RayleighExponentialDistribution = FMath::Clamp(8.f * TempRatio / Gravity, 2.f, 30.f);
	Atmosphere->RayleighScatteringScale = 0.0331f * Pressure;

	// Sulphur, water and CO2 thicken the haze; sulphur also yellows it.
	const float Haze = 1.f + 6.f * SO2 + 2.f * H2O + CO2;
	Atmosphere->MieScatteringScale = 0.003996f * Pressure * Haze;
	Atmosphere->MieAbsorptionScale = 0.000444f * Pressure * (1.f + 10.f * SO2);
	if (SO2 > 0.1f)
	{
		Atmosphere->MieScattering = FLinearColor(1.f, 0.82f, 0.45f);
	}
	Atmosphere->MarkRenderStateDirty();
}

void AGOPlanetActor::UpdateSun(float DeltaSeconds)
{
	const double DaySeconds = FMath::Max(static_cast<double>(Planet.DayLengthHours), 0.1) * 3600.0;
	SunHourAngle += DeltaSeconds * DayNightTimeScale * 2.0 * UE_DOUBLE_PI / DaySeconds;

	// The planet stays still and the sun circles its axis: same lighting, no moving colliders.
	const FVector SunLocal(
		FMath::Cos(SunDeclination) * FMath::Cos(SunHourAngle),
		FMath::Cos(SunDeclination) * FMath::Sin(SunHourAngle),
		FMath::Sin(SunDeclination));
	const FVector SunWorld = GetActorQuat().RotateVector(SunLocal);
	Sun->SetWorldRotation((-SunWorld).Rotation());
}

FVector AGOPlanetActor::WorldToPlanetKm(const FVector& WorldLocation) const
{
	return GetActorTransform().InverseTransformPosition(WorldLocation) / GOPlanetActorPrivate::CmPerKm;
}

bool AGOPlanetActor::CameraWorldLocation(FVector& OutLocation) const
{
	const UWorld* World = GetWorld();
	const APlayerController* Controller = World ? World->GetFirstPlayerController() : nullptr;
	if (Controller && Controller->PlayerCameraManager)
	{
		OutLocation = Controller->PlayerCameraManager->GetCameraLocation();
		return true;
	}
	return false;
}

double AGOPlanetActor::GetRadiusKm() const
{
	return Terrain.IsValid() ? Terrain->GetRadiusKm() : Planet.RadiusKm;
}

double AGOPlanetActor::GetAltitudeKm(FVector WorldLocation) const
{
	const FVector Local = WorldToPlanetKm(WorldLocation);
	if (!Terrain.IsValid())
	{
		return Local.Size() - Planet.RadiusKm;
	}
	return Local.Size() - Terrain->SurfaceRadiusKm(Local.GetSafeNormal(), MeshSettings.MaxDepth);
}

FVector AGOPlanetActor::GetSurfacePointWorld(FVector PlanetLocalDirection) const
{
	const FVector Dir = PlanetLocalDirection.GetSafeNormal();
	const double RadiusKm = Terrain.IsValid() ? Terrain->SurfaceRadiusKm(Dir, MeshSettings.MaxDepth) : Planet.RadiusKm;
	return GetActorTransform().TransformPosition(Dir * RadiusKm * GOPlanetActorPrivate::CmPerKm);
}

void AGOPlanetActor::UpdateLeaves(const FVector& CameraKm)
{
	LastLodCameraKm = CameraKm;
	Quadtree->Update(CameraKm);

	DesiredKeys.Reset();
	for (const FGOTerrainChunkId& Leaf : Quadtree->GetLeaves())
	{
		DesiredKeys.Add(Leaf.Key());
	}

	// Chunks that became leaves again before being released are simply reused.
	for (auto It = Retiring.CreateIterator(); It; ++It)
	{
		if (DesiredKeys.Contains(It.Key()) && !Chunks.Contains(It.Key()))
		{
			Chunks.Add(It.Key(), It.Value());
			It.RemoveCurrent();
		}
	}

	// Chunks that left the leaf set stay visible until their replacements arrive.
	const double Now = FPlatformTime::Seconds();
	for (auto It = Chunks.CreateIterator(); It; ++It)
	{
		if (!DesiredKeys.Contains(It.Key()))
		{
			It.Value().RetiredAtSeconds = Now;
			Retiring.Add(It.Key(), It.Value());
			It.RemoveCurrent();
		}
	}
}

void AGOPlanetActor::Tick(float DeltaSeconds)
{
	Super::Tick(DeltaSeconds);
	if (!Terrain.IsValid())
	{
		return;
	}
	UpdateSun(DeltaSeconds);
	UpdateTerrain();
}

void AGOPlanetActor::UpdateTerrain()
{
	using namespace GOPlanetActorPrivate;

	FVector CameraWorld;
	if (CameraWorldLocation(CameraWorld))
	{
		const FVector CameraKm = WorldToPlanetKm(CameraWorld);
		const double Altitude = FMath::Max(CameraKm.Size() - Terrain->GetRadiusKm(), 0.001);
		// Re-evaluate the quadtree once the camera has moved a few percent of its altitude.
		if (FVector::Dist(CameraKm, LastLodCameraKm) > FMath::Max(0.002, Altitude * 0.05))
		{
			UpdateLeaves(CameraKm);
		}
	}

	int32 Uploads = 0;
	for (int32 Index = 0; Index < Pending.Num() && Uploads < MaxUploadsPerFrame;)
	{
		if (!Pending[Index].Future.IsReady())
		{
			++Index;
			continue;
		}
		const TSharedPtr<FGOChunkMeshData, ESPMode::ThreadSafe> Data = Pending[Index].Future.Get();
		const uint64 Key = Pending[Index].Id.Key();
		PendingKeys.Remove(Key);
		Pending.RemoveAtSwap(Index);
		if (Data.IsValid() && DesiredKeys.Contains(Key) && !Chunks.Contains(Key))
		{
			ApplyChunk(*Data);
			++Uploads;
		}
	}

	if (Pending.Num() < MaxBuildsInFlight)
	{
		TArray<TPair<double, FGOTerrainChunkId>> Missing;
		for (const FGOTerrainChunkId& Leaf : Quadtree->GetLeaves())
		{
			const uint64 Key = Leaf.Key();
			if (!Chunks.Contains(Key) && !PendingKeys.Contains(Key))
			{
				Missing.Add(TPair<double, FGOTerrainChunkId>(Quadtree->NearestDistanceKm(Leaf, LastLodCameraKm), Leaf));
			}
		}
		Missing.Sort([](const TPair<double, FGOTerrainChunkId>& A, const TPair<double, FGOTerrainChunkId>& B) { return A.Key < B.Key; });
		for (int32 Index = 0; Index < Missing.Num() && Pending.Num() < MaxBuildsInFlight; ++Index)
		{
			Dispatch(Missing[Index].Value);
		}
	}

	// Drop retired chunks once the new ones are in (or after a short grace period while moving).
	const double Now = FPlatformTime::Seconds();
	for (auto It = Retiring.CreateIterator(); It; ++It)
	{
		if (Pending.Num() == 0 || Now - It.Value().RetiredAtSeconds > RetireGraceSeconds)
		{
			ReleaseChunk(It.Value());
			It.RemoveCurrent();
		}
	}
}

void AGOPlanetActor::Dispatch(const FGOTerrainChunkId& Id)
{
	TSharedPtr<const FGOPlanetTerrain, ESPMode::ThreadSafe> LocalTerrain = Terrain;
	TSharedPtr<const FGOContentDefs, ESPMode::ThreadSafe> LocalContent = Content;
	const FGOTerrainMeshSettings Settings = MeshSettings;

	FGOPendingChunkBuild& Build = Pending.AddDefaulted_GetRef();
	Build.Id = Id;
	Build.Future = Async(EAsyncExecution::ThreadPool, [LocalTerrain, LocalContent, Settings, Id]()
	{
		TSharedPtr<FGOChunkMeshData, ESPMode::ThreadSafe> Data = MakeShared<FGOChunkMeshData, ESPMode::ThreadSafe>();
		GOTerrainMesh::BuildChunk(*LocalTerrain, *LocalContent, Id, Settings, *Data);
		return Data;
	});
	PendingKeys.Add(Id.Key());
}

void AGOPlanetActor::PrewarmForCamera(const FVector& CameraWorldLocationIn)
{
	if (!Terrain.IsValid())
	{
		return;
	}

	const double StartTime = FPlatformTime::Seconds();
	UpdateLeaves(WorldToPlanetKm(CameraWorldLocationIn));

	TArray<FGOTerrainChunkId> ToBuild;
	for (const FGOTerrainChunkId& Leaf : Quadtree->GetLeaves())
	{
		if (!Chunks.Contains(Leaf.Key()))
		{
			ToBuild.Add(Leaf);
		}
	}

	TArray<FGOChunkMeshData> Built;
	Built.SetNum(ToBuild.Num());
	const FGOPlanetTerrain& TerrainRef = *Terrain;
	const FGOContentDefs& ContentRef = *Content;
	const FGOTerrainMeshSettings Settings = MeshSettings;
	ParallelFor(ToBuild.Num(), [&](int32 Index)
	{
		GOTerrainMesh::BuildChunk(TerrainRef, ContentRef, ToBuild[Index], Settings, Built[Index]);
	});
	bPrewarming = true;
	for (const FGOChunkMeshData& Data : Built)
	{
		ApplyChunk(Data);
	}
	bPrewarming = false;
	for (auto It = Retiring.CreateIterator(); It; ++It)
	{
		ReleaseChunk(It.Value());
		It.RemoveCurrent();
	}

	UE_LOG(LogGalacticProcGen, Log, TEXT("Pre-built %d terrain chunks in %.0f ms."), Built.Num(), (FPlatformTime::Seconds() - StartTime) * 1000.0);
}

UProceduralMeshComponent* AGOPlanetActor::AcquireMeshComponent()
{
	if (FreeMeshes.Num() > 0)
	{
		UProceduralMeshComponent* Reused = FreeMeshes.Pop();
		Reused->SetVisibility(true);
		return Reused;
	}

	UProceduralMeshComponent* Mesh = NewObject<UProceduralMeshComponent>(this, NAME_None, RF_Transient);
	Mesh->SetupAttachment(Root);
	Mesh->SetMobility(EComponentMobility::Movable);
	Mesh->SetCastShadow(true);
	Mesh->RegisterComponent();
	AddInstanceComponent(Mesh);
	RuntimeComponents.Add(Mesh);
	return Mesh;
}

void AGOPlanetActor::ApplyChunk(const FGOChunkMeshData& Data)
{
	FGOPlanetChunkState Chunk;
	Chunk.Id = Data.Id;
	Chunk.Mesh = AcquireMeshComponent();

	const bool bCollision = Data.Id.Depth >= CollisionMinDepth;
	Chunk.Mesh->bUseAsyncCooking = !bPrewarming;
	Chunk.Mesh->SetRelativeLocation(Data.OriginCm);
	Chunk.Mesh->CreateMeshSection_LinearColor(0, Data.Positions, Data.Triangles, Data.Normals, Data.UVs, Data.Colors,
		TArray<FProcMeshTangent>(), bCollision);
	Chunk.Mesh->SetCollisionProfileName(bCollision ? UCollisionProfile::BlockAll_ProfileName : UCollisionProfile::NoCollision_ProfileName);
	if (SurfaceMaterialInstance)
	{
		Chunk.Mesh->SetMaterial(0, SurfaceMaterialInstance);
	}

	if (Data.Scatter.Num() > 0 && Kit.IsValid())
	{
		TMap<FName, TArray<FTransform>> Groups;
		for (const FGOScatterInstance& Instance : Data.Scatter)
		{
			const FGOResolvedAsset Asset = Kit->Resolve(Instance.AssetId);
			Groups.FindOrAdd(Instance.AssetId).Add(FGOKitAssets::FitToSlot(Asset, Instance.Transform, Instance.SlotSizeCm));
		}

		for (const TPair<FName, TArray<FTransform>>& Group : Groups)
		{
			const FGOResolvedAsset Asset = Kit->Resolve(Group.Key);
			if (!Asset.Mesh)
			{
				continue;
			}
			const bool bScatterCollision = Asset.Row ? Asset.Row->bCollision : false;
			const float Cull = Asset.Row ? Asset.Row->CullDistanceCm : 0.f;

			UHierarchicalInstancedStaticMeshComponent* Instances = NewObject<UHierarchicalInstancedStaticMeshComponent>(this, NAME_None, RF_Transient);
			Instances->SetupAttachment(Root);
			Instances->SetMobility(EComponentMobility::Movable);
			Instances->SetRelativeLocation(Data.OriginCm);
			Instances->SetStaticMesh(Asset.Mesh);
			if (Asset.bFallback && Asset.Row)
			{
				Instances->SetMaterial(0, Kit->GetTintedMaterial(GreyboxMaterialInstance, Asset.Row->FallbackColor, this));
			}
			Instances->SetCollisionProfileName(bScatterCollision ? UCollisionProfile::BlockAll_ProfileName : UCollisionProfile::NoCollision_ProfileName);
			if (Cull > 0.f)
			{
				Instances->SetCullDistances(FMath::RoundToInt(Cull * 0.8f), FMath::RoundToInt(Cull));
			}
			Instances->RegisterComponent();
			AddInstanceComponent(Instances);
			Instances->AddInstances(Group.Value, /*bShouldReturnIndices*/ false);
			RuntimeComponents.Add(Instances);
			Chunk.Scatter.Add(Instances);
		}

		TArray<UObject*> Referenced;
		Kit->CollectReferencedObjects(Referenced);
		ReferencedAssets.Reset();
		ReferencedAssets.Append(Referenced);
	}

	Chunks.Add(Data.Id.Key(), Chunk);
}

void AGOPlanetActor::ReleaseChunk(FGOPlanetChunkState& Chunk)
{
	if (Chunk.Mesh)
	{
		Chunk.Mesh->ClearAllMeshSections();
		Chunk.Mesh->SetVisibility(false);
		FreeMeshes.Add(Chunk.Mesh);
		Chunk.Mesh = nullptr;
	}
	for (UHierarchicalInstancedStaticMeshComponent* Instances : Chunk.Scatter)
	{
		if (Instances)
		{
			RuntimeComponents.RemoveSwap(Instances);
			Instances->DestroyComponent();
		}
	}
	Chunk.Scatter.Reset();
}

void AGOPlanetActor::EndPlay(const EEndPlayReason::Type EndPlayReason)
{
	// Worker tasks own shared copies of the terrain and content, so they can finish safely.
	Pending.Reset();
	PendingKeys.Reset();
	Super::EndPlay(EndPlayReason);
}
