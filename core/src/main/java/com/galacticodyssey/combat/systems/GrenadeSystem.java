package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.components.GrenadeComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

public class GrenadeSystem extends IteratingSystem {
    public static final int PRIORITY = 8;

    private final EventBus eventBus;
    private final Array<Entity> pendingRemovals = new Array<>();

    public GrenadeSystem(EventBus eventBus) {
        super(Family.all(GrenadeComponent.class, ProjectileComponent.class,
                TransformComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        GrenadeComponent gc = GrenadeComponent.MAPPER.get(entity);
        if (gc.detonated) return;

        if (gc.fuseType == FuseType.TIMED) {
            gc.fuseTimer -= deltaTime;
        }

        if (gc.fuseTimer <= 0f) {
            detonate(entity, gc);
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        for (Entity e : pendingRemovals) {
            getEngine().removeEntity(e);
        }
        pendingRemovals.clear();
    }

    private void detonate(Entity entity, GrenadeComponent gc) {
        gc.detonated = true;
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        ProjectileComponent proj = entity.getComponent(ProjectileComponent.class);

        eventBus.publish(new DetonationEvent(
                proj.owner,
                transform.position,
                gc.damage,
                DamageType.EXPLOSIVE,
                gc.blastRadius,
                gc.blastFraction,
                gc.thermalFraction,
                gc.fragmentFraction,
                gc.isDirectional
        ));

        pendingRemovals.add(entity);
    }
}
