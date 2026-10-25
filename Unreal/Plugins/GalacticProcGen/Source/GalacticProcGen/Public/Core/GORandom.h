#pragma once

#include "CoreMinimal.h"

/**
 * Hierarchical seed derivation. Every generated thing (star, planet, terrain, hub, prop) gets
 * its own 64-bit seed derived from its parent, so any node of the universe can be regenerated
 * in isolation and in any order. Domain constants are carried over from the Java SeedDeriver
 * so both code bases describe the same seed tree.
 */
namespace GOSeed
{
	constexpr uint64 StarDomain           = 0x6C62272E07BB0142ull;
	constexpr uint64 PlanetDomain         = 0x517CC1B727220A95ull;
	constexpr uint64 MoonDomain           = 0xBF58476D1CE4E5B9ull;
	constexpr uint64 TerrainDomain        = 0x94D049BB133111EBull;
	constexpr uint64 AtmosphereDomain     = 0xC4CEB9FE1A85EC53ull;
	constexpr uint64 BiomeDomain          = 0xD2A98B26625EEE7Bull;
	constexpr uint64 StationDomain        = 0x3C79AC492BA7B653ull;
	constexpr uint64 InteriorDomain       = 0xE7037ED1A0B428DBull;
	constexpr uint64 FactionDomain        = 0x4F6CDD1CB33DA28Dull;
	constexpr uint64 NameDomain           = 0x8C4F9B29D25B9E63ull;
	constexpr uint64 NebulaDomain         = 0xA2F9836E4E441529ull;
	constexpr uint64 NpcDomain            = 0xF2A84C39E71B5D06ull;
	constexpr uint64 FloraDomain          = 0x6A1F4C8B3E7D2059ull;
	constexpr uint64 ContinentNoiseDomain = 0xF0A5C3B2E9D1784Eull;
	constexpr uint64 RidgeNoiseDomain     = 0x3B89D2F1C4A6E057ull;
	constexpr uint64 DetailNoiseDomain    = 0xA7B41E3C9D5F0826ull;

	// Domains introduced by the Unreal client.
	constexpr uint64 GalaxyShapeDomain    = 0x2545F4914F6CDD1Dull;
	constexpr uint64 OrbitDomain          = 0x9E3779B97F4A7C15ull;
	constexpr uint64 StartSelectDomain    = 0x1F83D9ABFB41BD6Bull;
	constexpr uint64 AcademyDomain        = 0x5BE0CD19137E2179ull;
	constexpr uint64 ScatterDomain        = 0xCBBB9D5DC1059ED8ull;
	constexpr uint64 LandingSiteDomain    = 0x629A292A367CD507ull;

	/** SplitMix64 finaliser: a cheap bijective 64-bit mix with good avalanche. */
	FORCEINLINE uint64 Mix(uint64 Z)
	{
		Z = (Z ^ (Z >> 30)) * 0xBF58476D1CE4E5B9ull;
		Z = (Z ^ (Z >> 27)) * 0x94D049BB133111EBull;
		return Z ^ (Z >> 31);
	}

	FORCEINLINE uint64 Domain(uint64 ParentSeed, uint64 DomainConstant)
	{
		return Mix(ParentSeed ^ DomainConstant);
	}

	FORCEINLINE uint64 ForId(uint64 DomainSeed, uint64 Id)
	{
		return Mix(DomainSeed ^ Id);
	}

	FORCEINLINE uint64 ForCell(uint64 DomainSeed, int32 X, int32 Y)
	{
		uint64 H = DomainSeed;
		H ^= static_cast<uint64>(static_cast<int64>(X)) * 0x9E3779B97F4A7C15ull;
		H ^= static_cast<uint64>(static_cast<int64>(Y)) * 0x6C62272E07BB0142ull;
		return Mix(H);
	}

	/** Turns free-form seed text (e.g. typed into a new-game screen) into a seed. FNV-1a. */
	GALACTICPROCGEN_API uint64 FromText(const FString& Text);

	/** Seeds are carried as int64 so they can be exposed to Blueprint. */
	FORCEINLINE int64 ToBlueprint(uint64 Seed) { return static_cast<int64>(Seed); }
	FORCEINLINE uint64 FromBlueprint(int64 Seed) { return static_cast<uint64>(Seed); }
}

/**
 * Small deterministic random stream (SplitMix64). Same call surface as FRandomStream, but with
 * a full 64-bit seed so the hierarchical seeds above do not collapse to 32 bits. Identical
 * seeds produce identical sequences on every platform.
 */
struct GALACTICPROCGEN_API FGORandom
{
	explicit FGORandom(uint64 InSeed = 0) : State(InSeed) {}

	FORCEINLINE uint64 NextU64()
	{
		State += 0x9E3779B97F4A7C15ull;
		return GOSeed::Mix(State);
	}

	FORCEINLINE uint32 NextU32() { return static_cast<uint32>(NextU64() >> 32); }

	/** Uniform float in [0, 1). */
	FORCEINLINE float FRand() { return static_cast<float>(NextU64() >> 40) * (1.0f / 16777216.0f); }

	/** Uniform double in [0, 1). */
	FORCEINLINE double DRand() { return static_cast<double>(NextU64() >> 11) * (1.0 / 9007199254740992.0); }

	/** Uniform float in [Min, Max). */
	FORCEINLINE float FRandRange(float Min, float Max) { return Min + FRand() * (Max - Min); }

	/** Uniform double in [Min, Max). */
	FORCEINLINE double DRandRange(double Min, double Max) { return Min + DRand() * (Max - Min); }

	/** Uniform integer in [0, Count). Returns 0 when Count <= 0. */
	FORCEINLINE int32 RandHelper(int32 Count)
	{
		return Count > 0 ? static_cast<int32>(NextU64() % static_cast<uint64>(Count)) : 0;
	}

	/** Uniform integer in [Min, Max] (inclusive, like FRandomStream::RandRange). */
	FORCEINLINE int32 RandRange(int32 Min, int32 Max)
	{
		return Max > Min ? Min + RandHelper(Max - Min + 1) : Min;
	}

	FORCEINLINE bool Chance(float Probability) { return FRand() < Probability; }

	/** Standard normal sample (Box-Muller). */
	float Gaussian();

	/** Index into Weights picked proportionally to weight; -1 if all weights are <= 0. */
	int32 PickWeighted(const TArray<float>& Weights);

	template <typename T>
	const T& Pick(const TArray<T>& Items)
	{
		check(Items.Num() > 0);
		return Items[RandHelper(Items.Num())];
	}

	template <typename T>
	void Shuffle(TArray<T>& Items)
	{
		for (int32 I = Items.Num() - 1; I > 0; --I)
		{
			const int32 J = RandHelper(I + 1);
			if (I != J)
			{
				Items.Swap(I, J);
			}
		}
	}

	uint64 GetState() const { return State; }

private:
	uint64 State;
};
