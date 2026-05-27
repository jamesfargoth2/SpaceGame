package com.galacticodyssey.ship;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.ship.components.*;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Assembles a complete ship {@link Entity} for the Ashley ECS.
 *
 * <p>Responsibilities per {@code createShip} call:
 * <ol>
 *   <li>Create a {@link ShipBlueprint} from the seed + size class.</li>
 *   <li>Generate hull geometry via {@link ShipHullGenerator}.</li>
 *   <li>Generate interior layout via {@link ShipInteriorGenerator}.</li>
 *   <li>Attach all required ECS components.</li>
 *   <li>Build the exterior convex-hull {@link btRigidBody} (static, mass=0).</li>
 *   <li>Create a dedicated interior {@link btDiscreteDynamicsWorld}.</li>
 *   <li>Add the entity to the Ashley {@link Engine}.</li>
 * </ol>
 *
 * <p>The hull {@link com.badlogic.gdx.graphics.Mesh} is intentionally left {@code null}
 * in {@link com.galacticodyssey.ship.components.ShipMeshComponent}; it must be created
 * later by the GameScreen which owns the OpenGL context.
 */
public class ShipFactory implements Disposable {

    // -------------------------------------------------------------------------
    // Stats per size class
    // -------------------------------------------------------------------------

    /** Minimum mass in kg for each size class (index: SMALL=0, MEDIUM=1, LARGE=2). */
    private static final float[] MASS_MIN   = { 5_000f,  30_000f, 150_000f };
    private static final float[] MASS_MAX   = { 15_000f, 80_000f, 500_000f };
    private static final float[] MAX_THRUST = { 50_000f, 200_000f, 500_000f };
    private static final float[] TURN_RATE  = { 90f, 45f, 20f };  // degrees/s
    private static final float[] MAX_SPEED  = { 150f, 100f, 60f };
    private static final float[] HULL_HP    = { 200f, 800f, 3_000f };

    // -------------------------------------------------------------------------
    // Flight parameters per size class
    // -------------------------------------------------------------------------

    private static final float[] LINEAR_THRUST       = { 50_000f,  200_000f, 500_000f };
    private static final float[] STRAFE_FRACTION     = { 0.60f,    0.40f,    0.25f };
    private static final float[] VERTICAL_FRACTION   = { 0.60f,    0.40f,    0.25f };
    private static final float[] PITCH_YAW_TORQUE    = { 20_000f,  50_000f,  100_000f };
    private static final float[] ROLL_TORQUE         = { 15_000f,  30_000f,   60_000f };
    private static final float[] LINEAR_DRAG         = { 0.3f,     0.5f,     0.7f };
    private static final float[] ANGULAR_DRAG        = { 2.0f,     3.0f,     5.0f };

    /**
     * Maximum number of hull vertices sampled when building the exterior
     * convex-hull collision shape (keeps Bullet overhead low).
     */
    private static final int MAX_CONVEX_POINTS = 64;

    // -------------------------------------------------------------------------

    private final Engine engine;
    private final BulletPhysicsSystem physics;
    private final ShipHullGenerator hullGenerator = new ShipHullGenerator();
    private final ShipInteriorGenerator interiorGenerator = new ShipInteriorGenerator();

    /** All Bullet objects that must be disposed when this factory is disposed. */
    private final Array<Disposable> disposables = new Array<>();

    // -------------------------------------------------------------------------

