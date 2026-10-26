#include "Universe/GOPlanetGenerator.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"
#include "Universe/GONameGenerator.h"

namespace GOPlanetGeneratorPrivate
{
	float MolecularMass(EGOGas Gas)
	{
		switch (Gas)
		{
		case EGOGas::N2:	return 28.f;
		case EGOGas::O2:	return 32.f;
		case EGOGas::CO2:	return 44.f;
		case EGOGas::H2O:	return 18.f;
		case EGOGas::SO2:	return 64.f;
		case EGOGas::HCl:	return 36.5f;
		case EGOGas::Ar:	return 40.f;
		case EGOGas::CH4:	return 16.f;
		case EGOGas::H2:	return 2.f;
		case EGOGas::He:	return 4.f;
		case EGOGas::NH3:	return 17.f;
		default:			return 28.f;
		}
	}

	/** Jeans escape: a gas is retained if escape velocity exceeds six times its thermal speed. */
	bool GasRetained(float EscapeVelocityKmS, float TempK, float MolecularMassAmu)
	{
		const float ThermalSpeed = 0.157f * FMath::Sqrt(TempK / MolecularMassAmu);
		return EscapeVelocityKmS > 6.f * ThermalSpeed;
	}

	float EquilibriumTemp(float StarLuminosity, float OrbitRadiusAU)
	{
		return 278.f * FMath::Pow(FMath::Max(StarLuminosity, 1e-6f), 0.25f) / FMath::Sqrt(FMath::Max(OrbitRadiusAU, 1e-3f));
	}

	void AddGas(TArray<FGOGasFraction>& Composition, EGOGas Gas, float Fraction)
	{
		if (Fraction > 0.f)
		{
			FGOGasFraction& Entry = Composition.AddDefaulted_GetRef();
			Entry.Gas = Gas;
			Entry.Fraction = Fraction;
		}
	}

	TArray<EGOBiome> AllowedBiomes(EGOPlanetType Type)
	{
		switch (Type)
		{
		case EGOPlanetType::Ocean:
			return { EGOBiome::Ocean, EGOBiome::IceSheet, EGOBiome::Swamp, EGOBiome::TropicalForest };
		case EGOPlanetType::Arid:
			return { EGOBiome::Desert, EGOBiome::AridShrub, EGOBiome::Badlands, EGOBiome::RockyWaste, EGOBiome::Steppe, EGOBiome::PolarDesert };
		case EGOPlanetType::Toxic:
			return { EGOBiome::Volcanic, EGOBiome::Badlands, EGOBiome::RockyWaste };
		case EGOPlanetType::IceWorld:
			return { EGOBiome::IceSheet, EGOBiome::IceField, EGOBiome::PolarDesert, EGOBiome::Tundra };
		case EGOPlanetType::Barren:
		case EGOPlanetType::Dwarf:
			return { EGOBiome::RockyWaste, EGOBiome::Desert, EGOBiome::PolarDesert };
		case EGOPlanetType::Molten:
			return { EGOBiome::Volcanic };
		case EGOPlanetType::Terran:
		{
			TArray<EGOBiome> All;
			for (int32 I = 0; I <= static_cast<int32>(EGOBiome::River); ++I)
			{
				All.Add(static_cast<EGOBiome>(I));
			}
			return All;
		}
		default:
			return { EGOBiome::RockyWaste };
		}
	}
}

EGOPlanetType FGOPlanetGenerator::RollPlanetType(FGORandom& Rng, EGOOrbitalZone Zone) const
{
	TArray<float> Weights;
	Weights.Reserve(Content.PlanetTypes.Num());
	for (const FGOPlanetTypeDef& Def : Content.PlanetTypes)
	{
		Weights.Add(Def.Zones.Contains(Zone) ? Def.Weight : 0.f);
	}
	const int32 Index = Rng.PickWeighted(Weights);
	return Index != INDEX_NONE ? Content.PlanetTypes[Index].Type : EGOPlanetType::Barren;
}

