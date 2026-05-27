package com.galacticodyssey.planet.terrain;

public class TractionResult {
    public final float actualForce;
    public final float slipFraction;

    public TractionResult(float actualForce, float slipFraction) {
        this.actualForce = actualForce;
        this.slipFraction = slipFraction;
    }
}
