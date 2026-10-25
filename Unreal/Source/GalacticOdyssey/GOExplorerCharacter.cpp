#include "GOExplorerCharacter.h"

#include "Camera/CameraComponent.h"
#include "Components/CapsuleComponent.h"
#include "Engine/LocalPlayer.h"
#include "EnhancedInputComponent.h"
#include "EnhancedInputSubsystems.h"
#include "GameFramework/CharacterMovementComponent.h"
#include "GameFramework/PlayerController.h"
#include "InputAction.h"
#include "InputActionValue.h"
#include "InputMappingContext.h"
#include "InputModifiers.h"

AGOExplorerCharacter::AGOExplorerCharacter()
{
	GetCapsuleComponent()->InitCapsuleSize(42.f, 96.f);

	Camera = CreateDefaultSubobject<UCameraComponent>(TEXT("Camera"));
	Camera->SetupAttachment(GetCapsuleComponent());
	Camera->SetRelativeLocation(FVector(0.0, 0.0, 64.0));
	Camera->bUsePawnControlRotation = true;

	bUseControllerRotationYaw = true;
	GetCharacterMovement()->bOrientRotationToMovement = false;
	GetCharacterMovement()->MaxWalkSpeed = WalkSpeed;
	GetCharacterMovement()->MaxFlySpeed = FlySpeed;
	GetCharacterMovement()->BrakingDecelerationFlying = 4000.f;
}

void AGOExplorerCharacter::EnsureInputObjects()
{
	if (MappingContext)
	{
		return;
	}

	auto MakeAction = [this](const TCHAR* Name, EInputActionValueType Type)
	{
		UInputAction* Action = NewObject<UInputAction>(this, Name);
		Action->ValueType = Type;
		return Action;
	};
	MoveAction = MakeAction(TEXT("IA_GO_Move"), EInputActionValueType::Axis2D);
	LookAction = MakeAction(TEXT("IA_GO_Look"), EInputActionValueType::Axis2D);
	VerticalAction = MakeAction(TEXT("IA_GO_Vertical"), EInputActionValueType::Axis1D);
	JumpAction = MakeAction(TEXT("IA_GO_Jump"), EInputActionValueType::Boolean);
	FlyAction = MakeAction(TEXT("IA_GO_ToggleFly"), EInputActionValueType::Boolean);
	BoostAction = MakeAction(TEXT("IA_GO_Boost"), EInputActionValueType::Boolean);

	MappingContext = NewObject<UInputMappingContext>(this, TEXT("IMC_GO_Explorer"));
	auto Map = [this](UInputAction* Action, const FKey& Key, bool bSwizzle, bool bNegate)
	{
		FEnhancedActionKeyMapping& Mapping = MappingContext->MapKey(Action, Key);
		if (bSwizzle)
		{
			Mapping.Modifiers.Add(NewObject<UInputModifierSwizzleAxis>(this));	// YXZ: key drives the Y axis
		}
		if (bNegate)
		{
			Mapping.Modifiers.Add(NewObject<UInputModifierNegate>(this));
		}
	};

	Map(MoveAction, EKeys::W, true, false);
	Map(MoveAction, EKeys::S, true, true);
	Map(MoveAction, EKeys::D, false, false);
	Map(MoveAction, EKeys::A, false, true);

	// Sticks get a dead zone (no drift); up on the mouse or right stick looks up.
	auto NegateY = [this]()
	{
		UInputModifierNegate* Negate = NewObject<UInputModifierNegate>(this);
		Negate->bX = false;
		Negate->bY = true;
		Negate->bZ = false;
		return Negate;
	};
	MappingContext->MapKey(MoveAction, EKeys::Gamepad_Left2D).Modifiers.Add(NewObject<UInputModifierDeadZone>(this));
	{
		FEnhancedActionKeyMapping& Stick = MappingContext->MapKey(LookAction, EKeys::Gamepad_Right2D);
		Stick.Modifiers.Add(NewObject<UInputModifierDeadZone>(this));
		Stick.Modifiers.Add(NegateY());
	}
	MappingContext->MapKey(LookAction, EKeys::Mouse2D).Modifiers.Add(NegateY());

	Map(VerticalAction, EKeys::E, false, false);
	Map(VerticalAction, EKeys::Q, false, true);
	Map(JumpAction, EKeys::SpaceBar, false, false);
	Map(JumpAction, EKeys::Gamepad_FaceButton_Bottom, false, false);
	Map(FlyAction, EKeys::F, false, false);
	Map(BoostAction, EKeys::LeftShift, false, false);
}

