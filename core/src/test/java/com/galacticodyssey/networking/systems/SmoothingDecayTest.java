package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmoothingDecayTest {

    @Test
    void offsetDecaysToNearZeroAfterSmoothingFrames() {
        Engine engine = new Engine();
        ReconciliationSystem system = new ReconciliationSystem();
        engine.addSystem(system);

        Entity player = new Entity();
        player.add(new TransformComponent());
        player.add(new NetworkIdComponent(1));
        PredictionComponent pred = new PredictionComponent();
        pred.smoothingOffsetX = 2.0f;
        pred.smoothingOffsetY = -1.5f;
        pred.smoothingOffsetZ = 0.8f;
        pred.smoothingFramesRemaining = PredictionComponent.SMOOTHING_FRAMES;
        player.add(pred);
        engine.addEntity(player);

        for (int i = 0; i < PredictionComponent.SMOOTHING_FRAMES; i++) {
            engine.update(0.016f);
        }

        assertEquals(0, pred.smoothingFramesRemaining);
        assertEquals(0f, pred.smoothingOffsetX, 0.01f);
        assertEquals(0f, pred.smoothingOffsetY, 0.01f);
        assertEquals(0f, pred.smoothingOffsetZ, 0.01f);
    }
}
