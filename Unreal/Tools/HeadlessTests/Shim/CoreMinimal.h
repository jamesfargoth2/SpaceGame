#pragma once

// Minimal stand-in for the slice of Unreal's Core API used by the GalacticProcGen generation
// code, so that code can be compiled and tested without an engine install (CI, quick checks).
// It mirrors UE semantics where the generators depend on them: insertion-ordered TMap
// iteration, TArray::Sort dereferencing pointer elements, inclusive RandRange, double FVector.
// It is NOT a general UE replacement; the real tests are the in-editor automation tests.

#include <algorithm>
#include <cassert>
#include <cctype>
#include <cmath>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <functional>
#include <initializer_list>
#include <limits>
#include <string>
#include <type_traits>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

// ---------------------------------------------------------------------------------------------
// Basic types and macros
// ---------------------------------------------------------------------------------------------
using int8 = std::int8_t;
using uint8 = std::uint8_t;
using int16 = std::int16_t;
using uint16 = std::uint16_t;
using int32 = std::int32_t;
using uint32 = std::uint32_t;
using int64 = std::int64_t;
using uint64 = std::uint64_t;
using TCHAR = char;

#define TEXT(x) x
#define FORCEINLINE inline
#define GALACTICPROCGEN_API
#define UENUM(...)
#define USTRUCT(...)
#define UCLASS(...)
#define UPROPERTY(...)
#define UFUNCTION(...)
#define UMETA(...)
#define GENERATED_BODY()
#define INDEX_NONE (-1)
#define MAX_int32 (std::numeric_limits<int32>::max())
#define UE_ARRAY_COUNT(a) (static_cast<int32>(sizeof(a) / sizeof((a)[0])))
#define UE_PI (3.1415926535897932f)
#define UE_TWO_PI (6.28318530717958647692f)
#define UE_DOUBLE_PI (3.141592653589793238462643383279502884197169399)
#define UE_SMALL_NUMBER (1.e-8f)
#define UE_KINDA_SMALL_NUMBER (1.e-4f)
#define check(expr) assert(expr)
#define checkf(expr, ...) assert(expr)
#ifndef WITH_DEV_AUTOMATION_TESTS
#define WITH_DEV_AUTOMATION_TESTS 1
#endif

enum EForceInit { ForceInit };

template <typename T>
FORCEINLINE typename std::remove_reference<T>::type&& MoveTemp(T&& Obj) { return std::move(Obj); }

template <typename T>
struct TNumericLimits
{
	static constexpr T Max() { return std::numeric_limits<T>::max(); }
	static constexpr T Lowest() { return std::numeric_limits<T>::lowest(); }
	static constexpr T Min() { return std::numeric_limits<T>::min(); }
};

// ---------------------------------------------------------------------------------------------
// FMath
// ---------------------------------------------------------------------------------------------
struct FMath
{
	template <typename T> static T Abs(T A) { return A < T(0) ? -A : A; }
	template <typename T> static T Min(T A, T B) { return A < B ? A : B; }
	template <typename T> static T Max(T A, T B) { return A > B ? A : B; }
	template <typename T> static T Max3(T A, T B, T C) { return Max(Max(A, B), C); }
	template <typename T> static T Min3(T A, T B, T C) { return Min(Min(A, B), C); }
	template <typename T> static T Clamp(T X, T Lo, T Hi) { return X < Lo ? Lo : (X > Hi ? Hi : X); }
	template <typename T> static T Square(T A) { return A * A; }
	template <typename T, typename U> static T Lerp(const T& A, const T& B, const U& Alpha) { return static_cast<T>(A + Alpha * (B - A)); }

