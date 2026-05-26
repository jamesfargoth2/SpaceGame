package com.galacticodyssey.galaxy.faction;

/** Immutable data bag holding all procedurally generated faction attributes. */
public final class FactionData {

    public final String id;
    public final String name;

    /** Galaxy-space capital coordinates (doubles for 64-bit precision). */
    public final double capitalX;
    public final double capitalY;
    public final double capitalZ;

    /** Relative strengths, 0-1. */
    public final float militaryStrength;
    public final float economicStrength;

    public final FactionEthos ethos;

    /** Map colour channels, 0-1 each. */
    public final float mapColorR;
    public final float mapColorG;
    public final float mapColorB;

    /** Maximum distance (in light-years) this faction's influence extends from its capital. */
    public final float influenceRadiusLY;

    /** Key into LanguageStyles for naming systems/stations in this faction's territory. */
    public final String namingStyleId;

    public FactionData(String id, String name,
                       double capitalX, double capitalY, double capitalZ,
                       float militaryStrength, float economicStrength,
                       FactionEthos ethos,
                       float mapColorR, float mapColorG, float mapColorB,
                       float influenceRadiusLY, String namingStyleId) {
        this.id = id;
        this.name = name;
        this.capitalX = capitalX;
        this.capitalY = capitalY;
        this.capitalZ = capitalZ;
        this.militaryStrength = militaryStrength;
        this.economicStrength = economicStrength;
        this.ethos = ethos;
        this.mapColorR = mapColorR;
        this.mapColorG = mapColorG;
        this.mapColorB = mapColorB;
        this.influenceRadiusLY = influenceRadiusLY;
        this.namingStyleId = namingStyleId;
    }
}
