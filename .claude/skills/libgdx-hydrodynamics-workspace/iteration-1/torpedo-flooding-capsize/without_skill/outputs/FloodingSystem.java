package com.galacticodyssey.ship.flooding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.ship.flooding.Compartment;
import com.galacticodyssey.ship.flooding.DoorwayConnection;
import com.galacticodyssey.ship.flooding.components.FloodingComponent;
import com.galacticodyssey.ship.flooding.events.BreachSealedEvent;
import com.galacticodyssey.ship.flooding.events.CapsizeEvent;
import com.galacticodyssey.ship.flooding.events.FloodingStartedEvent;
import com.galacticodyssey.ship.flooding.events.HullBreachEvent;
import com.galacticodyssey.ship.flooding.events.StabilityWarningEvent;

/**
 * Simulates water ingress through hull breaches and cross-flow between
 * connected compartments within a ship's interior.
 *
 * <h3>Physics Model</h3>
 * <ul>
 *   <li><b>External ingress:</b> Torricelli's theorem
 *       {@code Q = Cd * A * sqrt(2 * g * h)} where {@code h} is the
 *       breach depth below the pressure boundary.</li>
 *   <li><b>Cross-flow:</b> Orifice flow through doorways using the
 *       head difference between connected compartments.</li>
 *   <li><b>Free surface effect:</b> Partially flooded compartments
 *       reduce stability by allowing water to shift to the low side
 *       during roll, computed as
 *       {@code GZ_loss = rho * I_free / displacement}.</li>
 *   <li><b>Mass/CoM shift:</b> Flooded water mass and its centre-of-mass
 *       are applied to the parent hull's Bullet rigid body as a torque
 *       bias, causing the ship to list and eventually capsize.</li>
 * </ul>
 *
 * <h3>Integration</h3>
 * <p>Per CLAUDE.md rule 6, flooding compartments exist conceptually
 * in the ship's interior physics world. The mass and stability effects
 * are applied to the parent hull's rigid body in the outer world via
 * {@code applyCentralForce()} and {@code applyTorque()}, consistent
 * with the skill's integration points.</p>
 *
 * <p>All temporary vectors are obtained from libGDX {@code Pools} to
 * minimize GC pressure per the project conventions.</p>
 */
public class FloodingSystem extends EntitySystem {

    /** System priority -- runs after basic physics systems. */
    public static final int PRIORITY = 5;

    /** Discharge coefficient for a hull breach (sharp-edged orifice). */
    private static final float BREACH_CD = 0.6f;

    /** Minimum breach area below which we ignore ingress (m^2). */
    private static final float MIN_BREACH_AREA = 0.001f;

    /** Tiny epsilon to avoid floating-point noise triggering events. */
    private static final float WATER_EPSILON = 0.001f;

    /** Roll angle in degrees beyond which capsize is imminent. */
    private static final float CAPSIZE_ANGLE_DEG = 60f;

    /** Seconds the roll must exceed capsize angle before declaring capsize. */
    private static final float CAPSIZE_HOLD_TIME = 2.0f;

    /** GZ loss thresholds for stability warning severity levels. */
    private static final float GZ_CAUTION_THRESHOLD = 0.1f;
    private static final float GZ_WARNING_THRESHOLD = 0.3f;
    private static final float GZ_CRITICAL_THRESHOLD = 0.6f;

    private static final Family FAMILY =
            Family.all(FloodingComponent.class, PhysicsBodyComponent.class).get();

    private static final ComponentMapper<FloodingComponent> FLOOD_M =
            ComponentMapper.getFor(FloodingComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
            ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private final EventBus.EventListener<HullBreachEvent> breachListener = this::onHullBreach;
    private final EventBus.EventListener<BreachSealedEvent> sealedListener = this::onBreachSealed;

    private ImmutableArray<Entity> entities;

    public FloodingSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
        eventBus.subscribe(HullBreachEvent.class, breachListener);
        eventBus.subscribe(BreachSealedEvent.class, sealedListener);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
        eventBus.unsubscribe(HullBreachEvent.class, breachListener);
        eventBus.unsubscribe(BreachSealedEvent.class, sealedListener);
    }

