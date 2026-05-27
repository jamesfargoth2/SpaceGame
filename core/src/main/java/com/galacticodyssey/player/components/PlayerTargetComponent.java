package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class PlayerTargetComponent implements Component {
    public Entity lockedTarget;
    public Entity softTarget;
    public final Vector3 leadIndicatorPos = new Vector3();
    public float lockTimer;
}
