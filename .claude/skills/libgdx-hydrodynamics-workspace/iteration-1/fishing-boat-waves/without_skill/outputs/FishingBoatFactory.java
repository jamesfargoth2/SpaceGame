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
 *
 * <p>A fishing boat is a small surface vessel (~10 m long, ~3.5 m beam, ~5000 kg)
 * with 14 buoyancy sample points distributed across the hull: bow, stern, port,
 * starboard, keel centreline, and chines. This gives the boat realistic pitch,
 * roll, and heave response on waves.
 *
 * <p>The entity receives the following components:
 * <ul>
 *   <li>{@link TransformComponent} — position and rotation</li>
 *   <li>{@link PhysicsBodyComponent} — Bullet rigid body for force-based simulation</li>
 *   <li>{@link HullComponent} — hull geometry, sample points, drag coefficients</li>
 *   <li>{@link BoatMotorComponent} — engine thrust and rudder parameters</li>
 *   <li>{@link BoatInputComponent} — player input state (throttle, steering)</li>
 * </ul>
 *
 * <p>The Bullet rigid body uses gravity from the planet's surface gravity and has
 * low linear/angular damping since the {@code HydrodynamicDragSystem} handles
 * velocity decay through proper fluid dynamics.
 */
public class FishingBoatFactory implements Disposable {

    // ---- Hull dimensions (metres) ----
    private static final float HULL_LENGTH = 10f;
    private static final float HULL_BEAM = 3.5f;
    private static final float HULL_DRAFT = 1.2f;    // depth below waterline
    private static final float HULL_FREEBOARD = 0.8f; // height above waterline
    private static final float HULL_HEIGHT = HULL_DRAFT + HULL_FREEBOARD;

    // ---- Mass and volume ----
    private static final float DRY_MASS = 5000f;     // kg
    private static final float DISPLACEMENT_VOLUME = 5.5f; // m^3 (seawater at 1025 kg/m^3)
    private static final float WETTED_AREA = 28f;    // m^2

    // ---- Drag coefficients ----
    private static final float SKIN_FRICTION_CD = 0.06f;
    private static final float FORM_DRAG_CD = 0.9f;

    // ---- Motor defaults ----
    private static final float MAX_THRUST = 8000f;         // N
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

        buildSamplePoints(hull);
        entity.add(hull);

        // ---- Motor ----
        BoatMotorComponent motor = new BoatMotorComponent();
        motor.maxThrust = MAX_THRUST;
        motor.maxReverseThrust = MAX_REVERSE_THRUST;
        motor.rudderTorque = RUDDER_TORQUE;
        motor.throttleResponseRate = 2.0f;
        motor.rudderResponseRate = 1.5f;
        motor.minSpeedForFullRudder = 3.0f;
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
     * Populates the hull with 14 buoyancy sample points arranged in a pattern
     * that captures the boat's response to waves:
     *
     * <pre>
     *        BOW
     *    2 ---1--- 0      (bow: 3 points)
     *   /           \
     *  5      4      3    (forward quarter)
     *  |      |      |
     *  8      7      6    (aft quarter)
     *   \           /
     *   11 --10-- 9       (stern: 3 points)
     *       12            (keel centreline forward)
     *       13            (keel centreline aft)
     * </pre>
     *
     * Sample point normals follow the hull surface curvature: bottom points
     * face downward-outward, side points face outward, bow/stern points face
     * forward/aft with outward components.
     */
    private void buildSamplePoints(HullComponent hull) {
        float halfLen = HULL_LENGTH * 0.5f;
        float halfBeam = HULL_BEAM * 0.5f;
        float keelY = -HULL_DRAFT;
        float waterlineY = 0f; // reference datum is the waterline

        // Area per sample point (total wetted area / number of points)
        float patchArea = WETTED_AREA / 14f;

        // Bow points (z = +halfLen) -- forward-facing normals with upward component
        addSample(hull, halfBeam * 0.5f, waterlineY, halfLen * 0.9f,
                  0.3f, 0.5f, 0.8f, patchArea);          // 0: bow starboard
        addSample(hull, 0f, waterlineY, halfLen,
                  0f, 0.5f, 0.87f, patchArea);            // 1: bow centre
        addSample(hull, -halfBeam * 0.5f, waterlineY, halfLen * 0.9f,
                  -0.3f, 0.5f, 0.8f, patchArea);          // 2: bow port

        // Forward quarter (z = +halfLen * 0.4)
        addSample(hull, halfBeam, waterlineY, halfLen * 0.4f,
                  0.87f, 0.5f, 0f, patchArea);            // 3: starboard upper
        addSample(hull, 0f, keelY * 0.5f, halfLen * 0.4f,
                  0f, -1f, 0f, patchArea);                 // 4: keel fwd-centre
        addSample(hull, -halfBeam, waterlineY, halfLen * 0.4f,
                  -0.87f, 0.5f, 0f, patchArea);           // 5: port upper

        // Aft quarter (z = -halfLen * 0.4)
        addSample(hull, halfBeam, waterlineY, -halfLen * 0.4f,
                  0.87f, 0.5f, 0f, patchArea);            // 6: starboard aft upper
        addSample(hull, 0f, keelY * 0.5f, -halfLen * 0.4f,
                  0f, -1f, 0f, patchArea);                 // 7: keel aft-centre
        addSample(hull, -halfBeam, waterlineY, -halfLen * 0.4f,
                  -0.87f, 0.5f, 0f, patchArea);           // 8: port aft upper

        // Stern points (z = -halfLen) -- aft-facing normals
        addSample(hull, halfBeam * 0.5f, waterlineY, -halfLen * 0.9f,
                  0.3f, 0.5f, -0.8f, patchArea);          // 9: stern starboard
        addSample(hull, 0f, waterlineY, -halfLen,
                  0f, 0.5f, -0.87f, patchArea);            // 10: stern centre
        addSample(hull, -halfBeam * 0.5f, waterlineY, -halfLen * 0.9f,
                  -0.3f, 0.5f, -0.8f, patchArea);         // 11: stern port

        // Keel centreline (deep points for vertical stability)
        addSample(hull, 0f, keelY, halfLen * 0.25f,
                  0f, -1f, 0f, patchArea);                 // 12: keel forward
        addSample(hull, 0f, keelY, -halfLen * 0.25f,
                  0f, -1f, 0f, patchArea);                 // 13: keel aft
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
        btRigidBody body = new btRigidBody(ci);
        ci.dispose();

        Matrix4 worldTransform = new Matrix4();
        worldTransform.setToTranslation(x, y, z);
        body.setWorldTransform(worldTransform);

        // Set gravity for this planet
        body.setGravity(new Vector3(0f, -surfaceGravity, 0f));

        // Very low Bullet damping -- the HydrodynamicDragSystem handles drag properly
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
