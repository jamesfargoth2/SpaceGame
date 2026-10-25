#include "Tests/GOProcGenTestCases.h"

#if WITH_DEV_AUTOMATION_TESTS

#include "Core/GOContentDefs.h"
#include "Core/GONoise.h"
#include "Core/GORandom.h"
#include "Hub/GOHubGenerator.h"
#include "Planet/GOPlanetTerrain.h"
#include "Planet/GOTerrainMesh.h"
#include "Universe/GOGalaxyGenerator.h"
#include "Universe/GOPlanetGenerator.h"
#include "Universe/GOStarSystemGenerator.h"
#include "Universe/GOStartScenario.h"
#include "Universe/GOUniverseGenerator.h"

namespace GOProcGenTestsPrivate
{
#define GO_EXPECT(Condition, Message) \
	do { if (!(Condition)) { R.AddError(FString::Printf(TEXT("%s (line %d)"), *FString(Message), __LINE__)); } } while (0)

	FGOUniverseParams UniverseParams(uint64 Seed, EGOStartingRegion Region = EGOStartingRegion::InnerRim)
	{
		FGOUniverseParams Params;
		Params.Seed = Seed;
		Params.GalaxyType = EGOGalaxyType::Spiral;
		Params.GalaxySize = EGOGalaxySize::Medium;
		Params.StartingRegion = Region;
		return Params;
	}

	FGOStartScenarioParams ScenarioParams(uint64 Seed)
	{
		FGOStartScenarioParams Params;
		Params.Universe = UniverseParams(Seed);
		return Params;
	}

	FGOTerrainChunkId ChunkAt(const FVector& Direction, int32 Depth)
	{
		EGOCubeFace Face;
		double U, V;
		GOCubeSphere::DirectionToFaceUV(Direction, Face, U, V);
		const int32 Cells = 1 << Depth;
		return FGOTerrainChunkId(Face, Depth,
			FMath::Clamp(static_cast<int32>(U * Cells), 0, Cells - 1),
			FMath::Clamp(static_cast<int32>(V * Cells), 0, Cells - 1));
	}

	FVector RandomDirection(FGORandom& Rng)
	{
		const double Z = Rng.DRandRange(-1.0, 1.0);
		const double Lon = Rng.DRand() * 2.0 * UE_DOUBLE_PI;
		const double Ring = FMath::Sqrt(FMath::Max(0.0, 1.0 - Z * Z));
		return FVector(Ring * FMath::Cos(Lon), Ring * FMath::Sin(Lon), Z);
	}

	bool InsideRoom(const FGOHubRoom& Room, const FVector& P, double Slack = 1.0)
	{
		const FVector Front(Room.FrontDirection.X, Room.FrontDirection.Y, 0.0);
		const FVector Right(-Room.FrontDirection.Y, Room.FrontDirection.X, 0.0);
		const FVector D = P - Room.CenterCm;
		return FMath::Abs(FVector::DotProduct(D, Front)) <= Room.InteriorSizeCm.X * 0.5 + Slack
			&& FMath::Abs(FVector::DotProduct(D, Right)) <= Room.InteriorSizeCm.Y * 0.5 + Slack;
	}

	FVector2D HalfExtentXY(const FGOHubPiece& Piece)
	{
		const FVector AxisX = Piece.Transform.GetRotation().RotateVector(FVector(1.0, 0.0, 0.0));
		const double C = FMath::Abs(AxisX.X);
		const double S = FMath::Abs(AxisX.Y);
		return FVector2D((C * Piece.SlotSizeCm.X + S * Piece.SlotSizeCm.Y) * 0.5, (S * Piece.SlotSizeCm.X + C * Piece.SlotSizeCm.Y) * 0.5);
	}

	// ------------------------------------------------------------------------------------------

	void ContentValidates(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		TArray<FString> Errors;
		Content.Validate(Errors);
		for (const FString& Error : Errors)
		{
			R.AddError(Error);
		}
		GO_EXPECT(Content.FindAsset(TEXT("chair_crew")) && Content.FindAsset(TEXT("console_nav")) && Content.FindAsset(TEXT("desk_standard"))
			&& Content.FindAsset(TEXT("human_player")) && Content.FindAsset(TEXT("human_npc_generic")),
			TEXT("The asset kit keeps every id from the legacy libGDX manifests."));
	}

	void SeedsAndRandomStreams(const FGOContentDefs&, IGOTestReporter& R)
	{
		FGORandom Reference(0);
		GO_EXPECT(Reference.NextU64() == 0xE220A8397B1DCDAFull, TEXT("SplitMix64 reference vector for seed 0."));

		FGORandom A(12345);
		FGORandom B(12345);
		bool bSame = true;
		bool bInRange = true;
		bool bSeen[3] = { false, false, false };
		for (int32 I = 0; I < 2000; ++I)
		{
			bSame &= A.NextU64() == B.NextU64();
			const float F = A.FRand();
			const double D = A.DRand();
			bInRange &= F >= 0.f && F < 1.f && D >= 0.0 && D < 1.0;
			const int32 Roll = A.RandRange(3, 5);
			bInRange &= Roll >= 3 && Roll <= 5;
			if (Roll >= 3 && Roll <= 5)
			{
				bSeen[Roll - 3] = true;
			}
			B.FRand();
			B.DRand();
			B.RandRange(3, 5);
		}
		GO_EXPECT(bSame, TEXT("Equal seeds give equal streams."));
		GO_EXPECT(bInRange, TEXT("FRand/DRand in [0,1) and RandRange inclusive."));
		GO_EXPECT(bSeen[0] && bSeen[1] && bSeen[2], TEXT("RandRange reaches both bounds."));
		GO_EXPECT(GOSeed::Domain(7, GOSeed::StarDomain) != GOSeed::Domain(7, GOSeed::PlanetDomain), TEXT("Domains separate seeds."));
		GO_EXPECT(GOSeed::FromText(TEXT("Galactic")) == GOSeed::FromText(TEXT("Galactic")), TEXT("Seed text hashing is stable."));
		GO_EXPECT(GOSeed::FromText(TEXT("Galactic")) != GOSeed::FromText(TEXT("Galactiq")), TEXT("Seed text hashing distinguishes inputs."));
	}

