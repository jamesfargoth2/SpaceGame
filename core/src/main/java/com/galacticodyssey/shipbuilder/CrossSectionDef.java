package com.galacticodyssey.shipbuilder;

public final class CrossSectionDef {
    public float t;
    public float width;
    public float height;
    public float exponent;

    public CrossSectionDef() {}

    public CrossSectionDef(float t, float width, float height, float exponent) {
        this.t = t;
        this.width = width;
        this.height = height;
        this.exponent = exponent;
    }

    public CrossSectionDef copy() {
        return new CrossSectionDef(t, width, height, exponent);
    }
}
