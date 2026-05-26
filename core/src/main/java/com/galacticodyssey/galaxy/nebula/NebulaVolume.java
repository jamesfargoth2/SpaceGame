package com.galacticodyssey.galaxy.nebula;

import java.util.List;

/**
 * Generated nebula interior data containing density, ionisation, hazards, and embedded objects.
 */
public final class NebulaVolume {

    public final long seed;
    public final float peakDensity;
    public final float dustFraction;
    public final List<IonisationZone> ionZones;
    public final List<NebulaHazard> hazards;
    public final List<EmbeddedObject> embeddedObjects;
    public final float glowIntensity;
    public final float[] primaryColor;
    public final float[] secondaryColor;

    public NebulaVolume(long seed, float peakDensity, float dustFraction,
                        List<IonisationZone> ionZones, List<NebulaHazard> hazards,
                        List<EmbeddedObject> embeddedObjects, float glowIntensity,
                        float[] primaryColor, float[] secondaryColor) {
        this.seed = seed;
        this.peakDensity = peakDensity;
        this.dustFraction = dustFraction;
        this.ionZones = ionZones;
        this.hazards = hazards;
        this.embeddedObjects = embeddedObjects;
        this.glowIntensity = glowIntensity;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
    }
}
