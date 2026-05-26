package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.CrosshairComponent;

import java.util.ArrayList;
import java.util.List;

public class CrosshairSystem extends EntitySystem {
    private static final int PRIORITY = 15;
    private final List<DamageDealtEvent> pendingHits = new ArrayList<>();
    private final List<EntityKilledEvent> pendingKills = new ArrayList<>();
    private ImmutableArray<Entity> entities;

    public CrosshairSystem(EventBus eventBus) {
        super(PRIORITY);
        eventBus.subscribe(DamageDealtEvent.class, pendingHits::add);
        eventBus.subscribe(EntityKilledEvent.class, pendingKills::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(CrosshairComponent.class, RangedWeaponComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            CrosshairComponent ch = entity.getComponent(CrosshairComponent.class);

            ch.currentBloom = Math.max(0f, ch.currentBloom - ch.bloomDecayRate * deltaTime);
            ch.hitMarkerTimer = Math.max(0f, ch.hitMarkerTimer - deltaTime);
            ch.killConfirmTimer = Math.max(0f, ch.killConfirmTimer - deltaTime);

            for (DamageDealtEvent hit : pendingHits) {
                if (hit.attacker == entity) {
                    ch.hitMarkerTimer = ch.hitMarkerDuration;
                }
            }
            for (EntityKilledEvent kill : pendingKills) {
                if (kill.killer == entity) {
                    ch.killConfirmTimer = ch.killConfirmDuration;
                }
            }
        }
        pendingHits.clear();
        pendingKills.clear();
    }

    public void addBloom(Entity entity, float amount) {
        CrosshairComponent ch = entity.getComponent(CrosshairComponent.class);
        if (ch != null) {
            ch.currentBloom += amount;
        }
    }
}
