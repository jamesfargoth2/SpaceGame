package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class CoverComponent implements Component {
    public CoverPoint currentCoverPoint;
    public boolean inCover;
    public final Vector3 peekDirection = new Vector3();
}
