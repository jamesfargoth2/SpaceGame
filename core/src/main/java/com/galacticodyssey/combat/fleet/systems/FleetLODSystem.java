package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.FleetMemberComponent;
import com.galacticodyssey.combat.fleet.components.FleetMemberComponent.LODTier;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import java.util.function.Supplier;

/**
 * Assigns a distance-based {@link LODTier} to every fleet member once per second.
 *
 * <ul>
 *   <li>{@code FULL}       — within {@value #FULL_RANGE} m: full AI tick</li>
 *   <li>{@code SIMPLIFIED} — within {@value #SIMPLIFIED_RANGE} m: reduced-fidelity AI</li>
 *   <li>{@code ABSTRACT}   — beyond {@value #SIMPLIFIED_RANGE} m: statistics-only simulation</li>
 * </ul>
 */
public class FleetLODSystem extends EntitySystem {
    public static final int PRIORITY = 8;
    private static final float TICK_INTERVAL = 1.0f;

    public static final float FULL_RANGE = 2000f;
    public static final float SIMPLIFIED_RANGE = 10000f;

    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, TransformComponent.class
    ).get();

    private final EventBus eventBus;
    private final Supplier<Vector3> playerPositionSupplier;
    private float accumulator;
    private Engine engine;

    public FleetLODSystem(EventBus eventBus, Supplier<Vector3> playerPositionSupplier) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.playerPositionSupplier = playerPositionSupplier;
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
        accumulator += deltaTime;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        Vector3 playerPos = playerPositionSupplier.get();

        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            TransformComponent tc = TRANSFORM_M.get(e);
            float dist = playerPos.dst(tc.position);

            if (dist < FULL_RANGE) {
                fmc.lodTier = LODTier.FULL;
            } else if (dist < SIMPLIFIED_RANGE) {
                fmc.lodTier = LODTier.SIMPLIFIED;
            } else {
                fmc.lodTier = LODTier.ABSTRACT;
            }
        }
    }
}
