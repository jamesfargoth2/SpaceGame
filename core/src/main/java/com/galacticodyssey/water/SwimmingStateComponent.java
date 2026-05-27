package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;

public class SwimmingStateComponent implements Component {
    public SwimState swimState = SwimState.DRY;
    public SwimState previousState = SwimState.DRY;

    public float breath = 30f;
    public float maxBreath = 30f;

    public float currentDepth;
    public float immersionFraction;
    public float waterSurfaceHeight;

    public boolean isInInteriorWater;
    public float interiorWaterLevel;

    public float previousDepth;
    public float verticalSpeed;

    public float ascentSicknessTimer;
    public boolean hasAscentSickness;
}