    public ShipFactory(Engine engine, BulletPhysicsSystem physics) {
        this.engine  = engine;
        this.physics = physics;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates and returns a fully assembled ship entity at the given world position.
     *
     * @param seed      deterministic seed — same seed + sizeClass always produces identical geometry
     * @param sizeClass ship size tier
     * @param x         initial world X position (local-space float, floating-origin convention)
     * @param y         initial world Y position
     * @param z         initial world Z position
     * @return the assembled entity, already added to the engine
     */
    public Entity createShip(long seed, ShipSizeClass sizeClass, float x, float y, float z) {

        int si = sizeClass.ordinal(); // index into the per-size-class arrays

        // ----- 1. Blueprint -----
        ShipBlueprint blueprint = new ShipBlueprint(seed, sizeClass);

        // ----- 2. Hull + interior geometry -----
        HullGeometry    hull    = hullGenerator.generate(blueprint);
        InteriorLayout  layout  = interiorGenerator.generate(blueprint, hull);

        // Adjust Y so the hull bottom sits at the requested y (hull extends below center)
        Vector3 bboxMin = new Vector3();
        hull.boundingBox.getMin(bboxMin);
        float adjustedY = y - bboxMin.y;

        // ----- 3. Assemble entity -----
        Entity entity = new Entity();

        // Transform
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, adjustedY, z);
        entity.add(transform);

        // Ship data (deferred mesh creation: hullGeometry stored for GameScreen)
        float mass = lerp(MASS_MIN[si], MASS_MAX[si], 0.5f); // mid-point of range for determinism
        ShipDataComponent shipData = new ShipDataComponent();
        shipData.blueprint    = blueprint;
        shipData.mass         = mass;
        shipData.maxThrust    = MAX_THRUST[si];
        shipData.maxTurnRate  = TURN_RATE[si];
        shipData.maxSpeed     = MAX_SPEED[si];
        shipData.hullHp       = HULL_HP[si];
        shipData.currentHullHp = HULL_HP[si];
        shipData.hullGeometry = hull;
        entity.add(shipData);

        // Mesh component — hullMesh intentionally null until GL context available
        ShipMeshComponent meshComp = new ShipMeshComponent();
        meshComp.vertexStride = hull.vertexStride;
        entity.add(meshComp);

        // Interior physics world
        ShipInteriorComponent interiorComp = buildInteriorComponent(layout);
        entity.add(interiorComp);

        // Flight parameters
        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust          = LINEAR_THRUST[si];
        flight.strafeThrustFraction  = STRAFE_FRACTION[si];
        flight.verticalThrustFraction = VERTICAL_FRACTION[si];
        flight.pitchYawTorque        = PITCH_YAW_TORQUE[si];
        flight.rollTorque            = ROLL_TORQUE[si];
        flight.linearDrag            = LINEAR_DRAG[si];
        flight.angularDrag           = ANGULAR_DRAG[si];
        flight.currentThrottle       = 0f;
        entity.add(flight);

        // Pilot seat
        PilotSeatComponent pilotSeat = new PilotSeatComponent();
        pilotSeat.interiorPosition.set(layout.pilotSeatPosition);
        entity.add(pilotSeat);

        // Entry / airlock point
        ShipEntryPointComponent entryPoint = new ShipEntryPointComponent();
        entryPoint.worldPosition.set(x + layout.airlockPosition.x,
                                     adjustedY + layout.airlockPosition.y,
                                     z + layout.airlockPosition.z);
        entryPoint.interiorPosition.set(layout.airlockPosition);
        entryPoint.localExteriorPosition.set(0f, bboxMin.y - 0.5f, 0f);
        entity.add(entryPoint);

        // Exterior physics body (dynamic, zero-gravity so it hovers in place until piloted)
        PhysicsBodyComponent physicsBody = buildExteriorPhysicsBody(hull, mass, x, adjustedY, z);
        entity.add(physicsBody);

        // ----- 4. Register with engine -----
        engine.addEntity(entity);

        return entity;
    }

    // -------------------------------------------------------------------------
    // Interior physics world
    // -------------------------------------------------------------------------

