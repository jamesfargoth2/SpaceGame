package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class AwarenessStateComponent implements Component {
    public AwarenessState state = AwarenessState.UNAWARE;
    public float detectionAccumulator; // rises toward contribution, decays toward 0
    public float suspicionTimer;       // elapsed time in CURIOUS state
    public float searchTimer;          // elapsed time in SEARCHING state
    public final Vector3 lastKnownPosition = new Vector3();
}
