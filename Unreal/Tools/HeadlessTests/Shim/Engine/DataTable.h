#pragma once

#include "CoreMinimal.h"

/** Stand-in for Unreal's data-table row base (see Shim/CoreMinimal.h). */
struct FTableRowBase
{
	virtual ~FTableRowBase() = default;
};
