package com.galacticodyssey.planet.tectonic;

/** Classification of a plate boundary derived from relative plate motion. */
public enum BoundaryType {
    NONE,
    CONVERGENT_CONTINENTAL, // continent-continent collision -> mountains
    CONVERGENT_OCEANIC,     // subduction -> trench + volcanic arc
    DIVERGENT,              // spreading -> rift valley or mid-ocean ridge
    TRANSFORM               // lateral sliding -> fault, minor relief
}
