#pragma once

#include "CoreMinimal.h"

struct FGOContentDefs;
class UGOUniverseSettings;

/**
 * Fills FGOContentDefs from the DataTables assigned in UGOUniverseSettings, falling back per
 * table to the JSON files shipped in Plugins/GalacticProcGen/Data. The JSON files use the
 * DataTable import layout ("Name" = row name, other keys = UPROPERTY names), so the same files
 * can be imported as DataTable assets in the editor.
 */
namespace GOContentLoader
{
	GALACTICPROCGEN_API FString DefaultDataDirectory();

	GALACTICPROCGEN_API bool Load(const UGOUniverseSettings& Settings, FGOContentDefs& Out, TArray<FString>& OutMessages);

	GALACTICPROCGEN_API bool LoadFromJsonDirectory(const FString& Directory, FGOContentDefs& Out, TArray<FString>& OutMessages);
}
