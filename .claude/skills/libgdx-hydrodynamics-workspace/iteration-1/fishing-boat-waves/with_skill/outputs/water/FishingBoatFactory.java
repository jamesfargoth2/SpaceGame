package com.galacticodyssey.water;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.water.components.BoatInputComponent;
import com.galacticodyssey.water.components.BoatMotorComponent;

/**
 * Assembles a fishing boat {@link Entity} for the Ashley ECS.
 * <p>
 * A fishing boat is a small displacement vessel (~12 m long, ~4 m beam, ~5000 kg)
 * with 16 buoyancy sample points distributed across the hull: bow, stern, port,
 * starboard, keel centreline, and chines. This follows the skill's vessel archetype
 * reference (fishing boat: 16 pts, Cd 0.9, beam 4, length 12). The sample point
 * distribution gives the boat realistic pitch, roll, and heave response on waves.
 * <p>
 * The entity receives the following components:
 * <ul>
 *   <li>{@link TransformComponent} -- position and rotation</li>
 *   <li>{@link PhysicsBodyComponent} -- Bullet rigid body for force-based simulation</li>
 *   <li>{@link HullComponent} -- hull geometry, sample points, drag coefficients</li>
 *   <li>{@link WakeComponent} -- wake trail data for rendering</li>
 *   <li>{@link BoatMotorComponent} -- engine thrust and rudder parameters</li>
 *   <li>{@link BoatInputComponent} -- player input state (throttle, steering)</li>
 * </ul>
 * <p>
 * The Bullet rigid body has gravity set from the planet's surface gravity and low
 * linear/angular damping since the {@code HydrodynamicDragSystem} handles velocity
 * decay through proper fluid dynamics.
 */
public class FishingBoatFactory implements Disposable {

    // ---- Hull dimensions (metres) ----
    private static final float HULL_LENGTH = 12f;
    private static final float HULL_BEAM = 4f;
    private static final float HULL_DRAFT = 1.2f;    // depth below waterline
    private static final float HULL_FREEBOARD = 1.0f; // height above waterline
    private static final float HULL_HEIGHT = HULL_DRAFT + HULL_FREEBOARD;

    // ---- Mass and volume ----
    private static final float DRY_MASS = 5000f;     // kg
    // Displacement volume at full submersion. Block coefficient ~0.6 for a fishing boat.
    private static final float DISPLACEMENT_VOLUME = HULL_LENGTH * HULL_BEAM * HULL_DRAFT * 0.6f;
    // Wetted area approximation: (length * (beam + 2 * draft)) * 0.8 hull form factor
    private static final float WETTED_AREA = HULL_LENGTH * (HULL_BEAM + 2f * HULL_DRAFT) * 0.8f;

    // ---- Drag coefficients ----
    private static final float SKIN_FRICTION_CD = 0.05f;
    private static final float FORM_DRAG_CD = 0.9f;

    // ---- Motor defaults ----
    private static final float MAX_THRUST = 8000f;         // N (modest outboard motor)
    private static final float MAX_REVERSE_THRUST = 3000f; // N
    private static final float RUDDER_TORQUE = 25000f;     // N*m

    private final Engine engine;
    private final BulletPhysicsSystem physics;
    private final float surfaceGravity;

    private final Array<Disposable> disposables = new Array<>();

    /**
     * @param engine         the Ashley ECS engine
     * @param physics        the Bullet physics system (exterior world)
     * @param surfaceGravity gravitational acceleration on this planet (m/s^2)
     */
    public FishingBoatFactory(Engine engine, BulletPhysicsSystem physics, float surfaceGravity) {
        this.engine = engine;
        this.physics = physics;
        this.surfaceGravity = surfaceGravity;
    }

