package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.VFXEnums;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;

public class BulletTracerSystem extends EntitySystem {

    private static final int TRACER_PARTICLE_COUNT = 8;
    private static final float TRACER_SPEED = 150f;
    private static final float TRACER_LIFETIME = 0.3f;
    private static final float MUZZLE_FLASH_SIZE = 0.3f;
    private static final float MUZZLE_FLASH_LIFETIME = 0.1f;

    private final ParticlePoolComponent particlePool;

    private static final ComponentMapper<RangedWeaponComponent> WEAPON_M =
        ComponentMapper.getFor(RangedWeaponComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final Vector3 tmpVel = new Vector3();

    public BulletTracerSystem(EventBus eventBus, ParticlePoolComponent particlePool) {
        super(8);
        this.particlePool = particlePool;
        eventBus.subscribe(WeaponFiredEvent.class, this::onWeaponFired);
    }

    @Override
    public void update(float deltaTime) {}

    private void onWeaponFired(WeaponFiredEvent event) {
        Entity shooter = event.shooter;
        RangedWeaponComponent weapon = WEAPON_M.get(shooter);
        if (weapon == null) return;

        // Inherit shooter velocity so the flash moves with the barrel during its lifetime.
        PhysicsBodyComponent physics = PHYSICS_M.get(shooter);
        if (physics != null && physics.body != null) {
            tmpVel.set(physics.body.getLinearVelocity());
        } else {
            tmpVel.setZero();
        }

        Vector3 origin = new Vector3(event.muzzlePosition);
        Vector3 dir = new Vector3(event.aimDirection).nor();

        // Muzzle flash for the FP player is rendered as a screen-space overlay in GameScreen;
        // world-space flash is only spawned for non-player entities (e.g. NPCs).
        if (shooter.getComponent(PlayerTagComponent.class) == null) {
            spawnMuzzleFlash(origin, tmpVel);
        }
        spawnTracerParticles(origin, dir, weapon.range);
    }

    private void spawnMuzzleFlash(Vector3 origin, Vector3 shooterVelocity) {
        Particle flash = particlePool.obtain();
        flash.position.set(origin);
        flash.velocity.set(shooterVelocity);
        flash.acceleration.setZero();
        flash.life = MUZZLE_FLASH_LIFETIME;
        flash.maxLife = MUZZLE_FLASH_LIFETIME;
        flash.size = MUZZLE_FLASH_SIZE;
        flash.sizeEnd = MUZZLE_FLASH_SIZE * 2f;
        flash.color.set(1f, 0.9f, 0.5f, 1f);
        flash.colorEnd.set(1f, 0.6f, 0.2f, 0f);
        flash.flags = VFXEnums.FLAG_ADDITIVE_BLEND;
    }

    private void spawnTracerParticles(Vector3 origin, Vector3 dir, float range) {
        for (int i = 0; i < TRACER_PARTICLE_COUNT; i++) {
            Particle p = particlePool.obtain();

            float offset = i * 0.8f;
            p.position.set(
                origin.x + dir.x * offset,
                origin.y + dir.y * offset,
                origin.z + dir.z * offset
            );

            p.velocity.set(dir).scl(TRACER_SPEED);
            p.acceleration.setZero();

            p.life = TRACER_LIFETIME;
            p.maxLife = TRACER_LIFETIME;

            p.size = 0.12f;
            p.sizeEnd = 0.03f;

            p.color.set(1f, 0.95f, 0.6f, 0.9f);
            p.colorEnd.set(1f, 0.8f, 0.3f, 0f);
            p.flags = VFXEnums.FLAG_ADDITIVE_BLEND;
        }
    }
}
