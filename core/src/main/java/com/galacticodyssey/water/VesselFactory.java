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
import com.galacticodyssey.water.data.VesselData;

/**
 * Creates surface vessel entities from data-driven {@link VesselData} definitions.
 *
 * <p>Each vessel gets a {@link HullComponent} with auto-generated buoyancy sample
 * points distributed across the hull geometry. The number and distribution of
 * points scales with the hull dimensions and the requested
 * {@link VesselData#samplePointCount}, giving larger ships a denser sampling grid
 * and more realistic wave response.
 *
 * <p>Physics behaviour scales naturally with the data:
 * <ul>
 *   <li><b>Hull speed:</b> Froude = v / sqrt(g * L). Longer hulls reach the
 *       wave-drag peak at higher absolute speed.</li>
 *   <li><b>Roll period:</b> Heavier, wider hulls have more rotational inertia
 *       and roll more slowly.</li>
 *   <li><b>Turning radius:</b> Angular drag scales with length * beam.</li>
 *   <li><b>Wave response:</b> Small boats pitch violently; large ships ride
 *       over the same waves smoothly.</li>
 * </ul>
 */
public class VesselFactory implements Disposable {

    private final Engine engine;
    private final BulletPhysicsSystem physics;
    private final float surfaceGravity;

    private final Array<Disposable> disposables = new Array<>();

    public VesselFactory(Engine engine, BulletPhysicsSystem physics, float surfaceGravity) {
        this.engine = engine;
        this.physics = physics;
        this.surfaceGravity = surfaceGravity;
    }

    /**
     * Creates a fully configured vessel entity from a data definition.
     *
     * @param data vessel definition loaded from JSON
     * @param x    local-space X position
     * @param y    local-space Y position (should be near water surface)
     * @param z    local-space Z position
     * @return the new entity, already added to the engine
     */
    public Entity createVessel(VesselData data, float x, float y, float z) {
        Entity entity = new Entity();

        // Transform
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        // Hull with auto-generated sample points
        HullComponent hull = buildHull(data);
        entity.add(hull);

        // Wake trail data for rendering
        entity.add(new WakeComponent());

        // Motor
        BoatMotorComponent motor = new BoatMotorComponent();
        motor.maxThrust = data.maxThrust;
        motor.maxReverseThrust = data.maxReverseThrust;
        motor.rudderTorque = data.rudderTorque;
        motor.throttleResponseRate = data.throttleResponseRate;
        motor.rudderResponseRate = data.rudderResponseRate;
        motor.minSpeedForFullRudder = data.minSpeedForFullRudder;
        entity.add(motor);

        // Input
        entity.add(new BoatInputComponent());

        // Physics
        PhysicsBodyComponent physComp = buildPhysicsBody(data, x, y, z);
        entity.add(physComp);

        engine.addEntity(entity);
        return entity;
    }

    private HullComponent buildHull(VesselData data) {
        float hullHeight = data.draft + data.freeboard;
        float displacementVolume = data.length * data.beam * data.draft * data.blockCoefficient;
        float wettedArea = data.length * (data.beam + 2f * data.draft) * (0.7f + 0.15f * data.blockCoefficient);

        HullComponent hull = new HullComponent();
        hull.dryMass = data.dryMass;
        hull.totalDisplacementVolume = displacementVolume;
        hull.dragCoefficientLinear = data.skinFrictionCd;
        hull.dragCoefficientQuad = data.formDragCd;
        hull.wettedArea = wettedArea;
        hull.beamWidth = data.beam;
        hull.hullLength = data.length;
        hull.isSubmersible = false;
        hull.crushDepth = Float.MAX_VALUE;

        generateSamplePoints(hull, data);
        return hull;
    }

