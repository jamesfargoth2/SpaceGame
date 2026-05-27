package com.galacticodyssey.planet;

public enum Gas {
    N2(28.0f), O2(32.0f), CO2(44.0f), H2O(18.0f), SO2(64.0f),
    HCl(36.5f), Ar(40.0f), CH4(16.0f), H2(2.0f), He(4.0f), NH3(17.0f);

    public final float molecularMass;
    Gas(float molecularMass) { this.molecularMass = molecularMass; }
}