	void NoiseIsDeterministic(const FGOContentDefs&, IGOTestReporter& R)
	{
		const FGONoise A(123);
		const FGONoise B(123);
		const FGONoise C(124);
		FGORandom Rng(9);
		bool bSame = true;
		bool bDiffers = false;
		bool bBounded = true;
		for (int32 I = 0; I < 1000; ++I)
		{
			const float X = Rng.FRandRange(-50.f, 50.f);
			const float Y = Rng.FRandRange(-50.f, 50.f);
			const float Z = Rng.FRandRange(-50.f, 50.f);
			const float VA = A.Fbm3(X, Y, Z, 5);
			bSame &= VA == B.Fbm3(X, Y, Z, 5);
			bDiffers |= VA != C.Fbm3(X, Y, Z, 5);
			bBounded &= FMath::Abs(A.GetSimplex().Noise3(X, Y, Z)) <= 1.5f && FMath::Abs(A.GetSimplex().Noise2(X, Y)) <= 1.5f;
		}
		GO_EXPECT(bSame, TEXT("Same seed, same noise."));
		GO_EXPECT(bDiffers, TEXT("Different seeds, different noise."));
		GO_EXPECT(bBounded, TEXT("Simplex noise stays roughly within [-1, 1]."));
	}

	void GalaxyStarCounts(const FGOContentDefs&, IGOTestReporter& R)
	{
		const EGOGalaxyType Types[] = { EGOGalaxyType::Spiral, EGOGalaxyType::BarredSpiral, EGOGalaxyType::Elliptical, EGOGalaxyType::Irregular };
		for (const EGOGalaxyType Type : Types)
		{
			const FGOGalaxyConfig Config = FGOGalaxyConfig::Make(EGOGalaxySize::Small, Type);
			const FGOGalaxyGenerator Galaxy(77, Config);
			TArray<FGOStarRecord> Stars;
			Galaxy.GatherStars(FVector::ZeroVector, Config.RadiusLY * 1.01, Stars);
			const double Ratio = static_cast<double>(Stars.Num()) / Config.TargetStarCount;
			R.AddInfo(FString::Printf(TEXT("Galaxy type %d: %d stars for a target of %d"), static_cast<int32>(Type), Stars.Num(), Config.TargetStarCount));
			GO_EXPECT(Ratio > 0.6 && Ratio < 1.4, TEXT("Star count close to the target."));

			bool bInDisc = true;
			for (const FGOStarRecord& Star : Stars)
			{
				bInDisc &= FMath::Sqrt(Star.PositionLY.X * Star.PositionLY.X + Star.PositionLY.Y * Star.PositionLY.Y) <= Config.RadiusLY + 1.0;
			}
			GO_EXPECT(bInDisc, TEXT("Stars stay inside the disc."));
		}

		const FGOGalaxyGenerator Galaxy(5, FGOGalaxyConfig::Make(EGOGalaxySize::Medium, EGOGalaxyType::Spiral));
		TArray<FGOStarRecord> First, Second;
		Galaxy.GenerateChunk(FIntPoint(3, -2), First);
		Galaxy.GenerateChunk(FIntPoint(3, -2), Second);
		bool bSame = First.Num() == Second.Num();
		for (int32 I = 0; bSame && I < First.Num(); ++I)
		{
			bSame = First[I].StarId == Second[I].StarId && First[I].PositionLY == Second[I].PositionLY;
		}
		GO_EXPECT(bSame, TEXT("Chunks regenerate identically."));
	}

	void StarSystemsAreDeterministic(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOGalaxyGenerator Galaxy(31, FGOGalaxyConfig::Make(EGOGalaxySize::Medium, EGOGalaxyType::Spiral));
		const FGOStarSystemGenerator Gen(31, Content);
		TArray<FGOStarRecord> Stars;
		Galaxy.GatherStars(FVector(600.0, 300.0, 0.0), 400.0, Stars);
		GO_EXPECT(Stars.Num() > 10, TEXT("Enough stars to test."));

		for (int32 I = 0; I < FMath::Min(Stars.Num(), 60); ++I)
		{
			const EGOGalaxyRegion Region = Galaxy.ClassifyRegion(Stars[I].PositionLY);
			const FGOStarSystem A = Gen.Generate(Stars[I], Region);
			const FGOStarSystem B = Gen.Generate(Stars[I], Region);
			GO_EXPECT(A.Name == B.Name && A.SpectralClass == B.SpectralClass && A.Orbits.Num() == B.Orbits.Num()
				&& A.MassSolar == B.MassSolar, TEXT("Same star, same system."));
			GO_EXPECT(A.HabZoneInnerAU < A.HabZoneOuterAU && A.HabZoneOuterAU < A.FrostLineAU, TEXT("Zone boundaries ordered."));
			GO_EXPECT(!A.Name.IsEmpty(), TEXT("Stars are named."));
			for (int32 O = 1; O < A.Orbits.Num(); ++O)
			{
				GO_EXPECT(A.Orbits[O].RadiusAU >= A.Orbits[O - 1].RadiusAU, TEXT("Orbits move outward."));
			}
			if (A.LuminosityClass == EGOLuminosityClass::MainSequence)
			{
				const FGOSpectralClassDef* Def = Content.FindSpectralClass(A.SpectralClass);
				GO_EXPECT(Def && A.MassSolar >= Def->MassMin && A.MassSolar <= Def->MassMax, TEXT("Mass within its spectral class."));
			}
		}
	}

