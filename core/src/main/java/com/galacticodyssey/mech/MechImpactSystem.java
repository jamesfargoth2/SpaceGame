package com.galacticodyssey.mech;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mech.components.MechPhysicsComponent;
import com.galacticodyssey.mech.events.MechImpactEvent;

/**
 * Utility class (not an EntitySystem) that computes fall damage when a mech
 * lands after being airborne. Safe landing speed scales with local gravity so
 * low-gravity environments are more forgiving. Damage is quadratic with excess
 * impact velocity and scaled by the mech's {@code impactDamageFactor}.
 *
 * <p>Call {@link #onLanding} from a ground-contact system or collision callback
 * when the mech transitions from airborne to grounded.</p>
 */
public final class MechImpactSystem {

    private static final float STANDARD_GRAVITY = 9.81f;
    private static final float BASE_SAFE_LANDING_SPEED = 4f; // m/s at 1g

    private final EventBus eventBus;

    public MechImpactSystem(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Evaluates a mech landing and publishes a {@link MechImpactEvent} if the
     * impact speed exceeds the safe threshold.
     *
     * @param mech             the mech's physics component (position used for event)
     * @param impactVelocity   speed along gravity axis at moment of contact (positive = downward)
     * @param gravityMagnitude local gravitational acceleration magnitude (m/s^2)
     */
    public void onLanding(MechPhysicsComponent mech, float impactVelocity,
                          float gravityMagnitude) {
        // Scale safe speed with gravity: lower gravity = gentler impacts
        float safeLandingSpeed = BASE_SAFE_LANDING_SPEED * (gravityMagnitude / STANDARD_GRAVITY);
        float excess = impactVelocity - safeLandingSpeed;

        if (excess <= 0f) return;

        // Quadratic damage: double the excess speed = 4x the damage
        float rawDamage = excess * excess * mech.impactDamageFactor;

        eventBus.publish(new MechImpactEvent(mech.position, impactVelocity, rawDamage));
    }
}
