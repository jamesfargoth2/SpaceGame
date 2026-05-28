package com.galacticodyssey.shipbuilder;

public class ModuleCatalogEntry {
    public enum HardpointType { WEAPON, ENGINE, UTILITY }
    public enum HardpointSize { S, M, L }

    public String moduleId;
    public String name;
    public HardpointType hardpointType;
    public HardpointSize minSize;
    public float powerDraw;
    public float weight;
    public int price;

    public float dps;
    public float range;
    public float thrust;
    public float topSpeedBonus;
    public float shieldHp;
    public float sensorRange;
    public float cargoCapacity;

    public ModuleCatalogEntry() {}
}
