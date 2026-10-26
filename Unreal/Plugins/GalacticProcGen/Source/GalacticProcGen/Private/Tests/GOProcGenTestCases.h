#pragma once

#include "CoreMinimal.h"

struct FGOContentDefs;

/** Sink for test failures; implemented by the UE automation wrapper and the headless runner. */
class IGOTestReporter
{
public:
	virtual ~IGOTestReporter() = default;
	virtual void AddError(const FString& Message) = 0;
	virtual void AddInfo(const FString& Message) = 0;
};

struct FGOProcGenTestCase
{
	const TCHAR* Name;
	void (*Run)(const FGOContentDefs& Content, IGOTestReporter& Reporter);
};

/**
 * Engine-independent checks of the generators (determinism, physical sanity, layout validity).
 * A case passes when it reports no errors.
 */
namespace GOProcGenTests
{
	const TArray<FGOProcGenTestCase>& GetCases();
}
