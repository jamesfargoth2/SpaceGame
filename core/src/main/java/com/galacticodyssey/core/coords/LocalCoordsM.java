package com.galacticodyssey.core.coords;

import com.badlogic.gdx.math.Vector3;

/** Local-scene position in metres (float), relative to the current floating origin. */
public record LocalCoordsM(float x, float y, float z) {
    public Vector3 toVector3() { return new Vector3(x, y, z); }
}