    /**
     * Creates a fishing boat entity at the specified local-space position.
     *
     * @param x local-space X position
     * @param y local-space Y position (should be near water surface height)
     * @param z local-space Z position
     * @return the new entity, already added to the engine
     */
    public Entity createFishingBoat(float x, float y, float z) {
        Entity entity = new Entity();

        // ---- Transform ----
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        // ---- Hull ----
        HullComponent hull = new HullComponent();
        hull.dryMass = DRY_MASS;
        hull.totalDisplacementVolume = DISPLACEMENT_VOLUME;
        hull.dragCoefficientLinear = SKIN_FRICTION_CD;
        hull.dragCoefficientQuad = FORM_DRAG_CD;
        hull.wettedArea = WETTED_AREA;
        hull.beamWidth = HULL_BEAM;
        hull.hullLength = HULL_LENGTH;
        hull.isSubmersible = false;
        hull.crushDepth = Float.MAX_VALUE;
        buildSamplePoints(hull);
        entity.add(hull);

        // ---- Wake ----
        WakeComponent wake = new WakeComponent();
        entity.add(wake);

        // ---- Motor ----
        BoatMotorComponent motor = new BoatMotorComponent();
        motor.maxThrust = MAX_THRUST;
        motor.maxReverseThrust = MAX_REVERSE_THRUST;
        motor.rudderTorque = RUDDER_TORQUE;
        motor.throttleResponseRate = 2.0f;   // ~0.5 s to full throttle
        motor.rudderResponseRate = 1.5f;     // slightly sluggish steering
        motor.minSpeedForFullRudder = 3.0f;  // m/s
        entity.add(motor);

        // ---- Input ----
        entity.add(new BoatInputComponent());

        // ---- Physics body ----
        PhysicsBodyComponent physicsBody = buildPhysicsBody(x, y, z);
        entity.add(physicsBody);

        // ---- Add to engine ----
        engine.addEntity(entity);

        return entity;
    }

    /**
     * Populates the hull with 16 buoyancy sample points (matching the skill's fishing
     * boat archetype). Points are arranged to capture the boat's response to waves:
     * <pre>
     *        BOW (-Z forward)
     *    3 --2--1-- 0      (bow: 4 points)
     *   /              \
     *  7    6    5    4    (forward quarter)
     *  |              |
     * 11   10    9    8    (aft quarter)
     *   \              /
     *   15 -14--13- 12     (stern: 4 points)
     * </pre>
     * <p>
     * Sample point normals follow the hull surface curvature: bottom points face
     * downward, side points face outward, bow/stern points face forward/aft with
     * upward components. This produces natural restoring torques for roll and pitch
     * via the buoyancy system.
     */
    private void buildSamplePoints(HullComponent hull) {
        float hl = HULL_LENGTH * 0.5f;   // half-length
        float hb = HULL_BEAM * 0.5f;     // half-beam

        // Area per sample point
        float patchArea = WETTED_AREA / 16f;

        // --- Bow section (4 points, z = -hl, i.e. forward in -Z convention) ---
        addSample(hull, hb * 0.5f, -HULL_DRAFT * 0.3f, -hl * 0.9f,
                  0.3f, 0.5f, -0.8f, patchArea * 0.7f);       // 0: bow starboard
        addSample(hull, 0f, -HULL_DRAFT * 0.2f, -hl,
                  0f, 0.5f, -0.87f, patchArea * 0.5f);        // 1: bow tip
        addSample(hull, 0f, -HULL_DRAFT * 0.8f, -hl * 0.8f,
                  0f, -1f, 0f, patchArea * 0.5f);             // 2: bow bottom
        addSample(hull, -hb * 0.5f, -HULL_DRAFT * 0.3f, -hl * 0.9f,
                  -0.3f, 0.5f, -0.8f, patchArea * 0.7f);      // 3: bow port

        // --- Forward quarter (4 points) ---
        addSample(hull, hb, -HULL_DRAFT * 0.2f, -hl * 0.35f,
                  1f, 0.5f, 0f, patchArea);                   // 4: starboard waterline fwd
        addSample(hull, 0f, -HULL_DRAFT, -hl * 0.3f,
                  0f, -1f, 0f, patchArea * 1.2f);             // 5: keel centreline fwd
        addSample(hull, 0f, -HULL_DRAFT * 0.2f, -hl * 0.35f,
                  0f, 1f, 0f, patchArea * 0.8f);              // 6: deck centre fwd
        addSample(hull, -hb, -HULL_DRAFT * 0.2f, -hl * 0.35f,
                  -1f, 0.5f, 0f, patchArea);                  // 7: port waterline fwd

        // --- Aft quarter (4 points) ---
        addSample(hull, hb, -HULL_DRAFT * 0.2f, hl * 0.35f,
                  1f, 0.5f, 0f, patchArea);                   // 8: starboard waterline aft
        addSample(hull, 0f, -HULL_DRAFT, hl * 0.3f,
                  0f, -1f, 0f, patchArea * 1.2f);             // 9: keel centreline aft
        addSample(hull, 0f, -HULL_DRAFT * 0.2f, hl * 0.35f,
                  0f, 1f, 0f, patchArea * 0.8f);              // 10: deck centre aft
        addSample(hull, -hb, -HULL_DRAFT * 0.2f, hl * 0.35f,
                  -1f, 0.5f, 0f, patchArea);                  // 11: port waterline aft

        // --- Stern section (4 points) ---
        addSample(hull, hb * 0.5f, -HULL_DRAFT * 0.3f, hl * 0.9f,
                  0.3f, 0.5f, 0.8f, patchArea * 0.7f);       // 12: stern starboard
        addSample(hull, 0f, -HULL_DRAFT * 0.2f, hl,
                  0f, 0.5f, 0.87f, patchArea * 0.5f);         // 13: stern centre
        addSample(hull, 0f, -HULL_DRAFT * 0.8f, hl * 0.8f,
                  0f, -1f, 0f, patchArea * 0.5f);             // 14: stern bottom
        addSample(hull, -hb * 0.5f, -HULL_DRAFT * 0.3f, hl * 0.9f,
                  -0.3f, 0.5f, 0.8f, patchArea * 0.7f);      // 15: stern port
    }