FGOPlanet FGOPlanetGenerator::Generate(const FGOStarSystem& System, const FGOOrbitalSlot& Slot) const
{
	using namespace GOPlanetGeneratorPrivate;

	const uint64 PlanetSeed = GOSeed::ForId(
		GOSeed::Domain(GOSeed::FromBlueprint(System.Seed), GOSeed::PlanetDomain), static_cast<uint64>(Slot.Index));
	FGORandom Rng(PlanetSeed);

	FGOPlanet Planet;
	Planet.Seed = GOSeed::ToBlueprint(PlanetSeed);
	Planet.OrbitIndex = Slot.Index;
	Planet.OrbitRadiusAU = Slot.RadiusAU;
	Planet.Eccentricity = Slot.Eccentricity;
	Planet.Zone = Slot.Zone;
	Planet.Type = RollPlanetType(Rng, Slot.Zone);

	const FGOPlanetTypeDef* Def = Content.FindPlanetType(Planet.Type);
	Planet.RadiusEarth = Def ? Rng.FRandRange(Def->RadiusMin, Def->RadiusMax) : Rng.FRandRange(0.5f, 1.5f);
	Planet.RadiusKm = Planet.RadiusEarth * GOPlanetConstants::EarthRadiusKm;
	Planet.DensityGcc = Def ? Rng.FRandRange(Def->DensityMin, Def->DensityMax) : 5.f;
	Planet.MassEarth = (Planet.DensityGcc / GOPlanetConstants::EarthDensityGcc)
		* Planet.RadiusEarth * Planet.RadiusEarth * Planet.RadiusEarth;
	Planet.SurfaceGravityG = Planet.MassEarth / (Planet.RadiusEarth * Planet.RadiusEarth);
	Planet.EscapeVelocityKmS = 11.2f * FMath::Sqrt(Planet.MassEarth / Planet.RadiusEarth);

	Planet.DayLengthHours = FMath::Clamp(
		24.f / FMath::Sqrt(FMath::Max(Planet.MassEarth, 0.01f)) * Rng.FRandRange(0.5f, 2.f), 2.f, 2000.f);
	Planet.AxialTiltDeg = FMath::Min(FMath::Abs(Rng.Gaussian() * 12.f), 90.f);

	if (Slot.Zone == EGOOrbitalZone::Inner)
	{
		const float LockChance = System.SpectralClass == EGOSpectralClass::M ? 0.7f : 0.3f;
		Planet.bTidallyLocked = Rng.FRand() < LockChance;
	}
	if (Planet.bTidallyLocked)
	{
		Planet.DayLengthHours = Slot.PeriodYears * 365.25f * 24.f;
	}

	FGONameGenerator Names(Content);
	Planet.Name = Names.PlanetName(PlanetSeed);

	const int32 MoonMin = Def ? Def->MoonMin : 0;
	const int32 MoonMax = Def ? Def->MoonMax : 0;
	const int32 MoonCount = Rng.RandRange(MoonMin, FMath::Max(MoonMin, MoonMax));
	for (int32 MoonIndex = 0; MoonIndex < MoonCount; ++MoonIndex)
	{
		const uint64 MoonSeed = GOSeed::ForId(GOSeed::Domain(PlanetSeed, GOSeed::MoonDomain), static_cast<uint64>(MoonIndex));
		FGORandom MoonRng(MoonSeed);

		FGOMoon& Moon = Planet.Moons.AddDefaulted_GetRef();
		Moon.Seed = GOSeed::ToBlueprint(MoonSeed);
		Moon.Name = Names.MoonName(Planet.Name, MoonIndex);
		Moon.Type = MoonRng.FRand() < 0.5f ? EGOPlanetType::Barren : EGOPlanetType::IceWorld;
		const float MoonRadiusEarth = MoonRng.FRandRange(0.05f, FMath::Max(0.06f, Planet.RadiusEarth * 0.3f));
		Moon.RadiusKm = MoonRadiusEarth * GOPlanetConstants::EarthRadiusKm;
		Moon.OrbitRadiusKm = (0.002 + MoonIndex * 0.002) * MoonRng.FRandRange(0.8f, 1.2f) * GOPlanetConstants::AstronomicalUnitKm;
		Moon.Eccentricity = MoonRng.FRand() * 0.1f;
		Moon.InclinationRad = MoonRng.Gaussian() * 0.1f;
	}

	Planet.Atmosphere = GenerateAtmosphere(Planet, System);
	Planet.Biomes = GenerateBiomeParams(Planet);
	Planet.HabitabilityScore = ScoreHabitability(Planet);
	return Planet;
}

