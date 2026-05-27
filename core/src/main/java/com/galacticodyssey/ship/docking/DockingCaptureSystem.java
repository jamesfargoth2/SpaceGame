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
import com.galacticodyssey.ship.docking.DockingStateComponent.DockingPhase;
import com.galacticodyssey.ship.docking.events.DockingCaptureEvent;
import com.galacticodyssey.ship.docking.events.DockingFailureEvent;
import com.galacticodyssey.ship.docking.events.DockingFailureEvent.DockingFailureReason;

/**
 * Detects when two docking ports are within capture radius and attempts
 * soft capture. On success, applies a damping impulse (restitution ~0.2),
 * latches the ports, and publishes {@link DockingCaptureEvent}. On failure
 * (excessive speed, misalignment, or roll mismatch), publishes
 * {@link DockingFailureEvent}.
 */
public class DockingCaptureSystem extends EntitySystem {

    public static final int PRIORITY = 7;

    /** Anti-parallel dot threshold: cos(~11 degrees) negated. Normals must dot below this. */
    private static final float ALIGNMENT_DOT_THRESHOLD = -0.98f;

    /** Restitution coefficient for the damping impulse at soft capture. */
    private static final float CAPTURE_RESTITUTION = 0.2f;

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

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    public DockingCaptureSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
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
            processEntity(entities.get(i));
        }
    }

    private void processEntity(Entity chaser) {
        DockingStateComponent state = STATE_M.get(chaser);
        DockingPortComponent chaserPort = PORT_M.get(chaser);

        // Only attempt capture during the approach / contact phases
        if (state.dockingPhase != DockingPhase.FINAL_APPROACH
            && state.dockingPhase != DockingPhase.CONTACT) {
            return;
        }

        if (chaserPort.isLatched) return;

        Entity target = state.targetEntity;
        if (target == null) return;

        DockingPortComponent targetPort = PORT_M.get(target);
        TransformComponent chaserTransform = TRANSFORM_M.get(chaser);
        TransformComponent targetTransform = TRANSFORM_M.get(target);
        if (targetPort == null || chaserTransform == null || targetTransform == null) return;

        Vector3 chaserWorldPos = vectorPool.obtain();
        Vector3 targetWorldPos = vectorPool.obtain();
        Vector3 chaserWorldNormal = vectorPool.obtain();
        Vector3 targetWorldNormal = vectorPool.obtain();

        try {
            chaserPort.worldPosition(chaserTransform, chaserWorldPos);
            targetPort.worldPosition(targetTransform, targetWorldPos);

            float distance = chaserWorldPos.dst(targetWorldPos);
            float combinedRadius = chaserPort.captureRadius + targetPort.captureRadius;

            // Not within capture range yet
            if (distance > combinedRadius) return;

            // Transition to CONTACT phase
            state.dockingPhase = DockingPhase.CONTACT;

            // --- Alignment check: port normals must be anti-parallel ---
            chaserPort.worldNormal(chaserTransform, chaserWorldNormal);
            targetPort.worldNormal(targetTransform, targetWorldNormal);
            float normalDot = chaserWorldNormal.dot(targetWorldNormal);

            if (normalDot > ALIGNMENT_DOT_THRESHOLD) {
                float closingSpeed = computeClosingSpeed(chaser, target, chaserWorldNormal);
                eventBus.publish(new DockingFailureEvent(
                    chaser, target, DockingFailureReason.MISALIGNED, closingSpeed));
                return;
            }

            // --- Roll mismatch check ---
            float rollMismatchDeg = computeRollMismatch(chaserTransform, targetTransform,
                                                         chaserWorldNormal, targetWorldNormal);
            float maxRoll = Math.min(chaserPort.maxRollMismatchDeg, targetPort.maxRollMismatchDeg);
            if (rollMismatchDeg > maxRoll) {
                float closingSpeed = computeClosingSpeed(chaser, target, chaserWorldNormal);
                eventBus.publish(new DockingFailureEvent(
                    chaser, target, DockingFailureReason.ROLL_MISMATCH, closingSpeed));
                return;
            }

            // --- Closing speed check ---
            float closingSpeed = computeClosingSpeed(chaser, target, chaserWorldNormal);
            float maxCapture = Math.min(chaserPort.maxCaptureSpeed, targetPort.maxCaptureSpeed);

            if (closingSpeed > maxCapture) {
                eventBus.publish(new DockingFailureEvent(
                    chaser, target, DockingFailureReason.IMPACT_TOO_FAST, closingSpeed));
                return;
            }

            // --- Successful capture ---
            applyDampingImpulse(chaser, target, CAPTURE_RESTITUTION);

            chaserPort.isLatched = true;
            chaserPort.connectedPort = target;
            targetPort.isLatched = true;
            targetPort.connectedPort = chaser;

            state.dockingPhase = DockingPhase.HARD_DOCK;

            eventBus.publish(new DockingCaptureEvent(chaser, target));

        } finally {
            vectorPool.free(chaserWorldPos);
            vectorPool.free(targetWorldPos);
            vectorPool.free(chaserWorldNormal);
            vectorPool.free(targetWorldNormal);
        }
    }

    /**
     * Compute the scalar closing speed between two entities along the port normal.
     * Positive values mean the ships are approaching.
     */
    private float computeClosingSpeed(Entity entityA, Entity entityB, Vector3 portNormal) {
        PhysicsBodyComponent physA = PHYSICS_M.get(entityA);
        PhysicsBodyComponent physB = PHYSICS_M.get(entityB);
        if (physA == null || physB == null) return 0f;
        if (physA.body == null || physB.body == null) return 0f;

        Vector3 relVel = vectorPool.obtain();
        try {
            relVel.set(physB.body.getLinearVelocity())
                  .sub(physA.body.getLinearVelocity());
            float closing = relVel.dot(portNormal);
            return Math.abs(closing);
        } finally {
            vectorPool.free(relVel);
        }
    }

    /**
     * Apply a damping impulse to both bodies to reduce their relative velocity,
     * simulating the capture mechanism absorbing energy.
     */
    private void applyDampingImpulse(Entity entityA, Entity entityB, float restitution) {
        PhysicsBodyComponent physA = PHYSICS_M.get(entityA);
        PhysicsBodyComponent physB = PHYSICS_M.get(entityB);
        if (physA == null || physB == null) return;
        if (physA.body == null || physB.body == null) return;

        Vector3 relVel = vectorPool.obtain();
        Vector3 impulse = vectorPool.obtain();
        Vector3 impulseNeg = vectorPool.obtain();
        try {
            // Relative velocity of B w.r.t. A
            relVel.set(physB.body.getLinearVelocity())
                  .sub(physA.body.getLinearVelocity());

            // Impulse to damp relative motion: j = -(1 + e) * relVel / (1/mA + 1/mB)
            float invMassSum = 1f / physA.mass + 1f / physB.mass;
            if (invMassSum <= 0f) return;

            float dampFactor = -(1f + restitution);
            impulse.set(relVel).scl(dampFactor / invMassSum);

            // Apply equal-and-opposite impulses via Bullet
            impulseNeg.set(impulse).scl(-1f);
            physA.body.applyCentralImpulse(impulseNeg);
            physB.body.applyCentralImpulse(impulse);
        } finally {
            vectorPool.free(relVel);
            vectorPool.free(impulse);
            vectorPool.free(impulseNeg);
        }
    }

    /**
     * Estimates roll mismatch between two ports. Constructs a "right" vector for
     * each port (perpendicular to its normal), then measures the angle between them.
     */
    private float computeRollMismatch(TransformComponent transformA,
                                       TransformComponent transformB,
                                       Vector3 normalA, Vector3 normalB) {
        // Build an arbitrary perpendicular ("right") axis for each port by
        // rotating a reference vector through the entity's orientation.
        Vector3 refUp = vectorPool.obtain().set(0f, 1f, 0f);
        Vector3 rightA = vectorPool.obtain();
        Vector3 rightB = vectorPool.obtain();
        try {
            // Right = up x normal, unless normal is parallel to up
            rightA.set(refUp).crs(normalA);
            if (rightA.len2() < 1e-6f) {
                rightA.set(1f, 0f, 0f).crs(normalA);
            }
            rightA.nor();

            rightB.set(refUp).crs(normalB);
            if (rightB.len2() < 1e-6f) {
                rightB.set(1f, 0f, 0f).crs(normalB);
            }
            rightB.nor();

            float dot = MathUtils.clamp(rightA.dot(rightB), -1f, 1f);
            return (float) Math.acos(dot) * MathUtils.radiansToDegrees;
        } finally {
            vectorPool.free(refUp);
            vectorPool.free(rightA);
            vectorPool.free(rightB);
        }
    }
}
