package com.galacticodyssey.city.layout;

import com.galacticodyssey.galaxy.faction.FactionEthos;

import java.util.ArrayList;
import java.util.List;

/** Input contract for {@link CityLayoutGenerator}. Population is the size driver. */
public final class CityRequest {
    public long seed;
    public int population;
    public FactionEthos rulingEthos = FactionEthos.FEDERATION;
    public String factionId = "unknown";
    public TerrainSampler terrain = new FlatTerrainSampler();
    public boolean hasSpaceport = true;
    public List<AuthoredLandmark> authoredLandmarks = new ArrayList<>();
}
