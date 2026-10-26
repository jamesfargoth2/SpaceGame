#include "Core/GOContentDefs.h"

const FGOSpectralClassDef* FGOContentDefs::FindSpectralClass(EGOSpectralClass Class) const
{
	return SpectralClasses.FindByPredicate([Class](const FGOSpectralClassDef& Def) { return Def.Class == Class; });
}

const FGOPlanetTypeDef* FGOContentDefs::FindPlanetType(EGOPlanetType Type) const
{
	return PlanetTypes.FindByPredicate([Type](const FGOPlanetTypeDef& Def) { return Def.Type == Type; });
}

const FGOBiomeDef* FGOContentDefs::FindBiome(EGOBiome Biome) const
{
	return Biomes.FindByPredicate([Biome](const FGOBiomeDef& Def) { return Def.Biome == Biome; });
}

const FGOHubRoomTemplate* FGOContentDefs::FindRoom(FName TemplateId) const
{
	return HubRooms.FindByPredicate([TemplateId](const FGOHubRoomTemplate& Def) { return Def.TemplateId == TemplateId; });
}

FVector FGOContentDefs::GetFootprint(FName AssetId) const
{
	const FGOAssetKitRow* Row = FindAsset(AssetId);
	return Row ? Row->FootprintCm : FVector(100.0, 100.0, 100.0);
}

bool FGOContentDefs::Validate(TArray<FString>& OutErrors) const
{
	const int32 ErrorsBefore = OutErrors.Num();

	if (SpectralClasses.Num() == 0)
	{
		OutErrors.Add(TEXT("No spectral classes defined."));
	}
	if (PlanetTypes.Num() == 0)
	{
		OutErrors.Add(TEXT("No planet types defined."));
	}
	if (!FindPlanetType(EGOPlanetType::Terran))
	{
		OutErrors.Add(TEXT("Planet type Terran is required for the home world."));
	}
	if (Biomes.Num() == 0)
	{
		OutErrors.Add(TEXT("No biomes defined."));
	}

	const TCHAR* RequiredLists[] = {
		TEXT("starOnsets"), TEXT("starCodas"), TEXT("planetRoots"), TEXT("planetSuffixes"),
		TEXT("personOnsets"), TEXT("personCodas"), TEXT("stationAdjectives"), TEXT("stationNouns")
	};
	for (const TCHAR* List : RequiredLists)
	{
		const TArray<FString>* Values = FindNames(FName(List));
		if (!Values || Values->Num() == 0)
		{
			OutErrors.Add(FString::Printf(TEXT("Name list '%s' is missing or empty."), List));
		}
	}

	bool bHasAtrium = false;
	for (const FGOHubRoomTemplate& Room : HubRooms)
	{
		bHasAtrium |= Room.Placement == EGOHubRoomPlacement::Atrium;
		for (const FGOPropRule& Rule : Room.Props)
		{
			if (!AssetKit.Contains(Rule.AssetId))
			{
				OutErrors.Add(FString::Printf(TEXT("Room '%s' references unknown asset '%s'."),
					*Room.TemplateId.ToString(), *Rule.AssetId.ToString()));
			}
		}
	}
	if (!bHasAtrium)
	{
		OutErrors.Add(TEXT("Hub rooms need one template with Placement = Atrium (the graduation hall)."));
	}

	return OutErrors.Num() == ErrorsBefore;
}