    private void addSample(HullComponent hull, float x, float y, float z,
                            float nx, float ny, float nz, float area) {
        BuoyancySamplePoint sp = new BuoyancySamplePoint();
        sp.localOffset.set(x, y, z);
        sp.normal.set(nx, ny, nz).nor();
        sp.area = area;
        hull.samplePoints.add(sp);
    }

    private PhysicsBodyComponent buildPhysicsBody(float x, float y, float z) {
        // Collision shape: box approximating the hull
        Vector3 halfExtents = new Vector3(HULL_BEAM * 0.5f, HULL_HEIGHT * 0.5f, HULL_LENGTH * 0.5f);
        btCollisionShape shape = new btBoxShape(halfExtents);

        Vector3 inertia = new Vector3();
        shape.calculateLocalInertia(DRY_MASS, inertia);

        btRigidBody.btRigidBodyConstructionInfo ci =
            new btRigidBody.btRigidBodyConstructionInfo(DRY_MASS, null, shape, inertia);
        ci.setFriction(0.3f);
        ci.setRestitution(0.1f);
        btRigidBody body = new btRigidBody(ci);
        ci.dispose();

        Matrix4 worldTransform = new Matrix4();
        worldTransform.setToTranslation(x, y, z);
        body.setWorldTransform(worldTransform);

        // Set gravity for this planet
        body.setGravity(new Vector3(0f, -surfaceGravity, 0f));

        // Very low Bullet damping; the HydrodynamicDragSystem handles drag properly
        // through physics-based fluid dynamics.
        body.setDamping(0.02f, 0.05f);

        // Prevent the body from sleeping so buoyancy is always computed
        body.setActivationState(4); // DISABLE_DEACTIVATION

        physics.getDynamicsWorld().addRigidBody(body);
        physics.addManagedBody(body);

        PhysicsBodyComponent comp = new PhysicsBodyComponent();
        comp.body = body;
        comp.shape = shape;
        comp.mass = DRY_MASS;

        disposables.add(body);
        disposables.add(shape);

        return comp;
    }

    @Override
    public void dispose() {
        for (int i = 0; i < disposables.size; i++) {
            Disposable d = disposables.get(i);
            if (d instanceof btRigidBody) {
                btRigidBody body = (btRigidBody) d;
                physics.removeManagedBody(body);
                physics.getDynamicsWorld().removeRigidBody(body);
            }
        }
        for (int i = 0; i < disposables.size; i++) {
            disposables.get(i).dispose();
        }
        disposables.clear();
    }
}
