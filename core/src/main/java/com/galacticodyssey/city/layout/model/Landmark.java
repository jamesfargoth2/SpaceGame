package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Vector2;

public final class Landmark {
    public final LandmarkType type;
    public final Vector2 position; // local metres
    public final boolean authored;

    public Landmark(LandmarkType type, Vector2 position, boolean authored) {
        this.type = type;
        this.position = position;
        this.authored = authored;
    }
}
