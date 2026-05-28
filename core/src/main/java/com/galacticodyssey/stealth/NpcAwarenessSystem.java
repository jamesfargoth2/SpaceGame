package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.NoiseBurstEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.stealth.events.StealthHUDUpdateEvent;

/**
 * Ticks NPC awareness states each frame.
 *
 * <p>Iterates every entity with {@link AwarenessStateComponent}, {@link PerceptionComponent},
 * and {@link TransformComponent} that is <em>not</em> a ship ({@link ShipDataComponent} excluded).
 * For each NPC it:
 * <ol>
 *   <li>Computes a detection contribution from the player's {@link SignatureComponent}.</li>
 *   <li>Lerps the {@code detectionAccumulator} toward the contribution value.</li>
 *   <li>Advances the FSM ({@link AwarenessState}) accordingly.</li>
 *   <li>Publishes {@link AwarenessChangedEvent} on state transitions.</li>
 * </ol>
 * Also subscribes to {@link NoiseBurstEvent} to spike accumulator values.
 */
public final class NpcAwarenessSystem extends EntitySystem {

    // Package-private (not final) so tests can read them by name
    static float RISE_RATE        = 2.0f;
    static float DECAY_RATE       = 0.8f;
    static float DECAY_FLOOR      = 0.05f;
    static float SUSPICION_LIMIT  = 4.0f;
    static float CURIOUS_COOLDOWN = 2.0f;
    static float SEARCH_DURATION  = 15.0f;

    private static final float HUD_INTERVAL = 0.25f; // 4 Hz

    private final EventBus eventBus;
    private final LineOfSightQuery los;

    private static final ComponentMapper<AwarenessStateComponent> AWARE_M =
        ComponentMapper.getFor(AwarenessStateComponent.class);
    private static final ComponentMapper<PerceptionComponent> PERC_M =
        ComponentMapper.getFor(PerceptionComponent.class);
    private static final ComponentMapper<TransformComponent> XFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<SignatureComponent> SIG_M =
        ComponentMapper.getFor(SignatureComponent.class);

    /** Ground NPCs only — ships are excluded via ShipDataComponent. */
    private static final Family NPC_FAMILY = Family
        .all(AwarenessStateComponent.class, PerceptionComponent.class, TransformComponent.class)
        .exclude(ShipDataComponent.class)
        .get();

    private static final Family PLAYER_FAMILY = Family
        .all(PlayerTagComponent.class, SignatureComponent.class, TransformComponent.class)
        .get();

    private ImmutableArray<Entity> npcEntities;
    private ImmutableArray<Entity> playerEntities;
    private float hudTimer = 0f;

    // Scratch vectors — system runs on the game thread only (no concurrent access)
    private final Vector3 scratchDir = new Vector3();
    private final Vector3 scratchFwd = new Vector3();

    public NpcAwarenessSystem(EventBus eventBus, LineOfSightQuery los) {
        this.eventBus = eventBus;
        this.los = los;
        eventBus.subscribe(NoiseBurstEvent.class, this::onNoiseBurst);
    }

    @Override
    public void addedToEngine(Engine engine) {
        npcEntities    = engine.getEntitiesFor(NPC_FAMILY);
        playerEntities = engine.getEntitiesFor(PLAYER_FAMILY);
    }

    @Override
    public void update(float dt) {
        if (playerEntities == null || playerEntities.size() == 0) return;

        Entity player    = playerEntities.first();
        SignatureComponent sig = SIG_M.get(player);
        Vector3 playerPos = XFORM_M.get(player).position;

        AwarenessState highest = AwarenessState.UNAWARE;
        float nearest = Float.MAX_VALUE;

        for (Entity npc : npcEntities) {
            AwarenessStateComponent state = AWARE_M.get(npc);
            PerceptionComponent     perc  = PERC_M.get(npc);
            TransformComponent      xform = XFORM_M.get(npc);

            float contrib = computeContribution(perc, xform, playerPos, sig);
            float target  = contrib;
            float rate    = contrib > state.detectionAccumulator ? RISE_RATE : DECAY_RATE;
            state.detectionAccumulator = MathUtils.lerp(state.detectionAccumulator, target, rate * dt);

            boolean visible = contrib > 0f;
            AwarenessState before = state.state;
            tickFsm(state, perc, playerPos, visible, dt);

            if (state.state != before) {
                eventBus.publish(new AwarenessChangedEvent(
                    Integer.toHexString(System.identityHashCode(npc)),
                    before,
                    state.state,
                    state.lastKnownPosition));
            }

            if (state.state.ordinal() > highest.ordinal()) highest = state.state;
            float d = playerPos.dst(xform.position);
            if (d < nearest) nearest = d;
        }

        hudTimer += dt;
        if (hudTimer >= HUD_INTERVAL) {
            hudTimer = 0f;
            eventBus.publish(new StealthHUDUpdateEvent(
                highest,
                nearest == Float.MAX_VALUE ? -1f : nearest));
        }
    }

    // -------------------------------------------------------------------------
    // Package-private methods — called directly by NpcAwarenessSystemTest
    // (same package, no Ashley Engine required)
    // -------------------------------------------------------------------------

