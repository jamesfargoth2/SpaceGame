package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.Color;

/** Data-driven alien-plant definition. Flat superset: which fields matter depends on archetype. */
public class AlienPlantSpecies {
    public String id;
    public AlienArchetype archetype = AlienArchetype.BIOLUMINESCENT;

    // Stalk (all archetypes)
    public float stalkHeightMin = 1f, stalkHeightMax = 2f;
    public float stalkBaseRadius = 0.12f;
    public float stalkTaper = 0.8f;
    public int stalkSides = 6;
    public Color stalkColor = new Color(0.2f, 0.2f, 0.25f, 1f);

    // Canopy (shared)
    public Color canopyColor = new Color(0.3f, 0.8f, 0.7f, 1f);
    public float canopyEmissive = 0f;

    // Bioluminescent canopy
    public int clumpsMin = 3, clumpsMax = 6;
    public float clumpRadiusMin = 0.4f, clumpRadiusMax = 0.8f;
    // Bioluminescent details
    public int detailCountMin = 0, detailCountMax = 0;
    public float detailEmissive = 0f;

    // Carnivorous canopy
    public float mouthRadiusMin = 0.5f, mouthRadiusMax = 0.9f;
    public float canopyDepthMin = 0.6f, canopyDepthMax = 1.1f;
    public float lureEmissive = 0f;
    public int teethMin = 0, teethMax = 0;

    // Crystal canopy
    public int shardsMin = 4, shardsMax = 8;
    public float shardLenMin = 0.6f, shardLenMax = 1.6f;
    public int subShardsMin = 0, subShardsMax = 0;

    public int prototypeVariants = 6;
}
