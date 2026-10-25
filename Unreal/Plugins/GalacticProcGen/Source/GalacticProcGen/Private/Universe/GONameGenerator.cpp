#include "Universe/GONameGenerator.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"

namespace GONameGeneratorPrivate
{
	enum class ENameKind : uint64
	{
		Star = 0,
		Planet = 1,
		Station = 2,
		Faction = 3,
		Ship = 4,
		Person = 5,
		Galaxy = 6,
		Academy = 7
	};

	FGORandom StreamFor(uint64 Seed, ENameKind Kind)
	{
		return FGORandom(GOSeed::ForId(GOSeed::Domain(Seed, GOSeed::NameDomain), static_cast<uint64>(Kind)));
	}
}

FString FGONameGenerator::Pick(FGORandom& Rng, const TCHAR* ListId, const TCHAR* Fallback) const
{
	const TArray<FString>* Values = Content.FindNames(FName(ListId));
	if (!Values || Values->Num() == 0)
	{
		// Still consume a value so a missing list does not shift the rest of the stream.
		Rng.NextU64();
		return FString(Fallback);
	}
	return (*Values)[Rng.RandHelper(Values->Num())];
}

FString FGONameGenerator::RomanNumeral(int32 Value)
{
	static const int32 Numbers[] = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
	static const TCHAR* Symbols[] = {
		TEXT("M"), TEXT("CM"), TEXT("D"), TEXT("CD"), TEXT("C"), TEXT("XC"), TEXT("L"),
		TEXT("XL"), TEXT("X"), TEXT("IX"), TEXT("V"), TEXT("IV"), TEXT("I")
	};

	FString Result;
	int32 Remaining = FMath::Clamp(Value, 1, 3999);
	for (int32 I = 0; I < UE_ARRAY_COUNT(Numbers); ++I)
	{
		while (Remaining >= Numbers[I])
		{
			Result += Symbols[I];
			Remaining -= Numbers[I];
		}
	}
	return Result;
}

FString FGONameGenerator::GalaxyName(uint64 Seed) const
{
	FGORandom Rng = GONameGeneratorPrivate::StreamFor(Seed, GONameGeneratorPrivate::ENameKind::Galaxy);
	const FString Root = Pick(Rng, TEXT("galaxyRoots"), TEXT("Velar")) + Pick(Rng, TEXT("galaxySuffixes"), TEXT("ion"));
	return Root + TEXT(" ") + Pick(Rng, TEXT("galaxyNouns"), TEXT("Expanse"));
}

FString FGONameGenerator::StarName(uint64 Seed) const
{
	FGORandom Rng = GONameGeneratorPrivate::StreamFor(Seed, GONameGeneratorPrivate::ENameKind::Star);
	const FString Root = Pick(Rng, TEXT("starOnsets"), TEXT("Sol")) + Pick(Rng, TEXT("starCodas"), TEXT(""));
	if (Rng.FRand() < 0.3f)
	{
		return Pick(Rng, TEXT("greekLetters"), TEXT("Alpha")) + TEXT(" ") + Root;
	}
	return Root;
}

FString FGONameGenerator::PlanetName(uint64 Seed) const
{
	FGORandom Rng = GONameGeneratorPrivate::StreamFor(Seed, GONameGeneratorPrivate::ENameKind::Planet);
	const FString Root = Pick(Rng, TEXT("planetRoots"), TEXT("Tera")) + Pick(Rng, TEXT("planetSuffixes"), TEXT("is"));
	if (Rng.FRand() < 0.4f)
	{
		return Root + TEXT(" ") + RomanNumeral(Rng.RandRange(2, 9));
	}
	return Root;
}

FString FGONameGenerator::MoonName(const FString& PlanetName, int32 MoonIndex) const
{
	return PlanetName + TEXT(" ") + FString::Chr(static_cast<TCHAR>(TEXT('a') + FMath::Clamp(MoonIndex, 0, 25)));
}

FString FGONameGenerator::StationName(uint64 Seed) const
{
	FGORandom Rng = GONameGeneratorPrivate::StreamFor(Seed, GONameGeneratorPrivate::ENameKind::Station);
	return Pick(Rng, TEXT("stationAdjectives"), TEXT("Outer")) + TEXT(" ") + Pick(Rng, TEXT("stationNouns"), TEXT("Haven"));
}

FString FGONameGenerator::AcademyName(uint64 Seed) const
{
	FGORandom Rng = GONameGeneratorPrivate::StreamFor(Seed, GONameGeneratorPrivate::ENameKind::Academy);
	const FString Adjective = Pick(Rng, TEXT("stationAdjectives"), TEXT("High"));
	const FString Noun = Pick(Rng, TEXT("stationNouns"), TEXT("Spire"));
	return Adjective + TEXT(" ") + Noun + TEXT(" ") + Pick(Rng, TEXT("academyNouns"), TEXT("Academy"));
}

FString FGONameGenerator::PersonName(uint64 Seed) const
{
	FGORandom Rng = GONameGeneratorPrivate::StreamFor(Seed, GONameGeneratorPrivate::ENameKind::Person);
	const FString First = Pick(Rng, TEXT("personOnsets"), TEXT("Kal")) + Pick(Rng, TEXT("personCodas"), TEXT("en"));
	const FString Last = Pick(Rng, TEXT("personOnsets"), TEXT("Ven")) + Pick(Rng, TEXT("personCodas"), TEXT("or"));
	return First + TEXT(" ") + Last;
}

FString FGONameGenerator::ShipName(uint64 Seed) const
{
	FGORandom Rng = GONameGeneratorPrivate::StreamFor(Seed, GONameGeneratorPrivate::ENameKind::Ship);
	return Pick(Rng, TEXT("shipPrefixes"), TEXT("FCS")) + TEXT(" ") + Pick(Rng, TEXT("shipNames"), TEXT("Horizon"));
}
