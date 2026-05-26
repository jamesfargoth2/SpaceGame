package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.VFXEnums;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;

import java.util.ArrayList;
import java.util.List;

public class ParticleSpawnSystem extends EntitySystem {
    private static final int PRIORITY = 12;
    private final VFXRegistry registry;
    private final VFXEventBindings bindings;
    private final ParticlePoolComponent pool;

    private final List<SpawnRequest> pendingSpawns = new ArrayList<>();

    public ParticleSpawnSystem(EventBus eventBus, VFXRegistry registry,
                               VFXEventBindings bindings, ParticlePoolComponent pool) {
        super(PRIORITY);
        this.registry = registry;
        this.bindings = bindings;
        this.pool = pool;

        eventBus.subscribe(HitscanHitEvent.class, e ->
            queueSpawn("HitscanHitEvent", e.damageType.name(), e.hitPoint));
        eventBus.subscribe(ProjectileHitEvent.class, e ->
            queueSpawn("ProjectileHitEvent", e.damageType.name(), e.hitPoint));
        eventBus.subscribe(WeaponFiredEvent.class, e ->
            queueSpawn("WeaponFiredEvent", null, e.aimDirection));
        eventBus.subscribe(ShieldAbsorbEvent.class, e ->
            queueSpawn("ShieldAbsorbEvent", null, new Vector3()));
        eventBus.subscribe(EntityKilledEvent.class, e ->
            queueSpawn("EntityKilledEvent", null, new Vector3()));
        eventBus.subscribe(ShipWeaponFiredEvent.class, e ->
            queueSpawn("ShipWeaponFiredEvent", null, e.origin));
    }

    private void queueSpawn(String eventType, String variant, Vector3 position) {
        pendingSpawns.add(new SpawnRequest(eventType, variant, new Vector3(position)));
    }

    @Override
    public void update(float deltaTime) {
        for (SpawnRequest req : pendingSpawns) {
            String effectId = bindings.resolve(req.eventType, req.variant);
            if (effectId == null) continue;
            ParticleEffectDefinition def = registry.getEffect(effectId);
            if (def == null) continue;
            spawnBurst(def, req.position);
        }
        pendingSpawns.clear();
    }

    private void spawnBurst(ParticleEffectDefinition def, Vector3 origin) {
        int count = def.burstCount > 0 ? def.burstCount : 1;
        Color startColor = Color.valueOf(def.color);
        Color endColor = Color.valueOf(def.colorEnd);

        for (int i = 0; i < count; i++) {
            Particle p = pool.obtain();
            p.position.set(origin);
            float speed = MathUtils.random(def.speedMin, def.speedMax);
            float spreadRad = def.spread * MathUtils.degreesToRadians;
            float theta = MathUtils.random(0f, MathUtils.PI2);
            float phi = MathUtils.random(0f, spreadRad);
            p.velocity.set(
                speed * MathUtils.sin(phi) * MathUtils.cos(theta),
                speed * MathUtils.cos(phi),
                speed * MathUtils.sin(phi) * MathUtils.sin(theta)
            );
            p.acceleration.set(0, def.gravity, 0);
            p.life = MathUtils.random(def.lifetimeMin, def.lifetimeMax);
            p.maxLife = p.life;
            p.size = MathUtils.random(def.sizeMin, def.sizeMax);
            p.sizeEnd = def.sizeEnd;
            p.color.set(startColor);
            p.colorEnd.set(endColor);
            p.flags = def.blendMode == VFXEnums.BlendMode.ADDITIVE
                ? VFXEnums.FLAG_ADDITIVE_BLEND | VFXEnums.FLAG_FACE_CAMERA
                : VFXEnums.FLAG_FACE_CAMERA;
        }
    }

    private static class SpawnRequest {
        final String eventType;
        final String variant;
        final Vector3 position;

        SpawnRequest(String eventType, String variant, Vector3 position) {
            this.eventType = eventType;
            this.variant = variant;
            this.position = position;
        }
    }
}
