#pragma once

#include "CoreMinimal.h"

/** Path-only stand-ins for Unreal's soft references (see Shim/CoreMinimal.h). */
template <typename T>
struct TSoftObjectPtr
{
	FString Path;
	TSoftObjectPtr() = default;
	TSoftObjectPtr(const FString& InPath) : Path(InPath) {}
	bool IsNull() const { return Path.IsEmpty(); }
	FString ToString() const { return Path; }
};

template <typename T>
struct TSoftClassPtr
{
	FString Path;
	TSoftClassPtr() = default;
	TSoftClassPtr(const FString& InPath) : Path(InPath) {}
	bool IsNull() const { return Path.IsEmpty(); }
	FString ToString() const { return Path; }
};