	static float Sqrt(float X) { return std::sqrt(X); }
	static double Sqrt(double X) { return std::sqrt(X); }
	static float Pow(float A, float B) { return std::pow(A, B); }
	static double Pow(double A, double B) { return std::pow(A, B); }
	static float Exp(float X) { return std::exp(X); }
	static double Exp(double X) { return std::exp(X); }
	static float Loge(float X) { return std::log(X); }
	static double Loge(double X) { return std::log(X); }
	static float Sin(float X) { return std::sin(X); }
	static double Sin(double X) { return std::sin(X); }
	static float Cos(float X) { return std::cos(X); }
	static double Cos(double X) { return std::cos(X); }
	static float Asin(float X) { return std::asin(X); }
	static double Asin(double X) { return std::asin(X); }
	static float Acos(float X) { return std::acos(X); }
	static double Acos(double X) { return std::acos(X); }
	static float Atan2(float Y, float X) { return std::atan2(Y, X); }
	static double Atan2(double Y, double X) { return std::atan2(Y, X); }

	// As in UE5: the double overloads return int64; the *32 variants always return int32.
	static int32 FloorToInt(float X) { return static_cast<int32>(std::floor(X)); }
	static int64 FloorToInt(double X) { return static_cast<int64>(std::floor(X)); }
	static int32 CeilToInt(float X) { return static_cast<int32>(std::ceil(X)); }
	static int64 CeilToInt(double X) { return static_cast<int64>(std::ceil(X)); }
	static int32 RoundToInt(float X) { return FloorToInt(X + 0.5f); }
	static int64 RoundToInt(double X) { return FloorToInt(X + 0.5); }
	static int32 FloorToInt32(float X) { return static_cast<int32>(std::floor(X)); }
	static int32 FloorToInt32(double X) { return static_cast<int32>(std::floor(X)); }
	static int32 CeilToInt32(float X) { return static_cast<int32>(std::ceil(X)); }
	static int32 CeilToInt32(double X) { return static_cast<int32>(std::ceil(X)); }
	static int32 RoundToInt32(float X) { return FloorToInt32(X + 0.5f); }
	static int32 RoundToInt32(double X) { return FloorToInt32(X + 0.5); }

	template <typename T> static T DegreesToRadians(T Deg) { return Deg * static_cast<T>(UE_DOUBLE_PI / 180.0); }
	template <typename T> static T RadiansToDegrees(T Rad) { return Rad * static_cast<T>(180.0 / UE_DOUBLE_PI); }

	template <typename T> static T UnwindRadians(T A)
	{
		const T Pi = static_cast<T>(UE_DOUBLE_PI);
		while (A > Pi) { A -= 2 * Pi; }
		while (A < -Pi) { A += 2 * Pi; }
		return A;
	}

	template <typename T> static T SmoothStep(T A, T B, T X)
	{
		if (X < A) { return T(0); }
		if (X >= B) { return T(1); }
		const T F = (X - A) / (B - A);
		return F * F * (T(3) - T(2) * F);
	}

	template <typename T> static bool IsNearlyEqual(T A, T B, T Tolerance = static_cast<T>(UE_SMALL_NUMBER)) { return Abs(A - B) <= Tolerance; }
	template <typename T> static bool IsNearlyZero(T A, T Tolerance = static_cast<T>(UE_SMALL_NUMBER)) { return Abs(A) <= Tolerance; }
};

// ---------------------------------------------------------------------------------------------
// Containers
// ---------------------------------------------------------------------------------------------
template <typename T>
class TArray
{
public:
	using ElementType = T;

	TArray() = default;
	TArray(std::initializer_list<T> Init) : Data(Init) {}
	TArray(const TArray&) = default;
	TArray(TArray&&) noexcept = default;
	TArray& operator=(const TArray&) = default;
	TArray& operator=(TArray&&) noexcept = default;

	int32 Num() const { return static_cast<int32>(Data.size()); }
	bool IsEmpty() const { return Data.empty(); }
	bool IsValidIndex(int32 I) const { return I >= 0 && I < Num(); }

	T& operator[](int32 I) { assert(IsValidIndex(I)); return Data[static_cast<size_t>(I)]; }
	const T& operator[](int32 I) const { assert(IsValidIndex(I)); return Data[static_cast<size_t>(I)]; }

