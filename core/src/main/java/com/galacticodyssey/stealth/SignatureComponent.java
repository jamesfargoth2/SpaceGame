package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.Component;

public class SignatureComponent implements Component {

    // On-foot — written by movement and lighting systems each frame
    public float noiseLevel;      // 0–1: prone-still=0.0, walking=0.5, sprinting=1.0
    public float lightExposure;   // 0–1: written by LightingSystem
    public float gearMultiplier = 1.0f; // product of all equipped stealth gear modifiers
    public int   stealthSkill;    // 0–100; each 2 points = 1% reduction (max 50% at 100)

    // Ship — written by ShipSignatureSystem each frame
    public float emSignature;       // 0–1: shields + scanners raise this
    public float heatSignature;     // 0–1: scales with engine throttle
    public float visualSignature = 0.5f; // 0–1: stealth coating reduces this
    public boolean darkMode;        // engine cutoff — near-zero EM, zero heat, no thrust
    public boolean shieldsActive;   // set by ShipSignatureSystem (currentShield > 0)
    public boolean scannerActive;   // set by scanner system when active

    public float computeOnFootScore() {
        float base  = 0.3f + noiseLevel * 0.7f;
        float lit   = 0.1f + lightExposure * 0.9f;
        float skill = 1f - (stealthSkill / 200f);
        return base * lit * gearMultiplier * skill;
    }

    public float computeShipScore() {
        if (darkMode) return emSignature * 0.05f;
        return emSignature + heatSignature + visualSignature;
    }
}
