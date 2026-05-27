package com.galacticodyssey.mech.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.mech.components.MechLegStateComponent;

/**
 * Clamps all leg joint angles to physically plausible ranges each tick.
 * Runs after IK / procedural animation to prevent hyper-extension.
 */
public class JointLimitSystem extends EntitySystem {

    public static final int PRIORITY = 5;

    // Joint limits in radians
    public static final float HIP_PITCH_MIN   = -MathUtils.degreesToRadians * 40f;
    public static final float HIP_PITCH_MAX   =  MathUtils.degreesToRadians * 70f;
    public static final float KNEE_PITCH_MIN  =  MathUtils.degreesToRadians * 5f;
    public static final float KNEE_PITCH_MAX  =  MathUtils.degreesToRadians * 130f;
    public static final float ANKLE_PITCH_MIN = -MathUtils.degreesToRadians * 30f;
    public static final float ANKLE_PITCH_MAX =  MathUtils.degreesToRadians * 50f;

    private static final Family FAMILY = Family.all(MechLegStateComponent.class).get();

    private static final ComponentMapper<MechLegStateComponent> LEG_M =
        ComponentMapper.getFor(MechLegStateComponent.class);

    private ImmutableArray<Entity> entities;

    public JointLimitSystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (entities == null) return;

        for (int i = 0, n = entities.size(); i < n; i++) {
            clampLeg(LEG_M.get(entities.get(i)));
        }
    }

    private void clampLeg(MechLegStateComponent leg) {
        // Left leg
        leg.leftHipPitch   = MathUtils.clamp(leg.leftHipPitch,   HIP_PITCH_MIN,   HIP_PITCH_MAX);
        leg.leftKneePitch  = MathUtils.clamp(leg.leftKneePitch,  KNEE_PITCH_MIN,  KNEE_PITCH_MAX);
        leg.leftAnklePitch = MathUtils.clamp(leg.leftAnklePitch, ANKLE_PITCH_MIN, ANKLE_PITCH_MAX);

        // Right leg
        leg.rightHipPitch   = MathUtils.clamp(leg.rightHipPitch,   HIP_PITCH_MIN,   HIP_PITCH_MAX);
        leg.rightKneePitch  = MathUtils.clamp(leg.rightKneePitch,  KNEE_PITCH_MIN,  KNEE_PITCH_MAX);
        leg.rightAnklePitch = MathUtils.clamp(leg.rightAnklePitch, ANKLE_PITCH_MIN, ANKLE_PITCH_MAX);
    }
}
