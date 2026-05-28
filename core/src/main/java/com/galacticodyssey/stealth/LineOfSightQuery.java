package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;

@FunctionalInterface
public interface LineOfSightQuery {
    boolean hasLoS(Vector3 from, Vector3 to);
}
