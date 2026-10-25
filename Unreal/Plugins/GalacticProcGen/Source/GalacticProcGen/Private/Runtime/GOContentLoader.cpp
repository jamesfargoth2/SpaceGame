#include "Runtime/GOContentLoader.h"

#include "Core/GOContentDefs.h"
#include "Engine/DataTable.h"
#include "Interfaces/IPluginManager.h"
#include "JsonObjectConverter.h"
#include "Misc/FileHelper.h"
#include "Misc/PackageName.h"
#include "Misc/Paths.h"
#include "Runtime/GOUniverseSettings.h"
#include "Serialization/JsonReader.h"
#include "Serialization/JsonSerializer.h"

namespace GOContentLoaderPrivate
{
	template <typename RowT, typename AddFn>
	bool ReadJsonRows(const FString& Path, AddFn&& Add, TArray<FString>& OutMessages)
	{
		FString Text;
		if (!FFileHelper::LoadFileToString(Text, *Path))
		{
			OutMessages.Add(FString::Printf(TEXT("Cannot read %s"), *Path));
			return false;
		}

		TArray<TSharedPtr<FJsonValue>> Values;
		const TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Text);
		if (!FJsonSerializer::Deserialize(Reader, Values))
		{
			OutMessages.Add(FString::Printf(TEXT("%s is not a JSON array: %s"), *Path, *Reader->GetErrorMessage()));
			return false;
		}

		for (const TSharedPtr<FJsonValue>& Value : Values)
		{
			const TSharedPtr<FJsonObject> Object = Value.IsValid() ? Value->AsObject() : nullptr;
			FString Name;
			if (!Object.IsValid() || !Object->TryGetStringField(TEXT("Name"), Name) || Name.IsEmpty())
			{
				OutMessages.Add(FString::Printf(TEXT("%s: row without a \"Name\""), *Path));
				continue;
			}
			RowT Row;
			if (!FJsonObjectConverter::JsonObjectToUStruct(Object.ToSharedRef(), &Row))
			{
				OutMessages.Add(FString::Printf(TEXT("%s: row '%s' does not match %s"), *Path, *Name, *RowT::StaticStruct()->GetName()));
				continue;
			}
			Add(FName(*Name), Row);
		}
		return true;
	}

	template <typename RowT, typename AddFn>
	void LoadTable(const TSoftObjectPtr<UDataTable>& TableRef, const FString& JsonPath, AddFn&& Add, TArray<FString>& OutMessages)
	{
		// Only touch the asset registry/loader for tables that exist, so an unconfigured project
		// (or one where the setup script has not run yet) loads quietly from JSON.
		if (!TableRef.IsNull() && FPackageName::DoesPackageExist(TableRef.ToSoftObjectPath().GetLongPackageName()))
		{
			if (const UDataTable* Table = TableRef.LoadSynchronous())
			{
				if (Table->GetRowStruct() == RowT::StaticStruct())
				{
					Table->ForeachRow<RowT>(TEXT("GOContentLoader"), [&Add](const FName& Key, const RowT& Row) { Add(Key, Row); });
					return;
				}
				OutMessages.Add(FString::Printf(TEXT("%s does not use row struct %s; using %s"),
					*Table->GetPathName(), *RowT::StaticStruct()->GetName(), *JsonPath));
			}
			else
			{
				OutMessages.Add(FString::Printf(TEXT("%s failed to load; using %s"), *TableRef.ToString(), *JsonPath));
			}
		}
		ReadJsonRows<RowT>(JsonPath, Add, OutMessages);
	}

	bool LoadAll(const UGOUniverseSettings* Settings, const FString& Directory, FGOContentDefs& Out, TArray<FString>& OutMessages)
	{
		Out = FGOContentDefs();
		const TSoftObjectPtr<UDataTable> None;
		auto Table = [Settings, &None](TSoftObjectPtr<UDataTable> UGOUniverseSettings::* Member) -> const TSoftObjectPtr<UDataTable>&
		{
			return Settings ? Settings->*Member : None;
		};
		auto File = [&Directory](const TCHAR* Name) { return FPaths::Combine(Directory, Name); };

		LoadTable<FGOSpectralClassDef>(Table(&UGOUniverseSettings::SpectralClassTable), File(TEXT("SpectralClasses.json")),
			[&Out](const FName&, const FGOSpectralClassDef& Row) { Out.SpectralClasses.Add(Row); }, OutMessages);

		LoadTable<FGOPlanetTypeDef>(Table(&UGOUniverseSettings::PlanetTypeTable), File(TEXT("PlanetTypes.json")),
			[&Out](const FName&, const FGOPlanetTypeDef& Row) { Out.PlanetTypes.Add(Row); }, OutMessages);

		LoadTable<FGOBiomeDef>(Table(&UGOUniverseSettings::BiomeTable), File(TEXT("Biomes.json")),
			[&Out](const FName&, const FGOBiomeDef& Row) { Out.Biomes.Add(Row); }, OutMessages);

		LoadTable<FGONameListDef>(Table(&UGOUniverseSettings::NameListTable), File(TEXT("NameLists.json")),
			[&Out](const FName& Key, const FGONameListDef& Row) { Out.NameLists.Add(Key, Row.Values); }, OutMessages);

		LoadTable<FGOHubRoomTemplate>(Table(&UGOUniverseSettings::HubRoomTable), File(TEXT("HubRooms.json")),
			[&Out](const FName& Key, const FGOHubRoomTemplate& Row)
			{
				FGOHubRoomTemplate Copy = Row;
				Copy.TemplateId = Key;
				Out.HubRooms.Add(Copy);
			}, OutMessages);

		LoadTable<FGOAssetKitRow>(Table(&UGOUniverseSettings::AssetKitTable), File(TEXT("AssetKit.json")),
			[&Out](const FName& Key, const FGOAssetKitRow& Row)
			{
				FGOAssetKitRow Copy = Row;
				Copy.AssetId = Key;
				Out.AssetKit.Add(Key, Copy);
			}, OutMessages);

		TArray<FString> Errors;
		const bool bValid = Out.Validate(Errors);
		OutMessages.Append(Errors);
		return bValid;
	}
}

FString GOContentLoader::DefaultDataDirectory()
{
	if (const TSharedPtr<IPlugin> Plugin = IPluginManager::Get().FindPlugin(TEXT("GalacticProcGen")))
	{
		return FPaths::Combine(Plugin->GetBaseDir(), TEXT("Data"));
	}
	return FPaths::Combine(FPaths::ProjectPluginsDir(), TEXT("GalacticProcGen"), TEXT("Data"));
}

bool GOContentLoader::Load(const UGOUniverseSettings& Settings, FGOContentDefs& Out, TArray<FString>& OutMessages)
{
	FString Directory = Settings.FallbackDataDirectory;
	if (Directory.IsEmpty())
	{
		Directory = DefaultDataDirectory();
	}
	else if (FPaths::IsRelative(Directory))
	{
		Directory = FPaths::Combine(FPaths::ProjectDir(), Directory);
	}
	return GOContentLoaderPrivate::LoadAll(&Settings, Directory, Out, OutMessages);
}

bool GOContentLoader::LoadFromJsonDirectory(const FString& Directory, FGOContentDefs& Out, TArray<FString>& OutMessages)
{
	return GOContentLoaderPrivate::LoadAll(nullptr, Directory, Out, OutMessages);
}