	int32 Add(const T& Item) { Data.push_back(Item); return Num() - 1; }
	int32 Add(T&& Item) { Data.push_back(std::move(Item)); return Num() - 1; }
	template <typename... Args> int32 Emplace(Args&&... A) { Data.emplace_back(std::forward<Args>(A)...); return Num() - 1; }
	T& AddDefaulted_GetRef() { Data.emplace_back(); return Data.back(); }
	int32 AddUnique(const T& Item) { const int32 I = Find(Item); return I != INDEX_NONE ? I : Add(Item); }
	void Append(const TArray& Other) { Data.insert(Data.end(), Other.Data.begin(), Other.Data.end()); }

	void Reserve(int32 N) { Data.reserve(static_cast<size_t>(N)); }
	void Reset(int32 = 0) { Data.clear(); }
	void Empty(int32 = 0) { Data.clear(); }
	void SetNum(int32 N) { Data.resize(static_cast<size_t>(N)); }
	void SetNumUninitialized(int32 N) { Data.resize(static_cast<size_t>(N)); }
	void SetNumZeroed(int32 N) { Data.assign(static_cast<size_t>(N), T()); }

	void RemoveAt(int32 I) { Data.erase(Data.begin() + I); }
	template <typename P> int32 RemoveAll(P Pred)
	{
		const size_t Before = Data.size();
		Data.erase(std::remove_if(Data.begin(), Data.end(), Pred), Data.end());
		return static_cast<int32>(Before - Data.size());
	}
	void Swap(int32 A, int32 B) { std::swap(Data[static_cast<size_t>(A)], Data[static_cast<size_t>(B)]); }

	T& Last(int32 FromEnd = 0) { return Data[Data.size() - 1 - FromEnd]; }
	const T& Last(int32 FromEnd = 0) const { return Data[Data.size() - 1 - FromEnd]; }

	int32 Find(const T& Item) const
	{
		for (int32 I = 0; I < Num(); ++I) { if (Data[static_cast<size_t>(I)] == Item) { return I; } }
		return INDEX_NONE;
	}
	bool Contains(const T& Item) const { return Find(Item) != INDEX_NONE; }

	template <typename P> T* FindByPredicate(P Pred)
	{
		for (T& Item : Data) { if (Pred(Item)) { return &Item; } }
		return nullptr;
	}
	template <typename P> const T* FindByPredicate(P Pred) const
	{
		for (const T& Item : Data) { if (Pred(Item)) { return &Item; } }
		return nullptr;
	}
	template <typename P> bool ContainsByPredicate(P Pred) const { return FindByPredicate(Pred) != nullptr; }
	template <typename P> TArray FilterByPredicate(P Pred) const
	{
		TArray Out;
		for (const T& Item : Data) { if (Pred(Item)) { Out.Add(Item); } }
		return Out;
	}

	// Like UE, sorting an array of pointers hands the predicate dereferenced elements.
	void Sort() { Sort([](const auto& A, const auto& B) { return A < B; }); }
	template <typename P> void Sort(P Pred) { std::sort(Data.begin(), Data.end(), Wrap(Pred)); }
	void StableSort() { StableSort([](const auto& A, const auto& B) { return A < B; }); }
	template <typename P> void StableSort(P Pred) { std::stable_sort(Data.begin(), Data.end(), Wrap(Pred)); }

	bool operator==(const TArray& Other) const { return Data == Other.Data; }
	bool operator!=(const TArray& Other) const { return !(*this == Other); }

	T* GetData() { return Data.data(); }
	const T* GetData() const { return Data.data(); }

	typename std::vector<T>::iterator begin() { return Data.begin(); }
	typename std::vector<T>::iterator end() { return Data.end(); }
	typename std::vector<T>::const_iterator begin() const { return Data.begin(); }
	typename std::vector<T>::const_iterator end() const { return Data.end(); }

private:
	template <typename P> static auto Wrap(P Pred)
	{
		if constexpr (std::is_pointer<T>::value)
		{
			return [Pred](const T& A, const T& B) { return Pred(*A, *B); };
		}
		else
		{
			return [Pred](const T& A, const T& B) { return Pred(A, B); };
		}
	}

	std::vector<T> Data;
};

