package com.galacticodyssey.ship.modules;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;

import java.util.HashMap;
import java.util.Map;

public class ShipModuleData {
    public String id;
    public String name;
    public String description;
    public ShipModuleCategory category;
    public HardpointSize size;
    public float powerDraw;
    public float mass;
    public Map<String, Float> stats = new HashMap<>();
    public QualityTier qualityTier = QualityTier.COMMON;
    public int price;

    public ShipModuleData() {}

    public ShipModuleData(ShipModuleData source) {
        this.id = source.id;
        this.name = source.name;
        this.description = source.description;
        this.category = source.category;
        this.size = source.size;
        this.powerDraw = source.powerDraw;
        this.mass = source.mass;
        this.stats = new HashMap<>(source.stats);
        this.qualityTier = source.qualityTier;
        this.price = source.price;
    }

    public boolean isReactor() { return category == ShipModuleCategory.REACTOR; }
    public float getPowerGeneration() { return isReactor() ? Math.abs(powerDraw) : 0f; }
}
