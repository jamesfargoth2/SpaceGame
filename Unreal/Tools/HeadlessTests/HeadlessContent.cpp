#include "HeadlessContent.h"

#include "Core/GOContentDefs.h"
#include "HeadlessJson.h"
#include "HeadlessReflection.generated.inl"

#include <fstream>
#include <sstream>

namespace GOHeadless
{
	namespace
	{
		template <typename RowT, typename AddFn>
		void LoadRows(const std::string& Directory, const std::string& FileName, AddFn Add, std::vector<std::string>& Errors)
		{
			std::ifstream File(Directory + "/" + FileName);
			if (!File)
			{
				Errors.push_back(FileName + ": cannot open");
				return;
			}
			std::stringstream Buffer;
			Buffer << File.rdbuf();
			const std::string Text = Buffer.str();

			FJsonNode Root;
			try
			{
				Root = FJsonParser(Text).Parse();
			}
			catch (const std::exception& Ex)
			{
				Errors.push_back(FileName + ": " + Ex.what());
				return;
			}
			if (Root.Kind != FJsonNode::EKind::Array)
			{
				Errors.push_back(FileName + ": expected an array of rows");
				return;
			}

			FReadContext Context{ &Errors };
			for (const FJsonNode& Element : Root.Array)
			{
				const FJsonNode* Name = Element.Find("Name");
				if (!Name || Name->Kind != FJsonNode::EKind::String || Name->String.empty())
				{
					Errors.push_back(FileName + ": row without a \"Name\"");
					continue;
				}
				RowT Row{};
				ReadValue(Element, Row, Context, FileName + "[" + Name->String + "]");
				Add(Name->String, Row);
			}
		}
	}

	bool LoadContent(const std::string& Directory, FGOContentDefs& Out, std::vector<std::string>& Errors)
	{
		const size_t ErrorsBefore = Errors.size();

		LoadRows<FGOSpectralClassDef>(Directory, "SpectralClasses.json",
			[&](const std::string&, const FGOSpectralClassDef& Row) { Out.SpectralClasses.Add(Row); }, Errors);
		LoadRows<FGOPlanetTypeDef>(Directory, "PlanetTypes.json",
			[&](const std::string&, const FGOPlanetTypeDef& Row) { Out.PlanetTypes.Add(Row); }, Errors);
		LoadRows<FGOBiomeDef>(Directory, "Biomes.json",
			[&](const std::string&, const FGOBiomeDef& Row) { Out.Biomes.Add(Row); }, Errors);
		LoadRows<FGONameListDef>(Directory, "NameLists.json",
			[&](const std::string& Name, const FGONameListDef& Row) { Out.NameLists.Add(FName(Name.c_str()), Row.Values); }, Errors);
		LoadRows<FGOHubRoomTemplate>(Directory, "HubRooms.json",
			[&](const std::string& Name, const FGOHubRoomTemplate& Row)
			{
				FGOHubRoomTemplate Copy = Row;
				Copy.TemplateId = FName(Name.c_str());
				Out.HubRooms.Add(Copy);
			}, Errors);
		LoadRows<FGOAssetKitRow>(Directory, "AssetKit.json",
			[&](const std::string& Name, const FGOAssetKitRow& Row)
			{
				FGOAssetKitRow Copy = Row;
				Copy.AssetId = FName(Name.c_str());
				Out.AssetKit.Add(Copy.AssetId, Copy);
			}, Errors);

		return Errors.size() == ErrorsBefore;
	}
}
