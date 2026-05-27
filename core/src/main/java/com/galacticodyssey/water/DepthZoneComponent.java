package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;

public class DepthZoneComponent implements Component {
    public DepthZone currentZone = DepthZone.SUNLIT;
    public float currentDepth;
    public float ambientPressure = 1.0f;
    public float visibilityFraction = 1.0f;
    public final Color fogColor = new Color(0.1f, 0.4f, 0.7f, 1f);
    public boolean requiresLight;
}
