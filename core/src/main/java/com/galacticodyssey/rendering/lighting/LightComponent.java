package com.galacticodyssey.rendering.lighting;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;

public class LightComponent implements Component {
    public enum Type { DIRECTIONAL, POINT, SPOT }

    public Type type = Type.POINT;
    public final Color color = new Color(Color.WHITE);
    public float intensity = 1f;
    public float radius = 10f;
    public float innerCone = 30f;
    public float outerCone = 45f;
    public boolean castShadows = false;
}
