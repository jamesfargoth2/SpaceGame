package com.galacticodyssey.ship;

import com.galacticodyssey.galaxy.faction.FactionData;

/** Inputs to a ship generation pass. Mutable POJO for easy construction. */
public class ShipGenerationConfig {

    public long seed;
    public ShipSizeClass sizeClass = ShipSizeClass.SMALL;
    public FactionData faction;            // null => independent
    public ShipRole role = ShipRole.CIVILIAN;
    public float conditionFactor = 1.0f;   // 1 = pristine, 0 = derelict
    public boolean isFlagship = false;

    /** Independent, pristine, civilian config for {@code seed}/{@code sizeClass}. */
    public static ShipGenerationConfig defaults(long seed, ShipSizeClass sizeClass) {
        ShipGenerationConfig c = new ShipGenerationConfig();
        c.seed = seed;
        c.sizeClass = sizeClass;
        return c;
    }
}
