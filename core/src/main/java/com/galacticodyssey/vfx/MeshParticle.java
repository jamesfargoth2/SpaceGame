package com.galacticodyssey.vfx;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool.Poolable;

public final class MeshParticle implements Poolable {

    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public float gravity = -9.8f;
    public float bounce = 0.3f;
    public float life;
    public float maxLife;
    public String meshId;

    /** Advances physics by deltaTime. Applies gravity, moves position, bounces off y=0. */
    public void update(float delta) {
        velocity.y += gravity * delta;
        position.mulAdd(velocity, delta);
        if (position.y < 0f) {
            position.y = 0f;
            velocity.y = -velocity.y * bounce;
        }
        life -= delta;
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        gravity = -9.8f;
        bounce = 0.3f;
        life = 0f;
        maxLife = 0f;
        meshId = null;
    }
}
