package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Vector2;

public final class Street {
    public final Vector2 start;
    public final Vector2 end;
    public final StreetTier tier;

    public Street(Vector2 start, Vector2 end, StreetTier tier) {
        this.start = start;
        this.end = end;
        this.tier = tier;
    }
}
