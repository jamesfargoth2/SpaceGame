package com.galacticodyssey.fauna.rig;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

public final class TwoBoneIKSolver {

    public static final class Result {
        public final Quaternion upperRotation = new Quaternion();
        public final Quaternion lowerRotation = new Quaternion();
    }

    private static final Vector3 tmpDir = new Vector3();
    private static final Vector3 tmpAxis = new Vector3();

    public static Result solve(Vector3 rootPos, Vector3 targetPos, Vector3 poleVector,
                               float upperLen, float lowerLen) {
        Result r = new Result();
        float maxReach = upperLen + lowerLen;

        tmpDir.set(targetPos).sub(rootPos);
        float dist = tmpDir.len();
        if (dist < 1e-5f) {
            return r;
        }

        dist = Math.min(dist, maxReach - 0.001f);
        dist = Math.max(dist, Math.abs(upperLen - lowerLen) + 0.001f);
        tmpDir.nor();

        float cosUpper = (upperLen * upperLen + dist * dist - lowerLen * lowerLen)
                         / (2f * upperLen * dist);
        cosUpper = MathUtils.clamp(cosUpper, -1f, 1f);
        float upperAngle = (float) Math.acos(cosUpper);

        float cosElbow = (upperLen * upperLen + lowerLen * lowerLen - dist * dist)
                         / (2f * upperLen * lowerLen);
        cosElbow = MathUtils.clamp(cosElbow, -1f, 1f);
        float elbowAngle = (float) Math.acos(cosElbow);

        tmpAxis.set(tmpDir).crs(poleVector).nor();
        if (tmpAxis.len2() < 1e-6f) {
            tmpAxis.set(tmpDir).crs(Vector3.Z).nor();
            if (tmpAxis.len2() < 1e-6f) tmpAxis.set(tmpDir).crs(Vector3.X).nor();
        }

        Quaternion aimRot = new Quaternion().setFromCross(new Vector3(0, -1, 0), tmpDir);
        Quaternion offsetRot = new Quaternion().setFromAxisRad(tmpAxis, -upperAngle);
        r.upperRotation.set(aimRot).mul(offsetRot);

        float bendAngle = MathUtils.PI - elbowAngle;
        r.lowerRotation.setFromAxisRad(tmpAxis, bendAngle);

        return r;
    }

    private TwoBoneIKSolver() {}
}