void AGOExplorerCharacter::PawnClientRestart()
{
	Super::PawnClientRestart();
	EnsureInputObjects();

	if (const APlayerController* PlayerController = Cast<APlayerController>(GetController()))
	{
		if (UEnhancedInputLocalPlayerSubsystem* Subsystem = ULocalPlayer::GetSubsystem<UEnhancedInputLocalPlayerSubsystem>(PlayerController->GetLocalPlayer()))
		{
			Subsystem->RemoveMappingContext(MappingContext);
			Subsystem->AddMappingContext(MappingContext, 0);
		}
	}
}

void AGOExplorerCharacter::SetupPlayerInputComponent(UInputComponent* PlayerInputComponent)
{
	Super::SetupPlayerInputComponent(PlayerInputComponent);
	EnsureInputObjects();

	UEnhancedInputComponent* Input = Cast<UEnhancedInputComponent>(PlayerInputComponent);
	if (!Input)
	{
		return;
	}
	Input->BindAction(MoveAction, ETriggerEvent::Triggered, this, &AGOExplorerCharacter::OnMove);
	Input->BindAction(LookAction, ETriggerEvent::Triggered, this, &AGOExplorerCharacter::OnLook);
	Input->BindAction(VerticalAction, ETriggerEvent::Triggered, this, &AGOExplorerCharacter::OnVertical);
	Input->BindAction(JumpAction, ETriggerEvent::Started, this, &ACharacter::Jump);
	Input->BindAction(JumpAction, ETriggerEvent::Completed, this, &ACharacter::StopJumping);
	Input->BindAction(FlyAction, ETriggerEvent::Started, this, &AGOExplorerCharacter::ToggleFlying);
	Input->BindAction(BoostAction, ETriggerEvent::Started, this, &AGOExplorerCharacter::OnBoostStarted);
	Input->BindAction(BoostAction, ETriggerEvent::Completed, this, &AGOExplorerCharacter::OnBoostCompleted);
}

void AGOExplorerCharacter::OnMove(const FInputActionValue& Value)
{
	if (!Controller)
	{
		return;
	}
	const FVector2D Axis = Value.Get<FVector2D>();
	const FRotator ViewRotation = Controller->GetControlRotation();
	// Walking moves on the ground plane; flying follows the view (look down to descend).
	const FRotator Basis = IsFlying() ? ViewRotation : FRotator(0.0, ViewRotation.Yaw, 0.0);
	const FRotationMatrix Matrix(Basis);
	AddMovementInput(Matrix.GetUnitAxis(EAxis::X), Axis.Y);
	AddMovementInput(Matrix.GetUnitAxis(EAxis::Y), Axis.X);
}

void AGOExplorerCharacter::OnLook(const FInputActionValue& Value)
{
	const FVector2D Axis = Value.Get<FVector2D>();
	AddControllerYawInput(Axis.X);
	AddControllerPitchInput(Axis.Y);
}

void AGOExplorerCharacter::OnVertical(const FInputActionValue& Value)
{
	if (IsFlying())
	{
		AddMovementInput(FVector::UpVector, Value.Get<float>());
	}
}

bool AGOExplorerCharacter::IsFlying() const
{
	return GetCharacterMovement()->IsFlying();
}

void AGOExplorerCharacter::ToggleFlying()
{
	UCharacterMovementComponent* Movement = GetCharacterMovement();
	if (Movement->IsFlying())
	{
		SetActorEnableCollision(true);
		Movement->SetMovementMode(MOVE_Falling);
	}
	else
	{
		SetActorEnableCollision(false);
		Movement->SetMovementMode(MOVE_Flying);
	}
	ApplySpeeds();
}

void AGOExplorerCharacter::OnBoostStarted()
{
	bBoosting = true;
	ApplySpeeds();
}

void AGOExplorerCharacter::OnBoostCompleted()
{
	bBoosting = false;
	ApplySpeeds();
}

void AGOExplorerCharacter::ApplySpeeds()
{
	UCharacterMovementComponent* Movement = GetCharacterMovement();
	Movement->MaxWalkSpeed = bBoosting ? WalkSpeed * 2.f : WalkSpeed;
	Movement->MaxFlySpeed = bBoosting ? FlySpeed * FlyBoostMultiplier : FlySpeed;
	Movement->MaxAcceleration = bBoosting && Movement->IsFlying() ? Movement->MaxFlySpeed * 2.f : 2048.f;
	Movement->BrakingDecelerationFlying = Movement->MaxAcceleration * 2.f;
}
