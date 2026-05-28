package com.galacticodyssey.ship.docking;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.galaxy.faction.ReputationManager;
import com.galacticodyssey.galaxy.faction.ReputationTier;
import com.galacticodyssey.ship.docking.DockingStateComponent.DockingPhase;
import com.galacticodyssey.ship.docking.events.DockingAbortEvent;
import com.galacticodyssey.ship.docking.events.DockingAbortEvent.DockingAbortReason;
import com.galacticodyssey.ship.docking.events.DockingDeniedEvent;

/**
 * Monitors entities performing a docking approach and enforces the approach
 * corridor. Entities that stray outside the cone or exceed the maximum
 * approach speed receive a {@link DockingAbortEvent}.
 * <p>
 * The recommended approach speed is proportional to distance:
 * {@code clamp(distance * 0.1, 0.02, 0.5)} m/s, ensuring the chaser
 * decelerates smoothly as it nears the target port.
 */
public class DockingApproachSystem extends EntitySystem {

    public static final int PRIORITY = 6;

    private static final float SPEED_FACTOR = 0.1f;
    private static final float MIN_APPROACH_SPEED = 0.02f;
    private static final float MAX_APPROACH_SPEED = 0.5f;

    private static final Family FAMILY = Family.all(
        DockingStateComponent.class,
        DockingPortComponent.class,
        TransformComponent.class
    ).get();

    private static final ComponentMapper<DockingStateComponent> STATE_M =
        ComponentMapper.getFor(DockingStateComponent.class);
    private static final ComponentMapper<DockingPortComponent> PORT_M =
        ComponentMapper.getFor(DockingPortComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<MarketComponent> MARKET_M =
        ComponentMapper.getFor(MarketComponent.class);

    private final EventBus eventBus;
    private ReputationManager reputationManager;
    private ImmutableArray<Entity> entities;

    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    public DockingApproachSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    public void setReputationManager(ReputationManager reputationManager) {
        this.reputationManager = reputationManager;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (entities == null) return;

        for (int i = 0, n = entities.size(); i < n; i++) {
            processEntity(entities.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float deltaTime) {
        DockingStateComponent state = STATE_M.get(entity);

        if (state.dockingPhase == DockingPhase.NONE
            || state.dockingPhase == DockingPhase.CONTACT
            || state.dockingPhase == DockingPhase.HARD_DOCK) {
            return;
        }

        Entity target = state.targetEntity;
        if (target == null) return;

        // Reputation check: deny docking at HOSTILE faction stations
        if (reputationManager != null) {
            MarketComponent targetMarket = MARKET_M.get(target);
            if (targetMarket != null && targetMarket.ownerFactionId != null) {
                if (reputationManager.getTier(targetMarket.ownerFactionId) == ReputationTier.HOSTILE) {
                    state.dockingPhase = DockingPhase.NONE;
                    eventBus.publish(new DockingDeniedEvent(
                        targetMarket.stationId, targetMarket.ownerFactionId,
                        "HOSTILE reputation"));
                    return;
                }
            }
        }

        // Only check corridor during FINAL_APPROACH or MIDRANGE
        if (state.dockingPhase != DockingPhase.FINAL_APPROACH
            && state.dockingPhase != DockingPhase.MIDRANGE) {
            return;
        }

        TransformComponent chaserTransform = TRANSFORM_M.get(entity);
        TransformComponent targetTransform = TRANSFORM_M.get(target);
        if (chaserTransform == null || targetTransform == null) return;

        DockingPortComponent targetPort = PORT_M.get(target);
        if (targetPort == null) return;

        Vector3 portWorldPos = vectorPool.obtain();
        Vector3 toChaser = vectorPool.obtain();
        try {
            targetPort.worldPosition(targetTransform, portWorldPos);

            toChaser.set(chaserTransform.position).sub(portWorldPos);
            float distance = toChaser.len();

            // Inside the contact zone -- let DockingCaptureSystem handle it
            if (distance < 0.5f) return;

            // --- Corridor check ---
            // The approach axis points *toward* the approaching ship, so a valid
            // chaser lies roughly in the same direction as the axis.
            Vector3 chaserDir = toChaser.nor();
            float angleRad = (float) Math.acos(
                MathUtils.clamp(chaserDir.dot(state.approachAxis), -1f, 1f));
            float coneRad = state.coneHalfAngleDeg * MathUtils.degreesToRadians;

            if (angleRad > coneRad) {
                state.dockingPhase = DockingPhase.NONE;
                eventBus.publish(new DockingAbortEvent(entity, DockingAbortReason.OUT_OF_CORRIDOR));
                return;
            }

            // --- Speed check ---
            float recommendedSpeed = MathUtils.clamp(
                distance * SPEED_FACTOR, MIN_APPROACH_SPEED, MAX_APPROACH_SPEED);
            state.maxApproachSpeed = recommendedSpeed;

            float relativeSpeed = computeRelativeSpeed(entity, target);
            if (relativeSpeed > recommendedSpeed * 1.5f) {
                state.dockingPhase = DockingPhase.NONE;
                eventBus.publish(new DockingAbortEvent(entity, DockingAbortReason.TOO_FAST));
            }
        } finally {
            vectorPool.free(portWorldPos);
            vectorPool.free(toChaser);
        }
    }

    /**
     * Computes the scalar relative speed between two entities by reading
     * their Bullet rigid body linear velocities, or returning 0 if physics
     * bodies are not present.
     */
    private float computeRelativeSpeed(Entity chaserEntity, Entity targetEntity) {
        PhysicsBodyComponent chaserPhysics = PHYSICS_M.get(chaserEntity);
        PhysicsBodyComponent targetPhysics = PHYSICS_M.get(targetEntity);
        if (chaserPhysics == null || targetPhysics == null) return 0f;
        if (chaserPhysics.body == null || targetPhysics.body == null) return 0f;

        Vector3 relVel = vectorPool.obtain();
        try {
            relVel.set(chaserPhysics.body.getLinearVelocity())
                  .sub(targetPhysics.body.getLinearVelocity());
            return relVel.len();
        } finally {
            vectorPool.free(relVel);
        }
    }
}
