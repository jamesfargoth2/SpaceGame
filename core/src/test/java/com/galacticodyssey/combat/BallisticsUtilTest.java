package com.galacticodyssey.combat;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BallisticsUtilTest {

    @Test
    void leadPointConvergesForStationaryTarget() {
        Vector3 shooter = new Vector3(0, 0, 0);
        Vector3 target = new Vector3(100, 0, 0);
        Vector3 targetVel = new Vector3(0, 0, 0);

        Vector3 lead = BallisticsUtil.computeLeadPoint(shooter, 50f, target, targetVel);

        assertEquals(100f, lead.x, 0.01f);
        assertEquals(0f, lead.y, 0.01f);
        assertEquals(0f, lead.z, 0.01f);
    }

    @Test
    void leadPointLeadsMovingTarget() {
        Vector3 shooter = new Vector3(0, 0, 0);
        Vector3 target = new Vector3(100, 0, 0);
        Vector3 targetVel = new Vector3(0, 0, 20);

        Vector3 lead = BallisticsUtil.computeLeadPoint(shooter, 50f, target, targetVel);

        assertTrue(lead.z > 0f,
            "Lead point should be ahead of target in its velocity direction");
        assertEquals(100f, lead.x, 1f, "X should stay near target's X");
    }

    @Test
    void gravityLeadCompensatesForDrop() {
        Vector3 shooter = new Vector3(0, 0, 0);
        Vector3 target = new Vector3(100, 0, 0);
        Vector3 gravity = new Vector3(0, -9.81f, 0);

        Vector3 lead = BallisticsUtil.computeGravityLead(shooter, 50f, target, gravity);

        assertTrue(lead.y > 0f,
            "Gravity lead should aim above target to compensate for drop");
    }

    @Test
    void fullLeadCombinesBothCorrections() {
        Vector3 shooter = new Vector3(0, 0, 0);
        Vector3 target = new Vector3(100, 0, 0);
        Vector3 targetVel = new Vector3(0, 0, 10);
        Vector3 gravity = new Vector3(0, -9.81f, 0);

        Vector3 lead = BallisticsUtil.computeFullLead(shooter, 50f, target, targetVel, gravity);

        assertTrue(lead.z > 0f, "Should lead in target's velocity direction");
        assertTrue(lead.y > 0f, "Should compensate upward for gravity drop");
    }
}
