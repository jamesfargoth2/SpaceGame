package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.planet.BiomeType;
import java.util.ArrayList;
import java.util.List;

public final class ChunkPopulationRecord {
    public int chunkX, chunkZ;
    public BiomeType biome;
    public final List<SpeciesPopulation> populations = new ArrayList<>();
    public double lastTickTime;
}
