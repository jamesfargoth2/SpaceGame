package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.common.protocol.PlayerInput;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.networking.prediction.PredictedState;
import com.galacticodyssey.networking.prediction.TimestampedInput;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class ClientPredictionSystem extends EntitySystem {

    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
            ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PredictionComponent> predictionMapper =
            ComponentMapper.getFor(PredictionComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper =
            ComponentMapper.getFor(PlayerStateComponent.class);

    private ImmutableArray<Entity> predictedEntities;

    public ClientPredictionSystem() {
        super(0);
    }

    @Override
    public void addedToEngine(Engine engine) {
        predictedEntities = engine.getEntitiesFor(
                Family.all(TransformComponent.class, PlayerInputComponent.class,
                        PredictionComponent.class, PlayerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < predictedEntities.size(); i++) {
            Entity entity = predictedEntities.get(i);
            TransformComponent transform = transformMapper.get(entity);
            PlayerInputComponent localInput = inputMapper.get(entity);
            PredictionComponent prediction = predictionMapper.get(entity);

            int seq = prediction.advanceSequence();

            PlayerInput netInput = captureInput(localInput, seq, stateMapper.get(entity));

            PredictedState state = new PredictedState(
                    transform.position.x, transform.position.y, transform.position.z,
                    transform.rotation.x, transform.rotation.y,
                    transform.rotation.z, transform.rotation.w,
                    0, 0, 0
            );

            prediction.getInputBuffer().add(new TimestampedInput(seq, netInput, state));
        }
    }

    private PlayerInput captureInput(PlayerInputComponent local, int seq,
                                     PlayerStateComponent playerState) {
        PlayerInput pi = new PlayerInput();
        pi.sequenceNumber = seq;
        pi.moveForward = local.moveForward;
        pi.moveStrafe = local.moveStrafe;
        pi.mouseDeltaX = local.mouseDeltaX;
        pi.mouseDeltaY = local.mouseDeltaY;
        pi.jump = local.jumpRequested;
        pi.sprint = local.sprint;
        pi.crouch = local.crouch;
        pi.playerMode = playerState.currentMode.ordinal();
        return pi;
    }
}
