package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.core.EventBus;

/**
 * Slowly repairs ship hull HP for fleets in a post-battle recovery state.
 *
 * <p>Ships belonging to a fleet in {@link FleetState#REGROUPING} or
 * {@link FleetState#PATROL} recover 50% of their missing HP per 60 seconds
 * (a constant fractional rate of {@value #REPAIR_RATE} per second of missing HP).
 *
 * <p>Priority {@value #PRIORITY} — runs after simulation and LOD systems.
 */
public class FleetPostBattleSystem extends EntitySystem {

    public static final int PRIORITY = 15;

    /** Fraction of missing HP restored per second (50% over 60 s). */
    private static final float REPAIR_RATE = 0.5f / 60f;

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, HealthComponent.class
    ).get();

    private final EventBus eventBus;
    private Engine engine;

    public FleetPostBattleSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            if (fmc.fleetEntity == null) continue;

            FleetComponent fc = FLEET_M.get(fmc.fleetEntity);
            if (fc == null) continue;
            if (fc.state != FleetState.REGROUPING && fc.state != FleetState.PATROL) continue;

            HealthComponent hp = HEALTH_M.get(e);
            if (!hp.alive) continue;
            if (hp.currentHP < hp.maxHP) {
                float missing = hp.maxHP - hp.currentHP;
                float repair = missing * REPAIR_RATE * deltaTime;
                hp.currentHP = Math.min(hp.maxHP, hp.currentHP + repair);
            }
        }
    }
}