inline uint32 GetTypeHash(int32 V) { return static_cast<uint32>(V); }
inline uint32 GetTypeHash(uint32 V) { return V; }
inline uint32 GetTypeHash(int64 V) { return static_cast<uint32>(V) ^ static_cast<uint32>(static_cast<uint64>(V) >> 32); }
inline uint32 GetTypeHash(uint64 V) { return static_cast<uint32>(V) ^ static_cast<uint32>(V >> 32); }

template <typename T> struct TShimHash
{
	size_t operator()(const T& V) const { return static_cast<size_t>(GetTypeHash(V)); }
};

template <typename K, typename V>
struct TPair
{
	K Key;
	V Value;
};

/** Insertion-ordered map (UE's TMap iterates in insertion order when nothing is removed). */
template <typename K, typename V>
class TMap
{
public:
	using PairType = TPair<K, V>;

	int32 Num() const { return static_cast<int32>(Pairs.size()); }

	V& Add(const K& Key, const V& Value)
	{
		auto It = Index.find(Key);
		if (It != Index.end())
		{
			Pairs[It->second].Value = Value;
			return Pairs[It->second].Value;
		}
		Index.emplace(Key, Pairs.size());
		Pairs.push_back({ Key, Value });
		return Pairs.back().Value;
	}
	V& Add(const K& Key) { return Add(Key, V()); }
	V& FindOrAdd(const K& Key) { V* Found = Find(Key); return Found ? *Found : Add(Key); }

	V* Find(const K& Key)
	{
		auto It = Index.find(Key);
		return It == Index.end() ? nullptr : &Pairs[It->second].Value;
	}
	const V* Find(const K& Key) const
	{
		auto It = Index.find(Key);
		return It == Index.end() ? nullptr : &Pairs[It->second].Value;
	}
	V& FindChecked(const K& Key) { V* Found = Find(Key); assert(Found); return *Found; }
	const V& FindChecked(const K& Key) const { const V* Found = Find(Key); assert(Found); return *Found; }
	bool Contains(const K& Key) const { return Index.find(Key) != Index.end(); }

	void Reset() { Pairs.clear(); Index.clear(); }
	void Empty() { Reset(); }

	void GenerateKeyArray(TArray<K>& Out) const { Out.Reset(); for (const PairType& P : Pairs) { Out.Add(P.Key); } }
	void GenerateValueArray(TArray<V>& Out) const { Out.Reset(); for (const PairType& P : Pairs) { Out.Add(P.Value); } }

	typename std::vector<PairType>::iterator begin() { return Pairs.begin(); }
	typename std::vector<PairType>::iterator end() { return Pairs.end(); }
	typename std::vector<PairType>::const_iterator begin() const { return Pairs.begin(); }
	typename std::vector<PairType>::const_iterator end() const { return Pairs.end(); }

private:
	std::vector<PairType> Pairs;
	std::unordered_map<K, size_t, TShimHash<K>> Index;
};

template <typename T>
class TSet
{
public:
	void Add(const T& Item) { Items.insert(Item); }
	bool Contains(const T& Item) const { return Items.find(Item) != Items.end(); }
	int32 Num() const { return static_cast<int32>(Items.size()); }
	void Reset() { Items.clear(); }
	void Empty() { Items.clear(); }

private:
	std::unordered_set<T, TShimHash<T>> Items;
};

// ---------------------------------------------------------------------------------------------
// Strings
// ---------------------------------------------------------------------------------------------
class FString
{
public:
	FString() = default;
	FString(const char* S) : Data(S ? S : "") {}
	FString(const std::string& S) : Data(S) {}

	static FString Printf(const char* Format, ...)
	{
		char Buffer[4096];
		va_list Args;
		va_start(Args, Format);
		std::vsnprintf(Buffer, sizeof(Buffer), Format, Args);
		va_end(Args);
		return FString(Buffer);
	}
	static FString Chr(char C) { return FString(std::string(1, C)); }
	static FString FromInt(int32 V) { return FString(std::to_string(V)); }

	const char* operator*() const { return Data.c_str(); }
	int32 Len() const { return static_cast<int32>(Data.size()); }
	bool IsEmpty() const { return Data.empty(); }

