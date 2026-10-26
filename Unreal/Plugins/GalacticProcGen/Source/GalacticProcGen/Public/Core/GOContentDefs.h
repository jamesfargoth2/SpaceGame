#pragma once

#include "CoreMinimal.h"
#include "Universe/GOContentTypes.h"
#include "Hub/GOHubTypes.h"
#include "Assets/GOAssetKitTypes.h"

/**
 * Every piece of data-driven content the generators read, gathered in one immutable bundle.
 * Filled by UGOContentLibrary (DataTables or the plugin's JSON files) or by tests; generators
 * only ever see this struct, so they run identically in the editor, at runtime and headless.
 */
struct GALACTICPROCGEN_API FGOContentDefs
{
	TArray<FGOSpectralClassDef> SpectralClasses;
	TArray<FGOPlanetTypeDef> PlanetTypes;
	TArray<FGOBiomeDef> Biomes;
	TMap<FName, TArray<FString>> NameLists;
	TArray<FGOHubRoomTemplate> HubRooms;
	TMap<FName, FGOAssetKitRow> AssetKit;

	const FGOSpectralClassDef* FindSpectralClass(EGOSpectralClass Class) const;
	const FGOPlanetTypeDef* FindPlanetType(EGOPlanetType Type) const;
	const FGOBiomeDef* FindBiome(EGOBiome Biome) const;
	const FGOHubRoomTemplate* FindRoom(FName TemplateId) const;
	const FGOAssetKitRow* FindAsset(FName AssetId) const { return AssetKit.Find(AssetId); }
	const TArray<FString>* FindNames(FName ListId) const { return NameLists.Find(ListId); }

	/** Footprint of a kit asset, or a 1 m cube for unknown ids. */
	FVector GetFootprint(FName AssetId) const;

	/** Checks that the minimum content to generate a universe is present. */
	bool Validate(TArray<FString>& OutErrors) const;
};
