package com.galacticodyssey.galaxy.nebula;

/**
 * An object embedded within a nebula volume (galaxy-space coordinates).
 */
public final class EmbeddedObject {

    public final EmbeddedObjectType type;
    public final double posX;
    public final double posY;
    public final double posZ;
    public final long seed;

    public EmbeddedObject(EmbeddedObjectType type, double posX, double posY, double posZ, long seed) {
        this.type = type;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.seed = seed;
    }
}