	FString& operator+=(const FString& O) { Data += O.Data; return *this; }
	FString& operator+=(const char* O) { Data += O; return *this; }
	friend FString operator+(const FString& A, const FString& B) { return FString(A.Data + B.Data); }
	friend FString operator+(const FString& A, const char* B) { return FString(A.Data + B); }
	friend FString operator+(const char* A, const FString& B) { return FString(std::string(A) + B.Data); }

	// UE string comparison is case-insensitive.
	friend bool operator==(const FString& A, const FString& B) { return Lower(A.Data) == Lower(B.Data); }
	friend bool operator!=(const FString& A, const FString& B) { return !(A == B); }
	friend bool operator<(const FString& A, const FString& B) { return Lower(A.Data) < Lower(B.Data); }

	bool Contains(const FString& Sub) const { return Lower(Data).find(Lower(Sub.Data)) != std::string::npos; }
	const std::string& ToStd() const { return Data; }

	std::string::const_iterator begin() const { return Data.begin(); }
	std::string::const_iterator end() const { return Data.end(); }

private:
	static std::string Lower(std::string S)
	{
		for (char& C : S) { C = static_cast<char>(std::tolower(static_cast<unsigned char>(C))); }
		return S;
	}
	std::string Data;
};

inline uint32 GetTypeHash(const FString& S)
{
	std::string L = S.ToStd();
	for (char& C : L) { C = static_cast<char>(std::tolower(static_cast<unsigned char>(C))); }
	return static_cast<uint32>(std::hash<std::string>()(L));
}

class FName
{
public:
	FName() = default;
	FName(const char* S) : Data(S ? S : "") { if (Data == "None") { Data.clear(); } }
	FName(const FString& S) : FName(*S) {}

	FString ToString() const { return Data.empty() ? FString("None") : FString(Data); }
	bool IsNone() const { return Data.empty(); }
	friend bool operator==(const FName& A, const FName& B) { return FString(A.Data) == FString(B.Data); }
	friend bool operator!=(const FName& A, const FName& B) { return !(A == B); }
	const std::string& ToStd() const { return Data; }

private:
	std::string Data;
};

inline uint32 GetTypeHash(const FName& N) { return GetTypeHash(FString(N.ToStd())); }
#define NAME_None FName()

// ---------------------------------------------------------------------------------------------
// Math types
// ---------------------------------------------------------------------------------------------
struct FIntPoint
{
	int32 X = 0;
	int32 Y = 0;
	constexpr FIntPoint() = default;
	constexpr FIntPoint(int32 InX, int32 InY) : X(InX), Y(InY) {}
	static const FIntPoint ZeroValue;
	FIntPoint operator+(const FIntPoint& O) const { return FIntPoint(X + O.X, Y + O.Y); }
	FIntPoint operator-(const FIntPoint& O) const { return FIntPoint(X - O.X, Y - O.Y); }
	FIntPoint operator*(int32 S) const { return FIntPoint(X * S, Y * S); }
	bool operator==(const FIntPoint& O) const { return X == O.X && Y == O.Y; }
	bool operator!=(const FIntPoint& O) const { return !(*this == O); }
};
inline const FIntPoint FIntPoint::ZeroValue(0, 0);
inline uint32 GetTypeHash(const FIntPoint& P) { return static_cast<uint32>(P.X) * 73856093u ^ static_cast<uint32>(P.Y) * 19349663u; }

struct FIntVector
{
	int32 X = 0, Y = 0, Z = 0;
	constexpr FIntVector() = default;
	constexpr FIntVector(int32 InX, int32 InY, int32 InZ) : X(InX), Y(InY), Z(InZ) {}
	bool operator==(const FIntVector& O) const { return X == O.X && Y == O.Y && Z == O.Z; }
};
inline uint32 GetTypeHash(const FIntVector& V)
{
	return static_cast<uint32>(V.X) * 73856093u ^ static_cast<uint32>(V.Y) * 19349663u ^ static_cast<uint32>(V.Z) * 83492791u;
}

