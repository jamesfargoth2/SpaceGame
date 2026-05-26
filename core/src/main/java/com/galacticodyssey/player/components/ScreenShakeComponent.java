package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class ScreenShakeComponent implements Component {
    public float trauma;
    public float decayRate = 1.5f;
    public final Vector3 maxOffset = new Vector3(0.3f, 0.3f, 0.1f);
    public final Vector2 maxAngle = new Vector2(3f, 3f);
    public float frequency = 15f;
    public final Vector3 currentOffset = new Vector3();
    public final Vector2 currentAngle = new Vector2();

    public void addTrauma(float amount) {
        trauma = MathUtils.clamp(trauma + amount, 0f, 1f);
    }

    public float getIntensity() {
        return trauma * trauma;
    }
}
