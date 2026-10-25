#pragma once

#include <string>
#include <vector>

struct FGOContentDefs;

namespace GOHeadless
{
	/** Loads the plugin's JSON data files into FGOContentDefs, by UPROPERTY name like the editor does. */
	bool LoadContent(const std::string& DataDirectory, FGOContentDefs& Out, std::vector<std::string>& Errors);
}
