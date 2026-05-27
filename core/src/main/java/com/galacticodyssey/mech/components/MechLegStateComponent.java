package com.galacticodyssey.mech.components;

import com.badlogic.ashley.core.Component;

/**
 * Per-leg joint angles (radians) for a bipedal mech. Updated by IK or
 * procedural animation, then clamped by {@link com.galacticodyssey.mech.systems.JointLimitSystem}.
 */
public class MechLegStateComponent implements Component {

    // --- Left leg (radians) ---
    public float leftHipPitch;
    public float leftKneePitch;
    public float leftAnklePitch;

    // --- Right leg (radians) ---
    public float rightHipPitch;
    public float rightKneePitch;
    public float rightAnklePitch;
}
