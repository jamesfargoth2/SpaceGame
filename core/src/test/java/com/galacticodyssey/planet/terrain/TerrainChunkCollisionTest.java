package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
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

    /**
     * Drive the async quadtree until every visible leaf has been built (State.READY).
     * Async generation runs on a background pool; {@code pump()} drains finished results
     * on this thread, so we loop update+pump (bounded) until the tree settles.
     */
    private static void settle(TerrainQuadtree tree, PlanetCoordsKM camera) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            tree.update(camera);
            tree.pump();
            List<TerrainChunk> leaves = tree.getVisibleLeaves();
            boolean allReady = !leaves.isEmpty()
                && leaves.stream().allMatch(l -> l.state == TerrainChunk.State.READY);
            if (allReady) return;
            Thread.sleep(2);
        }
    }

    @Test
    void quadtreeCreatesCollisionBodiesForNearDeepLeaves() throws InterruptedException {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        // Origin on the +Z surface; camera just above it on the +Z axis so deep leaves form.
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 0, 6371.0);
        TerrainQuadtree quadtree = new TerrainQuadtree(6371.0, origin, noise, biomeMap, dynamicsWorld);

        settle(quadtree, new PlanetCoordsKM(0, 0, 6371.01));

        List<TerrainChunk> leaves = quadtree.getVisibleLeaves();
        assertFalse(leaves.isEmpty(), "Should have visible leaf chunks");

        // Bounded-shell contract: only leaves flagged wantsCollision (deep + near camera) carry a body.
        long wantsCollision = leaves.stream().filter(l -> l.wantsCollision).count();
        assertTrue(wantsCollision > 0, "Some near-camera deep leaves should request collision");
        assertTrue(wantsCollision < 256, "Collision shell should be bounded, was " + wantsCollision);

        // Every collision-wanting leaf must have an actual body; non-requesting leaves must not.
        long bodies = leaves.stream().filter(l -> l.collisionBody != null).count();
        for (TerrainChunk leaf : leaves) {
            if (leaf.wantsCollision) {
                assertNotNull(leaf.collisionBody, "wantsCollision leaf should have a collision body");
            } else {
                assertNull(leaf.collisionBody, "non-collision leaf should not have a collision body");
            }
        }
        assertEquals(wantsCollision, bodies, "body count should match wantsCollision leaf count");
        assertEquals(bodies, dynamicsWorld.getNumCollisionObjects(),
            "all collision bodies should be registered with the world");

        quadtree.dispose(dynamicsWorld);
    }

    @Test
    void disposeRemovesAllCollisionBodies() throws InterruptedException {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 0, 6371.0);
        TerrainQuadtree quadtree = new TerrainQuadtree(6371.0, origin, noise, biomeMap, dynamicsWorld);

        settle(quadtree, new PlanetCoordsKM(0, 0, 6371.01));
        int bodyCount = dynamicsWorld.getNumCollisionObjects();
        assertTrue(bodyCount > 0, "Should have collision bodies in world");

        quadtree.dispose(dynamicsWorld);
        assertEquals(0, dynamicsWorld.getNumCollisionObjects(),
            "All collision bodies should be removed after dispose");
    }
}
