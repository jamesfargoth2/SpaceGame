package com.galacticodyssey.vfx;

import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VFXRegistryTest {

    @Test
    void registerAndRetrieve_effectDefinition() {
        VFXRegistry registry = new VFXRegistry();
        ParticleEffectDefinition def = new ParticleEffectDefinition();
        def.id = "muzzle_flash_ballistic";
        def.burstCount = 12;
        def.maxParticles = 12;

        registry.register(def);

        ParticleEffectDefinition retrieved = registry.getEffect("muzzle_flash_ballistic");
        assertNotNull(retrieved);
        assertEquals(12, retrieved.burstCount);
    }

    @Test
    void eventBindings_resolvesEventToEffect() {
        VFXEventBindings bindings = new VFXEventBindings();
        bindings.bind("WeaponFiredEvent", "BALLISTIC", "muzzle_flash_ballistic");
        bindings.bind("HitscanHitEvent", null, "impact_sparks");

        assertEquals("muzzle_flash_ballistic", bindings.resolve("WeaponFiredEvent", "BALLISTIC"));
        assertEquals("impact_sparks", bindings.resolve("HitscanHitEvent", null));
        assertNull(bindings.resolve("UnknownEvent", null));
    }
}
