package com.galacticodyssey.vfx;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.vfx.components.ParticleEmitterComponent;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import com.galacticodyssey.vfx.systems.ParticleSpawnSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticleSpawnSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private VFXRegistry registry;
    private VFXEventBindings bindings;
    private ParticlePoolComponent pool;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        registry = new VFXRegistry();
        bindings = new VFXEventBindings();

        ParticleEffectDefinition sparks = new ParticleEffectDefinition();
        sparks.id = "impact_sparks";
        sparks.burstCount = 8;
        sparks.maxParticles = 8;
        sparks.lifetimeMin = 0.1f;
        sparks.lifetimeMax = 0.3f;
        sparks.speedMin = 3f;
        sparks.speedMax = 12f;
        sparks.spread = 60f;
        registry.register(sparks);
        bindings.bind("HitscanHitEvent", null, "impact_sparks");

        Entity poolEntity = new Entity();
        pool = new ParticlePoolComponent();
        poolEntity.add(pool);
        engine.addEntity(poolEntity);

        engine.addSystem(new ParticleSpawnSystem(eventBus, registry, bindings, pool));
    }

    @Test
    void hitscanHit_spawnsImpactParticles() {
        Entity shooter = new Entity();
        Entity target = new Entity();

        eventBus.publish(new HitscanHitEvent(shooter, target,
            new Vector3(5, 1, 3), new Vector3(0, 1, 0),
            HitRegion.TORSO, 20f, DamageType.BALLISTIC, "standard_round"));

        engine.update(0.016f);

        assertFalse(pool.active.isEmpty());
        assertEquals(8, pool.active.size());
    }
}
