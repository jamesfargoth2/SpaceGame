package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

public class RecoilComponent implements Component {
    public final Vector2 currentPunch = new Vector2();
    public float recoverySpeed = 5f;
    public Vector2[] pattern;
    public int patternIndex;
    public float patternResetDelay = 0.3f;
    public float timeSinceLastShot;
    public final Vector2 maxPunch = new Vector2(10f, 5f);
}
