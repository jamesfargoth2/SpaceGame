package com.galacticodyssey.npc.data;

import java.util.ArrayList;
import java.util.List;

public class SpeciesDefinition {
    public String id;
    public String name;
    public float accuracyMod;
    public float repairMod;
    public float medicalMod;
    public float pilotingMod;
    public float scienceMod;
    public float combatMod;
    public List<String> portraitIds = new ArrayList<>();
}
