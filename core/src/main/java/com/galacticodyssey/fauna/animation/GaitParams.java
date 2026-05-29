package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.Vector3;

public final class GaitParams {
    public float deltaTime;
    public float speed;           // current movement speed (0 = idle)
    public float maxSpeed;        // creature's max move speed
    public float sizeMultiplier;  // from CreatureSpec
    public final Vector3 heading = new Vector3(0, 0, 1);
    public final Vector3 position = new Vector3();
    public final Vector3 lookTarget = new Vector3();
    public boolean hasLookTarget = false;
    public float elapsedTime;     // total animation time (accumulated)
}
