package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ActiveScanEvent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.stealth.events.PlayerDetectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipDetectionSystemTest {

    private ShipDetectionSystem system;
    private EventBus eventBus;
    private List<PlayerDetectedEvent> detections;
    private List<AwarenessChangedEvent> stateChanges;

    @BeforeEach
    void setUp() {
        eventBus     = new EventBus();
        system       = new ShipDetectionSystem(eventBus);
        detections   = new ArrayList<>();
        stateChanges = new ArrayList<>();
        eventBus.subscribe(PlayerDetectedEvent.class, detections::add);
        eventBus.subscribe(AwarenessChangedEvent.class, stateChanges::add);
    }

    @Test
    void computeRawDetection_normalSignature_scaledByEffectivenessAndDistance() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.4f; sig.heatSignature = 0.3f; sig.visualSignature = 0.2f;
        // ship score = 0.9

        PerceptionComponent perc = new PerceptionComponent();
        perc.effectiveness = 1.0f;

        float dist = 100f;
        float raw = system.computeRawDetection(sig, perc, dist);
        // falloff = 1 / (1 + 100*100*0.0001) = 1 / 2 = 0.5
        float expected = 0.9f * 1.0f * 0.5f;
        assertEquals(expected, raw, 0.001f);
    }

    @Test
    void computeRawDetection_darkMode_nearZero() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.5f; sig.heatSignature = 0.8f; sig.visualSignature = 0.3f;
        sig.darkMode = true;
        // ship score = 0.5 * 0.05 = 0.025

        PerceptionComponent perc = new PerceptionComponent();
        perc.effectiveness = 1.0f;

        float raw = system.computeRawDetection(sig, perc, 0f);
        // falloff at 0 dist = 1/(1+0) = 1.0; raw = 0.025
        assertEquals(0.025f, raw, 0.001f);
    }

    @Test
    void tickShipFsm_unaware_accumulatorExceedsThreshold_becomesCurious() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.detectionAccumulator = 0.3f; // above curiousThreshold 0.20

        PerceptionComponent perc = new PerceptionComponent();
        perc.curiousThreshold = 0.20f; perc.alertThreshold = 0.55f;

        system.tickShipFsm(state, perc, new Vector3(0, 0, 0), 0.1f);
        assertEquals(AwarenessState.CURIOUS, state.state);
    }

    @Test
    void tickShipFsm_curious_alertThresholdReached_becomesAlerted() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.CURIOUS;
        state.detectionAccumulator = 0.7f; // above alertThreshold 0.55

        PerceptionComponent perc = new PerceptionComponent();
        perc.curiousThreshold = 0.20f; perc.alertThreshold = 0.55f;

        system.tickShipFsm(state, perc, new Vector3(5, 0, 0), 0.1f);
        assertEquals(AwarenessState.ALERTED, state.state);
    }

    @Test
    void activeScan_highSignature_publishesPlayerDetected() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.5f; sig.heatSignature = 0.8f; sig.visualSignature = 0.4f;
        // ship score = 1.7

        PerceptionComponent perc = new PerceptionComponent();
        perc.pingMultiplier = 2.0f; perc.alertThreshold = 0.45f;

        ActiveScanEvent e = new ActiveScanEvent(0, 0, 0, 500f, 2.0f);
        // pingScore = 1.7 * 2.0 * 2.0 = 6.8 > 0.45
        system.handleActiveScan(e, sig, perc, 100f);

        assertEquals(1, detections.size());
        assertEquals("SHIP_SCAN", detections.get(0).detectorType);
    }

    @Test
    void activeScan_lowSignature_noDetection() {
        SignatureComponent sig = new SignatureComponent();
        sig.darkMode = true; sig.emSignature = 0.05f; // score = 0.0025

        PerceptionComponent perc = new PerceptionComponent();
        perc.pingMultiplier = 2.0f; perc.alertThreshold = 0.45f;

        ActiveScanEvent e = new ActiveScanEvent(0, 0, 0, 500f, 2.0f);
        // pingScore = 0.0025 * 2.0 * 2.0 = 0.01 < 0.45
        system.handleActiveScan(e, sig, perc, 100f);

        assertTrue(detections.isEmpty());
    }
}
