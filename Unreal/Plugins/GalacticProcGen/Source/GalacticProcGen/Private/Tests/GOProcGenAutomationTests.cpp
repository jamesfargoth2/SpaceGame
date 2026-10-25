#include "CoreMinimal.h"
#include "Misc/AutomationTest.h"

#if WITH_DEV_AUTOMATION_TESTS

#include "Core/GOContentDefs.h"
#include "Runtime/GOContentLoader.h"
#include "Tests/GOProcGenTestCases.h"

namespace GOProcGenAutomationPrivate
{
	class FAutomationReporter final : public IGOTestReporter
	{
	public:
		explicit FAutomationReporter(FAutomationTestBase& InTest) : Test(InTest) {}
		virtual void AddError(const FString& Message) override { Test.AddError(Message); }
		virtual void AddInfo(const FString& Message) override { Test.AddInfo(Message); }

	private:
		FAutomationTestBase& Test;
	};
}

// Session Frontend > Automation > GalacticOdyssey.ProcGen. The same cases run headless via
// Unreal/Tools/HeadlessTests, against the JSON content shipped in the plugin.
IMPLEMENT_COMPLEX_AUTOMATION_TEST(FGOProcGenAutomationTest, "GalacticOdyssey.ProcGen",
	EAutomationTestFlags::EditorContext | EAutomationTestFlags::ClientContext | EAutomationTestFlags::ProductFilter)

void FGOProcGenAutomationTest::GetTests(TArray<FString>& OutBeautifiedNames, TArray<FString>& OutTestCommands) const
{
	for (const FGOProcGenTestCase& Case : GOProcGenTests::GetCases())
	{
		OutBeautifiedNames.Add(Case.Name);
		OutTestCommands.Add(Case.Name);
	}
}

bool FGOProcGenAutomationTest::RunTest(const FString& Parameters)
{
	FGOContentDefs Content;
	TArray<FString> Messages;
	if (!GOContentLoader::LoadFromJsonDirectory(GOContentLoader::DefaultDataDirectory(), Content, Messages))
	{
		for (const FString& Message : Messages)
		{
			AddError(Message);
		}
		return false;
	}

	for (const FGOProcGenTestCase& Case : GOProcGenTests::GetCases())
	{
		if (Parameters == Case.Name)
		{
			GOProcGenAutomationPrivate::FAutomationReporter Reporter(*this);
			Case.Run(Content, Reporter);
			return !HasAnyErrors();
		}
	}

	AddError(FString::Printf(TEXT("Unknown test case '%s'."), *Parameters));
	return false;
}

#endif // WITH_DEV_AUTOMATION_TESTS