    private ShipInteriorComponent buildInteriorComponent(InteriorLayout layout) {
        ShipInteriorComponent comp = new ShipInteriorComponent();
        comp.layout = layout;
        comp.active = false;

        // Create the interior Bullet world
        btDefaultCollisionConfiguration cfg    = new btDefaultCollisionConfiguration();
        btCollisionDispatcher           disp   = new btCollisionDispatcher(cfg);
        btDbvtBroadphase                bp     = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver sol = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world = new btDiscreteDynamicsWorld(disp, bp, sol, cfg);
        world.setGravity(new Vector3(0, -9.81f, 0));

        comp.collisionConfig = cfg;
        comp.dispatcher      = disp;
        comp.broadphase      = bp;
        comp.solver          = sol;
        comp.interiorWorld   = world;

        // If the interior has mesh data, create a static trimesh body from floor + wall geometry
        if (layout.floorVertices != null && layout.floorVertices.length > 0
                && layout.floorIndices != null && layout.floorIndices.length >= 3) {

            try {
                btCollisionShape shape = buildInteriorTriMeshShape(layout);
                if (shape != null) {
                    comp.interiorShape = shape;

                    btRigidBody.btRigidBodyConstructionInfo ci =
                        new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
                    btRigidBody staticBody = new btRigidBody(ci);
                    ci.dispose();

                    world.addRigidBody(staticBody);
                    comp.interiorStaticBody = staticBody;
                }
            } catch (Exception e) {
                // Interior mesh building can fail on degenerate geometry; world still usable
            }
        }

        return comp;
    }

    /**
     * Combines floor and wall vertex data into a single {@link btBvhTriangleMeshShape}.
     *
     * <p>The interior generator uses a stride of 10 floats (pos=3, normal=3, color=4).
     * We extract only the position (first 3 floats per vertex) into the Bullet buffer.
     */
    private btCollisionShape buildInteriorTriMeshShape(InteriorLayout layout) {
        final int SRC_STRIDE = ShipInteriorGenerator.VERTEX_STRIDE; // 10 floats per vertex

        // Merge floor + wall into one vertex/index set, extracting only position
        int floorVertCount = (layout.floorVertices.length > 0)
                ? layout.floorVertices.length / SRC_STRIDE : 0;
        int wallVertCount  = (layout.wallVertices != null && layout.wallVertices.length > 0)
                ? layout.wallVertices.length / SRC_STRIDE : 0;
        int totalVerts = floorVertCount + wallVertCount;

        int floorIdxCount = (layout.floorIndices != null) ? layout.floorIndices.length : 0;
        int wallIdxCount  = (layout.wallIndices  != null) ? layout.wallIndices.length  : 0;
        int totalIdx  = floorIdxCount + wallIdxCount;

        if (totalVerts < 3 || totalIdx < 3) return null;

        // Build position-only float buffer (3 floats per vertex)
        FloatBuffer verts = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(totalVerts * 3);
        extractPositions(layout.floorVertices, floorVertCount, SRC_STRIDE, verts);
        if (wallVertCount > 0) {
            extractPositions(layout.wallVertices, wallVertCount, SRC_STRIDE, verts);
        }
        verts.position(0);

        // Build index short buffer (wall indices offset by floor vertex count)
        ShortBuffer indices = com.badlogic.gdx.utils.BufferUtils.newShortBuffer(totalIdx);
        if (floorIdxCount > 0) {
            for (short idx : layout.floorIndices) indices.put(idx);
        }
        if (wallIdxCount > 0) {
            for (short idx : layout.wallIndices) indices.put((short)(idx + floorVertCount));
        }
        indices.position(0);

        // Build btTriangleIndexVertexArray
        btIndexedMesh mesh = new btIndexedMesh();
        mesh.setNumTriangles(totalIdx / 3);
        mesh.setTriangleIndexBase(indices);
        mesh.setTriangleIndexStride(3 * 2); // 3 shorts per triangle
        mesh.setNumVertices(totalVerts);
        mesh.setVertexBase(verts);
        mesh.setVertexStride(3 * 4); // 3 floats per vertex

        btTriangleIndexVertexArray triArray = new btTriangleIndexVertexArray();
        triArray.addIndexedMesh(mesh, PHY_ScalarType.PHY_SHORT);

        btBvhTriangleMeshShape shape = new btBvhTriangleMeshShape(triArray, true);

        // Track for disposal
        disposables.add(triArray);
        disposables.add(shape);

        return shape;
    }