	void PlanetPhysicsIsSane(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOGalaxyGenerator Galaxy(11, FGOGalaxyConfig::Make(EGOGalaxySize::Medium, EGOGalaxyType::Spiral));
		const FGOStarSystemGenerator StarGen(11, Content);
		const FGOPlanetGenerator PlanetGen(Content);
		TArray<FGOStarRecord> Stars;
		Galaxy.GatherStars(FVector(-500.0, 200.0, 0.0), 500.0, Stars);

		int32 Planets = 0;
		for (int32 I = 0; I < FMath::Min(Stars.Num(), 150); ++I)
		{
			const FGOStarSystem System = StarGen.Generate(Stars[I], Galaxy.ClassifyRegion(Stars[I].PositionLY));
			for (const FGOPlanet& P : PlanetGen.GenerateAll(System))
			{
				++Planets;
				GO_EXPECT(FMath::Abs(P.SurfaceGravityG - P.MassEarth / (P.RadiusEarth * P.RadiusEarth)) < 1e-3f, TEXT("g = M / r^2."));
				GO_EXPECT(FMath::Abs(P.RadiusKm - P.RadiusEarth * GOPlanetConstants::EarthRadiusKm) < 1e-3, TEXT("Radius units agree."));
				const FGOPlanetTypeDef* Def = Content.FindPlanetType(P.Type);
				GO_EXPECT(Def && Def->Zones.Contains(P.Zone), TEXT("Planet type allowed in its zone."));
				if (P.Atmosphere.bPresent)
				{
					float Sum = 0.f;
					for (const FGOGasFraction& Gas : P.Atmosphere.Composition)
					{
						Sum += Gas.Fraction;
					}
					GO_EXPECT(FMath::Abs(Sum - 1.f) < 1e-3f, TEXT("Gas fractions sum to one."));
				}
				if (P.Atmosphere.bBreathable)
				{
					const float O2 = P.Atmosphere.GetFraction(EGOGas::O2);
					GO_EXPECT(O2 >= 0.15f && O2 <= 0.25f && P.Atmosphere.SurfacePressureAtm >= 0.5f && P.Atmosphere.SurfacePressureAtm <= 2.f,
						TEXT("Breathable means 15-25% O2 at 0.5-2 atm."));
				}
				if (P.Type == EGOPlanetType::Terran)
				{
					GO_EXPECT(P.SurfaceGravityG > 0.4f && P.SurfaceGravityG < 2.5f, TEXT("Terran gravity is Earth-like (Earth-normalised mass)."));
				}
			}
		}
		R.AddInfo(FString::Printf(TEXT("Checked %d planets."), Planets));
	}

	void StartIsDeterministic(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOUniverseGenerator Gen(Content);
		const FGOUniverseStart A = Gen.GenerateStart(UniverseParams(42));
		const FGOUniverseStart B = Gen.GenerateStart(UniverseParams(42));
		const FGOUniverseStart C = Gen.GenerateStart(UniverseParams(43));
		GO_EXPECT(A.IsValid() && B.IsValid() && C.IsValid(), TEXT("Starts are valid."));
		if (A.IsValid() && B.IsValid() && C.IsValid())
		{
			GO_EXPECT(A.HomeStar.StarId == B.HomeStar.StarId && A.HomePlanetIndex == B.HomePlanetIndex
				&& A.GetHomePlanet().Name == B.GetHomePlanet().Name && A.GalaxyName == B.GalaxyName, TEXT("Same seed, same start."));
			GO_EXPECT(A.HomeStar.StarId != C.HomeStar.StarId, TEXT("Different seed, different start."));
		}
	}

	void StartFindsHabitableHome(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOUniverseGenerator Gen(Content);
		int32 Breathable = 0;
		int32 Temperate = 0;
		int32 Count = 0;
		int32 MaxExamined = 0;
		for (uint64 Seed = 1; Seed <= 24; ++Seed)
		{
			const FGOUniverseStart Start = Gen.GenerateStart(UniverseParams(Seed));
			GO_EXPECT(Start.IsValid(), TEXT("Every seed yields a home world."));
			if (!Start.IsValid())
			{
				continue;
			}
			++Count;
			const FGOPlanet& Home = Start.GetHomePlanet();
			GO_EXPECT(Home.HasSurface(), TEXT("Home world has a surface."));
			Breathable += Home.Type == EGOPlanetType::Terran && Home.Atmosphere.bBreathable ? 1 : 0;
			Temperate += Home.GetSurfaceTempK() >= 270.f && Home.GetSurfaceTempK() <= 305.f ? 1 : 0;
			MaxExamined = FMath::Max(MaxExamined, Start.CandidatesExamined);
		}
		R.AddInfo(FString::Printf(TEXT("%d/%d seeds start on a breathable Terran world, %d between 270 K and 305 K (worst case examined %d systems)."),
			Breathable, Count, Temperate, MaxExamined));
		GO_EXPECT(Breathable * 10 >= Count * 9, TEXT("At least 90% of seeds start on a breathable Terran world."));
		GO_EXPECT(Temperate * 10 >= Count * 9, TEXT("At least 90% of home worlds are temperate (270-305 K mean)."));
	}

