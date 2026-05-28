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
        if (shipClass == null || count <= 0) return 0f;
        return shipClass.firepowerWeight * count;
    }

    public float totalHp() {
        if (shipClass == null || count <= 0) return 0f;
        return shipClass.baseHullHp * count * Math.max(0f, avgHpRatio);
    }
}
