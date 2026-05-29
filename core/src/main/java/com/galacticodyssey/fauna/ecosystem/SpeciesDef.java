package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.fauna.behavior.*;
import com.galacticodyssey.planet.BiomeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpeciesDef {
    public String id;
    public String archetypeId;
    public Diet diet = Diet.HERBIVORE;
    public Temperament temperament = Temperament.NEUTRAL;
    public SocialStructure socialStructure = SocialStructure.SOLITARY;
    public int herdSizeMin = 1;
    public int herdSizeMax = 1;
    public final Map<BiomeType, Float> biomeAffinities = new HashMap<>();
    public int trophicLevel = 1;
    public final List<String> preySpecies = new ArrayList<>();
    public ActivityCycle activityCycle = ActivityCycle.DIURNAL;
    public float detectionRadius = 25f;
    public float fleeRadius = 15f;
    public float fleeSpeedMultiplier = 1.5f;
    public float safeDistance = 40f;
    public float birthRate = 0.02f;
    public int carryingCapacityBase = 30;
}