	void StartRespectsRegion(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOUniverseGenerator Gen(Content);
		const EGOStartingRegion Regions[] = { EGOStartingRegion::Core, EGOStartingRegion::InnerRim, EGOStartingRegion::Frontier };
		for (const EGOStartingRegion Region : Regions)
		{
			int32 Matches = 0;
			for (uint64 Seed = 100; Seed < 106; ++Seed)
			{
				const FGOUniverseStart Start = Gen.GenerateStart(UniverseParams(Seed, Region));
				Matches += Start.IsValid() && Start.HomeSystem.Region == FGOUniverseGenerator::GalaxyRegionFor(Region) ? 1 : 0;
			}
			GO_EXPECT(Matches >= 5, FString::Printf(TEXT("Home star lies in the requested region (%d/6 for region %d)."), Matches, static_cast<int32>(Region)));
		}
	}

	void CubeSphereRoundTrip(const FGOContentDefs&, IGOTestReporter& R)
	{
		FGORandom Rng(3);
		bool bOk = true;
		for (int32 I = 0; I < 5000; ++I)
		{
			const FVector Dir = RandomDirection(Rng);
			EGOCubeFace Face;
			double U, V;
			GOCubeSphere::DirectionToFaceUV(Dir, Face, U, V);
			bOk &= U >= 0.0 && U <= 1.0 && V >= 0.0 && V <= 1.0;
			bOk &= GOCubeSphere::FaceUVToDirection(Face, U, V).Equals(Dir, 1e-9);
		}
		GO_EXPECT(bOk, TEXT("Direction -> face UV -> direction round trip."));
	}

	void TerrainIsContinuous(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOUniverseStart Start = FGOUniverseGenerator(Content).GenerateStart(UniverseParams(7));
		if (!Start.IsValid())
		{
			R.AddError(TEXT("No start generated."));
			return;
		}
		const FGOPlanetTerrain Terrain(Start.GetHomePlanet(), Content);
		const double StepKm = 0.02;
		FGORandom Rng(21);
		int32 Steep = 0;
		double MaxJumpM = 0.0;
		bool bDeterministic = true;
		const int32 Samples = 2000;
		for (int32 I = 0; I < Samples; ++I)
		{
			const FVector Dir = RandomDirection(Rng);
			const FVector Helper = FMath::Abs(Dir.Z) < 0.9 ? FVector(0.0, 0.0, 1.0) : FVector(1.0, 0.0, 0.0);
			const FVector Tangent = FVector::CrossProduct(Dir, Helper).GetSafeNormal();
			const FVector Next = (Dir + Tangent * (StepKm / Terrain.GetRadiusKm())).GetSafeNormal();

			const double H0 = Terrain.SurfaceRadiusKm(Dir, 14);
			bDeterministic &= H0 == Terrain.SurfaceRadiusKm(Dir, 14);
			const double JumpM = FMath::Abs(Terrain.SurfaceRadiusKm(Next, 14) - H0) * 1000.0;
			MaxJumpM = FMath::Max(MaxJumpM, JumpM);
			Steep += JumpM > StepKm * 1000.0 ? 1 : 0;
		}
		R.AddInfo(FString::Printf(TEXT("Largest height change over %.0f m: %.1f m; slopes over 45 deg: %d/%d."), StepKm * 1000.0, MaxJumpM, Steep, Samples));
		GO_EXPECT(bDeterministic, TEXT("Terrain samples are deterministic."));
		GO_EXPECT(Steep * 200 <= Samples, TEXT("At most 0.5% of samples steeper than 45 degrees."));
		GO_EXPECT(MaxJumpM < 100.0, TEXT("No cliffs from biome changes (old per-biome amplitude switch made ~1 km steps)."));
	}

	void QuadtreeRefinesUnderCamera(const FGOContentDefs&, IGOTestReporter& R)
	{
		const double Radius = 6371.0;
		FGOTerrainQuadtree Tree(Radius, 14);
		const FVector Dir = FVector(0.3, -0.5, 0.8).GetSafeNormal();
		Tree.Update(Dir * (Radius + 0.002));

		const FGOTerrainChunkId Under = ChunkAt(Dir, 14);
		GO_EXPECT(Tree.GetLeaves().Contains(Under), TEXT("The chunk under a surface camera reaches max depth."));

		double Coverage = 0.0;
		for (const FGOTerrainChunkId& Leaf : Tree.GetLeaves())
		{
			Coverage += Leaf.CellSize() * Leaf.CellSize();
		}
		GO_EXPECT(FMath::Abs(Coverage - 6.0) < 1e-9, TEXT("Leaves tile all six faces exactly once."));
		GO_EXPECT(!Tree.Update(Dir * (Radius + 0.002)), TEXT("An unchanged camera leaves the tree unchanged."));
		R.AddInfo(FString::Printf(TEXT("%d leaves for a camera on the surface."), Tree.GetLeaves().Num()));

		Tree.Update(Dir * (Radius * 10.0));
		bool bCoarse = true;
		for (const FGOTerrainChunkId& Leaf : Tree.GetLeaves())
		{
			bCoarse &= Leaf.Depth <= 1;
		}
		GO_EXPECT(bCoarse, TEXT("A distant camera merges back to coarse chunks."));
	}

