package com.galacticodyssey.core.tether;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;

/**
 * Controls winch mechanics: reels tether in or out by adjusting its rest length,
 * and computes the motor load (power) required to reel against tension.
 */
public class WinchSystem extends EntitySystem {

    public static final int PRIORITY = 7;

    private static final ComponentMapper<WinchComponent> WINCH_M =
        ComponentMapper.getFor(WinchComponent.class);
    private static final ComponentMapper<TetherConstraintComponent> TETHER_M =
        ComponentMapper.getFor(TetherConstraintComponent.class);

    private static final Family WINCH_FAMILY =
        Family.all(WinchComponent.class, TetherConstraintComponent.class).get();

    private final EventBus eventBus;
    private ImmutableArray<Entity> winchEntities;

    public WinchSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        winchEntities = engine.getEntitiesFor(WINCH_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        winchEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (winchEntities == null) return;

        for (int i = 0, n = winchEntities.size(); i < n; i++) {
            Entity entity = winchEntities.get(i);
            WinchComponent winch = WINCH_M.get(entity);
            TetherConstraintComponent tether = TETHER_M.get(entity);
            processWinch(winch, tether, deltaTime);
        }
    }

    private void processWinch(WinchComponent winch, TetherConstraintComponent tether, float dt) {
        if (tether.isBroken) return;
        if (winch.reelRate == 0f) {
            winch.motorLoad = 0f;
            return;
        }

        // Adjust rest length: positive reelRate = reel in (shorten), negative = reel out (extend)
        tether.restLength -= winch.reelRate * dt;
        tether.restLength = Math.max(winch.minLength, Math.min(winch.maxLength, tether.restLength));

        // Also keep maxLength in sync when reeling in so the cable doesn't go slack
        // during reel-in operations
        if (tether.maxLength < tether.restLength) {
            tether.maxLength = tether.restLength;
        }

        // Motor load (watts) = tension * reel speed
        winch.motorLoad = tether.currentTension * Math.abs(winch.reelRate);
    }
}
