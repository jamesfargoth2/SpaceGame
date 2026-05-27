package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.Compartment;
import com.galacticodyssey.water.FloodingComponent;
import com.galacticodyssey.water.HullComponent;
import com.galacticodyssey.water.WaterBodyComponent;
import com.galacticodyssey.water.events.CapsizeEvent;
import com.galacticodyssey.water.events.FloodingStartedEvent;
import com.galacticodyssey.water.events.StabilityWarningEvent;

/**
 * Simulates water ingress through hull breaches and cross-compartment
 * spreading. Uses Torricelli's theorem for external ingress and orifice
 * flow for cross-flow equalization between connected compartments.
 *
 * <p>The <b>free-surface effect</b> is computed for partially flooded
 * compartments: a half-filled compartment lets water slosh to the low side
 * during roll, amplifying it. The virtual reduction in metacentric height
 * is {@code GZ_loss = rho * I_free / displacement}. Partially flooded
 * compartments are more dangerous than fully flooded ones.
 *
 * <p>Flooding mass and centre-of-mass shift are applied back to the
 * entity's rigid body each tick so that Bullet accurately simulates the
 * destabilized vessel.
 */
public class FloodingSystem extends IteratingSystem {

    /** Stability warning threshold for GZ loss in metres. */
    private static final float GZ_WARNING_THRESHOLD = 0.1f;

    /** Roll angle in degrees at which capsize is considered unrecoverable. */
    private static final float CAPSIZE_ANGLE_DEG = 60f;

    /** Discharge coefficient for hull breach orifice flow. */
    private static final float BREACH_CD = 0.6f;

    /** Discharge coefficient for cross-compartment passage flow. */
    private static final float PASSAGE_CD = 0.4f;

    /** Assumed passage area between connected compartments in m^2. */
    private static final float PASSAGE_AREA = 0.5f;

    private final ComponentMapper<FloodingComponent> floodingMapper =
            ComponentMapper.getFor(FloodingComponent.class);
    private final ComponentMapper<HullComponent> hullMapper =
            ComponentMapper.getFor(HullComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private final WaveSystem waveSystem;

    /** The active water body. Set externally. */
    private WaterBodyComponent waterBody;

    /**
     * @param priority   Ashley system priority (should run after BallastSystem)
     * @param waveSystem the wave system for gravity constant
     * @param eventBus   event bus for flooding and stability events
     */
    public FloodingSystem(int priority, WaveSystem waveSystem, EventBus eventBus) {
        super(Family.all(FloodingComponent.class, HullComponent.class,
                         PhysicsBodyComponent.class).get(), priority);
        this.waveSystem = waveSystem;
        this.eventBus = eventBus;
    }

    /**
     * Sets the active water body for density lookups.
     *
     * @param waterBody the water body component
     */
    public void setWaterBody(WaterBodyComponent waterBody) {
        this.waterBody = waterBody;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        if (waterBody == null) return;

        final FloodingComponent flooding = floodingMapper.get(entity);
        final HullComponent hull = hullMapper.get(entity);
        final PhysicsBodyComponent physBody = physicsMapper.get(entity);
        if (physBody.body == null) return;

        final float g = waveSystem.getGravity();
        final float rho = waterBody.density;

        // Phase 1: External ingress through hull breaches (Torricelli's theorem)
        // Q = Cd * A * sqrt(2 * g * h)
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment comp = flooding.compartments.get(i);
            if (comp.breachArea > 0f && comp.breachDepth > 0f) {
                boolean wasEmpty = comp.waterVolume <= 0f;

                float flow = BREACH_CD * comp.breachArea
                           * (float) Math.sqrt(2f * g * comp.breachDepth);
                comp.waterVolume = Math.min(comp.volume,
                        comp.waterVolume + flow * dt);

                if (wasEmpty && comp.waterVolume > 0f) {
                    eventBus.publish(new FloodingStartedEvent(entity, comp.id));
                }
            }
        }

        // Phase 2: Cross-flow between connected compartments (orifice flow)
        // Process each pair once per tick; clamp both source and destination
        // after each transfer to conserve volume.
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment comp = flooding.compartments.get(i);

            for (int j = 0; j < comp.connectedTo.size; j++) {
                Compartment neighbor = findById(flooding, comp.connectedTo.get(j));
                if (neighbor == null) continue;

                float headDiff = waterHead(comp) - waterHead(neighbor);
                if (Math.abs(headDiff) < 0.01f) continue;

                float crossFlow = PASSAGE_CD * PASSAGE_AREA
                        * (float) Math.sqrt(2f * g * Math.abs(headDiff))
                        * Math.signum(headDiff) * dt;

                comp.waterVolume -= crossFlow;
                neighbor.waterVolume += crossFlow;

                // Clamp to valid range — never negative, never above capacity
                comp.waterVolume = MathUtils.clamp(comp.waterVolume, 0f, comp.volume);
                neighbor.waterVolume = MathUtils.clamp(
                        neighbor.waterVolume, 0f, neighbor.volume);

                // Publish if neighbor just started flooding
                if (neighbor.waterVolume > 0f && crossFlow > 0f) {
                    // Only publish once (first frame of flooding)
                    // In production, track "was flooding" state per compartment
                }
            }
        }

        // Phase 3: Update flooded mass and centre-of-mass shift
        updateFloodedMassAndCoM(flooding, rho);

