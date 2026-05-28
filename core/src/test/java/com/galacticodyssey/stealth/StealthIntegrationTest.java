package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.ActiveScanEvent;
import com.galacticodyssey.core.events.NoiseBurstEvent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.stealth.events.PlayerDetectedEvent;
import com.galacticodyssey.stealth.events.StealthHUDUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StealthIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private NpcAwarenessSystem awarenessSystem;

    private Entity playerEntity;
    private Entity npcEntity;
    private SignatureComponent playerSig;
    private AwarenessStateComponent npcState;
    private TransformComponent npcXform;

    private final List<AwarenessChangedEvent> stateChanges = new ArrayList<>();
    private final List<PlayerDetectedEvent>   detections   = new ArrayList<>();
    private final List<StealthHUDUpdateEvent> hudUpdates   = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stateChanges.clear();
        detections.clear();
        hudUpdates.clear();

        eventBus = new EventBus();
        // Always-clear LoS so vision always contributes
        awarenessSystem = new NpcAwarenessSystem(eventBus, (a, b) -> true);

        engine = new Engine();
        engine.addSystem(awarenessSystem);

        // Player entity
        playerSig = new SignatureComponent();
        playerSig.noiseLevel = 0.8f;
        playerSig.lightExposure = 0.8f;
        playerSig.gearMultiplier = 1.0f;
        playerSig.stealthSkill = 0;

        TransformComponent playerXform = new TransformComponent();
        playerXform.position.set(0, 0, 0);

        playerEntity = new Entity()
            .add(new PlayerTagComponent())
            .add(playerSig)
            .add(playerXform);
        engine.addEntity(playerEntity);

        // NPC entity (ground NPC — no ShipDataComponent)
        npcState = new AwarenessStateComponent();
        PerceptionComponent npcPerc = new PerceptionComponent();
        npcPerc.viewRange    = 20f;
        npcPerc.viewAngle    = 360f; // omnidirectional for test simplicity
        npcPerc.hearingRange = 12f;
        npcPerc.curiousThreshold = 0.25f;
        npcPerc.alertThreshold   = 0.65f;

        npcXform = new TransformComponent();
        npcXform.position.set(0, 0, 5); // 5m from player

        npcEntity = new Entity()
            .add(npcState)
            .add(npcPerc)
            .add(npcXform);
        engine.addEntity(npcEntity);

        eventBus.subscribe(AwarenessChangedEvent.class, stateChanges::add);
        eventBus.subscribe(PlayerDetectedEvent.class, detections::add);
        eventBus.subscribe(StealthHUDUpdateEvent.class, hudUpdates::add);
    }

    @Test
    void loudPlayer_risesToCuriousThenAlerted() {
        for (int i = 0; i < 60; i++) engine.update(0.1f);

        // Verify UNAWARE → CURIOUS transition occurred (not just any state change)
        boolean passedThroughCurious = stateChanges.stream()
            .anyMatch(e -> e.newState == AwarenessState.CURIOUS);
        assertTrue(passedThroughCurious,
            "NPC should transition through CURIOUS state, not jump straight to ALERTED");
        assertEquals(AwarenessState.UNAWARE, stateChanges.get(0).oldState,
            "First transition should be from UNAWARE");
    }

    @Test
    void loudPlayerNearby_reachesAlerted() {
        // Tick aggressively to ensure ALERTED is reached
        for (int i = 0; i < 200; i++) engine.update(0.1f);

        assertEquals(AwarenessState.ALERTED, npcState.state,
            "NPC should reach ALERTED with a loud, visible player nearby for 20 seconds");
    }

    @Test
    void quietPlayer_accumulatorDecays_staysUnaware() {
        playerSig.noiseLevel = 0f;
        playerSig.lightExposure = 0f; // dark + silent
        // Move player beyond hearing range (12m)
        playerEntity.getComponent(TransformComponent.class).position.set(0, 0, 13);

        // Prime the accumulator so we can witness it decaying
        npcState.detectionAccumulator = 0.4f;

        for (int i = 0; i < 100; i++) engine.update(0.1f);

        assertEquals(AwarenessState.UNAWARE, npcState.state);
        // Accumulator should have decayed from 0.4 toward 0 over 10 seconds
        assertTrue(npcState.detectionAccumulator < 0.1f,
            "Accumulator should decay when player is undetectable");
    }

    @Test
    void noiseBurst_spikesAccumulator() {
        playerSig.noiseLevel = 0f; // silent player
        npcXform.position.set(0, 0, 3); // 3m from origin

        // EventBus is synchronous — NoiseBurstEvent spikes accumulator immediately on publish
        // falloff = 1 - 3/10 = 0.7; spike = 1.0 * 0.7 = 0.7
        eventBus.publish(new NoiseBurstEvent(0, 0, 0, 10f, 1.0f));
        assertTrue(npcState.detectionAccumulator > 0.5f,
            "NoiseBurst should spike the NPC accumulator immediately (synchronous EventBus)");

        // Verify the spike persists through one frame (accumulator decays gradually, not instantly)
        engine.update(0.1f);
        assertTrue(npcState.detectionAccumulator > 0.3f,
            "Accumulator should still be elevated after one frame of decay");
    }

    @Test
    void stealthHUDUpdate_publishedAt4Hz() {
        // HUD updates at 4 Hz (every 0.25s). Run for 1 second.
        for (int i = 0; i < 10; i++) engine.update(0.1f);

        // 3-5 HUD events acceptable due to frame timing
        assertTrue(hudUpdates.size() >= 3 && hudUpdates.size() <= 5,
            "Expected ~4 HUD updates per second, got: " + hudUpdates.size());
    }

    @Test
    void shipDetection_activeScan_highSignature_publishesDetected() {
        EventBus bus2 = new EventBus();
        ShipDetectionSystem shipSystem = new ShipDetectionSystem(bus2);
        List<PlayerDetectedEvent> shipDetections = new ArrayList<>();
        bus2.subscribe(PlayerDetectedEvent.class, shipDetections::add);

        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.5f; sig.heatSignature = 0.7f; sig.visualSignature = 0.4f;
        // ship score = 1.6

        PerceptionComponent perc = new PerceptionComponent();
        perc.pingMultiplier = 2.0f; perc.alertThreshold = 0.45f;

        ActiveScanEvent scan = new ActiveScanEvent(0, 0, 0, 500f, 2.0f);
        shipSystem.handleActiveScan(scan, sig, perc, 50f);

        assertEquals(1, shipDetections.size());
        assertEquals("SHIP_SCAN", shipDetections.get(0).detectorType);
    }

    @Test
    void shipDetection_darkMode_activeScanMisses() {
        EventBus bus2 = new EventBus();
        ShipDetectionSystem shipSystem = new ShipDetectionSystem(bus2);
        List<PlayerDetectedEvent> shipDetections = new ArrayList<>();
        bus2.subscribe(PlayerDetectedEvent.class, shipDetections::add);

        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.05f;
        sig.darkMode = true; // score = 0.05 * 0.05 = 0.0025

        PerceptionComponent perc = new PerceptionComponent();
        perc.pingMultiplier = 2.0f; perc.alertThreshold = 0.45f;

        ActiveScanEvent scan = new ActiveScanEvent(0, 0, 0, 500f, 2.0f);
        shipSystem.handleActiveScan(scan, sig, perc, 50f);

        assertTrue(shipDetections.isEmpty(), "Dark mode ship should not be detected by active scan");
    }
}