	void ChunkMeshesAreValid(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOStartScenario Scenario = GOStartScenario::Generate(Content, ScenarioParams(5));
		if (!Scenario.IsValid())
		{
			R.AddError(TEXT("No scenario generated."));
			return;
		}
		const FGOPlanetTerrain Terrain(Scenario.Universe.GetHomePlanet(), Content);
		FGOTerrainMeshSettings Settings;
		const int32 G = Settings.GridSize;

		const FGOTerrainChunkId Chunks[] = {
			FGOTerrainChunkId(EGOCubeFace::PosX, 0, 0, 0),
			FGOTerrainChunkId(EGOCubeFace::NegZ, 3, 5, 2),
			ChunkAt(Scenario.LandingDirection, 14)
		};
		for (const FGOTerrainChunkId& Id : Chunks)
		{
			FGOChunkMeshData Mesh;
			GOTerrainMesh::BuildChunk(Terrain, Content, Id, Settings, Mesh);
			GO_EXPECT(Mesh.SurfaceVertexCount == G * G && Mesh.Positions.Num() == G * G + 4 * (G - 1), TEXT("Vertex count = grid + skirt."));
			GO_EXPECT(Mesh.Triangles.Num() == (G - 1) * (G - 1) * 6 + 4 * (G - 1) * 6, TEXT("Triangle count = grid + skirt."));
			GO_EXPECT(Mesh.Normals.Num() == Mesh.Positions.Num() && Mesh.UVs.Num() == Mesh.Positions.Num() && Mesh.Colors.Num() == Mesh.Positions.Num(),
				TEXT("Vertex streams line up."));

			bool bIndices = true;
			for (const int32 Index : Mesh.Triangles)
			{
				bIndices &= Index >= 0 && Index < Mesh.Positions.Num();
			}
			GO_EXPECT(bIndices, TEXT("Indices in range."));
			if (!bIndices)
			{
				continue;
			}

			bool bFrontFacing = true;
			const int32 SurfaceIndices = (G - 1) * (G - 1) * 6;
			for (int32 T = 0; T < SurfaceIndices; T += 3)
			{
				const FVector& A = Mesh.Positions[Mesh.Triangles[T]];
				const FVector& B = Mesh.Positions[Mesh.Triangles[T + 1]];
				const FVector& C = Mesh.Positions[Mesh.Triangles[T + 2]];
				bFrontFacing &= GOMeshWinding::IsFrontFacing(A, B, C, (A + Mesh.OriginCm).GetSafeNormal());
			}
			GO_EXPECT(bFrontFacing, TEXT("Surface triangles face away from the planet centre."));

			bool bNormals = true;
			for (int32 V = 0; V < Mesh.SurfaceVertexCount; ++V)
			{
				const FVector Radial = (Mesh.Positions[V] + Mesh.OriginCm).GetSafeNormal();
				bNormals &= FMath::Abs(Mesh.Normals[V].Size() - 1.0) < 1e-6 && FVector::DotProduct(Mesh.Normals[V], Radial) > 0.0;
			}
			GO_EXPECT(bNormals, TEXT("Normals are unit length and point outward."));

			// Each skirt vertex hangs below the surface vertex it was dropped from (same direction).
			bool bSkirtBelow = true;
			for (int32 V = Mesh.SurfaceVertexCount; V < Mesh.Positions.Num(); ++V)
			{
				const FVector Skirt = Mesh.Positions[V] + Mesh.OriginCm;
				const FVector SkirtDir = Skirt.GetSafeNormal();
				double BestDot = -2.0;
				double EdgeRadius = 0.0;
				for (int32 S = 0; S < Mesh.SurfaceVertexCount; ++S)
				{
					const FVector Surface = Mesh.Positions[S] + Mesh.OriginCm;
					const double Dot = FVector::DotProduct(Surface.GetSafeNormal(), SkirtDir);
					if (Dot > BestDot)
					{
						BestDot = Dot;
						EdgeRadius = Surface.Size();
					}
				}
				bSkirtBelow &= Skirt.Size() < EdgeRadius;
			}
			GO_EXPECT(bSkirtBelow, TEXT("Skirt vertices hang below the chunk edge."));
		}
	}

	void ScatterIsStableAcrossDepth(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOStartScenario Scenario = GOStartScenario::Generate(Content, ScenarioParams(5));
		if (!Scenario.IsValid())
		{
			R.AddError(TEXT("No scenario generated."));
			return;
		}
		const FGOPlanetTerrain Terrain(Scenario.Universe.GetHomePlanet(), Content);
		FGOTerrainMeshSettings Settings;
		Settings.ScatterMinDepth = 12;

		const FGOTerrainChunkId Parent = ChunkAt(Scenario.LandingDirection, 13);
		TArray<FGOScatterInstance> ParentScatter, Again;
		const FVector ParentOrigin = GOTerrainMesh::ChunkOriginCm(Terrain, Parent);
		GOTerrainMesh::BuildScatter(Terrain, Content, Parent, Settings, ParentOrigin, ParentScatter);
		GOTerrainMesh::BuildScatter(Terrain, Content, Parent, Settings, ParentOrigin, Again);

		bool bSame = ParentScatter.Num() == Again.Num();
		for (int32 I = 0; bSame && I < ParentScatter.Num(); ++I)
		{
			bSame = ParentScatter[I].AssetId == Again[I].AssetId && ParentScatter[I].Transform.Equals(Again[I].Transform, 1e-6);
		}
		GO_EXPECT(bSame, TEXT("Scatter regenerates identically."));

		TArray<FVector> ChildDirections;
		int32 ChildCount = 0;
		for (int32 DY = 0; DY < 2; ++DY)
		{
			for (int32 DX = 0; DX < 2; ++DX)
			{
				const FGOTerrainChunkId Child = Parent.Child(DX, DY);
				const FVector Origin = GOTerrainMesh::ChunkOriginCm(Terrain, Child);
				TArray<FGOScatterInstance> ChildScatter;
				GOTerrainMesh::BuildScatter(Terrain, Content, Child, Settings, Origin, ChildScatter);
				ChildCount += ChildScatter.Num();
				for (const FGOScatterInstance& Instance : ChildScatter)
				{
					ChildDirections.Add((Instance.Transform.GetLocation() + Origin).GetSafeNormal());
				}
			}
		}

		int32 Matched = 0;
		for (const FGOScatterInstance& Instance : ParentScatter)
		{
			const FVector Dir = (Instance.Transform.GetLocation() + ParentOrigin).GetSafeNormal();
			for (const FVector& Other : ChildDirections)
			{
				if (Dir.Equals(Other, 2e-6))
				{
					++Matched;
					break;
				}
			}
		}
		R.AddInfo(FString::Printf(TEXT("Scatter: %d instances at depth 13, %d across its children, %d matched."), ParentScatter.Num(), ChildCount, Matched));
		GO_EXPECT(ParentScatter.Num() == 0 || Matched * 100 >= ParentScatter.Num() * 95, TEXT("Instances keep their place when a chunk splits."));
	}