    /** Copies only the XYZ position components from a strided vertex array into {@code out}. */
    private static void extractPositions(float[] src, int vertCount, int stride, FloatBuffer out) {
        for (int i = 0; i < vertCount; i++) {
            int base = i * stride;
            out.put(src[base]);
            out.put(src[base + 1]);
            out.put(src[base + 2]);
        }
    }

    // -------------------------------------------------------------------------
    // Exterior convex-hull physics body
    // -------------------------------------------------------------------------

    private PhysicsBodyComponent buildExteriorPhysicsBody(HullGeometry hull, float mass, float x, float y, float z) {
        btCollisionShape convexShape = buildConvexHull(hull);

        Vector3 inertia = new Vector3();
        convexShape.calculateLocalInertia(mass, inertia);

        btRigidBody.btRigidBodyConstructionInfo ci =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, convexShape, inertia);
        btRigidBody body = new btRigidBody(ci);
        ci.dispose();

        Matrix4 worldTransform = new Matrix4();
        worldTransform.setToTranslation(x, y, z);
        body.setWorldTransform(worldTransform);
        body.setGravity(new Vector3(0, 0, 0));
        body.setDamping(0.8f, 0.9f);
        body.setActivationState(4); // DISABLE_DEACTIVATION

        short shipGroup = 4;
        short shipMask = (short)(0xFFFF & ~1);
        physics.getDynamicsWorld().addRigidBody(body, shipGroup, shipMask);
        physics.addManagedBody(body);

        PhysicsBodyComponent comp = new PhysicsBodyComponent();
        comp.body   = body;
        comp.shape  = convexShape;
        comp.mass   = mass;

        disposables.add(body);
        disposables.add(convexShape);

        return comp;
    }

    /**
     * Builds a simplified {@link btConvexHullShape} from the hull geometry by sampling
     * approximately every Nth vertex until {@value #MAX_CONVEX_POINTS} points are selected.
     */
    private btCollisionShape buildConvexHull(HullGeometry hull) {
        int totalVerts = hull.vertexCount();
        int stride = hull.vertexStride;

        if (totalVerts < 4) {
            Vector3 halfExtents = new Vector3();
            hull.boundingBox.getDimensions(halfExtents).scl(0.5f);
            if (halfExtents.x < 0.1f) halfExtents.x = 0.1f;
            if (halfExtents.y < 0.1f) halfExtents.y = 0.1f;
            if (halfExtents.z < 0.1f) halfExtents.z = 0.1f;
            return new btBoxShape(halfExtents);
        }

        btConvexHullShape convex = new btConvexHullShape();

        int step = Math.max(1, totalVerts / MAX_CONVEX_POINTS);
        for (int i = 0; i < totalVerts; i += step) {
            int base = i * stride;
            convex.addPoint(new Vector3(
                hull.vertices[base],
                hull.vertices[base + 1],
                hull.vertices[base + 2]), false);
        }
        convex.recalcLocalAabb();

        return convex;
    }

    // -------------------------------------------------------------------------
    // Disposal
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        // Remove managed bodies from the exterior world before destroying them
        btDiscreteDynamicsWorld world = physics.getDynamicsWorld();
        for (int i = 0; i < disposables.size; i++) {
            Disposable d = disposables.get(i);
            if (d instanceof btRigidBody) {
                btRigidBody body = (btRigidBody) d;
                physics.removeManagedBody(body);
                if (world != null) {
                    world.removeRigidBody(body);
                }
            }
        }
        for (int i = 0; i < disposables.size; i++) {
            disposables.get(i).dispose();
        }
        disposables.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
