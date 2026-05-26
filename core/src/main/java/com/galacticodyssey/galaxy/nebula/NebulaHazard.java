package com.galacticodyssey.galaxy.nebula;

/**
 * A localized hazard within a nebula volume.
 */
public final class NebulaHazard {

    public final NebulaHazardType type;
    public final float centreX;
    public final float centreY;
    public final float centreZ;
    public final float radius;
    public final float intensity;
    public final float periodSeconds;

    public NebulaHazard(NebulaHazardType type, float centreX, float centreY, float centreZ,
                        float radius, float intensity, float periodSeconds) {
        this.type = type;
        this.centreX = centreX;
        this.centreY = centreY;
        this.centreZ = centreZ;
        this.radius = radius;
        this.intensity = intensity;
        this.periodSeconds = periodSeconds;
    }
}
