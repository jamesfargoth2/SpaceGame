package com.galacticodyssey.planet;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class Atmosphere {
    public final Map<Gas, Float> composition;
    public final float surfacePressure;
    public final float greenhouseMultiplier;
    public final float equilibriumTemp;
    public final float surfaceTemp;
    public final boolean breathable;
    public final EnumSet<AtmoHazard> hazards;

    public Atmosphere(Map<Gas, Float> composition, float surfacePressure,
                      float greenhouseMultiplier, float equilibriumTemp,
                      float surfaceTemp, boolean breathable,
                      EnumSet<AtmoHazard> hazards) {
        this.composition = new EnumMap<>(composition);
        this.surfacePressure = surfacePressure;
        this.greenhouseMultiplier = greenhouseMultiplier;
        this.equilibriumTemp = equilibriumTemp;
        this.surfaceTemp = surfaceTemp;
        this.breathable = breathable;
        this.hazards = hazards;
    }

    public float getTemperatureAtLatitude(float latRadians) {
        float sinLat = (float) Math.sin(latRadians);
        return surfaceTemp * (1.0f - 0.4f * sinLat * sinLat);
    }
}
