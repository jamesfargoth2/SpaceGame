package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.ActiveScanEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.stealth.events.PlayerDetectedEvent;

/**
 * Passive ship-to-ship detection system.
 *
 * <p>Iterates all patrol ship entities (those with {@link AwarenessStateComponent},
 * {@link PerceptionComponent}, {@link TransformComponent}, and {@link ShipDataComponent})
 * and ticks their detection accumulators and FSM states based on the player's
 * {@link SignatureComponent}.
 *
 * <p>Also subscribes to {@link ActiveScanEvent} to handle ping-based detection.
 */
public final class ShipDetectionSystem extends EntitySystem {

    // Package-private (not final) so tests can override thresholds by name
    static float FALLOFF_K       = 0.0001f;
    static float RISE_RATE       = 2.0f;
    static float DECAY_RATE      = 0.8f;
    static float SUSPICION_LIMIT = 8.0f;
    static float SEARCH_DURATION = 30.0f;

    private final EventBus eventBus;

    private static final ComponentMapper<AwarenessStateComponent> AWARE_M =
        ComponentMapper.getFor(AwarenessStateComponent.class);
    private static final ComponentMapper<PerceptionComponent> PERC_M =
        ComponentMapper.getFor(PerceptionComponent.class);
    private static final ComponentMapper<TransformComponent> XFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<SignatureComponent> SIG_M =
        ComponentMapper.getFor(SignatureComponent.class);

    private static final Family SCANNER_FAMILY = Family
        .all(AwarenessStateComponent.class, PerceptionComponent.class,
             TransformComponent.class, ShipDataComponent.class).get();
    private static final Family PLAYER_FAMILY = Family
        .all(PlayerTagComponent.class, SignatureComponent.class, TransformComponent.class).get();

    private ImmutableArray<Entity> scannerEntities;
    private ImmutableArray<Entity> playerEntities;

    public ShipDetectionSystem(EventBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(ActiveScanEvent.class, this::onActiveScan);
    }

    @Override
    public void addedToEngine(Engine engine) {
        scannerEntities = engine.getEntitiesFor(SCANNER_FAMILY);
        playerEntities  = engine.getEntitiesFor(PLAYER_FAMILY);
    }

    @Override
    public void update(float dt) {
        if (playerEntities == null || playerEntities.size() == 0) return;
        Entity player    = playerEntities.first();
        SignatureComponent sig   = SIG_M.get(player);
        Vector3 playerPos = XFORM_M.get(player).position;

        for (Entity scanner : scannerEntities) {
            AwarenessStateComponent state = AWARE_M.get(scanner);
            PerceptionComponent     perc  = PERC_M.get(scanner);
            Vector3 scannerPos = XFORM_M.get(scanner).position;

            float dist = playerPos.dst(scannerPos);
            float raw  = computeRawDetection(sig, perc, dist);
            float rate = raw > state.detectionAccumulator ? RISE_RATE : DECAY_RATE;
            state.detectionAccumulator = MathUtils.lerp(state.detectionAccumulator, raw, rate * dt);

            AwarenessState before = state.state;
            tickShipFsm(state, perc, playerPos, dt);

            if (state.state != before) {
                eventBus.publish(new AwarenessChangedEvent(
                    Integer.toHexString(System.identityHashCode(scanner)),
                    before, state.state, state.lastKnownPosition));
                if (state.state == AwarenessState.ALERTED) {
                    eventBus.publish(new PlayerDetectedEvent(
                        Integer.toHexString(System.identityHashCode(scanner)), "SHIP_PASSIVE"));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Package-private — called directly by ShipDetectionSystemTest
    // -------------------------------------------------------------------------

    /** Computes raw detection score [0..∞) from signature, perception, and distance. */
    float computeRawDetection(SignatureComponent sig, PerceptionComponent perc, float dist) {
        float score   = sig.computeShipScore();
        float falloff = 1f / (1f + dist * dist * FALLOFF_K);
        return score * perc.effectiveness * falloff;
    }

    /** Advances the ship FSM one tick given the current accumulator and timers. */
    void tickShipFsm(AwarenessStateComponent state, PerceptionComponent perc,
                     Vector3 playerPos, float dt) {
        switch (state.state) {
            case UNAWARE -> {
                if (state.detectionAccumulator > perc.curiousThreshold)
                    transition(state, AwarenessState.CURIOUS);
            }
            case CURIOUS -> {
                state.suspicionTimer += dt;
                if (state.detectionAccumulator > perc.alertThreshold
                        || state.suspicionTimer > SUSPICION_LIMIT) {
                    state.lastKnownPosition.set(playerPos);
                    transition(state, AwarenessState.ALERTED);
                } else if (state.detectionAccumulator < 0.05f && state.suspicionTimer > 5f) {
                    transition(state, AwarenessState.UNAWARE);
                }
            }
            case ALERTED -> {
                state.lastKnownPosition.set(playerPos);
                if (state.detectionAccumulator < 0.05f)
                    transition(state, AwarenessState.SEARCHING);
            }
            case SEARCHING -> {
                state.searchTimer += dt;
                if (state.detectionAccumulator > perc.curiousThreshold) {
                    state.lastKnownPosition.set(playerPos);
                    transition(state, AwarenessState.ALERTED);
                    eventBus.publish(new PlayerDetectedEvent(
                        "searching_re-detect", "SHIP_PASSIVE"));
                } else if (state.searchTimer > SEARCH_DURATION) {
                    transition(state, AwarenessState.UNAWARE);
                }
            }
        }
    }

    /**
     * Handles an active-scan ping for a specific scanner entity.
     * Package-private so tests can invoke it without a full Ashley Engine.
     */
    void handleActiveScan(ActiveScanEvent e, SignatureComponent sig,
                          PerceptionComponent perc, float dist) {
        if (dist > e.range) return;
        float pingScore = sig.computeShipScore() * e.pingMultiplier * perc.pingMultiplier;
        if (pingScore > perc.alertThreshold) {
            eventBus.publish(new PlayerDetectedEvent("active_scan", "SHIP_SCAN"));
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void onActiveScan(ActiveScanEvent e) {
        if (playerEntities == null || playerEntities.size() == 0) return;
        Entity player = playerEntities.first();
        SignatureComponent sig = SIG_M.get(player);
        Vector3 playerPos = XFORM_M.get(player).position;
        float dist = playerPos.dst(e.x, e.y, e.z);

        if (scannerEntities == null) return;
        for (Entity scanner : scannerEntities) {
            handleActiveScan(e, sig, PERC_M.get(scanner), dist);
        }
    }

    private void transition(AwarenessStateComponent state, AwarenessState next) {
        state.state = next;
        if (next == AwarenessState.CURIOUS)   { state.suspicionTimer = 0f; }
        if (next == AwarenessState.SEARCHING) { state.searchTimer = 0f; }
        if (next == AwarenessState.UNAWARE)   { state.suspicionTimer = 0f; state.searchTimer = 0f; }
    }
}
