package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.BoneRole;
import com.galacticodyssey.fauna.rig.CreatureRig;

import java.util.List;

public final class WalkGaitController implements GaitController {

    private static final float STRIDE_FREQ_BASE = 2.0f;
    private static final float STRIDE_AMP_BASE = 0.15f;
    private static final float BOB_AMP_BASE = 0.03f;
    private static final float IDLE_BREATHE_FREQ = 0.5f;
    private static final float IDLE_BREATHE_AMP = 0.005f;
    private static final float IDLE_SWAY_FREQ = 0.15f;
    private static final float IDLE_SWAY_AMP = 0.003f;

    @Override
    public void update(CreatureRig rig, GaitParams params) {
        float speedFrac = params.maxSpeed > 0 ? params.speed / params.maxSpeed : 0f;
        float freq = STRIDE_FREQ_BASE / Math.max(0.5f, params.sizeMultiplier);
        float t = params.elapsedTime;

        List<Bone> hips = rig.bonesWithRole(BoneRole.HIP);
        int legCount = hips.size();
        for (int i = 0; i < legCount; i++) {
            Bone hip = hips.get(i);
            float phase = (float) i / Math.max(1, legCount) * MathUtils.PI2;
            float swing = MathUtils.sin(t * freq * MathUtils.PI2 + phase) * STRIDE_AMP_BASE * speedFrac;
            hip.currentPose.set(hip.bindPose);
            hip.currentPose.rotate(Vector3.X, swing * MathUtils.radiansToDegrees);
        }

        Bone root = rig.root();
        float bob = MathUtils.sin(t * freq * MathUtils.PI2 * 2f) * BOB_AMP_BASE * speedFrac;
        root.currentPose.set(root.bindPose);
        root.currentPose.translate(0, bob, 0);

        if (speedFrac < 0.01f) {
            float breathe = MathUtils.sin(t * IDLE_BREATHE_FREQ * MathUtils.PI2) * IDLE_BREATHE_AMP;
            root.currentPose.translate(0, breathe, 0);
            float sway = MathUtils.sin(t * IDLE_SWAY_FREQ * MathUtils.PI2) * IDLE_SWAY_AMP;
            root.currentPose.translate(sway, 0, 0);
        }

        Bone neck = rig.findFirstWithRole(BoneRole.NECK);
        if (neck != null && params.hasLookTarget) {
            Vector3 toTarget = new Vector3(params.lookTarget).sub(params.position).nor();
            float yaw = MathUtils.atan2(toTarget.x, toTarget.z) * MathUtils.radiansToDegrees;
            float maxSlew = 5f;
            yaw = MathUtils.clamp(yaw, -maxSlew, maxSlew);
            neck.currentPose.set(neck.bindPose);
            neck.currentPose.rotate(Vector3.Y, yaw * 0.3f);
        }

        Bone tail = rig.findFirstWithRole(BoneRole.TAIL);
        if (tail != null) {
            float tailLag = MathUtils.sin(t * freq * MathUtils.PI2 - MathUtils.PI * 0.5f)
                            * STRIDE_AMP_BASE * 0.5f * speedFrac;
            tail.currentPose.set(tail.bindPose);
            tail.currentPose.rotate(Vector3.Y, tailLag * MathUtils.radiansToDegrees);
        }
    }

    @Override
    public void reset(CreatureRig rig) {
        for (int i = 0; i < rig.boneCount(); i++) {
            rig.getBone(i).currentPose.set(rig.getBone(i).bindPose);
        }
    }
}
