#pragma once

// Minimal JSON reader for the headless test build (the editor uses FJsonObjectConverter).

#include "CoreMinimal.h"
#include "UObject/SoftObjectPtr.h"

#include <stdexcept>

namespace GOHeadless
{
	struct FJsonNode
	{
		enum class EKind { Null, Bool, Number, String, Array, Object };
		EKind Kind = EKind::Null;
		bool Bool = false;
		double Number = 0.0;
		std::string String;
		std::vector<FJsonNode> Array;
		std::vector<std::pair<std::string, FJsonNode>> Object;

		const FJsonNode* Find(const std::string& Key) const
		{
			for (const auto& KV : Object)
			{
				if (KV.first == Key) { return &KV.second; }
			}
			return nullptr;
		}
	};

	class FJsonParser
	{
	public:
		explicit FJsonParser(const std::string& InText) : Text(InText) {}

		FJsonNode Parse()
		{
			FJsonNode Root = Value();
			SkipSpace();
			if (Pos != Text.size()) { Error("trailing characters"); }
			return Root;
		}

	private:
		[[noreturn]] void Error(const std::string& What) const
		{
			throw std::runtime_error("JSON error at offset " + std::to_string(Pos) + ": " + What);
		}

		void SkipSpace()
		{
			while (Pos < Text.size() && std::isspace(static_cast<unsigned char>(Text[Pos]))) { ++Pos; }
		}

		bool Consume(char C)
		{
			SkipSpace();
			if (Pos < Text.size() && Text[Pos] == C) { ++Pos; return true; }
			return false;
		}

		void Expect(char C)
		{
			if (!Consume(C)) { Error(std::string("expected '") + C + "'"); }
		}

		FJsonNode Value()
		{
			SkipSpace();
			if (Pos >= Text.size()) { Error("unexpected end"); }
			const char C = Text[Pos];
			FJsonNode Node;
			if (C == '{')
			{
				++Pos;
				Node.Kind = FJsonNode::EKind::Object;
				if (Consume('}')) { return Node; }
				do
				{
					SkipSpace();
					std::string Key = StringLiteral();
					Expect(':');
					Node.Object.emplace_back(Key, Value());
				} while (Consume(','));
				Expect('}');
			}
			else if (C == '[')
			{
				++Pos;
				Node.Kind = FJsonNode::EKind::Array;
				if (Consume(']')) { return Node; }
				do { Node.Array.push_back(Value()); } while (Consume(','));
				Expect(']');
			}
			else if (C == '"')
			{
				Node.Kind = FJsonNode::EKind::String;
				Node.String = StringLiteral();
			}
			else if (Text.compare(Pos, 4, "true") == 0) { Pos += 4; Node.Kind = FJsonNode::EKind::Bool; Node.Bool = true; }
			else if (Text.compare(Pos, 5, "false") == 0) { Pos += 5; Node.Kind = FJsonNode::EKind::Bool; Node.Bool = false; }
			else if (Text.compare(Pos, 4, "null") == 0) { Pos += 4; }
			else
			{
				size_t End = 0;
				Node.Kind = FJsonNode::EKind::Number;
				Node.Number = std::stod(Text.substr(Pos), &End);
				if (End == 0) { Error("bad value"); }
				Pos += End;
			}
			return Node;
		}

		std::string StringLiteral()
		{
			if (Pos >= Text.size() || Text[Pos] != '"') { Error("expected string"); }
			++Pos;
			std::string Out;
			while (Pos < Text.size() && Text[Pos] != '"')
			{
				char C = Text[Pos++];
				if (C == '\\' && Pos < Text.size())
				{
					const char E = Text[Pos++];
					switch (E)
					{
					case 'n': C = '\n'; break;
					case 't': C = '\t'; break;
					default: C = E; break;
					}
				}
				Out += C;
			}
			if (Pos >= Text.size()) { Error("unterminated string"); }
			++Pos;
			return Out;
		}

		const std::string& Text;
		size_t Pos = 0;
	};

	struct FReadContext
	{
		std::vector<std::string>* Errors = nullptr;
		void Fail(const std::string& Path, const std::string& What) { Errors->push_back(Path + ": " + What); }
	};

