package com.galacticodyssey.mission.job;

import java.util.List;

public class SectorContext {
    public String sectorId;
    public List<String> locationIds;
    public List<String> npcIds;
    public List<String> factionTags;
    public float dangerLevel;           // 0.0 – 1.0
}
