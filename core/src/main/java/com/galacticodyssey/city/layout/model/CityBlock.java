package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public final class CityBlock {
    public final Rectangle footprint; // axis-aligned, local metres
    public DistrictType district = DistrictType.UNKNOWN;

    public CityBlock(Rectangle footprint) {
        this.footprint = footprint;
    }

    public Vector2 centroid() {
        return new Vector2(footprint.x + footprint.width / 2f,
                           footprint.y + footprint.height / 2f);
    }
}
