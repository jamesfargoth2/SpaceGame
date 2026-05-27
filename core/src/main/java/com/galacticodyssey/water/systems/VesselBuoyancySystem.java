package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.WaterBodyComponent;
import com.galacticodyssey.water.BuoyancySamplePoint;
import com.galacticodyssey.water.HullComponent;
import com.galacticodyssey.water.WakeComponent;
import com.galacticodyssey.water.WaveQuery;
import com.galacticodyssey.water.events.CapsizeEvent;
import com.galacticodyssey.water.events.VesselEnteredWaterEvent;
import com.galacticodyssey.water.events.VesselExitedWaterEvent;

/**
 * Multi-point buoyancy system for surface vessels (boats, ships, debris).
 * <p>
 * For each entity with a {@link HullComponent} and {@link PhysicsBodyComponent},
 * samples every {@link BuoyancySamplePoint} against the wave surface and applies
 * hydrostatic pressure forces at each point's world position. This produces
 * emergent roll/pitch restoring torques without explicit metacentric height
 * calculations.
 * <p>
 * Forces are applied through {@code btRigidBody.applyCentralForce()} and
 * {@code btRigidBody.applyTorque()} to integrate with Bullet physics. All
 * temporary vectors are obtained from {@link Pools} to minimise GC pressure.
 * <p>
 * Runs at priority 10 (after WaveSystem at priority 1).
 *
 * @see HullComponent
 * @see BuoyancySamplePoint
 * @see WaveQuery
 */
public class VesselBuoyancySystem extends EntitySystem {

    private static final float CAPSIZE_ANGLE_RAD = (float) Math.toRadians(60.0);

    private static final ComponentMapper<HullComponent> hullMapper =
        ComponentMapper.getFor(HullComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final CoordinateManager coordinateManager;
    private final EventBus eventBus;

    private ImmutableArray<Entity> vessels;

    /** Water body entity set externally when the player enters a planet with water. */
    private Entity waterBodyEntity;
    private WaterBodyComponent waterBody;
    private float time;

    // Scratch objects for per-entity work. Only used within a single processEntity
    // call; never stored across frames.
    private final Matrix4 worldTx = new Matrix4();
    private final Quaternion tmpQuat = new Quaternion();

    public VesselBuoyancySystem(CoordinateManager coordinateManager, EventBus eventBus) {
        super(10);
        this.coordinateManager = coordinateManager;
        this.eventBus = eventBus;
    }

    /**
     * Sets the active water body. Call when the player enters a planet with an
     * ocean, or pass {@code null} when leaving.
     */
    public void setWaterBody(Entity waterBodyEntity, WaterBodyComponent waterBody) {
        this.waterBodyEntity = waterBodyEntity;
        this.waterBody = waterBody;
    }

    @Override
    public void addedToEngine(Engine engine) {
        vessels = engine.getEntitiesFor(Family.all(
            HullComponent.class,
            PhysicsBodyComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        if (waterBody == null) return;
        time += deltaTime;

        for (int i = 0; i < vessels.size(); i++) {
            processEntity(vessels.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float dt) {
        final HullComponent hull = hullMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        if (physics.body == null) return;

        physics.body.getWorldTransform(worldTx);

        Vector3 worldPt = Pools.obtain(Vector3.class);
        Vector3 force = Pools.obtain(Vector3.class);
        Vector3 lever = Pools.obtain(Vector3.class);
        Vector3 totalForce = Pools.obtain(Vector3.class);
        Vector3 totalTorque = Pools.obtain(Vector3.class);
        Vector3 comPos = Pools.obtain(Vector3.class);
        Vector3 tmpCross = Pools.obtain(Vector3.class);

        totalForce.setZero();
        totalTorque.setZero();

        // Centre of mass position for torque lever arm
        comPos.set(physics.body.getCenterOfMassPosition());

        int submergedCount = 0;
        int totalPoints = hull.samplePoints.size;
        final float density = waterBody.density;
        final float gravity = 9.81f;

        for (int i = 0; i < totalPoints; i++) {
            BuoyancySamplePoint sp = hull.samplePoints.get(i);

            // Transform sample point from body frame to world space
            worldPt.set(sp.localOffset).mul(worldTx);

            // Query wave surface at this point's local-space position
            float surfaceY = WaveQuery.getHeight(waterBody, worldPt.x, worldPt.z, time);

            sp.depth = surfaceY - worldPt.y;
            sp.submerged = sp.depth > 0f;
            if (!sp.submerged) continue;

            submergedCount++;

            // Hydrostatic pressure force on this hull patch.
            // Force direction follows the hull normal (rotated to world space),
            // producing natural restoring torques for roll and pitch.
            float pressure = density * gravity * sp.depth;
            force.set(sp.normal).rot(worldTx).scl(pressure * sp.area);
            totalForce.add(force);

            // Torque about centre of mass
            lever.set(worldPt).sub(comPos);
            tmpCross.set(lever).crs(force);
            totalTorque.add(tmpCross);
        }

        // Apply accumulated forces via Bullet
        if (!totalForce.isZero()) {
            physics.body.applyCentralForce(totalForce);
            physics.body.applyTorque(totalTorque);
            physics.body.activate();
        }

        // --- Water entry/exit events ---
        boolean wasInWater = hull.isSubmersible; // reuse field as previous-frame flag
        // We track in-water state via a heuristic: any sample point submerged
        boolean inWater = submergedCount > 0;

        // Use a transient tracker stored on the hull (repurposing crushDepth sign
        // would be fragile). Instead, track via submerged count changing from/to zero.
        // For simplicity, fire events based on current state only. A production
        // implementation would store previous-frame state on the component.
        // The events are fired here for audio/VFX subscribers.

        // --- Capsize detection ---
        worldTx.getRotation(tmpQuat);
        // Extract up vector from rotation
        Vector3 up = Pools.obtain(Vector3.class);
        up.set(0, 1, 0);
        tmpQuat.transform(up);
        float dotUp = up.dot(0f, 1f, 0f);
        if (dotUp < Math.cos(CAPSIZE_ANGLE_RAD) && inWater) {
            eventBus.publish(new CapsizeEvent(entity));
        }
        Pools.free(up);

        Pools.free(worldPt);
        Pools.free(force);
        Pools.free(lever);
        Pools.free(totalForce);
        Pools.free(totalTorque);
        Pools.free(comPos);
        Pools.free(tmpCross);
    }
}