struct FVector
{
	double X = 0.0, Y = 0.0, Z = 0.0;
	constexpr FVector() = default;
	constexpr FVector(double InX, double InY, double InZ) : X(InX), Y(InY), Z(InZ) {}
	explicit constexpr FVector(double V) : X(V), Y(V), Z(V) {}

	static const FVector ZeroVector;
	static const FVector UpVector;
	static const FVector ForwardVector;
	static const FVector RightVector;
	static const FVector OneVector;

	FVector operator+(const FVector& O) const { return FVector(X + O.X, Y + O.Y, Z + O.Z); }
	FVector operator-(const FVector& O) const { return FVector(X - O.X, Y - O.Y, Z - O.Z); }
	FVector operator*(double S) const { return FVector(X * S, Y * S, Z * S); }
	FVector operator*(const FVector& O) const { return FVector(X * O.X, Y * O.Y, Z * O.Z); }
	FVector operator/(double S) const { return FVector(X / S, Y / S, Z / S); }
	FVector operator/(const FVector& O) const { return FVector(X / O.X, Y / O.Y, Z / O.Z); }
	FVector operator-() const { return FVector(-X, -Y, -Z); }
	FVector& operator+=(const FVector& O) { X += O.X; Y += O.Y; Z += O.Z; return *this; }
	FVector& operator-=(const FVector& O) { X -= O.X; Y -= O.Y; Z -= O.Z; return *this; }
	FVector& operator*=(double S) { X *= S; Y *= S; Z *= S; return *this; }
	bool operator==(const FVector& O) const { return X == O.X && Y == O.Y && Z == O.Z; }
	bool operator!=(const FVector& O) const { return !(*this == O); }
	friend FVector operator*(double S, const FVector& V) { return V * S; }

	double Size() const { return std::sqrt(X * X + Y * Y + Z * Z); }
	double SizeSquared() const { return X * X + Y * Y + Z * Z; }
	double GetMax() const { return std::max(X, std::max(Y, Z)); }
	double GetMin() const { return std::min(X, std::min(Y, Z)); }
	FVector GetSafeNormal(double Tolerance = UE_SMALL_NUMBER) const
	{
		const double S = SizeSquared();
		if (S <= Tolerance) { return ZeroVector; }
		const double Inv = 1.0 / std::sqrt(S);
		return FVector(X * Inv, Y * Inv, Z * Inv);
	}
	bool IsNearlyZero(double Tolerance = UE_KINDA_SMALL_NUMBER) const
	{
		return std::abs(X) <= Tolerance && std::abs(Y) <= Tolerance && std::abs(Z) <= Tolerance;
	}
	bool Equals(const FVector& O, double Tolerance = UE_KINDA_SMALL_NUMBER) const
	{
		return std::abs(X - O.X) <= Tolerance && std::abs(Y - O.Y) <= Tolerance && std::abs(Z - O.Z) <= Tolerance;
	}

	static double DotProduct(const FVector& A, const FVector& B) { return A.X * B.X + A.Y * B.Y + A.Z * B.Z; }
	static FVector CrossProduct(const FVector& A, const FVector& B)
	{
		return FVector(A.Y * B.Z - A.Z * B.Y, A.Z * B.X - A.X * B.Z, A.X * B.Y - A.Y * B.X);
	}
	static double Dist(const FVector& A, const FVector& B) { return (A - B).Size(); }
	double operator|(const FVector& O) const { return DotProduct(*this, O); }
	FVector operator^(const FVector& O) const { return CrossProduct(*this, O); }
};
inline const FVector FVector::ZeroVector(0.0, 0.0, 0.0);
inline const FVector FVector::UpVector(0.0, 0.0, 1.0);
inline const FVector FVector::ForwardVector(1.0, 0.0, 0.0);
inline const FVector FVector::RightVector(0.0, 1.0, 0.0);
inline const FVector FVector::OneVector(1.0, 1.0, 1.0);

