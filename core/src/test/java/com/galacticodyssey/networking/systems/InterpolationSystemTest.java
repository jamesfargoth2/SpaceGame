package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.interpolation.EntitySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterpolationSystemTest {

    private Engine engine;
    private InterpolationSystem system;
    private Entity remote;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        system = new InterpolationSystem(0.05f);
        engine.addSystem(system);

        remote = new Entity();
        remote.add(new TransformComponent());
        remote.add(new InterpolationComponent());
        engine.addEntity(remote);
    }

    @Test
    void interpolatesPositionBetweenSnapshots() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0));
        ic.getSnapshotBuffer().add(new EntitySnapshot(4, 40, 0, 0, 0, 0, 0, 1, 0, 0, 0));

        system.setCurrentServerTick(4);
        engine.update(0.05f);

        TransformComponent t = remote.getComponent(TransformComponent.class);
        // Target tick = 4 - 2 = 2. t = (2-0)/(4-0) = 0.5 => posX = 20
        assertEquals(20f, t.position.x, 1.0f);
    }

    @Test
    void snapsRotationViaNlerp() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0));
        ic.getSnapshotBuffer().add(new EntitySnapshot(4, 0, 0, 0, 0, 0.707f, 0, 0.707f, 0, 0, 0));

        system.setCurrentServerTick(4);
        engine.update(0.05f);

        TransformComponent t = remote.getComponent(TransformComponent.class);
        // At t=0.5, rotation.y should be somewhere between 0 and 0.707
        assertTrue(t.rotation.y > 0.01f, "rotation.y should be > 0.01, was " + t.rotation.y);
        assertTrue(t.rotation.y < 0.71f, "rotation.y should be < 0.71, was " + t.rotation.y);
    }

    @Test
    void extrapolatesWhenNoNewData() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        // Snapshot at tick 0 with velocity 10 in X
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 10, 0, 0));

        // Server tick 5 => target = 3, no bracket => extrapolate
        system.setCurrentServerTick(5);
        engine.update(0.1f);

        TransformComponent t = remote.getComponent(TransformComponent.class);
        assertTrue(t.position.x > 0, "position.x should be > 0 after extrapolation");
        assertTrue(ic.extrapolationTimer > 0, "extrapolationTimer should be > 0");
    }

    @Test
    void freezesAfterMaxExtrapolation() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 50, 0, 0, 0, 0, 0, 1, 10, 0, 0));
        // Pre-arm the frozen state
        ic.extrapolationTimer = InterpolationComponent.MAX_EXTRAPOLATION_SECONDS;
        ic.frozen = true;

        TransformComponent t = remote.getComponent(TransformComponent.class);
        t.position.set(55, 0, 0);

        system.setCurrentServerTick(100);
        engine.update(0.05f);

        // Frozen entity must not move
        assertEquals(55f, t.position.x, 1e-3f);
    }

    @Test
    void doesNotCrashWithNoSnapshots() {
        system.setCurrentServerTick(5);
        engine.update(0.05f);
        TransformComponent t = remote.getComponent(TransformComponent.class);
        assertEquals(0f, t.position.x);
    }

    @Test
    void blendFramesDecreaseEachUpdateAndPositionConverges() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        // Two snapshots so bracketing is possible
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0));
        ic.getSnapshotBuffer().add(new EntitySnapshot(4, 40, 0, 0, 0, 0, 0, 1, 0, 0, 0));

        // Simulate mid-blend state (1 frame already elapsed, so blendT will be non-zero)
        ic.blendFramesRemaining = InterpolationComponent.BLEND_FRAMES - 1;
        ic.blendFromX = 99f;
        ic.blendFromY = 0f;
        ic.blendFromZ = 0f;
        ic.blendFromRotX = 0f;
        ic.blendFromRotY = 0f;
        ic.blendFromRotZ = 0f;
        ic.blendFromRotW = 1f;

        system.setCurrentServerTick(4);

        int initialBlend = ic.blendFramesRemaining; // 4
        engine.update(0.05f);

        assertEquals(initialBlend - 1, ic.blendFramesRemaining,
                "blendFramesRemaining should decrease by 1 per update");
        // blendT = 1 - 4/5 = 0.2; lerped target posX ~= 20; position = 99 + (20 - 99) * 0.2 ≈ 83.2
        TransformComponent t = remote.getComponent(TransformComponent.class);
        assertTrue(t.position.x < 99f, "position should have converged toward lerped target");
    }
}
