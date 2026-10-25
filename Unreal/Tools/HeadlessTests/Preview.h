#pragma once

#include <string>

struct FGOContentDefs;

namespace GOHeadless
{
	/**
	 * Writes previews of a seed's new-game scenario into OutDir:
	 *   academy_<seed>.svg  top-down plan of the generated academy (Unreal top view: +X up)
	 *   planet_<seed>.png   equirectangular biome/relief map of the home world, landing site marked
	 *   galaxy_<seed>.svg   star map coloured by spectral class, home star highlighted
	 */
	bool WritePreviews(const FGOContentDefs& Content, unsigned long long Seed, const std::string& OutDir);
}
