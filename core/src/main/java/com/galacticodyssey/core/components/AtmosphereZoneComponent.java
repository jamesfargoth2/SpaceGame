package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;

public class AtmosphereZoneComponent implements Component {
    public float atmosphereRadius;
    public float surfaceRadius;
    public float surfaceDensity;
    public float scaleHeight;
    public float transitionAltitude;
    public float speedOfSound = 343f;
    public float machThreshold = 3.0f;
    public String composition;
}
