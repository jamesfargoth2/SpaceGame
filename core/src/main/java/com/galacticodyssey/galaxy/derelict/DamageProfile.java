package com.galacticodyssey.galaxy.derelict;

/** Describes the physical damage pattern inflicted on a derelict. */
public final class DamageProfile {
    public final int impactSites;
    public final int explosionSites;
    /** Primary direction of damage (normalized xyz). */
    public final float primaryDamageDirX;
    public final float primaryDamageDirY;
    public final float primaryDamageDirZ;
    /** Overall systemic damage level, 0-1. */
    public final float systemicDamage;
    public final boolean radiationLeak;
    public final boolean atmosphereLost;
    /** Uniform wear from age, 0-1. */
    public final float uniformDegradation;
    public final int alienModifications;

    public DamageProfile(int impactSites, int explosionSites,
                         float primaryDamageDirX, float primaryDamageDirY, float primaryDamageDirZ,
                         float systemicDamage, boolean radiationLeak, boolean atmosphereLost,
                         float uniformDegradation, int alienModifications) {
        this.impactSites = impactSites;
        this.explosionSites = explosionSites;
        this.primaryDamageDirX = primaryDamageDirX;
        this.primaryDamageDirY = primaryDamageDirY;
        this.primaryDamageDirZ = primaryDamageDirZ;
        this.systemicDamage = systemicDamage;
        this.radiationLeak = radiationLeak;
        this.atmosphereLost = atmosphereLost;
        this.uniformDegradation = uniformDegradation;
        this.alienModifications = alienModifications;
    }
}
