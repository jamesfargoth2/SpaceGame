package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ExplosionAffectedComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.BlastDamageEvent;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.combat.events.EMPHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExplosionSystemTest {

    private EventBus eventBus;
    private Engine engine;

    private final List<BlastDamageEvent> blastEvents = new ArrayList<>();
    private final List<EMPHitEvent> empEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ExplosionSystem(eventBus));

        eventBus.subscribe(BlastDamageEvent.class, blastEvents::add);
        eventBus.subscribe(EMPHitEvent.class, empEvents::add);
    }

    private Entity createTarget(float x, float y, float z) {
        Entity target = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);

        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        ExplosionAffectedComponent affected = new ExplosionAffectedComponent();

        target.add(transform);
        target.add(health);
        target.add(affected);
        engine.addEntity(target);
        return target;
    }

    private void detonate(float x, float y, float z, float damage, float areaOfEffect) {
        eventBus.publish(new DetonationEvent(
            null, new Vector3(x, y, z), damage, DamageType.EXPLOSIVE, areaOfEffect
        ));
        engine.update(0.016f);
    }

    @Test
    void blastDamageDecreasesWithDistance() {
        Entity close = createTarget(5f, 0f, 0f);
        Entity far = createTarget(15f, 0f, 0f);

        detonate(0f, 0f, 0f, 100f, 20f);

        assertEquals(2, blastEvents.size(), "Both targets should receive blast damage");

        float closeDamage = -1f;
        float farDamage = -1f;
        for (BlastDamageEvent e : blastEvents) {
            if (e.target == close) closeDamage = e.damage;
            if (e.target == far) farDamage = e.damage;
        }

        assertTrue(closeDamage > 0f, "Close target must take damage");
        assertTrue(farDamage > 0f, "Far target must take damage");
        assertTrue(closeDamage > farDamage,
            "Closer target must take more damage (" + closeDamage + " vs " + farDamage + ")");
    }

    @Test
    void targetOutsideRadiusTakesNoDamage() {
        createTarget(25f, 0f, 0f);

        detonate(0f, 0f, 0f, 100f, 20f);

        assertTrue(blastEvents.isEmpty(),
            "Target outside areaOfEffect must not receive a BlastDamageEvent");
    }

    @Test
    void empHitPublished() {
        createTarget(5f, 0f, 0f);

        eventBus.publish(new DetonationEvent(
            null, new Vector3(0f, 0f, 0f), 100f, DamageType.EMP, 20f
        ));
        engine.update(0.016f);

        assertFalse(empEvents.isEmpty(), "Target within empRadius must receive an EMPHitEvent");
        assertTrue(empEvents.get(0).effectStrength > 0f, "EMP effectStrength must be positive");
    }

    @Test
    void empHardenedTargetReducedEffect() {
        Entity unhardened = createTarget(5f, 0f, 0f);
        unhardened.getComponent(ExplosionAffectedComponent.class).empHardeningFactor = 0f;

        Entity hardened = createTarget(5f, 5f, 0f);
        hardened.getComponent(ExplosionAffectedComponent.class).empHardeningFactor = 0.8f;

        // Both at same distance from origin (about 5 and ~7.07 — let's place them the same)
        hardened.getComponent(TransformComponent.class).position.set(0f, 5f, 0f);

        eventBus.publish(new DetonationEvent(
            null, new Vector3(0f, 0f, 0f), 100f, DamageType.EMP, 20f
        ));
        engine.update(0.016f);

        assertEquals(2, empEvents.size(), "Both targets should receive EMPHitEvent");

        float unhardenedStrength = -1f;
        float hardenedStrength = -1f;
        for (EMPHitEvent e : empEvents) {
            if (e.target == unhardened) unhardenedStrength = e.effectStrength;
            if (e.target == hardened) hardenedStrength = e.effectStrength;
        }

        assertTrue(unhardenedStrength > 0f);
        assertTrue(hardenedStrength > 0f);
        assertTrue(hardenedStrength < unhardenedStrength,
            "Hardened target (" + hardenedStrength + ") must have less effect than unhardened (" + unhardenedStrength + ")");

        // With 0.8 hardening, the effect should be roughly 20% of unhardened at same distance
        float ratio = hardenedStrength / unhardenedStrength;
        assertTrue(ratio < 0.3f,
            "Hardened/unhardened ratio should be low; was " + ratio);
    }

    @Test
    void shapedChargeConcentratesDamage() {
        // Target in the cone direction
        Entity inCone = createTarget(10f, 0f, 0f);
        // Target at same distance but perpendicular (outside cone)
        Entity outOfCone = createTarget(0f, 10f, 0f);

        // Create a shaped-charge detonation pointing along +X
        ExplosionSystem system = engine.getSystem(ExplosionSystem.class);

        // We need to publish a DetonationEvent and override the ExplosionData to be directional.
        // Since buildExplosionData is private and produces omnidirectional data, we test
        // the directional multiplier by crafting the scenario through the system's package-visible method.
        com.galacticodyssey.combat.ExplosionData data = new com.galacticodyssey.combat.ExplosionData();
        data.origin.set(0f, 0f, 0f);
        data.totalEnergy = 100000f;
        data.isDirectional = true;
        data.directionNormal.set(1f, 0f, 0f);
        data.coneHalfAngle = MathUtils.PI / 6f; // 30 degrees

        Vector3 inConeDir = new Vector3(1f, 0f, 0f); // straight along cone axis
        Vector3 outConeDir = new Vector3(0f, 1f, 0f); // 90 degrees off axis

        float inConeMult = system.directionalMultiplier(data, inConeDir);
        float outConeMult = system.directionalMultiplier(data, outConeDir);

        assertTrue(inConeMult > 1f,
            "In-cone multiplier should be > 1; was " + inConeMult);
        assertTrue(outConeMult < 1f,
            "Out-of-cone multiplier should be < 1; was " + outConeMult);
        assertTrue(inConeMult > outConeMult * 5f,
            "In-cone damage should be much greater than out-of-cone");
    }

    @Test
    void impulseDirectionAwayFromBlast() {
        createTarget(10f, 0f, 0f);

        detonate(0f, 0f, 0f, 100f, 20f);

        assertFalse(blastEvents.isEmpty(), "Must have at least one BlastDamageEvent");
        BlastDamageEvent event = blastEvents.get(0);

        // Impulse should point away from the blast origin (positive X direction)
        assertTrue(event.impulse.x > 0f,
            "Impulse X should be positive (away from blast at origin); was " + event.impulse.x);

        // The Y and Z components should be near zero since the target is directly along +X
        assertEquals(0f, event.impulse.y, 0.001f, "Impulse Y should be ~0");
        assertEquals(0f, event.impulse.z, 0.001f, "Impulse Z should be ~0");
    }

    @Test
    void customBlastFractionsAreUsedFromDetonationEvent() {
        // Create a target at distance 3.0 from origin
        Entity target = new Entity();
        target.add(new TransformComponent());
        target.getComponent(TransformComponent.class).position.set(3f, 0f, 0f);
        target.add(new HealthComponent());
        target.add(new ExplosionAffectedComponent());
        engine.addEntity(target);

        // Fire two detonations: one default fractions, one custom
        List<BlastDamageEvent> defaultEvents = new ArrayList<>();
        List<BlastDamageEvent> customEvents = new ArrayList<>();

        EventBus.EventListener<BlastDamageEvent> defaultListener = defaultEvents::add;
        EventBus.EventListener<BlastDamageEvent> customListener = customEvents::add;

        eventBus.subscribe(BlastDamageEvent.class, defaultListener);

        // Default fractions (0.4 blast, 0.3 thermal, 0.3 fragment)
        DetonationEvent defaultDet = new DetonationEvent(
                new Entity(), new Vector3(0, 0, 0), 50f,
                DamageType.EXPLOSIVE, 10f);
        eventBus.publish(defaultDet);
        engine.update(0.016f);

        eventBus.unsubscribe(BlastDamageEvent.class, defaultListener);
        eventBus.subscribe(BlastDamageEvent.class, customListener);

        // Custom fractions (0.8 blast, 0.1 thermal, 0.1 fragment)
        DetonationEvent customDet = new DetonationEvent(
                new Entity(), new Vector3(0, 0, 0), 50f,
                DamageType.EXPLOSIVE, 10f,
                0.8f, 0.1f, 0.1f, false);
        eventBus.publish(customDet);
        engine.update(0.016f);

        assertEquals(1, defaultEvents.size());
        assertEquals(1, customEvents.size());
        // Higher blast fraction should produce more damage at same distance
        assertTrue(customEvents.get(0).damage > defaultEvents.get(0).damage,
                "Custom blast fraction 0.8 should produce more damage than default 0.4");
    }
}
