package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;

public class CreatureBehaviorSystem extends IteratingSystem {

    private final ComponentMapper<CreatureBehaviorComponent> behaviorMapper =
        ComponentMapper.getFor(CreatureBehaviorComponent.class);
    private final ComponentMapper<CreatureDrivesComponent> drivesMapper =
        ComponentMapper.getFor(CreatureDrivesComponent.class);
    private final ComponentMapper<TransformComponent> txMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final Vector3 playerPosition = new Vector3();
    private boolean hasPlayer = false;

    public CreatureBehaviorSystem(int priority) {
        super(Family.all(CreatureBehaviorComponent.class, CreatureDrivesComponent.class,
                         TransformComponent.class).get(), priority);
    }

    public void setPlayerPosition(Vector3 pos) {
        playerPosition.set(pos);
        hasPlayer = true;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CreatureBehaviorComponent beh = behaviorMapper.get(entity);
        CreatureDrivesComponent drives = drivesMapper.get(entity);
        TransformComponent tx = txMapper.get(entity);
        Vector3 threat = hasPlayer ? playerPosition : null;
        updateEntity(entity, beh, drives, tx, deltaTime, threat, -1f);
    }

    public static void updateEntity(Entity entity, CreatureBehaviorComponent beh,
                                     CreatureDrivesComponent drives, TransformComponent tx,
                                     float dt, Vector3 threatPos, float timeOfDay) {
        if (beh.stateMachine == null) return;

        beh.stateTimer += dt;
        CreatureState current = beh.stateMachine.getCurrentState();

        float threatDist = threatPos != null ? tx.position.dst(threatPos) : Float.MAX_VALUE;
        float effectiveFlee = beh.fleeRadius;
        if (drives.fear > 0.5f) effectiveFlee *= 1.5f;

        switch (current) {
            case IDLE:
                drives.moving = false;
                drives.sprinting = false;
                if (threatDist < effectiveFlee) {
                    beh.stateMachine.changeState(CreatureState.FLEE);
                    drives.fear = Math.min(1f, drives.fear + 1f);
                } else if (threatDist < beh.detectionRadius) {
                    beh.stateMachine.changeState(CreatureState.ALERT);
                } else if (beh.stateTimer > beh.idleDuration) {
                    beh.stateMachine.changeState(CreatureState.WANDER);
                    beh.stateTimer = 0f;
                }
                break;

            case WANDER:
                drives.moving = true;
                drives.sprinting = false;
                if (threatDist < effectiveFlee) {
                    beh.stateMachine.changeState(CreatureState.FLEE);
                    drives.fear = Math.min(1f, drives.fear + 1f);
                } else if (threatDist < beh.detectionRadius) {
                    beh.stateMachine.changeState(CreatureState.ALERT);
                } else if (beh.stateTimer > 8f) {
                    beh.stateMachine.changeState(CreatureState.IDLE);
                    beh.stateTimer = 0f;
                }
                break;

            case ALERT:
                drives.moving = false;
                drives.sprinting = false;
                if (threatDist < effectiveFlee) {
                    beh.stateMachine.changeState(CreatureState.FLEE);
                    drives.fear = Math.min(1f, drives.fear + 1f);
                } else if (threatDist > beh.detectionRadius) {
                    beh.stateMachine.changeState(CreatureState.IDLE);
                    beh.stateTimer = 0f;
                }
                break;

            case FLEE:
                drives.moving = true;
                drives.sprinting = true;
                if (threatDist > beh.safeDistance) {
                    beh.stateMachine.changeState(CreatureState.ALERT);
                    beh.stateTimer = 0f;
                } else if (drives.energy <= 0f) {
                    beh.stateMachine.changeState(CreatureState.IDLE);
                    beh.stateTimer = 0f;
                }
                break;

            case HUNT:
                drives.moving = true;
                drives.sprinting = false;
                break;

            case ATTACK:
                drives.moving = false;
                drives.sprinting = false;
                break;

            case FEED:
                drives.moving = false;
                drives.sprinting = false;
                if (drives.hunger < 0.2f) {
                    beh.stateMachine.changeState(CreatureState.IDLE);
                    beh.stateTimer = 0f;
                }
                break;
        }
    }
}
