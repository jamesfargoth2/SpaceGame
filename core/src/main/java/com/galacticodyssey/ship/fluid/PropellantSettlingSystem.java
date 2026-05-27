package com.galacticodyssey.ship.fluid;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.fluid.events.UllageBurnEvent;

/**
 * Utility (not an EntitySystem) for checking whether propellant has settled
 * toward the engine intake and for requesting ullage burns.
 *
 * <p>In zero-g, liquid propellant floats freely inside the tank and may not
 * cover the engine feed line. A short ullage burn pushes the ship forward,
 * using inertia to settle the liquid toward the intake.</p>
 */
public class PropellantSettlingSystem {

    private final EventBus eventBus;

    public PropellantSettlingSystem(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Returns true when the fluid centre-of-mass is close enough to the
     * expected settled position along {@code settleDir} that the engine
     * feed is guaranteed to be submerged.
     *
     * @param tank       the slosh tank to check
     * @param settleDir  direction toward the engine intake in local space
     *                   (does not need to be normalised)
     * @param threshold  maximum acceptable distance from settled position (m)
     * @return true if the propellant is settled
     */
    public boolean isSettled(SloshTankComponent tank, Vector3 settleDir,
                             float threshold) {
        Vector3 expected = Pools.obtain(Vector3.class);
        try {
            expected.set(settleDir).nor()
                .scl(tank.tankRadius * tank.fillFraction());
            return tank.fluidLocalPos.dst(expected) < threshold;
        } finally {
            Pools.free(expected);
        }
    }

    /**
     * Requests a small ullage burn to settle propellant before main engine
     * ignition. Applies a short impulse in the ship's forward direction
     * and publishes an {@link UllageBurnEvent}.
     *
     * @param ship          the ship entity (must have PhysicsBodyComponent and
     *                      TransformComponent)
     * @param ullageThrust  thrust magnitude of the ullage motors (N)
     */
    public void requestUllageBurn(Entity ship, float ullageThrust) {
        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        TransformComponent transform = ship.getComponent(TransformComponent.class);

        if (physics == null || physics.body == null || transform == null) return;

        Vector3 burnDir = Pools.obtain(Vector3.class);
        try {
            // Forward is +Z in local space; rotate to world
            burnDir.set(0f, 0f, 1f).mul(transform.rotation).scl(ullageThrust);

            physics.body.applyCentralForce(burnDir);
        } finally {
            Pools.free(burnDir);
        }

        eventBus.publish(new UllageBurnEvent(ship));
    }
}
