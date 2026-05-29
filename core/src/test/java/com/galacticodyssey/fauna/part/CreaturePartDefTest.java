package com.galacticodyssey.fauna.part;

import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreaturePartDefTest {
    @Test
    void findsSocketByIdAndComputesVolume() {
        CreaturePartDef torso = new CreaturePartDef();
        torso.id = "torso_a";
        torso.partType = PartType.TORSO;
        Socket s = new Socket();
        s.id = "leg_front";
        s.acceptedType = PartType.LIMB_LEG;
        s.localPosition.set(0.3f, 0f, 0.4f);
        torso.sockets.add(s);

        assertSame(s, torso.findSocket("leg_front"));
        assertNull(torso.findSocket("nope"));

        PartGeometrySpec g = torso.geometry;
        g.shape = PartGeometrySpec.Shape.CAPSULE;
        g.length = 2f; g.radius = 0.5f; g.taper = 1f;
        assertTrue(g.approxVolume() > 0f, "volume must be positive");
    }
}
