package com.galacticodyssey.galaxy.asteroid;

/**
 * A mineral vein deposit embedded within an asteroid.
 */
public final class VeinDeposit {

    public final float centreX;
    public final float centreY;
    public final float centreZ;
    public final MineralType mineral;
    public final float radiusKm;
    public final float richness;

    public VeinDeposit(float centreX, float centreY, float centreZ,
                       MineralType mineral, float radiusKm, float richness) {
        this.centreX = centreX;
        this.centreY = centreY;
        this.centreZ = centreZ;
        this.mineral = mineral;
        this.radiusKm = radiusKm;
        this.richness = Math.max(0f, Math.min(1f, richness));
    }
}
