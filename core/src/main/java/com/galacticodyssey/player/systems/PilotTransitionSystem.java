package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.PlayerStartPilotingEvent;
import com.galacticodyssey.core.events.PlayerStopPilotingEvent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ui.events.CockpitHUDShowEvent;
import com.galacticodyssey.ui.events.CockpitHUDHideEvent;

/**
 * Listens for {@link PlayerStartPilotingEvent} and {@link PlayerStopPilotingEvent} and
 * orchestrates the enter/exit pilot-seat transition:
 * <ul>
 *   <li>Adds/removes {@link ShipFlightInputComponent} on the player entity.</li>
 *   <li>Publishes {@link CockpitHUDShowEvent} / {@link CockpitHUDHideEvent} to the event bus.</li>
 *   <li>Tracks a short transition timer so callers can query {@link #isTransitioning()}.</li>
 * </ul>
 * Priority 2 — runs after input (0) and interaction (0), before movement/flight (1 is reserved
 * for movement; transition state needs to be committed before those systems read it).
 */
public class PilotTransitionSystem extends EntitySystem {

    private static final float TRANSITION_DURATION = 0.5f;

    private final EventBus eventBus;
    private boolean transitioning;
    private float transitionTimer;

    // Cached vectors for future camera lerp support — not yet used but allocated once.
    @SuppressWarnings("unused")
    private final Vector3 startPos = new Vector3();
    @SuppressWarnings("unused")
    private final Vector3 endPos = new Vector3();
    @SuppressWarnings("unused")
    private final Quaternion startRot = new Quaternion();
    @SuppressWarnings("unused")
    private final Quaternion endRot = new Quaternion();

    public PilotTransitionSystem(EventBus eventBus) {
        super(2);
        this.eventBus = eventBus;
        eventBus.subscribe(PlayerStartPilotingEvent.class, this::onStartPiloting);
        eventBus.subscribe(PlayerStopPilotingEvent.class, this::onStopPiloting);
    }

    // -----------------------------------------------------------------------
    // Event handlers
    // -----------------------------------------------------------------------

    private void onStartPiloting(PlayerStartPilotingEvent event) {
        Entity player = event.player;
        if (player.getComponent(ShipFlightInputComponent.class) == null) {
            player.add(new ShipFlightInputComponent());
        }
        eventBus.publish(new CockpitHUDShowEvent(event.ship));
        beginTransition();
    }

    private void onStopPiloting(PlayerStopPilotingEvent event) {
        event.player.remove(ShipFlightInputComponent.class);
        eventBus.publish(new CockpitHUDHideEvent());
        beginTransition();
    }

    // -----------------------------------------------------------------------
    // EntitySystem lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        if (!transitioning) return;
        transitionTimer += deltaTime;
        if (transitionTimer >= TRANSITION_DURATION) {
            transitioning = false;
            transitionTimer = 0f;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void beginTransition() {
        transitioning = true;
        transitionTimer = 0f;
    }

    /** Returns {@code true} while the camera/animation transition is still in progress. */
    public boolean isTransitioning() {
        return transitioning;
    }
}
