package com.galacticodyssey.combat;

import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeDataRegistryTest {

    private GrenadeDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GrenadeDataRegistry();

        GrenadeData frag = new GrenadeData();
        frag.id = "frag";
        frag.displayName = "M4 Fragmentation Grenade";
        frag.fuseType = FuseType.TIMED;
        frag.fuseDuration = 3.0f;
        frag.cookable = true;
        frag.throwForce = 18.0f;
        frag.damage = 50.0f;
        frag.blastRadius = 8.0f;
        frag.blastFraction = 0.5f;
        frag.bounceRestitution = 0.3f;
        frag.maxBounces = 5;
        frag.maxCarry = 4;
        registry.register(frag);
    }

    @Test
    void registersFragGrenade() {
        assertEquals(1, registry.size());
        assertTrue(registry.has("frag"));

        GrenadeData frag = registry.get("frag");
        assertEquals("frag", frag.id);
        assertEquals("M4 Fragmentation Grenade", frag.displayName);
        assertEquals(FuseType.TIMED, frag.fuseType);
        assertEquals(3.0f, frag.fuseDuration, 0.001f);
        assertTrue(frag.cookable);
        assertEquals(18.0f, frag.throwForce, 0.001f);
        assertEquals(50.0f, frag.damage, 0.001f);
        assertEquals(8.0f, frag.blastRadius, 0.001f);
        assertEquals(0.5f, frag.blastFraction, 0.001f);
        assertEquals(0.3f, frag.bounceRestitution, 0.001f);
        assertEquals(5, frag.maxBounces);
        assertEquals(4, frag.maxCarry);
    }

    @Test
    void returnsNullForUnknownId() {
        assertNull(registry.get("nonexistent"));
        assertFalse(registry.has("nonexistent"));
    }
}
