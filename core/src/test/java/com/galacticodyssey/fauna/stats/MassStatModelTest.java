package com.galacticodyssey.fauna.stats;

import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MassStatModelTest {
    private BodyPlanArchetypeDef arch() {
        BodyPlanArchetypeDef a = new BodyPlanArchetypeDef();
        a.density = 1000f; a.kHp = 12f; a.kSpeed = 9f; a.kDamage = 4f;
        return a;
    }

    @Test
    void massIsVolumeTimesDensity() {
        MassStatModel m = new MassStatModel();
        assertEquals(2000f, m.mass(2f, 1000f), 1e-3f);
    }

    @Test
    void biggerMassMeansMoreHpMoreDamageLessSpeed() {
        MassStatModel m = new MassStatModel();
        BodyPlanArchetypeDef a = arch();
        float[] small = m.deriveStats(10f, a);   // [hp, speed, dmg]
        float[] big   = m.deriveStats(1000f, a);
        assertTrue(big[0] > small[0], "HP grows with mass");
        assertTrue(big[2] > small[2], "damage grows with mass");
        assertTrue(big[1] < small[1], "speed falls with mass");
    }

    @Test
    void statsAreClampedToSaneRanges() {
        MassStatModel m = new MassStatModel();
        float[] tiny = m.deriveStats(0.0001f, arch());
        assertTrue(tiny[0] >= 1f, "HP floor");
        assertTrue(tiny[1] <= 30f, "speed ceiling");
        float[] huge = m.deriveStats(1e9f, arch());
        assertTrue(huge[1] >= 0.5f, "speed floor");
        assertTrue(huge[0] <= 100000f, "HP ceiling");
    }
}
