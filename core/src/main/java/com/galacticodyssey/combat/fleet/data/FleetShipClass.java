package com.galacticodyssey.combat.fleet.data;

import com.galacticodyssey.ship.ShipSizeClass;

public enum FleetShipClass {
    FIGHTER(ShipSizeClass.SMALL, FleetRole.INTERCEPTOR, true, 1f, 500f, 100f),
    BOMBER(ShipSizeClass.SMALL, FleetRole.FIRE_SUPPORT, true, 2f, 300f, 200f),
    CORVETTE(ShipSizeClass.SMALL, FleetRole.ESCORT, true, 3f, 600f, 300f),
    FRIGATE(ShipSizeClass.MEDIUM, FleetRole.ESCORT, false, 8f, 400f, 800f),
    DESTROYER(ShipSizeClass.MEDIUM, FleetRole.VANGUARD, false, 12f, 500f, 600f),
    CRUISER(ShipSizeClass.MEDIUM, FleetRole.FIRE_SUPPORT, false, 20f, 350f, 1500f),
    BATTLECRUISER(ShipSizeClass.LARGE, FleetRole.VANGUARD, false, 35f, 300f, 3000f),
    BATTLESHIP(ShipSizeClass.LARGE, FleetRole.FIRE_SUPPORT, false, 50f, 200f, 5000f),
    CARRIER(ShipSizeClass.LARGE, FleetRole.SUPPORT, false, 25f, 150f, 4000f),
    DREADNOUGHT(ShipSizeClass.LARGE, FleetRole.FLAGSHIP, false, 80f, 150f, 8000f);

    public final ShipSizeClass sizeClass;
    public final FleetRole defaultRole;
    public final boolean expendable;
    public final float firepowerWeight;
    public final float baseSpeed;
    public final float baseHullHp;

    FleetShipClass(ShipSizeClass sizeClass, FleetRole defaultRole, boolean expendable,
                   float firepowerWeight, float baseSpeed, float baseHullHp) {
        this.sizeClass = sizeClass;
        this.defaultRole = defaultRole;
        this.expendable = expendable;
        this.firepowerWeight = firepowerWeight;
        this.baseSpeed = baseSpeed;
        this.baseHullHp = baseHullHp;
    }
}
