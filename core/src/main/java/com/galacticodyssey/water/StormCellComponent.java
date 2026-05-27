package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;

public class StormCellComponent implements Component {
    public double centerGalaxyX;
    public double centerGalaxyZ;
    public float radius = 3000f;
    public WeatherPhase currentPhase = WeatherPhase.CALM;
    public float phaseTimer;
    public float phaseDuration;
    public float windDirection;
    public float windSpeed;
    public float driftVelocityX;
    public float driftVelocityZ;
    public float intensity = 1.0f;
    public boolean playerInside;
    public boolean playerApproaching;
}
