#pragma once

#include "CoreMinimal.h"
#include "Modules/ModuleInterface.h"

GALACTICPROCGEN_API DECLARE_LOG_CATEGORY_EXTERN(LogGalacticProcGen, Log, All);

class FGalacticProcGenModule : public IModuleInterface
{
public:
	virtual void StartupModule() override {}
	virtual void ShutdownModule() override {}
};