	void LandingSitesAreSafe(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		for (uint64 Seed = 1; Seed <= 8; ++Seed)
		{
			const FGOStartScenario Scenario = GOStartScenario::Generate(Content, ScenarioParams(Seed));
			if (!Scenario.IsValid())
			{
				R.AddError(TEXT("No scenario generated."));
				continue;
			}
			const FGOPlanetTerrain Terrain(Scenario.Universe.GetHomePlanet(), Content);
			const FGOTerrainSample Sample = Terrain.Sample(Scenario.LandingDirection, Scenario.TerrainMaxDepth);
			const FVector Normal = Terrain.SurfaceNormal(Scenario.LandingDirection, Scenario.TerrainMaxDepth, 0.05);
			const double SlopeDeg = FMath::RadiansToDegrees(FMath::Acos(FMath::Clamp(FVector::DotProduct(Normal, Scenario.LandingDirection), -1.0, 1.0)));
			const double LatitudeDeg = FMath::RadiansToDegrees(GOCubeSphere::Latitude(Scenario.LandingDirection));
			GO_EXPECT(!Sample.bWater, TEXT("Landing site is dry."));
			GO_EXPECT(SlopeDeg < 25.0, FString::Printf(TEXT("Landing site is gentle (%.1f deg)."), SlopeDeg));
			GO_EXPECT(FMath::Abs(LatitudeDeg) <= 40.0 + 1e-6, TEXT("Landing site is temperate."));
		}
	}

	FGOHubLayout MakeHub(const FGOContentDefs& Content, uint64 Seed)
	{
		FGOHubGenParams Params;
		const FIntPoint Facings[4] = { FIntPoint(1, 0), FIntPoint(0, 1), FIntPoint(-1, 0), FIntPoint(0, -1) };
		Params.FrontDirection = Facings[Seed % 4];
		return FGOHubGenerator(Content).Generate(Seed, Params, TEXT("Test Academy"));
	}

	void HubIsDeterministic(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOHubLayout A = MakeHub(Content, 99);
		const FGOHubLayout B = MakeHub(Content, 99);
		bool bSame = A.Rooms.Num() == B.Rooms.Num() && A.Structure.Num() == B.Structure.Num()
			&& A.Props.Num() == B.Props.Num() && A.Spawns.Num() == B.Spawns.Num();
		for (int32 I = 0; bSame && I < A.Props.Num(); ++I)
		{
			bSame = A.Props[I].AssetId == B.Props[I].AssetId && A.Props[I].Transform.Equals(B.Props[I].Transform, 1e-6);
		}
		for (int32 I = 0; bSame && I < A.Spawns.Num(); ++I)
		{
			bSame = A.Spawns[I].DisplayName == B.Spawns[I].DisplayName;
		}
		GO_EXPECT(bSame, TEXT("Same seed, same academy."));
		R.AddInfo(FString::Printf(TEXT("Academy: %d rooms, %d structure pieces, %d props, %d spawns, %d lights."),
			A.Rooms.Num(), A.Structure.Num(), A.Props.Num(), A.Spawns.Num(), A.Lights.Num()));
	}

	void HubHasRequiredRooms(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		for (uint64 Seed = 1; Seed <= 12; ++Seed)
		{
			const FGOHubLayout Hub = MakeHub(Content, Seed);
			for (const FString& Warning : Hub.Warnings)
			{
				R.AddError(FString::Printf(TEXT("Seed %d: %s"), static_cast<int32>(Seed), *Warning));
			}
			GO_EXPECT(Hub.Rooms.IsValidIndex(Hub.GraduationHallIndex), TEXT("There is a graduation hall."));
			GO_EXPECT(Hub.Rooms.IsValidIndex(Hub.HangarIndex), TEXT("There is a hangar."));
			for (const FGOHubRoomTemplate& Template : Content.HubRooms)
			{
				GO_EXPECT(Hub.CountRooms(Template.TemplateId) >= Template.MinCount,
					FString::Printf(TEXT("Seed %d has %s."), static_cast<int32>(Seed), *Template.TemplateId.ToString()));
			}
		}
	}

