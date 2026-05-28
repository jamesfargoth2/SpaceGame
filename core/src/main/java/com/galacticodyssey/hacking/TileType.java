package com.galacticodyssey.hacking;

/**
 * Enum representing the shape and connectivity of a hacking puzzle tile.
 *
 * Connector sides are encoded as a 4-bit mask:
 * - bit 0 = North (top)
 * - bit 1 = East (right)
 * - bit 2 = South (bottom)
 * - bit 3 = West (left)
 *
 * Each tile has a base connector pattern (at rotation 0) and supports
 * clockwise 90° rotations (0–3 steps).
 *
 * Rotation formula: new = (old >> 3) | ((old << 1) & 0xE)
 * This rotates the bit pattern clockwise by shifting west to north.
 */
public enum TileType {
    /** Straight connector: North + South (vertical or horizontal after rotation). */
    STRAIGHT,

    /** Elbow connector: North + East (L-shape after rotations). */
    ELBOW,

    /** Tee connector: North + East + South (T-shape after rotations). */
    TEE,

    /** Cross connector: all four sides (+ shape). */
    CROSS,

    /** Empty tile: no connectors. */
    EMPTY;

    /**
     * Returns the base connector bitmask for this tile type at rotation 0.
     *
     * @return 4-bit mask encoding open sides
     */
    private int baseSides() {
        switch (this) {
            case STRAIGHT: return 0b0101; // North + South
            case ELBOW:    return 0b0011; // North + East
            case TEE:      return 0b0111; // North + East + South
            case CROSS:    return 0b1111; // All four sides
            default:       return 0b0000; // Empty has no sides
        }
    }

    /**
     * Returns the connector bitmask after applying {@code rotation} clockwise 90° steps.
     *
     * Each rotation step applies: new = (old >> 3) | ((old << 1) & 0xE)
     *
     * @param rotation number of clockwise 90° rotations (0–3)
     * @return 4-bit mask with rotated connector sides
     */
    public int openSides(int rotation) {
        int base = baseSides();
        // Normalize rotation to 0–3 range
        rotation = rotation & 0x3;
        for (int i = 0; i < rotation; i++) {
            base = (base >> 3) | ((base << 1) & 0xE);
        }
        return base;
    }

    /**
     * Checks if this tile has a connector opening to the North after rotation.
     *
     * @param rotation number of clockwise 90° rotations (0–3)
     * @return true if north side is open
     */
    public boolean hasNorth(int rotation) {
        return (openSides(rotation) & 0b0001) != 0;
    }

    /**
     * Checks if this tile has a connector opening to the East after rotation.
     *
     * @param rotation number of clockwise 90° rotations (0–3)
     * @return true if east side is open
     */
    public boolean hasEast(int rotation) {
        return (openSides(rotation) & 0b0010) != 0;
    }

    /**
     * Checks if this tile has a connector opening to the South after rotation.
     *
     * @param rotation number of clockwise 90° rotations (0–3)
     * @return true if south side is open
     */
    public boolean hasSouth(int rotation) {
        return (openSides(rotation) & 0b0100) != 0;
    }

    /**
     * Checks if this tile has a connector opening to the West after rotation.
     *
     * @param rotation number of clockwise 90° rotations (0–3)
     * @return true if west side is open
     */
    public boolean hasWest(int rotation) {
        return (openSides(rotation) & 0b1000) != 0;
    }
}
