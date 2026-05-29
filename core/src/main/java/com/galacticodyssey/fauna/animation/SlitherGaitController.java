package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.BoneRole;
import com.galacticodyssey.fauna.rig.CreatureRig;

import java.util.List;

public final class SlitherGaitController implements GaitController {

    private static final float WAVE_FREQ = 1.5f;
    private static final float WAVE_AMP_BASE = 15f;
    private static final float PHASE_PER_BONE = 0.8f;

    @Override
    public void update(CreatureRig rig, GaitParams params) {
        float speedFrac = params.maxSpeed > 0 ? params.speed / params.maxSpeed : 0f;
        float t = params.elapsedTime;
        float amp = WAVE_AMP_BASE * Math.max(0.15f, speedFrac);

        Bone root = rig.root();
        float rootAngle = MathUtils.sin(t * WAVE_FREQ * MathUtils.PI2) * amp * 0.5f;
        root.currentPose.set(root.bindPose);
        root.currentPose.rotate(Vector3.Y, rootAngle);

        List<Bone> spines = rig.bonesWithRole(BoneRole.SPINE);
        for (int i = 0; i < spines.size(); i++) {
            Bone spine = spines.get(i);
            float phase = (i + 1) * PHASE_PER_BONE;
            float angle = MathUtils.sin(t * WAVE_FREQ * MathUtils.PI2 - phase) * amp;
            spine.currentPose.set(spine.bindPose);
            spine.currentPose.rotate(Vector3.Y, angle);
        }

        Bone neck = rig.findFirstWithRole(BoneRole.NECK);
        if (neck != null) {
            float headPhase = (spines.size() + 1) * PHASE_PER_BONE;
            float headAngle = MathUtils.sin(t * WAVE_FREQ * MathUtils.PI2 - headPhase) * amp * 0.3f;
            neck.currentPose.set(neck.bindPose);
            neck.currentPose.rotate(Vector3.Y, -headAngle);
        }
    }

    @Override
    public void reset(CreatureRig rig) {
        for (int i = 0; i < rig.boneCount(); i++) {
            rig.getBone(i).currentPose.set(rig.getBone(i).bindPose);
        }
    }
}