    /**
     * Distributes buoyancy sample points across the hull in a scalable grid.
     *
     * <p>8 points are reserved for bow and stern endcaps (4 each) with
     * angled normals that capture the hull's fore/aft curvature. The remaining
     * points are distributed in evenly spaced longitudinal stations, each with
     * 4 points (starboard, keel, port, deck). This produces natural restoring
     * torques for roll and pitch via the hull-normal buoyancy model.
     *
     * <pre>
     *        BOW (-Z)
     *    P --*--*-- S        (4 endcap points)
     *   /            \
     *  P    K    D    S      (station 0: 4 body points)
     *  P    K    D    S      (station 1)
     *  ...              ...  (more stations for higher point counts)
     *   \            /
     *    P --*--*-- S        (4 endcap points)
     *        STERN (+Z)
     * </pre>
     */
    private void generateSamplePoints(HullComponent hull, VesselData data) {
        float hl = data.length * 0.5f;
        float hb = data.beam * 0.5f;
        float patchArea = hull.wettedArea / data.samplePointCount;

        int bodyPoints = Math.max(0, data.samplePointCount - 8);
        int stations = Math.max(2, bodyPoints / 4);

        // Bow endcap (4 points)
        addSample(hull, hb * 0.4f, -data.draft * 0.3f, -hl * 0.9f,
                  0.3f, 0.5f, -0.8f, patchArea * 0.7f);
        addSample(hull, 0f, -data.draft * 0.2f, -hl,
                  0f, 0.5f, -0.87f, patchArea * 0.5f);
        addSample(hull, 0f, -data.draft * 0.8f, -hl * 0.8f,
                  0f, -1f, 0f, patchArea * 0.5f);
        addSample(hull, -hb * 0.4f, -data.draft * 0.3f, -hl * 0.9f,
                  -0.3f, 0.5f, -0.8f, patchArea * 0.7f);

        // Body stations — evenly spaced from forward to aft
        for (int s = 0; s < stations; s++) {
            float t = (s + 0.5f) / stations;
            float z = -hl * 0.7f + t * hl * 1.4f;

            addSample(hull, hb, -data.draft * 0.2f, z,
                      1f, 0.3f, 0f, patchArea);
            addSample(hull, 0f, -data.draft, z,
                      0f, -1f, 0f, patchArea * 1.2f);
            addSample(hull, -hb, -data.draft * 0.2f, z,
                      -1f, 0.3f, 0f, patchArea);
            addSample(hull, 0f, -data.draft * 0.1f, z,
                      0f, 1f, 0f, patchArea * 0.8f);
        }

        // Stern endcap (4 points)
        addSample(hull, hb * 0.4f, -data.draft * 0.3f, hl * 0.9f,
                  0.3f, 0.5f, 0.8f, patchArea * 0.7f);
        addSample(hull, 0f, -data.draft * 0.2f, hl,
                  0f, 0.5f, 0.87f, patchArea * 0.5f);
        addSample(hull, 0f, -data.draft * 0.8f, hl * 0.8f,
                  0f, -1f, 0f, patchArea * 0.5f);
        addSample(hull, -hb * 0.4f, -data.draft * 0.3f, hl * 0.9f,
                  -0.3f, 0.5f, 0.8f, patchArea * 0.7f);
    }

    private static void addSample(HullComponent hull, float x, float y, float z,
                                   float nx, float ny, float nz, float area) {
        BuoyancySamplePoint sp = new BuoyancySamplePoint();
        sp.localOffset.set(x, y, z);
        sp.normal.set(nx, ny, nz).nor();
        sp.area = area;
        hull.samplePoints.add(sp);
    }

    private PhysicsBodyComponent buildPhysicsBody(VesselData data, float x, float y, float z) {
        float hullHeight = data.draft + data.freeboard;
        Vector3 halfExtents = new Vector3(
                data.beam * 0.5f, hullHeight * 0.5f, data.length * 0.5f);
        btCollisionShape shape = new btBoxShape(halfExtents);

        Vector3 inertia = new Vector3();
        shape.calculateLocalInertia(data.dryMass, inertia);

        btRigidBody.btRigidBodyConstructionInfo ci =
                new btRigidBody.btRigidBodyConstructionInfo(data.dryMass, null, shape, inertia);
        ci.setFriction(0.3f);
        ci.setRestitution(0.1f);
        btRigidBody body = new btRigidBody(ci);
        ci.dispose();

        body.setWorldTransform(new Matrix4().setToTranslation(x, y, z));
        body.setGravity(new Vector3(0f, -surfaceGravity, 0f));
        body.setDamping(data.linearDamping, data.angularDamping);
        body.setActivationState(4); // DISABLE_DEACTIVATION

        physics.getDynamicsWorld().addRigidBody(body);
        physics.addManagedBody(body);

        PhysicsBodyComponent comp = new PhysicsBodyComponent();
        comp.body = body;
        comp.shape = shape;
        comp.mass = data.dryMass;

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
