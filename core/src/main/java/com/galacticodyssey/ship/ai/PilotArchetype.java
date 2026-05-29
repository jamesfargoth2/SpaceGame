package com.galacticodyssey.ship.ai;

/** Data-driven tuning for an NPC pilot's behaviour. Loaded from data/ai/pilot_archetypes.json. */
public class PilotArchetype {
    public String id;
    /** Seconds of decision latency before firing / switching targets. */
    public float reactionTimeSec = 0.3f;
    /** Half-angle (degrees) of the random cone added to the aim point. */
    public float aimErrorDeg = 4f;
    /** 0..1 bias toward attacking vs. caution. */
    public float aggression = 0.6f;
    /** Health fraction at or below which the pilot prefers to evade. */
    public float evadeHealthThreshold = 0.35f;
    /** Distance (metres) the pilot tries to hold from the target. */
    public float preferredEngageRange = 350f;
    /** Closure overshoot distance (metres) that triggers extend-and-reengage. */
    public float overshootExtendDist = 130f;
    /** 0..1 how aggressively the pilot manages throttle/energy. */
    public float throttleDiscipline = 0.6f;
    /** Detection radius (metres) for acquiring a target when none is assigned. */
    public float aggroRange = 2000f;
    /** Whether this pilot launches guided missiles. */
    public boolean usesMissiles = false;
}
