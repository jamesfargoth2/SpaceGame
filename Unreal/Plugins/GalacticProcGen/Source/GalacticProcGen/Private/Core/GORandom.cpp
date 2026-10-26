#include "Core/GORandom.h"

uint64 GOSeed::FromText(const FString& Text)
{
	uint64 Hash = 0xCBF29CE484222325ull;
	for (const TCHAR Ch : Text)
	{
		Hash ^= static_cast<uint64>(static_cast<uint32>(Ch));
		Hash *= 0x100000001B3ull;
	}
	return Mix(Hash);
}

float FGORandom::Gaussian()
{
	// Keep U1 away from zero so the log stays finite.
	const double U1 = FMath::Max(DRand(), 1e-12);
	const double U2 = DRand();
	return static_cast<float>(FMath::Sqrt(-2.0 * FMath::Loge(U1)) * FMath::Cos(2.0 * UE_DOUBLE_PI * U2));
}

int32 FGORandom::PickWeighted(const TArray<float>& Weights)
{
	float Total = 0.f;
	for (const float W : Weights)
	{
		Total += FMath::Max(W, 0.f);
	}
	if (Total <= 0.f)
	{
		return INDEX_NONE;
	}

	const float Roll = FRand() * Total;
	float Cumulative = 0.f;
	int32 LastPositive = INDEX_NONE;
	for (int32 I = 0; I < Weights.Num(); ++I)
	{
		if (Weights[I] <= 0.f)
		{
			continue;
		}
		LastPositive = I;
		Cumulative += Weights[I];
		if (Roll < Cumulative)
		{
			return I;
		}
	}
	return LastPositive;
}