    /**
     * Computes the raw detection contribution [0..∞) for the given NPC→player pair.
     *
     * <ul>
     *   <li>Hearing: omnidirectional, no LoS check, linear falloff over {@code hearingRange}.</li>
     *   <li>Vision: cone + LoS check, linear falloff over {@code viewRange}.</li>
     * </ul>
     */
    float computeContribution(PerceptionComponent perc, TransformComponent npcXform,
                              Vector3 playerPos, SignatureComponent sig) {
        float dist = npcXform.position.dst(playerPos);
        float contrib = 0f;

        // Hearing — no LoS required
        if (perc.hearingRange > 0f && dist < perc.hearingRange) {
            float falloff = 1f - (dist / perc.hearingRange);
            contrib += sig.noiseLevel * falloff;
        }

        // Vision — requires LoS and view cone
        if (perc.viewRange > 0f && dist < perc.viewRange
                && inViewCone(npcXform, playerPos, perc.viewAngle)) {
            if (los.hasLoS(npcXform.position, playerPos)) {
                float falloff = 1f - (dist / perc.viewRange);
                contrib += sig.computeOnFootScore() * falloff;
            }
        }

        return contrib;
    }

    /**
     * Advances the NPC FSM one tick.
     *
     * @param playerVisible true if {@code computeContribution} returned > 0 this frame.
     * @param dt            elapsed seconds.
     */
    void tickFsm(AwarenessStateComponent state, PerceptionComponent perc,
                 Vector3 playerPos, boolean playerVisible, float dt) {
        switch (state.state) {
            case UNAWARE -> {
                if (state.detectionAccumulator > perc.curiousThreshold) {
                    transition(state, AwarenessState.CURIOUS);
                }
            }
            case CURIOUS -> {
                state.suspicionTimer += dt;
                if (state.detectionAccumulator > perc.alertThreshold
                        || state.suspicionTimer > SUSPICION_LIMIT) {
                    state.lastKnownPosition.set(playerPos);
                    transition(state, AwarenessState.ALERTED);
                } else if (state.detectionAccumulator < DECAY_FLOOR
                        && state.suspicionTimer > CURIOUS_COOLDOWN) {
                    transition(state, AwarenessState.UNAWARE);
                }
            }
            case ALERTED -> {
                // Keep last-known position fresh while visible
                if (playerVisible) {
                    state.lastKnownPosition.set(playerPos);
                } else {
                    transition(state, AwarenessState.SEARCHING);
                }
            }
            case SEARCHING -> {
                state.searchTimer += dt;
                if (playerVisible && state.detectionAccumulator > perc.curiousThreshold) {
                    state.lastKnownPosition.set(playerPos);
                    transition(state, AwarenessState.ALERTED);
                } else if (state.searchTimer > SEARCH_DURATION) {
                    transition(state, AwarenessState.UNAWARE);
                }
            }
        }
    }

    /**
     * Adds a noise-burst spike to {@code state.detectionAccumulator} when the NPC is within radius.
     * Falloff is linear; accumulator is clamped to 1.0.
     */
    void applyNoiseBurst(Vector3 npcPos, AwarenessStateComponent state, NoiseBurstEvent e) {
        float dist = npcPos.dst(e.x, e.y, e.z);
        if (dist < e.radius) {
            float spike = e.intensity * (1f - dist / e.radius);
            state.detectionAccumulator = Math.min(1f, state.detectionAccumulator + spike);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Called by EventBus subscription when a noise burst fires in the world. */
    private void onNoiseBurst(NoiseBurstEvent e) {
        if (npcEntities == null) return;
        for (Entity npc : npcEntities) {
            applyNoiseBurst(XFORM_M.get(npc).position, AWARE_M.get(npc), e);
        }
    }

    /** Resets per-state timers and changes {@code state.state}. */
    private void transition(AwarenessStateComponent state, AwarenessState next) {
        state.state = next;
        switch (next) {
            case CURIOUS   -> state.suspicionTimer = 0f;
            case SEARCHING -> state.searchTimer = 0f;
            case UNAWARE   -> { state.suspicionTimer = 0f; state.searchTimer = 0f; }
            default        -> { /* ALERTED has no timer to reset */ }
        }
    }

    /**
     * Returns true when {@code playerPos} lies inside the NPC's view cone.
     *
     * <p>Uses scratch vectors to avoid allocation.  {@code Quaternion.transform(Vector3)}
     * mutates the passed vector in-place, so we copy the identity forward (0,0,1) into
     * {@code scratchFwd} before rotating.
     */
    private boolean inViewCone(TransformComponent npcXform, Vector3 playerPos, float viewAngleDegrees) {
        scratchDir.set(playerPos).sub(npcXform.position).nor();
        scratchFwd.set(0f, 0f, 1f);
        npcXform.rotation.transform(scratchFwd); // rotates scratchFwd in-place
        return scratchFwd.dot(scratchDir) >= MathUtils.cosDeg(viewAngleDegrees * 0.5f);
    }
}
