package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public final class BuildingLot {
    public final Rectangle footprint; // axis-aligned, local metres
    public final DistrictType district;
    public BuildingFunction function = BuildingFunction.EMPTY_LOT;

    public BuildingLot(Rectangle footprint, DistrictType district) {
        this.footprint = footprint;
        this.district = district;
    }

    public Vector2 centroid() {
        return new Vector2(footprint.x + footprint.width / 2f,
                           footprint.y + footprint.height / 2f);
    }
}