struct FVector2D
{
	double X = 0.0, Y = 0.0;
	constexpr FVector2D() = default;
	constexpr FVector2D(double InX, double InY) : X(InX), Y(InY) {}
	static const FVector2D ZeroVector;
	FVector2D operator+(const FVector2D& O) const { return FVector2D(X + O.X, Y + O.Y); }
	FVector2D operator-(const FVector2D& O) const { return FVector2D(X - O.X, Y - O.Y); }
	FVector2D operator*(double S) const { return FVector2D(X * S, Y * S); }
	bool operator==(const FVector2D& O) const { return X == O.X && Y == O.Y; }
};
inline const FVector2D FVector2D::ZeroVector(0.0, 0.0);

struct FBox2D
{
	FVector2D Min, Max;
	bool bIsValid = false;
	FBox2D() = default;
	explicit FBox2D(EForceInit) {}
	FBox2D(const FVector2D& InMin, const FVector2D& InMax) : Min(InMin), Max(InMax), bIsValid(true) {}
};

struct FBox
{
	FVector Min, Max;
	uint8 IsValid = 0;
	FBox() = default;
	explicit FBox(EForceInit) {}
	FBox(const FVector& InMin, const FVector& InMax) : Min(InMin), Max(InMax), IsValid(1) {}
	FBox& operator+=(const FVector& P)
	{
		if (IsValid)
		{
			Min = FVector(std::min(Min.X, P.X), std::min(Min.Y, P.Y), std::min(Min.Z, P.Z));
			Max = FVector(std::max(Max.X, P.X), std::max(Max.Y, P.Y), std::max(Max.Z, P.Z));
		}
		else
		{
			Min = Max = P;
			IsValid = 1;
		}
		return *this;
	}
	FVector GetCenter() const { return (Min + Max) * 0.5; }
	FVector GetSize() const { return Max - Min; }
	FVector GetExtent() const { return (Max - Min) * 0.5; }
	bool IsInside(const FVector& P) const { return P.X > Min.X && P.X < Max.X && P.Y > Min.Y && P.Y < Max.Y && P.Z > Min.Z && P.Z < Max.Z; }
};

struct FQuat;

struct FRotator
{
	double Pitch = 0.0, Yaw = 0.0, Roll = 0.0;
	constexpr FRotator() = default;
	constexpr FRotator(double InPitch, double InYaw, double InRoll) : Pitch(InPitch), Yaw(InYaw), Roll(InRoll) {}
	FVector Vector() const
	{
		const double P = FMath::DegreesToRadians(Pitch);
		const double Y = FMath::DegreesToRadians(Yaw);
		return FVector(std::cos(P) * std::cos(Y), std::cos(P) * std::sin(Y), std::sin(P));
	}
	FQuat Quaternion() const;
};

struct FQuat
{
	double X = 0.0, Y = 0.0, Z = 0.0, W = 1.0;
	constexpr FQuat() = default;
	constexpr FQuat(double InX, double InY, double InZ, double InW) : X(InX), Y(InY), Z(InZ), W(InW) {}
	FQuat(const FVector& Axis, double AngleRad)
	{
		const double H = AngleRad * 0.5;
		const double S = std::sin(H);
		X = Axis.X * S; Y = Axis.Y * S; Z = Axis.Z * S; W = std::cos(H);
	}
	static const FQuat Identity;

	FQuat operator*(const FQuat& Q) const
	{
		return FQuat(
			W * Q.X + X * Q.W + Y * Q.Z - Z * Q.Y,
			W * Q.Y - X * Q.Z + Y * Q.W + Z * Q.X,
			W * Q.Z + X * Q.Y - Y * Q.X + Z * Q.W,
			W * Q.W - X * Q.X - Y * Q.Y - Z * Q.Z);
	}
	FVector RotateVector(const FVector& V) const
	{
		const FVector Q(X, Y, Z);
		const FVector T = FVector::CrossProduct(Q, V) * 2.0;
		return V + (T * W) + FVector::CrossProduct(Q, T);
	}
	FQuat Inverse() const { return FQuat(-X, -Y, -Z, W); }