TArray<FGOPlanet> FGOPlanetGenerator::GenerateAll(const FGOStarSystem& System) const
{
	TArray<FGOPlanet> Planets;
	Planets.Reserve(System.Orbits.Num());
	for (const FGOOrbitalSlot& Slot : System.Orbits)
	{
		Planets.Add(Generate(System, Slot));
	}
	return Planets;
}

FGOAtmosphere FGOPlanetGenerator::GenerateAtmosphere(const FGOPlanet& Planet, const FGOStarSystem& System) const
{
	using namespace GOPlanetGeneratorPrivate;

	FGOAtmosphere Atmo;
	Atmo.EquilibriumTempK = EquilibriumTemp(System.Luminosity, Planet.OrbitRadiusAU);
	Atmo.SurfaceTempK = Atmo.EquilibriumTempK;

	if (Planet.Type == EGOPlanetType::Molten || Planet.Type == EGOPlanetType::Dwarf
		|| Planet.Type == EGOPlanetType::GasGiant || Planet.Type == EGOPlanetType::IceGiant)
	{
		Atmo.Hazards.Add(EGOAtmoHazard::Vacuum);
		return Atmo;
	}

	FGORandom Rng(GOSeed::ForId(GOSeed::Domain(GOSeed::FromBlueprint(Planet.Seed), GOSeed::AtmosphereDomain), 0));
	if ((Planet.Type == EGOPlanetType::Barren && Rng.FRand() >= 0.1f)
		|| (Planet.Type == EGOPlanetType::IceWorld && Rng.FRand() >= 0.3f))
	{
		Atmo.Hazards.Add(EGOAtmoHazard::Vacuum);
		return Atmo;
	}

	TArray<FGOGasFraction> Composition;
	float VolatileInventory = 0.f;
	switch (Planet.Type)
	{
	case EGOPlanetType::Arid:
	{
		const float CO2 = Rng.FRandRange(0.75f, 0.85f);
		const float N2 = Rng.FRandRange(0.10f, 0.20f);
		AddGas(Composition, EGOGas::CO2, CO2);
		AddGas(Composition, EGOGas::N2, N2);
		AddGas(Composition, EGOGas::Ar, FMath::Max(0.f, 1.f - CO2 - N2));
		VolatileInventory = Rng.FRandRange(0.05f, 0.5f);
		break;
	}
	case EGOPlanetType::Terran:
	{
		const float N2 = Rng.FRandRange(0.60f, 0.80f);
		const float O2 = Rng.FRandRange(0.15f, 0.25f);
		AddGas(Composition, EGOGas::N2, N2);
		AddGas(Composition, EGOGas::O2, O2);
		AddGas(Composition, EGOGas::Ar, FMath::Max(0.f, 1.f - N2 - O2));
		VolatileInventory = Rng.FRandRange(0.6f, 1.6f);
		break;
	}
	case EGOPlanetType::Ocean:
	{
		const float N2 = Rng.FRandRange(0.50f, 0.70f);
		const float H2O = Rng.FRandRange(0.20f, 0.40f);
		AddGas(Composition, EGOGas::N2, N2);
		AddGas(Composition, EGOGas::H2O, H2O);
		AddGas(Composition, EGOGas::CO2, FMath::Max(0.f, 1.f - N2 - H2O));
		VolatileInventory = Rng.FRandRange(0.8f, 2.5f);
		break;
	}
	case EGOPlanetType::Toxic:
	{
		const float SO2 = Rng.FRandRange(0.35f, 0.45f);
		const float CO2 = Rng.FRandRange(0.30f, 0.40f);
		const float HCl = Rng.FRandRange(0.02f, 0.08f);
		AddGas(Composition, EGOGas::SO2, SO2);
		AddGas(Composition, EGOGas::CO2, CO2);
		AddGas(Composition, EGOGas::HCl, HCl);
		AddGas(Composition, EGOGas::N2, FMath::Max(0.f, 1.f - SO2 - CO2 - HCl));
		VolatileInventory = Rng.FRandRange(5.f, 90.f);
		break;
	}
	case EGOPlanetType::Barren:
	{
		const float CO2 = 0.9f + Rng.FRand() * 0.1f;
		AddGas(Composition, EGOGas::CO2, CO2);
		AddGas(Composition, EGOGas::N2, 1.f - CO2);
		VolatileInventory = Rng.FRandRange(0.0005f, 0.01f);
		break;
	}
	case EGOPlanetType::IceWorld:
	{
		const float N2 = 0.85f + Rng.FRand() * 0.15f;
		AddGas(Composition, EGOGas::N2, N2);
		AddGas(Composition, EGOGas::Ar, 1.f - N2);
		VolatileInventory = Rng.FRandRange(0.01f, 0.5f);
		break;
	}
	default:
		AddGas(Composition, EGOGas::N2, 1.f);
		VolatileInventory = 0.1f;
		break;
	}

	// Light gases escape small or hot worlds before the mix is normalised.
	Composition.RemoveAll([&](const FGOGasFraction& Entry)
	{
		return !GasRetained(Planet.EscapeVelocityKmS, Atmo.EquilibriumTempK, MolecularMass(Entry.Gas));
	});

	float Sum = 0.f;
	for (const FGOGasFraction& Entry : Composition)
	{
		Sum += Entry.Fraction;
	}
	if (Sum <= 0.f)
	{
		Atmo.Hazards.Add(EGOAtmoHazard::Vacuum);
		return Atmo;
	}
	for (FGOGasFraction& Entry : Composition)
	{
		Entry.Fraction /= Sum;
	}

	Atmo.bPresent = true;
	Atmo.Composition = MoveTemp(Composition);

	const float Outgassing = 1.f - FMath::Exp(-System.AgeGyr * 0.5f);
	Atmo.SurfacePressureAtm = Planet.SurfaceGravityG * VolatileInventory * Outgassing;

	const float P = Atmo.SurfacePressureAtm;
	float Effect = 0.f;
	Effect += 0.7f * FMath::Loge(1.f + Atmo.GetFraction(EGOGas::CO2) * P * 10.f);
	Effect += 1.0f * FMath::Loge(1.f + Atmo.GetFraction(EGOGas::H2O) * P * 8.f);
	Effect += 1.5f * FMath::Loge(1.f + Atmo.GetFraction(EGOGas::CH4) * P * 200.f);
	Effect += 0.3f * FMath::Loge(1.f + Atmo.GetFraction(EGOGas::SO2) * P * 5.f);
	Atmo.GreenhouseMultiplier = FMath::Max(1.f, 1.f + Effect);
	Atmo.SurfaceTempK = Atmo.EquilibriumTempK * Atmo.GreenhouseMultiplier;

	const float O2 = Atmo.GetFraction(EGOGas::O2);
	const float SO2 = Atmo.GetFraction(EGOGas::SO2);
	const float HCl = Atmo.GetFraction(EGOGas::HCl);
	const float NH3 = Atmo.GetFraction(EGOGas::NH3);
	Atmo.bBreathable = O2 >= 0.15f && O2 <= 0.25f
		&& P >= 0.5f && P <= 2.f
		&& SO2 <= 0.01f && HCl <= 0.01f && NH3 <= 0.01f;

	if (P < 0.01f)							Atmo.Hazards.Add(EGOAtmoHazard::Vacuum);
	if (P > 10.f)							Atmo.Hazards.Add(EGOAtmoHazard::Crushing);
	if (SO2 > 0.05f || HCl > 0.05f || NH3 > 0.05f)	Atmo.Hazards.Add(EGOAtmoHazard::Toxic);
	if (SO2 > 0.2f && P > 5.f)				Atmo.Hazards.Add(EGOAtmoHazard::Corrosive);
	if (Atmo.SurfaceTempK > 400.f)			Atmo.Hazards.Add(EGOAtmoHazard::ExtremeHeat);
	if (Atmo.SurfaceTempK < 180.f)			Atmo.Hazards.Add(EGOAtmoHazard::ExtremeCold);
	return Atmo;
}

