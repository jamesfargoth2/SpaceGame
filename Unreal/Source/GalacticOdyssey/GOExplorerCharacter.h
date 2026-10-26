#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Character.h"
#include "GOExplorerCharacter.generated.h"

class UCameraComponent;
class UInputAction;
class UInputMappingContext;
struct FInputActionValue;

/**
 * First-person explorer for walking the generated academy and planet. Enhanced Input actions and
 * the mapping context are created in code (WASD/mouse, Space jump, F toggles fly, Shift boosts,
 * E/Q up/down while flying), so the project runs without any input assets. Swap in your own
 * pawn via the GameMode's DefaultPawnClass when the real player controller exists.
 */
UCLASS()
class GALACTICODYSSEY_API AGOExplorerCharacter : public ACharacter
{
	GENERATED_BODY()

public:
	AGOExplorerCharacter();

	virtual void SetupPlayerInputComponent(UInputComponent* PlayerInputComponent) override;
	virtual void PawnClientRestart() override;

	/** Noclip flight, e.g. to leave the academy and descend to the planet. */
	UFUNCTION(BlueprintCallable, Category = "Galactic|Explorer")
	void ToggleFlying();

	UFUNCTION(BlueprintPure, Category = "Galactic|Explorer")
	bool IsFlying() const;

protected:
	UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "Galactic|Explorer")
	TObjectPtr<UCameraComponent> Camera;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Explorer")
	float WalkSpeed = 600.f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Explorer")
	float FlySpeed = 3000.f;

	/** Flight speed multiplier while Shift is held (orbit to ground in well under a minute). */
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Galactic|Explorer")
	float FlyBoostMultiplier = 500.f;

private:
	void EnsureInputObjects();
	void ApplySpeeds();
	void OnMove(const FInputActionValue& Value);
	void OnLook(const FInputActionValue& Value);
	void OnVertical(const FInputActionValue& Value);
	void OnBoostStarted();
	void OnBoostCompleted();

	UPROPERTY(Transient)
	TObjectPtr<UInputMappingContext> MappingContext;

	UPROPERTY(Transient)
	TObjectPtr<UInputAction> MoveAction;

	UPROPERTY(Transient)
	TObjectPtr<UInputAction> LookAction;

	UPROPERTY(Transient)
	TObjectPtr<UInputAction> VerticalAction;

	UPROPERTY(Transient)
	TObjectPtr<UInputAction> JumpAction;

	UPROPERTY(Transient)
	TObjectPtr<UInputAction> FlyAction;

	UPROPERTY(Transient)
	TObjectPtr<UInputAction> BoostAction;

	bool bBoosting = false;
};
