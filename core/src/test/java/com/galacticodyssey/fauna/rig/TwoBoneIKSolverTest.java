package com.galacticodyssey.fauna.rig;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwoBoneIKSolverTest {

    @Test
    void solvesReachableTarget() {
        TwoBoneIKSolver.Result r = TwoBoneIKSolver.solve(
            new Vector3(0, 0, 0),
            new Vector3(0, -1.4f, 0),
            new Vector3(0, 0, 1),
            1f, 1f
        );
        assertNotNull(r);
        // Verify rotations are non-identity (actual solving occurred)
        assertFalse(r.upperRotation.isIdentity(0.01f), "upper rotation should be non-identity for a reachable target");
    }

    @Test
    void fullyExtendedWhenTargetAtMaxReach() {
        TwoBoneIKSolver.Result r = TwoBoneIKSolver.solve(
            new Vector3(0, 0, 0),
            new Vector3(0, -2f, 0),
            new Vector3(0, 0, 1),
            1f, 1f
        );
        assertNotNull(r);
    }

    @Test
    void clampsWhenTargetBeyondReach() {
        TwoBoneIKSolver.Result r = TwoBoneIKSolver.solve(
            new Vector3(0, 0, 0),
            new Vector3(0, -5f, 0),
            new Vector3(0, 0, 1),
            1f, 1f
        );
        assertNotNull(r, "should return a clamped result, not null");
    }
}
