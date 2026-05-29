package com.galacticodyssey.fauna.rig;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoneRoleTest {

    @Test
    void mapsKnownHintsToRoles() {
        assertEquals(BoneRole.HIP, BoneRole.fromJointHint("hip"));
        assertEquals(BoneRole.SHOULDER, BoneRole.fromJointHint("shoulder"));
        assertEquals(BoneRole.NECK, BoneRole.fromJointHint("neck"));
        assertEquals(BoneRole.SPINE, BoneRole.fromJointHint("spine"));
        assertEquals(BoneRole.TAIL, BoneRole.fromJointHint("tail"));
    }

    @Test
    void nullAndUnknownMapToStructural() {
        assertEquals(BoneRole.STRUCTURAL, BoneRole.fromJointHint(null));
        assertEquals(BoneRole.STRUCTURAL, BoneRole.fromJointHint("unknown"));
    }
}
