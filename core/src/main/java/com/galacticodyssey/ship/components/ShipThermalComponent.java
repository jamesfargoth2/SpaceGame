package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;

public class ShipThermalComponent implements Component {
    public float currentHeat;
    public float maxHeat = 100f;
    public float dissipationRate = 5f;
    public float heatShieldFactor = 1f;
}
