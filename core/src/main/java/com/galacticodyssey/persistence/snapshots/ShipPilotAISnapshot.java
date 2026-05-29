package com.galacticodyssey.persistence.snapshots;

/** Persisted form of ShipPilotAIComponent. Behaviour tree + live target are rebuilt at runtime. */
public class ShipPilotAISnapshot {
    public ShipPilotAISnapshot() {}

    public String archetypeId;
    public float decisionInterval;
}
