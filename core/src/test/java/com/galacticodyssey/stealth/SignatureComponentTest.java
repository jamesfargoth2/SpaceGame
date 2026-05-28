package com.galacticodyssey.stealth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignatureComponentTest {

    // computeOnFootScore formula:
    // base  = 0.3 + noiseLevel * 0.7
    // lit   = 0.1 + lightExposure * 0.9
    // skill = 1.0 - stealthSkill / 200.0
    // score = base * lit * gearMultiplier * skill

    @Test
    void onFoot_defaultValues_returnsMidRangeScore() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 0.5f;
        sig.lightExposure = 0.5f;
        sig.gearMultiplier = 1.0f;
        sig.stealthSkill = 0;
        // base = 0.3 + 0.35 = 0.65; lit = 0.1 + 0.45 = 0.55; skill = 1.0
        float expected = 0.65f * 0.55f * 1.0f * 1.0f;
        assertEquals(expected, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void onFoot_proneInDark_nearZero() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 0.0f;
        sig.lightExposure = 0.0f;
        sig.gearMultiplier = 1.0f;
        sig.stealthSkill = 0;
        // base = 0.3; lit = 0.1; score = 0.03
        assertEquals(0.03f, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void onFoot_sprintingInLight_nearOne() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 1.0f;
        sig.lightExposure = 1.0f;
        sig.gearMultiplier = 1.0f;
        sig.stealthSkill = 0;
        // base = 1.0; lit = 1.0; score = 1.0
        assertEquals(1.0f, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void onFoot_stealthSkill100_reducesBy50Percent() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 0.5f;
        sig.lightExposure = 0.5f;
        sig.gearMultiplier = 1.0f;
        sig.stealthSkill = 100;
        // skill modifier = 1.0 - 100/200 = 0.5
        float noSkill = 0.65f * 0.55f * 1.0f;
        assertEquals(noSkill * 0.5f, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void onFoot_stealthSuit_reducesScore() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 0.5f;
        sig.lightExposure = 0.5f;
        sig.gearMultiplier = 0.6f;
        sig.stealthSkill = 0;
        float noGear = 0.65f * 0.55f;
        assertEquals(noGear * 0.6f, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void ship_normalOperation_sumsThreeSignatures() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.4f;
        sig.heatSignature = 0.3f;
        sig.visualSignature = 0.2f;
        sig.darkMode = false;
        assertEquals(0.9f, sig.computeShipScore(), 0.001f);
    }

    @Test
    void ship_darkMode_returnsNearZeroEM() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.5f;
        sig.heatSignature = 0.8f;
        sig.visualSignature = 0.3f;
        sig.darkMode = true;
        // dark mode: emSignature * 0.05 only
        assertEquals(0.5f * 0.05f, sig.computeShipScore(), 0.001f);
    }
}
