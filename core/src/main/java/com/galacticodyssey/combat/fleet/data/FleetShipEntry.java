package com.galacticodyssey.combat.fleet.data;

public final class FleetShipEntry {
    public FleetShipClass shipClass;
    public int count;
    public float avgHpRatio;

    public FleetShipEntry() {}

    public FleetShipEntry(FleetShipClass shipClass, int count, float avgHpRatio) {
        this.shipClass = shipClass;
        this.count = count;
        this.avgHpRatio = avgHpRatio;
    }

    public float totalFirepower() {
        return shipClass.firepowerWeight * count;
    }

    public float totalHp() {
        return shipClass.baseHullHp * count * avgHpRatio;
    }
}