    @Override
    public void update(float deltaTime) {
        if (entities == null || deltaTime <= 0f) return;

        for (int i = 0, n = entities.size(); i < n; i++) {
            processEntity(entities.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float dt) {
        FloodingComponent flood = FLOOD_M.get(entity);
        PhysicsBodyComponent physics = PHYSICS_M.get(entity);
        if (flood == null || flood.capsized) return;

        // Phase 1: External water ingress through breaches
        simulateExternalIngress(entity, flood, dt);

        // Phase 2: Cross-flow between compartments through doorways
        simulateCrossFlow(entity, flood, dt);

        // Phase 3: Update total flooded mass and centre-of-mass
        updateFloodedMassAndCoM(flood);

        // Phase 4: Apply flooding forces to the hull rigid body
        if (physics.body != null && flood.totalFloodedMass > 0f) {
            applyFloodingForces(flood, physics);
        }

        // Phase 5: Read orientation from rigid body
        if (physics.body != null) {
            updateOrientationReadings(flood, physics);
        }

        // Phase 6: Free surface stability analysis
        flood.freeSurfaceGzLoss = computeFreeSurfaceEffect(flood, physics);
        checkStabilityWarnings(entity, flood);

        // Phase 7: Capsize detection
        checkCapsize(entity, flood, dt);
    }

    // ------------------------------------------------------------------
    // Phase 1: External ingress via Torricelli's theorem
    // ------------------------------------------------------------------

    private void simulateExternalIngress(Entity entity, FloodingComponent flood, float dt) {
        for (int c = 0, cn = flood.compartments.size; c < cn; c++) {
            Compartment comp = flood.compartments.get(c);
            if (comp.sealed || comp.breachArea < MIN_BREACH_AREA) continue;
            if (comp.breachDepth <= 0f) continue;

            float prevWater = comp.waterVolume;

            // Torricelli: Q = Cd * A * sqrt(2 * g * h)
            float velocity = (float) Math.sqrt(2f * flood.gravity * comp.breachDepth);
            float flowRate = BREACH_CD * comp.breachArea * velocity;
            comp.waterVolume = Math.min(comp.volume, comp.waterVolume + flowRate * dt);

            if (prevWater < WATER_EPSILON && comp.waterVolume >= WATER_EPSILON) {
                eventBus.publish(new FloodingStartedEvent(entity, comp.id, true));
            }
        }
    }

    // ------------------------------------------------------------------
    // Phase 2: Cross-flow through doorways
    // ------------------------------------------------------------------

    private void simulateCrossFlow(Entity entity, FloodingComponent flood, float dt) {
        for (int d = 0, dn = flood.doorways.size; d < dn; d++) {
            DoorwayConnection door = flood.doorways.get(d);
            if (door.sealed) return;

            Compartment compA = findCompartment(flood, door.compartmentA);
            Compartment compB = findCompartment(flood, door.compartmentB);
            if (compA == null || compB == null) continue;

            float headA = compA.waterHead(flood.compartmentHeight);
            float headB = compB.waterHead(flood.compartmentHeight);
            float deltaHead = headA - headB;
            if (Math.abs(deltaHead) < WATER_EPSILON) continue;

            float velocity = (float) Math.sqrt(2f * flood.gravity * Math.abs(deltaHead));
            float flowRate = door.dischargeCoefficient * door.passageArea * velocity;
            float transferVolume = flowRate * dt;

            // Determine flow direction: high head -> low head
            Compartment source, target;
            if (deltaHead > 0f) {
                source = compA;
                target = compB;
            } else {
                source = compB;
                target = compA;
            }

            // Clamp transfer volume for conservation
            transferVolume = Math.min(transferVolume, source.waterVolume);
            transferVolume = Math.min(transferVolume, target.volume - target.waterVolume);
            if (transferVolume <= 0f) continue;

            float prevTargetWater = target.waterVolume;

            source.waterVolume -= transferVolume;
            target.waterVolume += transferVolume;

            // Defensive clamp
            source.waterVolume = Math.max(0f, source.waterVolume);
            target.waterVolume = Math.min(target.waterVolume, target.volume);

            // Publish flooding started for newly flooding compartments
            if (prevTargetWater < WATER_EPSILON && target.waterVolume >= WATER_EPSILON) {
                eventBus.publish(new FloodingStartedEvent(entity, target.id, false));
            }
        }
    }

    // ------------------------------------------------------------------
    // Phase 3: Mass and centre-of-mass
    // ------------------------------------------------------------------

    private void updateFloodedMassAndCoM(FloodingComponent flood) {
        Vector3 weightedSum = Pools.obtain(Vector3.class);
        try {
            weightedSum.setZero();
            float totalMass = 0f;

            for (int c = 0, cn = flood.compartments.size; c < cn; c++) {
                Compartment comp = flood.compartments.get(c);
                float mass = comp.waterVolume * flood.fluidDensity;
                if (mass > 0f) {
                    weightedSum.x += comp.centroid.x * mass;
                    weightedSum.y += comp.centroid.y * mass;
                    weightedSum.z += comp.centroid.z * mass;
                    totalMass += mass;
                }
            }

            flood.totalFloodedMass = totalMass;
            if (totalMass > 0f) {
                flood.floodedCoM.set(
                        weightedSum.x / totalMass,
                        weightedSum.y / totalMass,
                        weightedSum.z / totalMass);
            } else {
                flood.floodedCoM.setZero();
            }
        } finally {
            Pools.free(weightedSum);
        }
    }

    // ------------------------------------------------------------------
    // Phase 4: Force application to the parent hull rigid body
    // ------------------------------------------------------------------

    /**
     * Applies the gravitational force of the flooded water mass and the
     * torque from the off-centre CoM to the ship's Bullet rigid body.
     * Also updates the rigid body mass to include flooded water.
     */
    private void applyFloodingForces(FloodingComponent flood, PhysicsBodyComponent physics) {
        Vector3 force = Pools.obtain(Vector3.class);
        Vector3 torque = Pools.obtain(Vector3.class);
        Vector3 lever = Pools.obtain(Vector3.class);
        Vector3 comWorld = Pools.obtain(Vector3.class);
        Vector3 rbCom = Pools.obtain(Vector3.class);
        Vector3 inertia = Pools.obtain(Vector3.class);
        Matrix4 worldTx = Pools.obtain(Matrix4.class);
        try {
            physics.body.getWorldTransform(worldTx);

            // Transform flooded CoM from body frame to world space
            comWorld.set(flood.floodedCoM).mul(worldTx);

            // Gravity force on the flooded water
            force.set(0f, -flood.gravity * flood.totalFloodedMass, 0f);
            physics.body.applyCentralForce(force);

            // Torque = lever x force
            worldTx.getTranslation(rbCom);
            lever.set(comWorld).sub(rbCom);
            torque.set(lever).crs(force);
            physics.body.applyTorque(torque);

            // Update rigid body mass (dry mass + flooded water)
            float totalMass = physics.mass + flood.totalFloodedMass;
            physics.body.getCollisionShape().calculateLocalInertia(totalMass, inertia);
            physics.body.setMassProps(totalMass, inertia);
        } finally {
            Pools.free(force);
            Pools.free(torque);
            Pools.free(lever);
            Pools.free(comWorld);
            Pools.free(rbCom);
            Pools.free(inertia);
            Pools.free(worldTx);
        }
    }

    // ------------------------------------------------------------------
    // Phase 5: Orientation readings
    // ------------------------------------------------------------------

    private void updateOrientationReadings(FloodingComponent flood,
                                            PhysicsBodyComponent physics) {
        Quaternion q = Pools.obtain(Quaternion.class);
        Matrix4 worldTx = Pools.obtain(Matrix4.class);
        try {
            physics.body.getWorldTransform(worldTx);
            worldTx.getRotation(q);

            // Roll (rotation about local Z): atan2(2(qw*qx + qy*qz), 1 - 2(qx^2 + qy^2))
            float sinr = 2f * (q.w * q.x + q.y * q.z);
            float cosr = 1f - 2f * (q.x * q.x + q.y * q.y);
            flood.currentRollDeg = (float) Math.toDegrees(Math.atan2(sinr, cosr));

            // Pitch (rotation about local X): asin(2(qw*qy - qz*qx))
            float sinp = MathUtils.clamp(2f * (q.w * q.y - q.z * q.x), -1f, 1f);
            flood.currentPitchDeg = (float) Math.toDegrees(Math.asin(sinp));
        } finally {
            Pools.free(q);
            Pools.free(worldTx);
        }
    }

    // ------------------------------------------------------------------
    // Phase 6: Free surface effect
    // ------------------------------------------------------------------

    /**
     * Computes the virtual reduction in metacentric height (GZ) caused
     * by partially flooded compartments. A half-filled compartment lets
     * water slosh to the low side during roll, amplifying it.
     *
     * <p>Formula: {@code GZ_loss = rho * sum(I_free_i) / displacement}
     * where {@code I_free} is the second moment of area of the free
     * surface and displacement is total ship mass.
     *
     * <p>This is the primary real-world cause of capsizing in damaged
     * ships -- partially flooded compartments are more dangerous than
     * fully flooded ones (per the skill's gotchas section).
     */
    private float computeFreeSurfaceEffect(FloodingComponent flood,
                                            PhysicsBodyComponent physics) {
        float iTotal = 0f;
        for (int c = 0, cn = flood.compartments.size; c < cn; c++) {
            Compartment comp = flood.compartments.get(c);
            float fill = comp.fillFraction();
            if (fill <= 0.01f || fill >= 0.99f) continue;

            // Approximate rectangular free surface: floor area = volume / height
            float floorArea = comp.volume / flood.compartmentHeight;
            float side = (float) Math.sqrt(floorArea);
            float iFree = (side * side * side * side) / 12f;

            // Parabolic envelope: effect peaks at 50% fill
            float fillEffect = 4f * fill * (1f - fill);
            iTotal += iFree * fillEffect;
        }

        float displacement = (physics.mass + flood.totalFloodedMass) * flood.gravity;
        if (displacement < 1f) return 0f;
        return flood.fluidDensity * iTotal / displacement;
    }

    private void checkStabilityWarnings(Entity entity, FloodingComponent flood) {
        if (flood.freeSurfaceGzLoss < GZ_CAUTION_THRESHOLD) return;

        int severity;
        if (flood.freeSurfaceGzLoss >= GZ_CRITICAL_THRESHOLD) {
            severity = 2;
        } else if (flood.freeSurfaceGzLoss >= GZ_WARNING_THRESHOLD) {
            severity = 1;
        } else {
            severity = 0;
        }

        eventBus.publish(new StabilityWarningEvent(
                entity, flood.freeSurfaceGzLoss, flood.currentRollDeg, severity));
    }

    // ------------------------------------------------------------------
    // Phase 7: Capsize detection
    // ------------------------------------------------------------------

    private void checkCapsize(Entity entity, FloodingComponent flood, float dt) {
        float absRoll = Math.abs(flood.currentRollDeg);
        if (absRoll >= CAPSIZE_ANGLE_DEG) {
            flood.capsizeTimer += dt;
            if (flood.capsizeTimer >= CAPSIZE_HOLD_TIME) {
                flood.capsized = true;
                eventBus.publish(new CapsizeEvent(
                        entity, flood.currentRollDeg, flood.totalFloodedMass));
            }
        } else {
            flood.capsizeTimer = 0f;
        }
    }

    // ------------------------------------------------------------------
    // Event handlers
    // ------------------------------------------------------------------

    private void onHullBreach(HullBreachEvent event) {
        if (entities == null) return;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity entity = entities.get(i);
            if (entity != event.shipEntity) continue;

            FloodingComponent flood = FLOOD_M.get(entity);
            if (flood == null) continue;

            Compartment comp = findCompartment(flood, event.compartmentId);
            if (comp != null) {
                comp.breachArea = event.breachArea;
                comp.breachDepth = event.breachDepth;
                comp.sealed = false;
            }
            break;
        }
    }

    private void onBreachSealed(BreachSealedEvent event) {
        if (entities == null) return;
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity entity = entities.get(i);
            if (entity != event.shipEntity) continue;

            FloodingComponent flood = FLOOD_M.get(entity);
            if (flood == null) continue;

            Compartment comp = findCompartment(flood, event.compartmentId);
            if (comp != null) {
                comp.sealed = true;
                comp.breachArea = 0f;
            }
            break;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Compartment findCompartment(FloodingComponent flood, String id) {
        for (int i = 0, n = flood.compartments.size; i < n; i++) {
            if (flood.compartments.get(i).id.equals(id)) {
                return flood.compartments.get(i);
            }
        }
        return null;
    }
}
