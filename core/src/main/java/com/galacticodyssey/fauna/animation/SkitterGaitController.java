package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.BoneRole;
import com.galacticodyssey.fauna.rig.CreatureRig;

import java.util.List;

public final class SkitterGaitController implements GaitController {

    private static final float FREQ_BASE = 3.5f;
    private static final float AMP_BASE = 0.10f;
    private static final float IDLE_BREATHE_AMP = 0.003f;

    @Override
    public void update(CreatureRig rig, GaitParams params) {
        float speedFrac = params.maxSpeed > 0 ? params.speed / params.maxSpeed : 0f;
        float freq = FREQ_BASE / Math.max(0.3f, params.sizeMultiplier);
        float t = params.elapsedTime;

        List<Bone> hips = rig.bonesWithRole(BoneRole.HIP);
        for (int i = 0; i < hips.size(); i++) {
            Bone hip = hips.get(i);
            float phase = (i % 2 == 0) ? 0f : MathUtils.PI;
            float swing = MathUtils.sin(t * freq * MathUtils.PI2 + phase) * AMP_BASE * speedFrac;
            hip.currentPose.set(hip.bindPose);
            hip.currentPose.rotate(Vector3.X, swing * MathUtils.radiansToDegrees);
        }

        Bone root = rig.root();
        root.currentPose.set(root.bindPose);
        if (speedFrac < 0.01f) {
            float breathe = MathUtils.sin(t * 0.5f * MathUtils.PI2) * IDLE_BREATHE_AMP;
            root.currentPose.translate(0, breathe, 0);
        }

        Bone neck = rig.findFirstWithRole(BoneRole.NECK);
        if (neck != null) {
            neck.currentPose.set(neck.bindPose);
            if (params.hasLookTarget) {
                Vector3 toTarget = new Vector3(params.lookTarget).sub(params.position).nor();
                float yaw = MathUtils.atan2(toTarget.x, toTarget.z) * MathUtils.radiansToDegrees;
                yaw = MathUtils.clamp(yaw, -5f, 5f);
                neck.currentPose.rotate(Vector3.Y, yaw * 0.3f);
            }
        }
    }

    @Override
    public void reset(CreatureRig rig) {
        for (int i = 0; i < rig.boneCount(); i++) {
            rig.getBone(i).currentPose.set(rig.getBone(i).bindPose);
        }
    }
}
