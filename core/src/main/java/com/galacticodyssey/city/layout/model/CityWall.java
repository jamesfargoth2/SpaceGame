package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;

public final class CityWall {
    public final List<Vector2> hull;          // ordered hull vertices, local metres
    public final List<CityGate> gates = new ArrayList<>();
    public float heightM = 8f;
    public float thicknessM = 2f;

    public CityWall(List<Vector2> hull) { this.hull = hull; }
}
