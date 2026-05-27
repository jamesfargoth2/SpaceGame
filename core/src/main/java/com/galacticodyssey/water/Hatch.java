package com.galacticodyssey.water;

import com.badlogic.gdx.math.Vector3;

public class Hatch {
    public String id;
    public boolean isOpen;
    public float area;
    public final Vector3 localPosition = new Vector3();
    public String compartmentA;
    public String compartmentB;

    public Hatch(String id, float area, String compartmentA, String compartmentB) {
        this.id = id;
        this.area = area;
        this.compartmentA = compartmentA;
        this.compartmentB = compartmentB;
        this.isOpen = true;
    }
}
