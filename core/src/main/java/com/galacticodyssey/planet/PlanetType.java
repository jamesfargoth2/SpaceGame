package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.OrbitalZone;
import java.util.EnumSet;

public enum PlanetType {
    MOLTEN(0.3f, 0.8f, 0.2f, 0.6f, 0, 0, false, EnumSet.of(OrbitalZone.INNER)),
    BARREN(0.4f, 1.2f, 0.3f, 0.9f, 0, 1, false, EnumSet.of(OrbitalZone.INNER, OrbitalZone.HABITABLE)),
    ARID(0.7f, 1.5f, 0.5f, 1.2f, 0, 2, true, EnumSet.of(OrbitalZone.HABITABLE)),
    TERRAN(0.8f, 1.8f, 0.7f, 1.4f, 0, 3, true, EnumSet.of(OrbitalZone.HABITABLE)),
    OCEAN(1.0f, 2.0f, 0.8f, 1.5f, 0, 2, true, EnumSet.of(OrbitalZone.HABITABLE)),
    TOXIC(0.8f, 1.6f, 0.6f, 1.3f, 0, 1, true, EnumSet.of(OrbitalZone.INNER, OrbitalZone.HABITABLE)),
    GAS_GIANT(6f, 15f, 1.5f, 3.0f, 2, 8, false, EnumSet.of(OrbitalZone.OUTER, OrbitalZone.DEEP)),
    ICE_GIANT(3f, 6f, 1.0f, 1.8f, 1, 5, false, EnumSet.of(OrbitalZone.OUTER, OrbitalZone.DEEP)),
    ICE_WORLD(0.3f, 1.0f, 0.2f, 0.7f, 0, 1, false, EnumSet.of(OrbitalZone.OUTER, OrbitalZone.DEEP)),
    DWARF(0.1f, 0.4f, 0.03f, 0.15f, 0, 0, false, EnumSet.of(OrbitalZone.DEEP));

    public final float radiusMin;
    public final float radiusMax;
    public final float gravityMin;
    public final float gravityMax;
    public final int moonMin;
    public final int moonMax;
    public final boolean hasAtmosphere;
    public final EnumSet<OrbitalZone> validZones;

    PlanetType(float radiusMin, float radiusMax, float gravityMin, float gravityMax,
               int moonMin, int moonMax, boolean hasAtmosphere, EnumSet<OrbitalZone> validZones) {
        this.radiusMin = radiusMin;
        this.radiusMax = radiusMax;
        this.gravityMin = gravityMin;
        this.gravityMax = gravityMax;
        this.moonMin = moonMin;
        this.moonMax = moonMax;
        this.hasAtmosphere = hasAtmosphere;
        this.validZones = validZones;
    }

    public boolean hasSurface() {
        return this != GAS_GIANT && this != ICE_GIANT;
    }
}