	void HubIsConnectedWithoutOverlaps(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		for (uint64 Seed = 1; Seed <= 12; ++Seed)
		{
			const FGOHubLayout Hub = MakeHub(Content, Seed);
			TMap<FIntPoint, int32> Owner;	// room index, or -2 for corridor
			bool bOverlap = false;
			for (int32 Index = 0; Index < Hub.Rooms.Num(); ++Index)
			{
				const FGOHubRoom& Room = Hub.Rooms[Index];
				for (int32 Y = 0; Y < Room.SizeCells.Y; ++Y)
				{
					for (int32 X = 0; X < Room.SizeCells.X; ++X)
					{
						const FIntPoint Cell = Room.MinCell + FIntPoint(X, Y);
						bOverlap |= Owner.Contains(Cell);
						Owner.Add(Cell, Index);
					}
				}
			}
			for (const FIntPoint& Cell : Hub.CorridorCells)
			{
				bOverlap |= Owner.Contains(Cell);
				Owner.Add(Cell, -2);
			}
			GO_EXPECT(!bOverlap, TEXT("Rooms and corridors never share a cell."));

			TSet<FIntVector> Doors;
			bool bDoorsValid = true;
			for (int32 Index = 0; Index < Hub.Rooms.Num(); ++Index)
			{
				GO_EXPECT(Hub.Rooms[Index].Doors.Num() > 0, TEXT("Every room has a door."));
				for (const FGOHubDoor& Door : Hub.Rooms[Index].Doors)
				{
					const int32* Inside = Owner.Find(Door.Cell);
					const int32* Outside = Owner.Find(Door.OutsideCell);
					const FIntPoint Delta = Door.OutsideCell - Door.Cell;
					bDoorsValid &= Inside && *Inside == Index && Outside && *Outside == -2 && FMath::Abs(Delta.X) + FMath::Abs(Delta.Y) == 1;
					Doors.Add(FIntVector(Door.Cell.X, Door.Cell.Y, Door.OutsideCell.X * 100003 + Door.OutsideCell.Y));
					Doors.Add(FIntVector(Door.OutsideCell.X, Door.OutsideCell.Y, Door.Cell.X * 100003 + Door.Cell.Y));
				}
			}
			GO_EXPECT(bDoorsValid, TEXT("Doors join a room cell to an adjacent corridor cell."));

			const FGOHubSpawnPoint* Player = Hub.FindSpawn(EGOHubSpawnRole::Player);
			GO_EXPECT(Player != nullptr, TEXT("There is a player spawn."));
			if (!Player)
			{
				continue;
			}
			const FVector P = Player->Transform.GetLocation();
			const FIntPoint StartCell(FMath::RoundToInt(P.X / Hub.Params.CellSizeCm), FMath::RoundToInt(P.Y / Hub.Params.CellSizeCm));

			TSet<FIntPoint> Visited;
			TArray<FIntPoint> Frontier = { StartCell };
			Visited.Add(StartCell);
			const FIntPoint Dirs[4] = { FIntPoint(1, 0), FIntPoint(-1, 0), FIntPoint(0, 1), FIntPoint(0, -1) };
			while (Frontier.Num() > 0)
			{
				const FIntPoint Cell = Frontier.Last();
				Frontier.RemoveAt(Frontier.Num() - 1);
				const int32* CellOwner = Owner.Find(Cell);
				for (const FIntPoint& Dir : Dirs)
				{
					const FIntPoint Next = Cell + Dir;
					const int32* NextOwner = Owner.Find(Next);
					if (!CellOwner || !NextOwner || Visited.Contains(Next))
					{
						continue;
					}
					const bool bSameSpace = *CellOwner == *NextOwner;
					const bool bThroughDoor = Doors.Contains(FIntVector(Cell.X, Cell.Y, Next.X * 100003 + Next.Y));
					if (bSameSpace || bThroughDoor)
					{
						Visited.Add(Next);
						Frontier.Add(Next);
					}
				}
			}
			for (int32 Index = 0; Index < Hub.Rooms.Num(); ++Index)
			{
				GO_EXPECT(Visited.Contains(Hub.Rooms[Index].MinCell),
					FString::Printf(TEXT("Seed %d: %s reachable from the player spawn."), static_cast<int32>(Seed), *Hub.Rooms[Index].DisplayName));
			}
		}
	}

	void HubIsWatertight(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		for (uint64 Seed = 1; Seed <= 6; ++Seed)
		{
			const FGOHubLayout Hub = MakeHub(Content, Seed);
			const double C = Hub.Params.CellSizeCm;
			const double WallZ = Hub.Params.WallHeightCm * 0.5;

			// Wall-like pieces keyed by their centre in half-cell units.
			TSet<FIntPoint> Edges;
			for (const FGOHubPiece& Piece : Hub.Structure)
			{
				const FVector L = Piece.Transform.GetLocation();
				if (FMath::Abs(L.Z - WallZ) < 1.0)
				{
					Edges.Add(FIntPoint(FMath::RoundToInt(L.X * 2.0 / C), FMath::RoundToInt(L.Y * 2.0 / C)));
				}
			}

			int32 Missing = 0;
			for (int32 Index = 0; Index < Hub.Rooms.Num(); ++Index)
			{
				const FGOHubRoom& Room = Hub.Rooms[Index];
				for (int32 Y = 0; Y < Room.SizeCells.Y; ++Y)
				{
					for (int32 X = 0; X < Room.SizeCells.X; ++X)
					{
						const FIntPoint Cell = Room.MinCell + FIntPoint(X, Y);
						const FIntPoint Dirs[4] = { FIntPoint(1, 0), FIntPoint(-1, 0), FIntPoint(0, 1), FIntPoint(0, -1) };
						for (const FIntPoint& Dir : Dirs)
						{
							if (Room.ContainsCell(Cell + Dir))
							{
								continue;
							}
							Missing += Edges.Contains(FIntPoint(Cell.X * 2 + Dir.X, Cell.Y * 2 + Dir.Y)) ? 0 : 1;
						}
					}
				}
			}
			GO_EXPECT(Missing == 0, FString::Printf(TEXT("Seed %d: every room edge has a wall, window or door (%d missing)."), static_cast<int32>(Seed), Missing));
		}
	}

