package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.Subsystem;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.events.SubsystemDisabledEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes {@link ShipDamageEvent}: applies hull damage, routes the hit to a subsystem,
 * applies EMP soft-disable timers, and ticks those timers down each frame. Publishes
 * {@link SubsystemDisabledEvent} the moment a subsystem transitions to non-operational.
 */
public class ShipSubsystemSystem extends EntitySystem {

    public static final int PRIORITY = 9;

    /** Seconds of engine lockout per EMP hit (capped). */
    private static final float EMP_DURATION_PER_HIT = 4f;
    private static final float EMP_MAX_TIMER = 12f;

    private static final ComponentMapper<ShipSubsystemsComponent> SUB_M =
        ComponentMapper.getFor(ShipSubsystemsComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ShipDataComponent> DATA_M =
        ComponentMapper.getFor(ShipDataComponent.class);

    private final EventBus eventBus;
    private final List<ShipDamageEvent> pending = new ArrayList<>();
    private ImmutableArray<Entity> empTickEntities;

    public ShipSubsystemSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(ShipDamageEvent.class, pending::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        empTickEntities = engine.getEntitiesFor(Family.all(ShipSubsystemsComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        empTickEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        // 1. Apply queued damage.
        for (int i = 0; i < pending.size(); i++) {
            applyDamage(pending.get(i));
        }
        pending.clear();

        // 2. Tick EMP timers; recovery does not re-publish disable events.
        if (empTickEntities != null) {
            for (int i = 0, n = empTickEntities.size(); i < n; i++) {
                ShipSubsystemsComponent c = SUB_M.get(empTickEntities.get(i));
                for (Subsystem s : c.subsystems.values()) {
                    if (s.empDisableTimer > 0f) {
                        s.empDisableTimer = Math.max(0f, s.empDisableTimer - deltaTime);
                    }
                }
            }
        }
    }

    private void applyDamage(ShipDamageEvent event) {
        ShipSubsystemsComponent subs = SUB_M.get(event.target);
        if (subs == null) return;

        // Hull damage (EMP does ~no hull damage).
        if (event.damageType != DamageType.EMP) {
            ShipDataComponent data = DATA_M.get(event.target);
            if (data != null) {
                data.currentHullHp = Math.max(0f, data.currentHullHp - event.damage);
            }
        }

        SubsystemType type = resolveSubsystem(event.target, event.hitPosition);
        Subsystem sub = subs.get(type);
        if (sub == null) return;

        boolean wasOperational = isOperational(sub);

        if (event.damageType == DamageType.EMP) {
            sub.empDisableTimer = Math.min(EMP_MAX_TIMER, sub.empDisableTimer + EMP_DURATION_PER_HIT);
        } else {
            sub.health = Math.max(0f, sub.health - event.damage);
            if (sub.health <= 0f) sub.destroyed = true;
        }

        boolean nowOperational = isOperational(sub);
        if (wasOperational && !nowOperational) {
            eventBus.publish(new SubsystemDisabledEvent(event.target, type));
        }
    }

    private static boolean isOperational(Subsystem s) {
        return s.health > 0f && s.empDisableTimer <= 0f;
    }

    /**
     * Map a world-space hit to a subsystem by the hit's ship-local Z.
     * Aft (forward is -Z in this engine) = ENGINES, fore = WEAPONS, mid = SHIELDS.
     */
    private SubsystemType resolveSubsystem(Entity ship, Vector3 hitWorld) {
        TransformComponent tc = TRANSFORM_M.get(ship);
        float localZ = tc == null ? 0f : (hitWorld.z - tc.position.z);
        if (localZ < -3f) return SubsystemType.ENGINES;   // aft
        if (localZ > 3f) return SubsystemType.WEAPONS;     // fore
        return SubsystemType.SHIELDS;                      // midships
    }
}
