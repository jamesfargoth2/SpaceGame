package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.*;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainChunkCollisionTest {
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
    }

    @AfterEach
    void tearDown() {
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }

    @Test
    void quadtreeCreatesCollisionBodiesForLeafChunks() {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        TerrainQuadtree quadtree = new TerrainQuadtree(6371f, noise, biomeMap, dynamicsWorld);

        quadtree.update(new Vector3(0, 0, 6371f));

        List<TerrainChunk> leaves = quadtree.getVisibleLeaves();
        assertFalse(leaves.isEmpty(), "Should have visible leaf chunks");
        for (TerrainChunk leaf : leaves) {
            // Mesh may be null in headless tests (no GL context) -- that is expected.
            // The important assertion is that collision bodies are created.
            assertNotNull(leaf.collisionBody, "Leaf chunk should have a collision body");
        }

        quadtree.dispose(dynamicsWorld);
    }

    @Test
    void disposeRemovesAllCollisionBodies() {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        TerrainQuadtree quadtree = new TerrainQuadtree(6371f, noise, biomeMap, dynamicsWorld);

        quadtree.update(new Vector3(0, 0, 6371f));
        int bodyCount = dynamicsWorld.getNumCollisionObjects();
        assertTrue(bodyCount > 0, "Should have collision bodies in world");

        quadtree.dispose(dynamicsWorld);
        assertEquals(0, dynamicsWorld.getNumCollisionObjects(),
            "All collision bodies should be removed after dispose");
    }
}
