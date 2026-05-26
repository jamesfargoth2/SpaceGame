package com.galacticodyssey.vfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool.Poolable;

public class Particle implements Poolable {
    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public final Vector3 acceleration = new Vector3();
    public float life, maxLife;
    public float size, sizeEnd;
    public final Color color = new Color(Color.WHITE);
    public final Color colorEnd = new Color(Color.WHITE);
    public float rotation, angularVelocity;
    public TextureRegion textureRegion;
    public int flags;

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        acceleration.setZero();
        life = 0f;
        maxLife = 0f;
        size = 1f;
        sizeEnd = 0f;
        color.set(Color.WHITE);
        colorEnd.set(Color.WHITE);
        rotation = 0f;
        angularVelocity = 0f;
        textureRegion = null;
        flags = 0;
    }

    public float getLifeRatio() {
        return maxLife > 0 ? 1f - (life / maxLife) : 1f;
    }

    public float getCurrentSize() {
        float t = getLifeRatio();
        return size + (sizeEnd - size) * t;
    }

    public Color getCurrentColor() {
        float t = getLifeRatio();
        return new Color(
            color.r + (colorEnd.r - color.r) * t,
            color.g + (colorEnd.g - color.g) * t,
            color.b + (colorEnd.b - color.b) * t,
            color.a + (colorEnd.a - color.a) * t
        );
    }
}
