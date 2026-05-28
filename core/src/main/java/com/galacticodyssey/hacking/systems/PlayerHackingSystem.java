package com.galacticodyssey.hacking.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.InteractionPromptEvent;
import com.galacticodyssey.hacking.HackableComponent;
import com.galacticodyssey.hacking.HackingController;
import com.galacticodyssey.hacking.HackingStateComponent;
import com.galacticodyssey.hacking.events.HackStartedEvent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;

public class PlayerHackingSystem extends EntitySystem {

    private final EventBus eventBus;

    private final ComponentMapper<HackingStateComponent> hackStateMapper =
        ComponentMapper.getFor(HackingStateComponent.class);
    private final ComponentMapper<HackableComponent> hackableMapper =
        ComponentMapper.getFor(HackableComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PlayerStatsComponent> statsMapper =
        ComponentMapper.getFor(PlayerStatsComponent.class);

    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> hackableEntities;

    private final Vector3 tempVec = new Vector3();

    public PlayerHackingSystem(EventBus eventBus) {
        super(-1); // runs before InteractionSystem (priority 0)
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            HackingStateComponent.class, TransformComponent.class,
            PlayerInputComponent.class, PlayerStatsComponent.class).get());
        hackableEntities = engine.getEntitiesFor(
            Family.all(HackableComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float dt) {
        for (int i = 0; i < playerEntities.size(); i++) {
            processPlayer(playerEntities.get(i), dt);
        }
    }

    private void processPlayer(Entity player, float dt) {
        HackingStateComponent hackState = hackStateMapper.get(player);
        TransformComponent playerTransform = transformMapper.get(player);
        PlayerInputComponent input = inputMapper.get(player);
        PlayerStatsComponent stats = statsMapper.get(player);

        int hackingSkill = stats.pointSkills.get(PointSkill.HACKING, 0);

        // Active hack: tick controller and check for interruptions
        if (hackState.controller != null) {
            HackingController controller = hackState.controller;
            HackableComponent hackable = hackableMapper.get(hackState.currentTarget);

            // Target destroyed / component removed
            if (hackable == null) {
                controller.cancel();
                clearHack(hackState);
                return;
            }

            // Physical hack: cancel if player moved out of range
            if (!hackState.isRemoteHack) {
                TransformComponent targetTransform = transformMapper.get(hackState.currentTarget);
                if (targetTransform == null) {
                    controller.cancel();
                    clearHack(hackState);
                    return;
                }
                float dist = tempVec.set(playerTransform.position).dst(targetTransform.position);
                if (dist > hackable.interactionRange + 0.5f) {
                    controller.cancel();
                    clearHack(hackState);
                    eventBus.publish(new InteractionPromptEvent("", false));
                    return;
                }
            }

            controller.tick(dt);

            if (controller.getState() != HackingController.State.ACTIVE) {
                clearHack(hackState);
            }
            return;
        }

        // No active hack: find nearest in-range hackable
        Entity nearest = null;
        float nearestDist = Float.MAX_VALUE;
        boolean nearestIsRemote = false;

        for (int i = 0; i < hackableEntities.size(); i++) {
            Entity target = hackableEntities.get(i);
            HackableComponent hackable = hackableMapper.get(target);
            TransformComponent targetTransform = transformMapper.get(target);
            float dist = tempVec.set(playerTransform.position).dst(targetTransform.position);

            boolean inRange = false;
            boolean isRemote = false;

            if (dist <= hackable.interactionRange) {
                inRange = true;
            } else if (hackingSkill >= 5 && !hackable.requiresPhysicalAccess) {
                float remoteRange = 10f + (hackingSkill - 5) * 2f;
                if (dist <= remoteRange) {
                    inRange = true;
                    isRemote = true;
                }
            }

            if (inRange && dist < nearestDist) {
                nearest = target;
                nearestDist = dist;
                nearestIsRemote = isRemote;
            }
        }

        if (nearest == null) {
            eventBus.publish(new InteractionPromptEvent("", false));
            return;
        }

        HackableComponent hackable = hackableMapper.get(nearest);

        if (hackable.lockoutTimer > 0f) {
            eventBus.publish(new InteractionPromptEvent(
                String.format("[LOCKED OUT: %.0fs]", hackable.lockoutTimer), true));
            return;
        }

        if (hackingSkill < hackable.difficulty) {
            eventBus.publish(new InteractionPromptEvent(
                String.format("[HACKING %d REQUIRED]", hackable.difficulty), true));
            return;
        }

        String remoteLabel = nearestIsRemote ? " [REMOTE]" : "";
        eventBus.publish(new InteractionPromptEvent(
            String.format("[F] Hack%s  Rank %d", remoteLabel, hackable.difficulty), true));

        if (input.interactPressed) {
            input.interactPressed = false; // consume so InteractionSystem doesn't also fire
            HackingController controller = new HackingController(
                eventBus, player, nearest, hackable, hackingSkill, nearestIsRemote);
            hackState.controller = controller;
            hackState.currentTarget = nearest;
            hackState.isRemoteHack = nearestIsRemote;
            eventBus.publish(new HackStartedEvent(player, nearest));
        }
    }

    private void clearHack(HackingStateComponent hackState) {
        hackState.controller = null;
        hackState.currentTarget = null;
        hackState.isRemoteHack = false;
    }
}
