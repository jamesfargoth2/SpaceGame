package com.galacticodyssey.city.layout.model;

import com.galacticodyssey.galaxy.faction.FactionEthos;

import java.util.ArrayList;
import java.util.List;

/** The complete, deterministic, pure-data output of sub-project A. */
public final class CityLayout {
    public long cityId;
    public String name;
    public long seed;
    public int population;
    public CityType type;
    public CityForm form;
    public FactionEthos rulingEthos;
    public String factionId;
    public final GalaxyAnchor localToGalaxyAnchor = new GalaxyAnchor(); // filled by sub-project E

    public final List<Landmark> landmarks = new ArrayList<>();
    public final List<Street> streets = new ArrayList<>();
    public final List<CityBlock> blocks = new ArrayList<>();
    public final List<BuildingLot> lots = new ArrayList<>();
    public CityWall wall; // nullable
}
