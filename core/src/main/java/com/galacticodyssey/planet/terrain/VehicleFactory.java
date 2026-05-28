package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.VehicleDefinition;
import com.galacticodyssey.data.VehicleDefinition.VehicleWeaponStats;

/** Builds a fully combat-ready ground-vehicle entity from a {@link VehicleDefinition}. */
public class VehicleFactory {

    /**
     * Creates the vehicle entity, registers it with {@code engine} and adds its rigid body to
     * {@code world}. The render layer attaches a model later via {@link VehicleRenderComponent}.
     */
    public Entity create(Engine engine, btDiscreteDynamicsWorld world,
                         VehicleDefinition def, Vector3 spawnPos) {
        Entity e = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnPos);
        e.add(transform);

        VehicleTagComponent tag = new VehicleTagComponent();
        tag.definitionId = def.id;
        e.add(tag);

        GroundVehicleComponent gv = new GroundVehicleComponent();
        gv.mass = def.mass;
        gv.wheelbase = def.wheelbase;
        gv.trackWidth = def.trackWidth;
        gv.groundClearance = def.groundClearance;
        gv.maxDriveForce = def.maxDriveForce;
        gv.maxSteerAngle = def.maxSteerAngle;
        gv.anchorBreakForce = def.anchorBreakForce;
        gv.dynamicLift = def.dynamicLift;
        e.add(gv);

        e.add(buildPhysics(def, spawnPos, world));

        HealthComponent health = new HealthComponent();
        health.maxHP = def.maxHP;
        health.currentHP = def.maxHP;
        health.alive = true;
        e.add(health);

        ArmorComponent armor = new ArmorComponent();
        for (HitRegion region : HitRegion.values()) {
            armor.armorRating.put(region, def.armorValue);
        }
        e.add(armor);

        HitboxComponent hitbox = new HitboxComponent();
        hitbox.bodyHeight = Math.max(1f, def.groundClearance + 1.5f);
        e.add(hitbox);

        if (def.weapon != null) {
            e.add(buildWeapon(def.weapon));
            e.add(new CombatInputComponent());
        }

        VehicleEntryPointComponent entry = new VehicleEntryPointComponent();
        entry.triggerRadius = Math.max(2.5f, def.trackWidth + 1.5f);
        e.add(entry);

        VehicleRenderComponent render = new VehicleRenderComponent();
        render.modelPath = def.modelPath;
        e.add(render);

        engine.addEntity(e);
        return e;
    }

    private PhysicsBodyComponent buildPhysics(VehicleDefinition def, Vector3 spawnPos,
                                              btDiscreteDynamicsWorld world) {
        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(
            def.trackWidth * 0.5f,
            Math.max(0.5f, def.groundClearance + 0.5f) * 0.5f,
            def.wheelbase * 0.5f));
        physics.mass = def.mass;
        physics.friction = 0.8f;
        physics.restitution = 0f;

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(physics.mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(physics.mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(spawnPos));
        physics.body.setFriction(physics.friction);
        physics.body.setDamping(0.15f, 0.6f);
        info.dispose();

        world.addRigidBody(physics.body);
        return physics;
    }

    private RangedWeaponComponent buildWeapon(VehicleWeaponStats w) {
        RangedWeaponComponent ranged = new RangedWeaponComponent();
        ranged.damage = w.damage;
        ranged.fireRate = w.fireRate;
        ranged.range = w.range;
        ranged.hitscan = w.hitscan;
        ranged.projectileSpeed = w.projectileSpeed;
        ranged.damageType = w.damageType;
        ranged.firingMode = w.firingMode;
        ranged.magSize = w.magSize;
        ranged.currentAmmo = w.magSize;
        ranged.reloadTime = w.reloadTime;
        ranged.spread = w.spread;
        return ranged;
    }
}