	inline void ReadValue(const FJsonNode& N, double& Out, FReadContext& C, const std::string& Path)
	{
		if (N.Kind != FJsonNode::EKind::Number) { C.Fail(Path, "expected a number"); return; }
		Out = N.Number;
	}
	inline void ReadValue(const FJsonNode& N, float& Out, FReadContext& C, const std::string& Path)
	{
		double V = Out;
		ReadValue(N, V, C, Path);
		Out = static_cast<float>(V);
	}
	inline void ReadValue(const FJsonNode& N, int32& Out, FReadContext& C, const std::string& Path)
	{
		if (N.Kind != FJsonNode::EKind::Number || std::floor(N.Number) != N.Number) { C.Fail(Path, "expected an integer"); return; }
		Out = static_cast<int32>(N.Number);
	}
	inline void ReadValue(const FJsonNode& N, bool& Out, FReadContext& C, const std::string& Path)
	{
		if (N.Kind != FJsonNode::EKind::Bool) { C.Fail(Path, "expected true/false"); return; }
		Out = N.Bool;
	}
	inline void ReadValue(const FJsonNode& N, FString& Out, FReadContext& C, const std::string& Path)
	{
		if (N.Kind != FJsonNode::EKind::String) { C.Fail(Path, "expected a string"); return; }
		Out = FString(N.String);
	}
	inline void ReadValue(const FJsonNode& N, FName& Out, FReadContext& C, const std::string& Path)
	{
		FString S;
		ReadValue(N, S, C, Path);
		Out = FName(S);
	}
	template <typename T>
	void ReadValue(const FJsonNode& N, TSoftObjectPtr<T>& Out, FReadContext& C, const std::string& Path)
	{
		ReadValue(N, Out.Path, C, Path);
	}
	template <typename T>
	void ReadValue(const FJsonNode& N, TSoftClassPtr<T>& Out, FReadContext& C, const std::string& Path)
	{
		ReadValue(N, Out.Path, C, Path);
	}

	/** Reads an object of numeric fields with exactly the given names (FVector, FIntPoint, ...). */
	template <size_t Count, typename F>
	void ReadNumbers(const FJsonNode& N, const char* const (&Names)[Count], F Assign, FReadContext& C, const std::string& Path)
	{
		if (N.Kind != FJsonNode::EKind::Object) { C.Fail(Path, "expected an object"); return; }
		for (const auto& KV : N.Object)
		{
			bool bKnown = false;
			for (size_t I = 0; I < Count; ++I)
			{
				if (KV.first == Names[I])
				{
					bKnown = true;
					if (KV.second.Kind != FJsonNode::EKind::Number) { C.Fail(Path + "." + KV.first, "expected a number"); }
					else { Assign(I, KV.second.Number); }
				}
			}
			if (!bKnown) { C.Fail(Path, "unknown component '" + KV.first + "'"); }
		}
	}

	inline void ReadValue(const FJsonNode& N, FVector& Out, FReadContext& C, const std::string& Path)
	{
		static const char* const Names[] = { "X", "Y", "Z" };
		ReadNumbers(N, Names, [&](size_t I, double V) { (I == 0 ? Out.X : I == 1 ? Out.Y : Out.Z) = V; }, C, Path);
	}
	inline void ReadValue(const FJsonNode& N, FVector2D& Out, FReadContext& C, const std::string& Path)
	{
		static const char* const Names[] = { "X", "Y" };
		ReadNumbers(N, Names, [&](size_t I, double V) { (I == 0 ? Out.X : Out.Y) = V; }, C, Path);
	}
	inline void ReadValue(const FJsonNode& N, FIntPoint& Out, FReadContext& C, const std::string& Path)
	{
		static const char* const Names[] = { "X", "Y" };
		ReadNumbers(N, Names, [&](size_t I, double V) { (I == 0 ? Out.X : Out.Y) = static_cast<int32>(V); }, C, Path);
	}
	inline void ReadValue(const FJsonNode& N, FLinearColor& Out, FReadContext& C, const std::string& Path)
	{
		static const char* const Names[] = { "R", "G", "B", "A" };
		ReadNumbers(N, Names, [&](size_t I, double V)
		{
			float* Channels[] = { &Out.R, &Out.G, &Out.B, &Out.A };
			*Channels[I] = static_cast<float>(V);
		}, C, Path);
	}
}
