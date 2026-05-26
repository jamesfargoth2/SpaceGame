package com.galacticodyssey.core;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.utils.BufferUtils;
import com.galacticodyssey.data.TerrainGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

class HeightfieldCollisionTest {

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
        dynamicsWorld.addRigidBody(body);
        info.dispose();
        return body;
    }

    @Test
    void capsuleLandsOnFlatHeightfield() {
        float flatHeight = 5f;
        float[] heights = new float[17 * 17];
        for (int i = 0; i < heights.length; i++) heights[i] = flatHeight;

        FloatBuffer buffer = BufferUtils.newFloatBuffer(heights.length);
        buffer.put(heights);
        buffer.flip();

        btHeightfieldTerrainShape shape = new btHeightfieldTerrainShape(
            17, 17, buffer, 1f, flatHeight, flatHeight, 1, false);
        shape.setLocalScaling(new Vector3(100f / 16f, 1f, 100f / 16f));

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
        btRigidBody terrain = new btRigidBody(info);
        terrain.setWorldTransform(new Matrix4().setToTranslation(0, flatHeight, 0));
        dynamicsWorld.addRigidBody(terrain);
        info.dispose();

        btRigidBody capsule = createCapsule(0, flatHeight + 3f, 0);

        Matrix4 mat = new Matrix4();
        Vector3 pos = new Vector3();
        for (int i = 0; i < 300; i++) {
            capsule.activate();
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        capsule.getWorldTransform(mat);
        mat.getTranslation(pos);

        float expectedY = flatHeight + CAPSULE_HALF_HEIGHT;
        assertEquals(expectedY, pos.y, 0.5f,
            "Capsule should rest on flat heightfield at Y~" + expectedY);

        dynamicsWorld.removeRigidBody(capsule);
        dynamicsWorld.removeRigidBody(terrain);
        capsule.dispose();
        lastCapsuleShape.dispose();
        terrain.dispose();
        shape.dispose();
    }

    @Test
    void capsuleRestsOnVaryingHeightfield() {
        float[] heights = TerrainGenerator.generateHeightmap(
            VERTS_X, VERTS_Z, WORLD_W, WORLD_D, SEED);

        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        for (float h : heights) {
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
        }

        FloatBuffer buffer = BufferUtils.newFloatBuffer(heights.length);
        buffer.put(heights);
        buffer.flip();

        btHeightfieldTerrainShape shape = new btHeightfieldTerrainShape(
            VERTS_X, VERTS_Z, buffer, 1f, minH, maxH, 1, false);
        shape.setLocalScaling(new Vector3(
            WORLD_W / (VERTS_X - 1), 1f, WORLD_D / (VERTS_Z - 1)));

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
        btRigidBody terrain = new btRigidBody(info);
        float midH = (minH + maxH) / 2f;
        terrain.setWorldTransform(new Matrix4().setToTranslation(0, midH, 0));
        terrain.setFriction(0.9f);
        dynamicsWorld.addRigidBody(terrain);
        info.dispose();

        float centerH = TerrainGenerator.getHeightAt(
            heights, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, 0, 0);
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
            heights, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, pos.x, pos.z);
        float delta = pos.y - terrainAtCapsule;

        assertTrue(delta > 0f && delta < CAPSULE_HALF_HEIGHT + 0.5f,
            "Capsule should rest ON terrain surface. Center Y=" + pos.y
            + ", terrain at (" + pos.x + "," + pos.z + ")=" + terrainAtCapsule
            + ", delta=" + delta);

        dynamicsWorld.removeRigidBody(capsule);
        dynamicsWorld.removeRigidBody(terrain);
        capsule.dispose();
        lastCapsuleShape.dispose();
        terrain.dispose();
        shape.dispose();
    }

    @Test
    void capsuleNeverFallsBelowTerrain() {
        float[] heights = TerrainGenerator.generateHeightmap(
            VERTS_X, VERTS_Z, WORLD_W, WORLD_D, SEED);

        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        for (float h : heights) {
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
        }

        FloatBuffer buffer = BufferUtils.newFloatBuffer(heights.length);
        buffer.put(heights);
        buffer.flip();

        btHeightfieldTerrainShape shape = new btHeightfieldTerrainShape(
            VERTS_X, VERTS_Z, buffer, 1f, minH, maxH, 1, false);
        shape.setLocalScaling(new Vector3(
            WORLD_W / (VERTS_X - 1), 1f, WORLD_D / (VERTS_Z - 1)));

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
        btRigidBody terrain = new btRigidBody(info);
        float midH = (minH + maxH) / 2f;
        terrain.setWorldTransform(new Matrix4().setToTranslation(0, midH, 0));
        terrain.setFriction(0.9f);
        dynamicsWorld.addRigidBody(terrain);
        info.dispose();

        float centerH = TerrainGenerator.getHeightAt(
            heights, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, 0, 0);
        btRigidBody capsule = createCapsule(0, centerH + 2f, 0);

        Matrix4 mat = new Matrix4();
        Vector3 pos = new Vector3();

        for (int i = 0; i < 600; i++) {
            capsule.activate();
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);

            capsule.getWorldTransform(mat);
            mat.getTranslation(pos);
            float terrainH = TerrainGenerator.getHeightAt(
                heights, VERTS_X, VERTS_Z, WORLD_W, WORLD_D, pos.x, pos.z);

            assertTrue(pos.y > terrainH - 0.1f,
                "Capsule penetrated terrain at frame " + i
                + "! Y=" + pos.y + ", terrainH=" + terrainH
                + " at (" + pos.x + "," + pos.z + ")");
        }

        dynamicsWorld.removeRigidBody(capsule);
        dynamicsWorld.removeRigidBody(terrain);
        capsule.dispose();
        lastCapsuleShape.dispose();
        terrain.dispose();
        shape.dispose();
    }
}
