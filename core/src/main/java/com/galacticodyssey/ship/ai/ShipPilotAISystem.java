package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.combat.fleet.components.FleetMemberComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponSystem;

/**
 * Drives NPC ship pilots. Priority 2 — after player input (0), before ShipFlightSystem (3), so
 * the inputs it writes are applied the same frame.
 */
public class ShipPilotAISystem extends IteratingSystem {

    public static final int PRIORITY = 2;

    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);
    private static final ComponentMapper<TransformComponent> TX_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<ShipFlightInputComponent> IN_M =
        ComponentMapper.getFor(ShipFlightInputComponent.class);
    private static final ComponentMapper<HealthComponent> HP_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ShipHardpointComponent> HARD_M =
        ComponentMapper.getFor(ShipHardpointComponent.class);
    private static final ComponentMapper<FleetMemberComponent> FLEET_M =
        ComponentMapper.getFor(FleetMemberComponent.class);

    private final ShipWeaponSystem weaponSystem;
    private final ShipSteeringController steering = new ShipSteeringController();

    private final Vector3 selfPos = new Vector3();
    private final Vector3 selfVel = new Vector3();
    private final Vector3 targetPos = new Vector3();
    private final Vector3 targetVel = new Vector3();
    private final Vector3 angVelLocal = new Vector3();
    private final Quaternion invRot = new Quaternion();

    public ShipPilotAISystem(EventBus eventBus, ShipWeaponSystem weaponSystem) {
        super(Family.all(
            ShipPilotAIComponent.class,
            ShipFlightInputComponent.class,
            TransformComponent.class,
            PhysicsBodyComponent.class).get(), PRIORITY);
        this.weaponSystem = weaponSystem;
        eventBus.subscribe(EntityKilledEvent.class, this::onEntityKilled);
    }

    private void onEntityKilled(EntityKilledEvent event) {
        for (Entity e : getEntities()) {
            ShipPilotAIComponent ai = AI_M.get(e);
            if (ai != null && ai.currentTarget == event.target) {
                ai.currentTarget = null;
                ai.blackboard.clearTarget();
            }
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipPilotAIComponent ai = AI_M.get(entity);
        ShipFlightInputComponent input = IN_M.get(entity);

        HealthComponent hp = HP_M.get(entity);
        if (hp != null && !hp.alive) { zero(input); return; }

        FleetMemberComponent fm = FLEET_M.get(entity);
        FleetMemberComponent.LODTier tier = fm != null ? fm.lodTier : FleetMemberComponent.LODTier.FULL;
        if (tier == FleetMemberComponent.LODTier.ABSTRACT) return;

        if (ai.currentTarget != null) {
            HealthComponent tHp = HP_M.get(ai.currentTarget);
            if (tHp != null && !tHp.alive) ai.currentTarget = null;
        }

        if (ai.currentTarget == null) {
            ai.currentTarget = acquireTarget(entity, ai);
        }

        if (ai.currentTarget == null) {
            ai.blackboard.clearTarget();
            input.throttle = 0.2f;
            input.pitchInput = input.yawInput = input.rollInput = 0f;
            return;
        }

        PhysicsBodyComponent selfPhys = PHYS_M.get(entity);
        TransformComponent selfTx = TX_M.get(entity);
        TransformComponent tgtTx = TX_M.get(ai.currentTarget);
        PhysicsBodyComponent tgtPhys = PHYS_M.get(ai.currentTarget);
        if (tgtTx == null) { ai.currentTarget = null; return; }

        selfPos.set(selfTx.position);
        selfVel.set(selfPhys.body.getLinearVelocity());
        targetPos.set(tgtTx.position);
        targetVel.set(tgtPhys != null ? tgtPhys.body.getLinearVelocity() : Vector3.Zero);

        float muzzle = gunMuzzleSpeed(entity);
        ai.blackboard.selfHealthPercent = hp != null && hp.maxHP > 0 ? hp.currentHP / hp.maxHP : 1f;
        ai.blackboard.updateSensors(selfPos, selfTx.rotation, selfVel, targetPos, targetVel, muzzle);

        if (tier == FleetMemberComponent.LODTier.FULL) {
            tickFull(entity, ai, input, deltaTime);
        } else {
            tickSimplified(entity, ai, input);
        }
    }

    private void tickFull(Entity entity, ShipPilotAIComponent ai,
                          ShipFlightInputComponent input, float deltaTime) {
        ai.decisionTimer -= deltaTime;
        if (ai.behaviorTree != null && ai.decisionTimer <= 0f) {
            ai.decisionTimer = Math.max(ai.decisionInterval,
                ai.archetype != null ? ai.archetype.reactionTimeSec : 0f);
            ai.behaviorTree.step();
        }
        applyIntents(entity, ai, input);
    }

    private void tickSimplified(Entity entity, ShipPilotAIComponent ai,
                                ShipFlightInputComponent input) {
        float preferred = ai.archetype != null ? ai.archetype.preferredEngageRange : 350f;
        ai.blackboard.desiredThrottle = ai.blackboard.rangeToTarget > preferred ? 0.9f : 0.4f;
        ai.blackboard.desiredRoll = 0f;
        ai.blackboard.fireGuns = ai.blackboard.angleOffBore < 4f && ai.blackboard.rangeToTarget < gunRange(entity);
        ai.blackboard.fireMissiles = false;
        applyIntents(entity, ai, input);
    }

    private void applyIntents(Entity entity, ShipPilotAIComponent ai, ShipFlightInputComponent input) {
        PhysicsBodyComponent selfPhys = PHYS_M.get(entity);
        TransformComponent selfTx = TX_M.get(entity);

        invRot.set(selfTx.rotation).conjugate();
        angVelLocal.set(selfPhys.body.getAngularVelocity()).mul(invRot);

        steering.computeInputs(selfTx.rotation, angVelLocal,
            ai.blackboard.desiredAimDir, ai.blackboard.desiredThrottle, input);
        input.rollInput = MathUtils.clamp(input.rollInput + ai.blackboard.desiredRoll, -1f, 1f);

        if (ai.blackboard.fireGuns || ai.blackboard.fireMissiles) {
            fireWeapons(entity, ai);
        }
    }

    private void fireWeapons(Entity entity, ShipPilotAIComponent ai) {
        ShipHardpointComponent hpc = HARD_M.get(entity);
        if (hpc == null) return;
        hpc.currentTarget = ai.currentTarget;
        for (Hardpoint hp : hpc.hardpoints) {
            if (hp.isEmpty()) continue;
            boolean isMissile = hp.mountedWeapon.category == ShipWeaponCategory.MISSILE_LAUNCHER;
            if (isMissile && !ai.blackboard.fireMissiles) continue;
            if (!isMissile && !ai.blackboard.fireGuns) continue;
            weaponSystem.fireHardpoint(entity, hp.id);
        }
    }

    private static final Family PLAYER_FAMILY =
        Family.all(PlayerTagComponent.class, TransformComponent.class).get();
    private static final Family AI_SHIP_FAMILY =
        Family.all(ShipPilotAIComponent.class, TransformComponent.class, HealthComponent.class).get();

    private Entity acquireTarget(Entity self, ShipPilotAIComponent ai) {
        TransformComponent selfTx = TX_M.get(self);
        if (selfTx == null) return null;
        float aggro = ai.archetype != null ? ai.archetype.aggroRange : 2000f;
        float bestDist = aggro;
        Entity best = null;

        ImmutableArray<Entity> players = getEngine().getEntitiesFor(PLAYER_FAMILY);
        for (int i = 0; i < players.size(); i++) {
            Entity p = players.get(i);
            float d = selfTx.position.dst(TX_M.get(p).position);
            if (d < bestDist) { bestDist = d; best = p; }
        }

        FleetMemberComponent myFleet = FLEET_M.get(self);
        String myFleetId = myFleet != null ? myFleet.fleetId : null;
        ImmutableArray<Entity> ships = getEngine().getEntitiesFor(AI_SHIP_FAMILY);
        for (int i = 0; i < ships.size(); i++) {
            Entity other = ships.get(i);
            if (other == self) continue;
            HealthComponent oh = HP_M.get(other);
            if (oh != null && !oh.alive) continue;
            FleetMemberComponent of = FLEET_M.get(other);
            String otherFleetId = of != null ? of.fleetId : null;
            if (myFleetId != null && myFleetId.equals(otherFleetId)) continue;
            if (myFleetId == null && otherFleetId == null) continue;
            float d = selfTx.position.dst(TX_M.get(other).position);
            if (d < bestDist) { bestDist = d; best = other; }
        }
        return best;
    }

    private float gunMuzzleSpeed(Entity entity) {
        ShipHardpointComponent hpc = HARD_M.get(entity);
        if (hpc != null) {
            for (Hardpoint hp : hpc.hardpoints) {
                if (!hp.isEmpty() && hp.mountedWeapon.category != ShipWeaponCategory.MISSILE_LAUNCHER) {
                    return hp.mountedWeapon.projectileSpeed;
                }
            }
        }
        return 600f;
    }

    private float gunRange(Entity entity) {
        ShipHardpointComponent hpc = HARD_M.get(entity);
        if (hpc != null) {
            for (Hardpoint hp : hpc.hardpoints) {
                if (!hp.isEmpty() && hp.mountedWeapon.category != ShipWeaponCategory.MISSILE_LAUNCHER) {
                    return hp.mountedWeapon.range;
                }
            }
        }
        return 500f;
    }

    private void zero(ShipFlightInputComponent input) {
        if (input == null) return;
        input.throttle = input.strafe = input.verticalThrust = 0f;
        input.pitchInput = input.yawInput = input.rollInput = 0f;
    }
}
