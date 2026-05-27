package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerModelComponent;
import com.galacticodyssey.player.components.PlayerModelComponent.AnimState;

public class PlayerAnimationSystem extends IteratingSystem {

    private static final float CROSSFADE_DURATION = 0.2f;
    private static final float WALK_SPEED_THRESHOLD = 0.5f;
    private static final float RUN_SPEED_THRESHOLD = 5.0f;

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerModelComponent> modelMapper =
        ComponentMapper.getFor(PlayerModelComponent.class);
    private final ComponentMapper<MovementStateComponent> movementMapper =
        ComponentMapper.getFor(MovementStateComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);

    private final Quaternion tmpQuat = new Quaternion();

    public PlayerAnimationSystem() {
        super(Family.all(
            TransformComponent.class,
            PlayerModelComponent.class,
            MovementStateComponent.class,
            FPSCameraComponent.class
        ).get(), 5);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = transformMapper.get(entity);
        PlayerModelComponent model = modelMapper.get(entity);
        MovementStateComponent movement = movementMapper.get(entity);
        FPSCameraComponent cam = cameraMapper.get(entity);

        AnimState desired = resolveAnimState(movement);

        if (desired != model.currentAnim) {
            model.currentAnim = desired;
            if (model.animationController != null) {
                int loopCount = (desired == AnimState.JUMP) ? 1 : -1;
                model.animationController.animate(desired.id, loopCount, 1f, null, CROSSFADE_DURATION);
            }
        }

        if (model.modelInstance == null) return;

        if (model.animationController != null) {
            model.animationController.update(deltaTime);
        }

        tmpQuat.setFromAxis(0, 1, 0, -cam.yawAngle);
        model.modelInstance.transform.idt();
        model.modelInstance.transform.translate(
            transform.position.x,
            transform.position.y + model.modelYOffset,
            transform.position.z);
        model.modelInstance.transform.rotate(tmpQuat);
    }

    private AnimState resolveAnimState(MovementStateComponent movement) {
        if (!movement.isGrounded) {
            return movement.fallVelocity > 0 ? AnimState.JUMP : AnimState.FALL;
        }
        if (movement.isCrouching) {
            return movement.currentSpeed > WALK_SPEED_THRESHOLD
                ? AnimState.CROUCH_WALK : AnimState.CROUCH_IDLE;
        }
        if (movement.currentSpeed > RUN_SPEED_THRESHOLD) {
            return AnimState.RUN;
        }
        if (movement.currentSpeed > WALK_SPEED_THRESHOLD) {
            return AnimState.WALK;
        }
        return AnimState.IDLE;
    }
}