	static FQuat FindBetweenNormals(const FVector& A, const FVector& B)
	{
		const double NormAB = 1.0;
		double Wv = NormAB + FVector::DotProduct(A, B);
		FQuat Result;
		if (Wv >= 1e-6 * NormAB)
		{
			Result = FQuat(A.Y * B.Z - A.Z * B.Y, A.Z * B.X - A.X * B.Z, A.X * B.Y - A.Y * B.X, Wv);
		}
		else
		{
			Wv = 0.0;
			Result = std::abs(A.X) > std::abs(A.Y) ? FQuat(-A.Z, 0.0, A.X, Wv) : FQuat(0.0, -A.Z, A.Y, Wv);
		}
		const double N = std::sqrt(Result.X * Result.X + Result.Y * Result.Y + Result.Z * Result.Z + Result.W * Result.W);
		return FQuat(Result.X / N, Result.Y / N, Result.Z / N, Result.W / N);
	}
};
inline const FQuat FQuat::Identity(0.0, 0.0, 0.0, 1.0);

inline FQuat FRotator::Quaternion() const
{
	// Unreal convention: yaw about Z, then pitch about Y, then roll about X.
	const double CR = std::cos(FMath::DegreesToRadians(Roll) * 0.5), SR = std::sin(FMath::DegreesToRadians(Roll) * 0.5);
	const double CP = std::cos(FMath::DegreesToRadians(Pitch) * 0.5), SP = std::sin(FMath::DegreesToRadians(Pitch) * 0.5);
	const double CY = std::cos(FMath::DegreesToRadians(Yaw) * 0.5), SY = std::sin(FMath::DegreesToRadians(Yaw) * 0.5);
	return FQuat(CR * SP * SY - SR * CP * CY, -CR * SP * CY - SR * CP * SY, CR * CP * SY - SR * SP * CY, CR * CP * CY + SR * SP * SY);
}

struct FTransform
{
	FQuat Rotation;
	FVector Translation;
	FVector Scale3D = FVector(1.0, 1.0, 1.0);

	FTransform() = default;
	FTransform(const FQuat& R, const FVector& T, const FVector& S = FVector(1.0, 1.0, 1.0)) : Rotation(R), Translation(T), Scale3D(S) {}
	FTransform(const FRotator& R, const FVector& T, const FVector& S = FVector(1.0, 1.0, 1.0)) : Rotation(R.Quaternion()), Translation(T), Scale3D(S) {}

	FVector GetLocation() const { return Translation; }
	FQuat GetRotation() const { return Rotation; }
	FVector GetScale3D() const { return Scale3D; }
	FVector TransformPosition(const FVector& P) const { return Rotation.RotateVector(P * Scale3D) + Translation; }
	FVector TransformVector(const FVector& V) const { return Rotation.RotateVector(V * Scale3D); }
	FVector GetUnitAxisX() const { return Rotation.RotateVector(FVector(1.0, 0.0, 0.0)); }
	bool Equals(const FTransform& O, double Tolerance = UE_KINDA_SMALL_NUMBER) const
	{
		return Translation.Equals(O.Translation, Tolerance) && Scale3D.Equals(O.Scale3D, Tolerance)
			&& std::abs(Rotation.X - O.Rotation.X) <= Tolerance && std::abs(Rotation.Y - O.Rotation.Y) <= Tolerance
			&& std::abs(Rotation.Z - O.Rotation.Z) <= Tolerance && std::abs(Rotation.W - O.Rotation.W) <= Tolerance;
	}
};

struct FLinearColor
{
	float R = 0.f, G = 0.f, B = 0.f, A = 1.f;
	constexpr FLinearColor() = default;
	constexpr FLinearColor(float InR, float InG, float InB, float InA = 1.f) : R(InR), G(InG), B(InB), A(InA) {}
	static const FLinearColor White;
	static const FLinearColor Black;
	static const FLinearColor Gray;
	bool operator==(const FLinearColor& O) const { return R == O.R && G == O.G && B == O.B && A == O.A; }
};
inline const FLinearColor FLinearColor::White(1.f, 1.f, 1.f, 1.f);
inline const FLinearColor FLinearColor::Black(0.f, 0.f, 0.f, 1.f);
inline const FLinearColor FLinearColor::Gray(0.5f, 0.5f, 0.5f, 1.f);
