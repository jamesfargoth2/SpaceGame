#pragma once

#include "CoreMinimal.h"
#include "GameFramework/HUD.h"
#include "GODebugHUD.generated.h"

/** Prints the generated universe facts (seed, star, home world, academy, altitude) and controls. */
UCLASS()
class GALACTICODYSSEY_API AGODebugHUD : public AHUD
{
	GENERATED_BODY()

public:
	virtual void DrawHUD() override;
};
