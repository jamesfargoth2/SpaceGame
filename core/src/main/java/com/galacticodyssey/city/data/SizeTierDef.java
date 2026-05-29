package com.galacticodyssey.city.data;

import com.badlogic.gdx.utils.Array;

/** One population tier row from size_tiers.json. Public fields for libGDX Json. */
public class SizeTierDef {
    public String type;            // CityType name
    public int minPopulation;
    public int maxPopulation;      // inclusive
    public float radiusMin;
    public float radiusMax;
    public String wall;            // "yes" | "no" | "maybe"
    public float density;          // 0..1
    public Array<String> formBias = new Array<>(); // CityForm names, repeats = weight
}
