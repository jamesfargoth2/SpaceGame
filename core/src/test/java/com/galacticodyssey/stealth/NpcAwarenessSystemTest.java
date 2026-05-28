package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.NoiseBurstEvent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcAwarenessSystemTest {

    private NpcAwarenessSystem system;
    private EventBus eventBus;
    private List<AwarenessChangedEvent> stateChanges;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        system = new NpcAwarenessSystem(eventBus, (a, b) -> true); // always clear LoS
        stateChanges = new ArrayList<>();
        eventBus.subscribe(AwarenessChangedEvent.class, stateChanges::add);
    }

    // Helper to make a TransformComponent with a position
    private com.galacticodyssey.core.components.TransformComponent makeTransform(float x, float y, float z) {
        com.galacticodyssey.core.components.TransformComponent t =
            new com.galacticodyssey.core.components.TransformComponent();
        t.position.set(x, y, z);
        return t;
    }

    // Helper to make a SignatureComponent
    private SignatureComponent makeSig(float noise, float light, float gearMult, int skill) {
        SignatureComponent s = new SignatureComponent();
        s.noiseLevel = noise; s.lightExposure = light;
        s.gearMultiplier = gearMult; s.stealthSkill = skill;
        return s;
    }

    // Helper to make a PerceptionComponent
    private PerceptionComponent makePerception(float viewRange, float viewAngle, float hearingRange) {
        PerceptionComponent p = new PerceptionComponent();
        p.viewRange = viewRange; p.viewAngle = viewAngle; p.hearingRange = hearingRange;
        return p;
    }

    // --- computeContribution tests ---

    @Test
    void hearingContribution_noLoS_stillDetects() {
        NpcAwarenessSystem noLosSys = new NpcAwarenessSystem(eventBus, (a, b) -> false);
        SignatureComponent sig = makeSig(1.0f, 1.0f, 1.0f, 0);
        PerceptionComponent p = makePerception(0f, 0f, 12f); // hearing only
        com.galacticodyssey.core.components.TransformComponent npcT = makeTransform(0, 0, 6);
        float c = noLosSys.computeContribution(p, npcT, new Vector3(0, 0, 0), sig);
        assertTrue(c > 0f, "Hearing should detect without LoS");
    }

    @Test
    void hearingFalloff_atEdge_isNearZero() {
        SignatureComponent sig = makeSig(1.0f, 0f, 1.0f, 0);
        PerceptionComponent p = makePerception(0f, 0f, 10f);
        com.galacticodyssey.core.components.TransformComponent npcT = makeTransform(0, 0, 10);
        float c = system.computeContribution(p, npcT, new Vector3(0, 0, 0), sig);
        // NPC is at exactly hearingRange — the strict `dist < hearingRange` guard excludes it,
        // so contribution is 0 regardless of noiseLevel.
        assertEquals(0f, c, 0.01f);
    }

    @Test
    void visionContribution_outOfRange_isZero() {
        SignatureComponent sig = makeSig(0f, 1.0f, 1.0f, 0);
        PerceptionComponent p = makePerception(20f, 110f, 0f); // vision only
        com.galacticodyssey.core.components.TransformComponent npcT = makeTransform(0, 0, 25); // beyond viewRange
        float c = system.computeContribution(p, npcT, new Vector3(0, 0, 0), sig);
        assertEquals(0f, c, 0.001f);
    }

    // --- tickFsm tests ---

    @Test
    void unaware_accumulatorExceedsThreshold_becomesCurious() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.detectionAccumulator = 0.4f; // above curiousThreshold 0.25
        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), true, 0.1f);
        assertEquals(AwarenessState.CURIOUS, state.state);
        assertEquals(0f, state.suspicionTimer, 0.001f);
    }

    @Test
    void curious_accumulatorExceedsAlertThreshold_becomesAlerted() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.CURIOUS;
        state.detectionAccumulator = 0.8f; // above alertThreshold 0.65
        PerceptionComponent p = makePerception(20f, 110f, 12f);
        Vector3 playerPos = new Vector3(3, 0, 2);
        system.tickFsm(state, p, playerPos, true, 0.1f);
        assertEquals(AwarenessState.ALERTED, state.state);
        assertEquals(playerPos.x, state.lastKnownPosition.x, 0.001f);
    }

    @Test
    void curious_suspicionTimerExpires_becomesAlerted() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.CURIOUS;
        state.detectionAccumulator = 0.1f; // below alertThreshold
        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), true, NpcAwarenessSystem.SUSPICION_LIMIT + 0.1f);
        assertEquals(AwarenessState.ALERTED, state.state);
    }

    @Test
    void curious_lowAccumulatorAfterCooldown_returnsUnaware() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.CURIOUS;
        state.detectionAccumulator = 0.02f; // below DECAY_FLOOR 0.05
        state.suspicionTimer = NpcAwarenessSystem.CURIOUS_COOLDOWN + 0.1f;
        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), false, 0.1f);
        assertEquals(AwarenessState.UNAWARE, state.state);
    }

    @Test
    void alerted_playerNotVisible_becomesSearching() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.ALERTED;
        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), false, 0.1f);
        assertEquals(AwarenessState.SEARCHING, state.state);
        assertEquals(0f, state.searchTimer, 0.001f);
    }

    @Test
    void searching_timerExpires_returnsUnaware() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.SEARCHING;
        state.detectionAccumulator = 0.01f;
        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), false, NpcAwarenessSystem.SEARCH_DURATION + 0.1f);
        assertEquals(AwarenessState.UNAWARE, state.state);
    }

    // --- NoiseBurst tests ---

    @Test
    void applyNoiseBurst_withinRadius_spikesAccumulator() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.detectionAccumulator = 0f;
        NoiseBurstEvent e = new NoiseBurstEvent(0, 0, 0, 10f, 1.0f);
        system.applyNoiseBurst(new Vector3(0, 0, 5), state, e);
        // falloff = 1 - 5/10 = 0.5; spike = 1.0 * 0.5 = 0.5
        assertEquals(0.5f, state.detectionAccumulator, 0.01f);
    }

    @Test
    void applyNoiseBurst_outsideRadius_doesNothing() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.detectionAccumulator = 0f;
        NoiseBurstEvent e = new NoiseBurstEvent(0, 0, 0, 5f, 1.0f);
        system.applyNoiseBurst(new Vector3(0, 0, 10), state, e);
        assertEquals(0f, state.detectionAccumulator, 0.001f);
    }
}
