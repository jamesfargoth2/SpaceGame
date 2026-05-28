package com.galacticodyssey.hacking.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.hacking.HackableComponent;
import com.galacticodyssey.hacking.events.DataAccessedEvent;
import com.galacticodyssey.hacking.events.HackEffectExpiredEvent;
import com.galacticodyssey.hacking.events.HackFailedEvent;
import com.galacticodyssey.hacking.events.HackSucceededEvent;
import com.galacticodyssey.hacking.events.SecurityAlarmEvent;

public class HackingSystem extends IteratingSystem {

    private final ComponentMapper<HackableComponent> hackableMapper =
        ComponentMapper.getFor(HackableComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final EventBus eventBus;

    public HackingSystem(int priority, EventBus eventBus) {
        super(Family.all(HackableComponent.class).get(), priority);
        this.eventBus = eventBus;

        eventBus.subscribe(HackFailedEvent.class, this::onHackFailed);
        eventBus.subscribe(HackSucceededEvent.class, this::onHackSucceeded);
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        HackableComponent hackable = hackableMapper.get(entity);

        if (hackable.lockoutTimer > 0f) {
            hackable.lockoutTimer = Math.max(0f, hackable.lockoutTimer - dt);
        }

        if (hackable.effectTimer > 0f) {
            hackable.effectTimer -= dt;
            if (hackable.effectTimer <= 0f) {
                hackable.effectTimer = 0f;
                eventBus.publish(new HackEffectExpiredEvent(entity, hackable.effect));
            }
        }
    }

    private void onHackFailed(HackFailedEvent event) {
        HackableComponent hackable = hackableMapper.get(event.target);
        if (hackable == null) return;
        hackable.lockoutTimer = hackable.lockoutDuration;

        TransformComponent transform = transformMapper.get(event.target);
        Vector3 location = transform != null ? transform.position : new Vector3();
        eventBus.publish(new SecurityAlarmEvent(location, 50f));
    }

    private void onHackSucceeded(HackSucceededEvent event) {
        HackableComponent hackable = hackableMapper.get(event.target);
        if (hackable == null) return;

        switch (event.effect) {
            case UNLOCK:
                hackable.unlocked = true;
                break;
            case ACCESS_DATA:
                eventBus.publish(new DataAccessedEvent(event.player, event.target, hackable.terminalId));
                break;
            case DISABLE_CAMERA:
                hackable.effectTimer = 60f; break;
            case DISABLE_TURRET:
                hackable.effectTimer = 45f; break;
            case DISABLE_ENGINES:
                hackable.effectTimer = 30f; break;
            case DISABLE_WEAPONS:
                hackable.effectTimer = 30f; break;
            case DISABLE_SHIELDS:
                hackable.effectTimer = 20f; break;
            case SUBVERT_DRONE:
                hackable.effectTimer = 90f; break;
        }
    }
}