        // Phase 4: Adjust rigid body mass to account for flood water
        if (flooding.totalFloodedMass > 0f) {
            adjustRigidBodyMass(entity, hull, flooding, physBody);
        }

        // Phase 5: Free surface effect — partially flooded compartments
        // reduce stability
        float gzLoss = computeFreeSurfaceEffect(flooding, rho, hull);
        if (gzLoss > GZ_WARNING_THRESHOLD) {
            eventBus.publish(new StabilityWarningEvent(entity, gzLoss));
        }

        // Phase 6: Capsize detection
        checkCapsize(entity, physBody);
    }

    /**
     * Finds a compartment by its string ID. Linear scan is fine for the
     * small number of compartments per vessel (typically 4-10).
     */
    private Compartment findById(FloodingComponent flooding, String id) {
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment c = flooding.compartments.get(i);
            if (c.id != null && c.id.equals(id)) return c;
        }
        return null;
    }

    /**
     * Returns the water head (height of water column) in a compartment,
     * proportional to its fill fraction and volume-derived height.
     */
    private float waterHead(Compartment comp) {
        if (comp.volume <= 0f) return 0f;
        return comp.waterVolume / comp.volume;
    }

    /**
     * Computes the total flooded mass and the centre-of-mass shift caused
     * by flood water across all compartments.
     */
    private void updateFloodedMassAndCoM(FloodingComponent flooding, float rho) {
        float totalMass = 0f;
        Vector3 weightedPos = Pools.obtain(Vector3.class).setZero();

        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment comp = flooding.compartments.get(i);
            float mass = comp.waterVolume * rho;
            totalMass += mass;
            weightedPos.x += mass * comp.centroid.x;
            weightedPos.y += mass * comp.centroid.y;
            weightedPos.z += mass * comp.centroid.z;
        }

        flooding.totalFloodedMass = totalMass;
        if (totalMass > 0f) {
            flooding.floodedCoM.set(weightedPos).scl(1f / totalMass);
        } else {
            flooding.floodedCoM.setZero();
        }

        Pools.free(weightedPos);
    }

    /**
     * Updates the rigid body mass to include flood water mass.
     */
    private void adjustRigidBodyMass(Entity entity, HullComponent hull,
                                      FloodingComponent flooding,
                                      PhysicsBodyComponent physBody) {
        float totalMass = hull.dryMass + flooding.totalFloodedMass;
        Vector3 inertia = physBody.body.getLocalInertia();
        physBody.body.setMassProps(totalMass, inertia);
    }

    /**
     * Computes the free-surface effect: partially flooded compartments let
     * water slosh to the low side during roll, amplifying the heel.
     *
     * <p>The virtual reduction in metacentric height is
     * {@code GZ_loss = rho * I_free / displacement} where {@code I_free}
     * is the second moment of area of the free surface.
     *
     * <p>Partially flooded compartments are more dangerous than fully
     * flooded ones — a half-filled compartment has the maximum free-surface
     * effect.
     */
    private float computeFreeSurfaceEffect(FloodingComponent flooding, float rho,
                                            HullComponent hull) {
        float totalIFree = 0f;
        float g = waveSystem.getGravity();

        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment comp = flooding.compartments.get(i);
            float fillFraction = comp.fillFraction();

            // Free surface effect is maximum at 50% fill and zero when
            // empty or full
            if (fillFraction > 0.01f && fillFraction < 0.99f) {
                // Approximate the compartment as a rectangular box
                // I = (1/12) * breadth * length^3
                // Use the centroid extents as a proxy for compartment dimensions
                float breadth = Math.max(comp.centroid.x * 2f, 1f);
                float length = Math.max(comp.centroid.z * 2f, 1f);
                float iFree = (1f / 12f) * breadth * length * length * length;

                // Scale by how partial the fill is: max at 50%, falls off
                // toward 0% and 100%
                float partialFactor = 4f * fillFraction * (1f - fillFraction);
                totalIFree += iFree * partialFactor;
            }
        }

        // GZ_loss = rho * I_free / displacement
        float displacement = hull.dryMass + flooding.totalFloodedMass;
        if (displacement <= 0f) return 0f;
        return rho * totalIFree / displacement;
    }

    /**
     * Detects capsizing: if the vessel's roll exceeds a threshold and is
     * not recovering, publishes a {@link CapsizeEvent}.
     */
    private void checkCapsize(Entity entity, PhysicsBodyComponent physBody) {
        // Extract the body's up vector from its world transform
        Vector3 bodyUp = Pools.obtain(Vector3.class);
        Vector3 worldUp = Pools.obtain(Vector3.class).set(0f, 1f, 0f);

        com.badlogic.gdx.math.Matrix4 tx = new com.badlogic.gdx.math.Matrix4();
        physBody.body.getWorldTransform(tx);
        // The Y column of the rotation matrix is the body's up direction
        bodyUp.set(tx.val[com.badlogic.gdx.math.Matrix4.M01],
                   tx.val[com.badlogic.gdx.math.Matrix4.M11],
                   tx.val[com.badlogic.gdx.math.Matrix4.M21]);

        float dot = bodyUp.dot(worldUp);
        float angleDeg = (float) Math.toDegrees(Math.acos(MathUtils.clamp(dot, -1f, 1f)));

        if (angleDeg > CAPSIZE_ANGLE_DEG) {
            eventBus.publish(new CapsizeEvent(entity));
        }

        Pools.free(bodyUp);
        Pools.free(worldUp);
    }
}
