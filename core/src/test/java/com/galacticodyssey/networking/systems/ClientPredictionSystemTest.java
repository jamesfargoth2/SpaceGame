package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientPredictionSystemTest {

    private Engine engine;
    private ClientPredictionSystem system;
    private Entity player;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        system = new ClientPredictionSystem();
        engine.addSystem(system);

        player = new Entity();
        player.add(new TransformComponent());
        player.add(new PlayerInputComponent());
        player.add(new PredictionComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerStateComponent.PlayerMode.ON_FOOT_EXTERIOR;
        player.add(state);
        engine.addEntity(player);
    }

    @Test
    void capturesInputIntoBuffer() {
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        input.moveForward = 1.0f;
        input.sprint = true;

        engine.update(0.05f);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        assertEquals(1, pred.getInputBuffer().size());
        assertEquals(0, pred.getInputBuffer().get(0).sequenceNumber);
    }

    @Test
    void sequenceIncrementsEachFrame() {
        engine.update(0.05f);
        engine.update(0.05f);
        engine.update(0.05f);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        assertEquals(3, pred.getInputBuffer().size());
        assertEquals(3, pred.nextSequenceNumber);
    }

    @Test
    void capturesPredictedPosition() {
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(10, 20, 30);

        engine.update(0.05f);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        var entry = pred.getInputBuffer().get(0);
        assertNotNull(entry.predictedState);
        assertEquals(10f, entry.predictedState.posX, 1e-6f);
        assertEquals(20f, entry.predictedState.posY, 1e-6f);
        assertEquals(30f, entry.predictedState.posZ, 1e-6f);
    }

    @Test
    void capturesFPSInputFields() {
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        input.moveForward = 0.8f;
        input.moveStrafe = -0.3f;
        input.sprint = true;
        input.jumpRequested = true;

        engine.update(0.05f);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        var captured = pred.getInputBuffer().get(0).input;
        assertEquals(0.8f, captured.moveForward, 1e-6f);
        assertEquals(-0.3f, captured.moveStrafe, 1e-6f);
        assertTrue(captured.sprint);
        assertTrue(captured.jump);
    }

    @Test
    void doesNothingWithoutPredictionComponent() {
        Entity npc = new Entity();
        npc.add(new TransformComponent());
        npc.add(new PlayerInputComponent());
        npc.add(new PlayerStateComponent());
        engine.addEntity(npc);

        engine.update(0.05f);
        // Should not throw — NPC has no PredictionComponent so it's excluded from the Family
    }
}
