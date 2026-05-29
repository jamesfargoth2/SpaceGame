package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.LandmarkType;

/** A hand-placed landmark supplied by the caller (hybrid procedural + handcrafted). */
public final class AuthoredLandmark {
    public final LandmarkType type;
    public final Vector2 position; // local metres

    public AuthoredLandmark(LandmarkType type, Vector2 position) {
        this.type = type;
        this.position = position;
    }
}
