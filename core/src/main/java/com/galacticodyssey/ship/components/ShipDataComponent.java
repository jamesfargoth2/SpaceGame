package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.ship.ShipBlueprint;

public class ShipDataComponent implements Component {
    public ShipBlueprint blueprint;
    public float mass;
    public float maxThrust;
    public float maxTurnRate;
    public float maxSpeed;
    public float hullHp;
    public float currentHullHp;
    public HullGeometry hullGeometry; // transient, used for deferred Mesh creation in GameScreen
}