FGOBiomeParams FGOPlanetGenerator::GenerateBiomeParams(const FGOPlanet& Planet) const
{
	const uint64 BiomeSeed = GOSeed::ForId(GOSeed::Domain(GOSeed::FromBlueprint(Planet.Seed), GOSeed::BiomeDomain), 0);
	FGORandom Rng(BiomeSeed);

	FGOBiomeParams Params;
	Params.Seed = GOSeed::ToBlueprint(BiomeSeed);

	// Sea level is in continent-noise units (roughly [-0.6, 0.6]); dry worlds have none.
	switch (Planet.Type)
	{
	case EGOPlanetType::Terran:		Params.SeaLevel = -0.08f + Rng.FRand() * 0.2f; break;
	case EGOPlanetType::Ocean:		Params.SeaLevel = 0.15f + Rng.FRand() * 0.2f; break;
	case EGOPlanetType::Toxic:		Params.SeaLevel = -0.1f + Rng.FRand() * 0.2f; break;
	case EGOPlanetType::IceWorld:	Params.SeaLevel = 0.1f + Rng.FRand() * 0.2f; break;
	case EGOPlanetType::Arid:		Params.SeaLevel = -0.6f + Rng.FRand() * 0.1f; break;
	default:						Params.SeaLevel = -1.f; Rng.NextU64(); break;
	}

	Params.SnowLine = 0.6f + Rng.FRand() * 0.3f;

	switch (Planet.Type)
	{
	case EGOPlanetType::Ocean:		Params.BaseMoisture = 0.8f + Rng.FRand() * 0.2f; break;
	case EGOPlanetType::Terran:		Params.BaseMoisture = 0.45f + Rng.FRand() * 0.3f; break;
	case EGOPlanetType::Arid:		Params.BaseMoisture = 0.05f + Rng.FRand() * 0.15f; break;
	case EGOPlanetType::Toxic:		Params.BaseMoisture = 0.05f + Rng.FRand() * 0.1f; break;
	case EGOPlanetType::IceWorld:	Params.BaseMoisture = 0.2f + Rng.FRand() * 0.3f; break;
	case EGOPlanetType::Barren:		Params.BaseMoisture = 0.01f + Rng.FRand() * 0.05f; break;
	case EGOPlanetType::Molten:		Params.BaseMoisture = 0.f; Rng.NextU64(); break;
	default:						Params.BaseMoisture = 0.3f; Rng.NextU64(); break;
	}

	Params.SurfaceTempK = Planet.GetSurfaceTempK();
	Params.AllowedBiomes = GOPlanetGeneratorPrivate::AllowedBiomes(Planet.Type);
	return Params;
}

float FGOPlanetGenerator::ScoreHabitability(const FGOPlanet& Planet)
{
	if (!Planet.HasSurface())
	{
		return 0.f;
	}

	float Score = 0.f;
	if (Planet.Type == EGOPlanetType::Terran)
	{
		Score += 2.f;
	}
	else if (Planet.Type == EGOPlanetType::Ocean)
	{
		Score += 1.f;
	}

	if (Planet.Atmosphere.bBreathable)
	{
		Score += 3.f;
	}

	const float Temp = Planet.GetSurfaceTempK();
	Score += 3.f * (1.f - FMath::Clamp(FMath::Abs(Temp - 288.f) / 40.f, 0.f, 1.f));
	Score += 1.5f * (1.f - FMath::Clamp(FMath::Abs(Planet.SurfaceGravityG - 1.f) / 0.6f, 0.f, 1.f));

	const FGOAtmosphere& Atmo = Planet.Atmosphere;
	if (Atmo.HasHazard(EGOAtmoHazard::Toxic) || Atmo.HasHazard(EGOAtmoHazard::Crushing) || Atmo.HasHazard(EGOAtmoHazard::Corrosive))
	{
		Score -= 2.f;
	}
	return FMath::Max(Score, 0.f);
}
