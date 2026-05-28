package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.Component;

public class PerceptionComponent implements Component {
    public float viewRange   = 20f;   // metres — LoS cone max
    public float viewAngle   = 110f;  // degrees — full cone (not half-angle)
    public float hearingRange = 12f;  // metres — omnidirectional, no LoS required
    public float curiousThreshold = 0.25f; // accumulator level to enter CURIOUS
    public float alertThreshold   = 0.65f; // accumulator level to enter ALERTED
    // Ship-only fields
    public float effectiveness   = 1.0f;  // scanner quality scalar
    public float pingMultiplier  = 2.0f;  // active scan intensity multiplier
}
