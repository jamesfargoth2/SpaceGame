package com.galacticodyssey.core;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.data.TerrainGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriangleMeshCollisionTest {

    private static final int VERTS_X = 257;
    private static final int VERTS_Z = 257;
    private static final float WORLD_W = 500f;
    private static final float WORLD_D = 500f;
    private static final long SEED = 42L;
    private static final float CAPSULE_HALF_HEIGHT = 0.9f;

    private btDiscreteDynamicsWorld dynamicsWorld;
    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;

    @BeforeAll
    static void initBullet() {
        Bullet.init();
    }

    @BeforeEach
    void setUp() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -9.81f, 0));
    }

    @AfterEach
    void tearDown() {
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }

    private btTriangleMesh buildTriangleMesh(float[] heightmap) {
        float cellW = WORLD_W / (VERTS_X - 1);
        float cellD = WORLD_D / (VERTS_Z - 1);
        float halfW = WORLD_W / 2f;
        float halfD = WORLD_D / 2f;

        btTriangleMesh triMesh = new btTriangleMesh();

        Vector3 v0 = new Vector3(), v1 = new Vector3();
        Vector3 v2 = new Vector3(), v3 = new Vector3();

        for (int z = 0; z < VERTS_Z - 1; z++) {
            for (int x = 0; x < VERTS_X - 1; x++) {
                float x0 = x * cellW - halfW;
                float x1 = (x + 1) * cellW - halfW;
                float z0 = z * cellD - halfD;
                float z1 = (z + 1) * cellD - halfD;

                float h00 = heightmap[z * VERTS_X + x];
                float h10 = heightmap[z * VERTS_X + x + 1];
                float h01 = heightmap[(z + 1) * VERTS_X + x];
                float h11 = heightmap[(z + 1) * VERTS_X + x + 1];

                v0.set(x0, h00, z0);
                v1.set(x0, h01, z1);
                v2.set(x1, h10, z0);
                v3.set(x1, h11, z1);

                triMesh.addTriangle(v0, v1, v2);
                triMesh.addTriangle(v2, v1, v3);
            }
        }
        return triMesh;
    }

    private btCapsuleShape lastCapsuleShape;

    private btRigidBody createCapsule(float x, float y, float z) {
        lastCapsuleShape = new btCapsuleShape(0.3f, 1.2f);
        Vector3 inertia = new Vector3();
        lastCapsuleShape.calculateLocalInertia(80f, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(80f, null, lastCapsuleShape, inertia);
        btRigidBody body = new btRigidBody(info);
        body.setWorldTransform(new Matrix4().setToTranslation(x, y, z));
        body.setAngularFactor(new Vector3(0, 0, 0));
        body.setFriction(1.0f);
        body.setRestitution(0f);
        body.setCcdMotionThreshold(0.1f);
        body.setCcdSweptSphereRadius(0.2f);
        dynamicsWorld.addRigidBody(body);
        info.dispose();
        return body;
    }

    @Test
    void capsuleLandsOnTriangleMeshTerrain() {
        float[] heightmap = TerrainGenerator.generateHeightmap(VERTS_X, VERTS_Z, WORLD_W, WORLD_D, SEED);

        btTriangleMesh triMesh = buildTriangleMesh(heightmap);
        btBvhTriangleMeshShape terrainShape = new btBvhTriangleMeshShape(triMesh, true);

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, terrainShape);
        btRigidBody terrain = new btRigidBody(info);
        terrain.setFriction(0.9f);
        dynamicsWorld.addRigidBody(terrain);
        info.dispose();

        float centerH = TerrainGenerator.getHeightAt(heightmap, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, 0, 0);
        btRigidBody capsule = createCapsule(0, centerH + 3f, 0);

        Matrix4 mat = new Matrix4();
        Vector3 pos = new Vector3();

        for (int i = 0; i < 300; i++) {
            capsule.activate();
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        capsule.getWorldTransform(mat);
        mat.getTranslation(pos);

        float terrainAtCapsule = TerrainGenerator.getHeightAt(
            heightmap, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, pos.x, pos.z);
        float delta = pos.y - terrainAtCapsule;

        System.out.println("Capsule pos: " + pos + ", terrain at capsule: " + terrainAtCapsule + ", delta: " + delta);

        assertTrue(delta > 0f && delta < CAPSULE_HALF_HEIGHT + 0.5f,
            "Capsule should rest ON terrain. Y=" + pos.y
            + ", terrainH=" + terrainAtCapsule + ", delta=" + delta);

        dynamicsWorld.removeRigidBody(capsule);
        dynamicsWorld.removeRigidBody(terrain);
        capsule.dispose();
        lastCapsuleShape.dispose();
        terrain.dispose();
        terrainShape.dispose();
        triMesh.dispose();
    }

    @Test
    void capsuleNeverFallsThroughTriangleMesh() {
        float[] heightmap = TerrainGenerator.generateHeightmap(VERTS_X, VERTS_Z, WORLD_W, WORLD_D, SEED);

        btTriangleMesh triMesh = buildTriangleMesh(heightmap);
        btBvhTriangleMeshShape terrainShape = new btBvhTriangleMeshShape(triMesh, true);

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, terrainShape);
        btRigidBody terrain = new btRigidBody(info);
        terrain.setFriction(0.9f);
        dynamicsWorld.addRigidBody(terrain);
        info.dispose();

        float centerH = TerrainGenerator.getHeightAt(heightmap, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, 0, 0);
        btRigidBody capsule = createCapsule(0, centerH + 2f, 0);

        Matrix4 mat = new Matrix4();
        Vector3 pos = new Vector3();

        for (int i = 0; i < 600; i++) {
            capsule.activate();
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);

            capsule.getWorldTransform(mat);
            mat.getTranslation(pos);
            float terrainH = TerrainGenerator.getHeightAt(
                heightmap, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, pos.x, pos.z);

            assertTrue(pos.y > terrainH - 0.1f,
                "Capsule fell through terrain at frame " + i
                + "! Y=" + pos.y + ", terrainH=" + terrainH
                + " at (" + pos.x + "," + pos.z + ")");
        }

        dynamicsWorld.removeRigidBody(capsule);
        dynamicsWorld.removeRigidBody(terrain);
        capsule.dispose();
        lastCapsuleShape.dispose();
        terrain.dispose();
        terrainShape.dispose();
        triMesh.dispose();
    }

    @Test
    void boxLandsOnTriangleMeshTerrain() {
        float[] heightmap = TerrainGenerator.generateHeightmap(VERTS_X, VERTS_Z, WORLD_W, WORLD_D, SEED);

        btTriangleMesh triMesh = buildTriangleMesh(heightmap);
        btBvhTriangleMeshShape terrainShape = new btBvhTriangleMeshShape(triMesh, true);

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, terrainShape);
        btRigidBody terrain = new btRigidBody(info);
        terrain.setFriction(0.9f);
        dynamicsWorld.addRigidBody(terrain);
        info.dispose();

        float bx = 20f, bz = -15f;
        float terrainH = TerrainGenerator.getHeightAt(heightmap, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, bx, bz);

        btBoxShape boxShape = new btBoxShape(new Vector3(1f, 1f, 1f));
        Vector3 boxInertia = new Vector3();
        boxShape.calculateLocalInertia(10f, boxInertia);
        btRigidBody.btRigidBodyConstructionInfo boxInfo =
            new btRigidBody.btRigidBodyConstructionInfo(10f, null, boxShape, boxInertia);
        btRigidBody box = new btRigidBody(boxInfo);
        box.setWorldTransform(new Matrix4().setToTranslation(bx, terrainH + 5f, bz));
        box.setFriction(0.8f);
        dynamicsWorld.addRigidBody(box);
        boxInfo.dispose();

        Matrix4 mat = new Matrix4();
        Vector3 pos = new Vector3();

        for (int i = 0; i < 300; i++) {
            box.activate();
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        box.getWorldTransform(mat);
        mat.getTranslation(pos);
        float finalTerrainH = TerrainGenerator.getHeightAt(
            heightmap, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, pos.x, pos.z);

        System.out.println("Box pos: " + pos + ", terrain: " + finalTerrainH);

        assertTrue(pos.y > finalTerrainH - 0.1f,
            "Box should be above terrain. Y=" + pos.y + ", terrainH=" + finalTerrainH);

        dynamicsWorld.removeRigidBody(box);
        dynamicsWorld.removeRigidBody(terrain);
        box.dispose();
        boxShape.dispose();
        terrain.dispose();
        terrainShape.dispose();
        triMesh.dispose();
    }
}
