package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;

public class DiveGearComponent implements Component {
    public String gearId;
    public float oxygenCapacity;
    public float oxygenRemaining;
    public float maxPressure;
    public boolean providesLight;
    public float lightRadius;
    public float swimSpeedModifier = 1.0f;
}