	void HubPropsAndSpawnsFit(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		for (uint64 Seed = 1; Seed <= 8; ++Seed)
		{
			const FGOHubLayout Hub = MakeHub(Content, Seed);
			const double HalfHeight = Hub.Params.WallHeightCm * 0.5;

			bool bInside = true;
			int32 Overlaps = 0;
			for (int32 I = 0; I < Hub.Props.Num(); ++I)
			{
				const FGOHubPiece& A = Hub.Props[I];
				if (A.RoomIndex == INDEX_NONE || A.Transform.GetLocation().Z > HalfHeight)
				{
					continue;	// corridor fixtures and ceiling lights
				}
				const FGOHubRoom& Room = Hub.Rooms[A.RoomIndex];
				const FVector2D HA = HalfExtentXY(A);
				const FVector LA = A.Transform.GetLocation();
				bInside &= InsideRoom(Room, LA + FVector(HA.X, HA.Y, 0.0), 2.0) && InsideRoom(Room, LA - FVector(HA.X, HA.Y, 0.0), 2.0);

				for (int32 J = I + 1; J < Hub.Props.Num(); ++J)
				{
					const FGOHubPiece& B = Hub.Props[J];
					if (B.RoomIndex != A.RoomIndex || B.Transform.GetLocation().Z > HalfHeight)
					{
						continue;
					}
					const FVector2D HB = HalfExtentXY(B);
					const FVector LB = B.Transform.GetLocation();
					Overlaps += FMath::Abs(LA.X - LB.X) < HA.X + HB.X - 2.0 && FMath::Abs(LA.Y - LB.Y) < HA.Y + HB.Y - 2.0 ? 1 : 0;
				}
			}
			GO_EXPECT(bInside, TEXT("Props stay inside their rooms."));
			GO_EXPECT(Overlaps == 0, FString::Printf(TEXT("Seed %d: props do not overlap (%d overlaps)."), static_cast<int32>(Seed), Overlaps));

			int32 Players = 0;
			int32 Ships = 0;
			int32 Cadets = 0;
			bool bSpawnsInside = true;
			for (const FGOHubSpawnPoint& Spawn : Hub.Spawns)
			{
				Players += Spawn.Role == EGOHubSpawnRole::Player ? 1 : 0;
				Cadets += Spawn.Role == EGOHubSpawnRole::Cadet ? 1 : 0;
				if (Spawn.Role == EGOHubSpawnRole::Ship)
				{
					++Ships;
					GO_EXPECT(Spawn.RoomIndex == Hub.HangarIndex, TEXT("The ship waits in the hangar."));
				}
				bSpawnsInside &= Hub.Rooms.IsValidIndex(Spawn.RoomIndex) && InsideRoom(Hub.Rooms[Spawn.RoomIndex], Spawn.Transform.GetLocation(), 5.0);
				if (Spawn.Role == EGOHubSpawnRole::Player)
				{
					GO_EXPECT(Spawn.RoomIndex == Hub.GraduationHallIndex, TEXT("The player starts in the graduation hall."));
				}
			}
			GO_EXPECT(Players == 1, TEXT("Exactly one player spawn."));
			GO_EXPECT(Ships == 1, TEXT("Exactly one ship."));
			GO_EXPECT(Cadets >= 10, FString::Printf(TEXT("A graduating class (%d cadets)."), Cadets));
			GO_EXPECT(bSpawnsInside, TEXT("Spawns lie inside their rooms."));
		}
	}

	void ScenarioIsDeterministic(const FGOContentDefs& Content, IGOTestReporter& R)
	{
		const FGOStartScenario A = GOStartScenario::Generate(Content, ScenarioParams(2024));
		const FGOStartScenario B = GOStartScenario::Generate(Content, ScenarioParams(2024));
		GO_EXPECT(A.IsValid() && B.IsValid(), TEXT("Scenarios are valid."));
		GO_EXPECT(A.LandingDirection == B.LandingDirection && A.Academy.AcademyName == B.Academy.AcademyName
			&& A.Academy.Structure.Num() == B.Academy.Structure.Num() && A.HubOrbitRadiusKm == B.HubOrbitRadiusKm,
			TEXT("Same seed, same scenario."));
		for (const FString& Line : A.Log)
		{
			R.AddInfo(Line);
		}
	}

#undef GO_EXPECT
}

const TArray<FGOProcGenTestCase>& GOProcGenTests::GetCases()
{
	using namespace GOProcGenTestsPrivate;
	static const TArray<FGOProcGenTestCase> Cases = {
		{ TEXT("ContentValidates"), &ContentValidates },
		{ TEXT("SeedsAndRandomStreams"), &SeedsAndRandomStreams },
		{ TEXT("NoiseIsDeterministic"), &NoiseIsDeterministic },
		{ TEXT("GalaxyStarCounts"), &GalaxyStarCounts },
		{ TEXT("StarSystemsAreDeterministic"), &StarSystemsAreDeterministic },
		{ TEXT("PlanetPhysicsIsSane"), &PlanetPhysicsIsSane },
		{ TEXT("StartIsDeterministic"), &StartIsDeterministic },
		{ TEXT("StartFindsHabitableHome"), &StartFindsHabitableHome },
		{ TEXT("StartRespectsRegion"), &StartRespectsRegion },
		{ TEXT("CubeSphereRoundTrip"), &CubeSphereRoundTrip },
		{ TEXT("TerrainIsContinuous"), &TerrainIsContinuous },
		{ TEXT("QuadtreeRefinesUnderCamera"), &QuadtreeRefinesUnderCamera },
		{ TEXT("ChunkMeshesAreValid"), &ChunkMeshesAreValid },
		{ TEXT("ScatterIsStableAcrossDepth"), &ScatterIsStableAcrossDepth },
		{ TEXT("LandingSitesAreSafe"), &LandingSitesAreSafe },
		{ TEXT("HubIsDeterministic"), &HubIsDeterministic },
		{ TEXT("HubHasRequiredRooms"), &HubHasRequiredRooms },
		{ TEXT("HubIsConnectedWithoutOverlaps"), &HubIsConnectedWithoutOverlaps },
		{ TEXT("HubIsWatertight"), &HubIsWatertight },
		{ TEXT("HubPropsAndSpawnsFit"), &HubPropsAndSpawnsFit },
		{ TEXT("ScenarioIsDeterministic"), &ScenarioIsDeterministic },
	};
	return Cases;
}

#endif // WITH_DEV_AUTOMATION_TESTS
