// Headless runner for the GalacticProcGen test cases (same cases as the editor's
// "GalacticOdyssey.ProcGen" automation tests).
//   GOHeadlessTests [DataDir] [NameFilter] [-v]     run test cases
//   GOHeadlessTests --report <Count> [DataDir]       print new-game summaries for seeds 1..Count
//   GOHeadlessTests --preview <Seed> <OutDir> [DataDir]  write academy/planet/galaxy previews

#include "Core/GOContentDefs.h"
#include "HeadlessContent.h"
#include "Preview.h"
#include "Tests/GOProcGenTestCases.h"
#include "Universe/GOStartScenario.h"

#include <chrono>
#include <cstdlib>
#include <cstring>
#include <iostream>

namespace
{
	class FConsoleReporter final : public IGOTestReporter
	{
	public:
		explicit FConsoleReporter(bool bInVerbose) : bVerbose(bInVerbose) {}
		void AddError(const FString& Message) override { ++Errors; std::cout << "    ERROR: " << *Message << "\n"; }
		void AddInfo(const FString& Message) override { if (bVerbose) { std::cout << "    " << *Message << "\n"; } }
		int32 Errors = 0;

	private:
		bool bVerbose;
	};
}

static int Report(int Count, const FGOContentDefs& Content)
{
	int Temperate = 0;
	for (int Seed = 1; Seed <= Count; ++Seed)
	{
		FGOStartScenarioParams Params;
		Params.Universe.Seed = static_cast<uint64>(Seed);
		const FGOStartScenario S = GOStartScenario::Generate(Content, Params);
		if (!S.IsValid())
		{
			std::cout << Seed << ": no start\n";
			continue;
		}
		const FGOPlanet& P = S.Universe.GetHomePlanet();
		const float T = P.GetSurfaceTempK();
		Temperate += T >= 270.f && T <= 305.f ? 1 : 0;
		std::cout << FString::Printf("%3d  %-22s %-14s %s  %-7s r=%.2f g=%.2f T=%3.0fK P=%.2fatm %s  site=%-16s score=%.2f examined=%d rooms=%d\n",
			Seed, *S.Universe.GalaxyName, *S.Universe.HomeSystem.Name,
			*GOUniverseText::SpectralClassName(S.Universe.HomeSystem.SpectralClass),
			*GOUniverseText::PlanetTypeName(P.Type), P.RadiusEarth, P.SurfaceGravityG, T, P.Atmosphere.SurfacePressureAtm,
			P.Atmosphere.bBreathable ? "air" : "---", *GOUniverseText::BiomeName(S.LandingBiome), P.HabitabilityScore,
			S.Universe.CandidatesExamined, S.Academy.Rooms.Num()).ToStd();
	}
	std::cout << Temperate << "/" << Count << " home worlds between 270 K and 305 K\n";
	return 0;
}

int main(int argc, char** argv)
{
	std::string DataDir = GO_DEFAULT_DATA_DIR;
	if (argc >= 4 && std::strcmp(argv[1], "--preview") == 0)
	{
		FGOContentDefs Content;
		std::vector<std::string> Errors;
		if (!GOHeadless::LoadContent(argc >= 5 ? argv[4] : DataDir, Content, Errors))
		{
			return 2;
		}
		return GOHeadless::WritePreviews(Content, std::strtoull(argv[2], nullptr, 10), argv[3]) ? 0 : 1;
	}
	if (argc >= 3 && std::strcmp(argv[1], "--report") == 0)
	{
		FGOContentDefs Content;
		std::vector<std::string> Errors;
		if (!GOHeadless::LoadContent(argc >= 4 ? argv[3] : DataDir, Content, Errors))
		{
			return 2;
		}
		return Report(std::atoi(argv[2]), Content);
	}
	std::string Filter;
	bool bVerbose = false;
	int Positional = 0;
	for (int I = 1; I < argc; ++I)
	{
		if (std::strcmp(argv[I], "-v") == 0)
		{
			bVerbose = true;
		}
		else if (Positional++ == 0)
		{
			if (argv[I][0] != '\0')
			{
				DataDir = argv[I];
			}
		}
		else
		{
			Filter = argv[I];
		}
	}

	FGOContentDefs Content;
	std::vector<std::string> LoadErrors;
	if (!GOHeadless::LoadContent(DataDir, Content, LoadErrors))
	{
		std::cout << "Failed to load content from " << DataDir << ":\n";
		for (const std::string& Error : LoadErrors)
		{
			std::cout << "  " << Error << "\n";
		}
		return 2;
	}

	int Failed = 0;
	int Run = 0;
	for (const FGOProcGenTestCase& Case : GOProcGenTests::GetCases())
	{
		if (!Filter.empty() && std::string(Case.Name).find(Filter) == std::string::npos)
		{
			continue;
		}
		++Run;
		FConsoleReporter Reporter(bVerbose);
		const auto Start = std::chrono::steady_clock::now();
		Case.Run(Content, Reporter);
		const double Ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - Start).count();
		std::cout << (Reporter.Errors == 0 ? "[PASS] " : "[FAIL] ") << Case.Name << " (" << static_cast<int>(Ms) << " ms)\n";
		Failed += Reporter.Errors == 0 ? 0 : 1;
	}

	std::cout << "\n" << (Run - Failed) << "/" << Run << " test cases passed.\n";
	return Failed == 0 ? 0 : 1;
}
