package com.galacticodyssey.ship.structure;

import com.badlogic.ashley.core.Component;

/**
 * Ashley component for crew / pilot G-force tolerance and fatigue tracking.
 */
public class GForceToleranceComponent implements Component {

    /** Maximum sustained G-force before injury / incapacitation. */
    public float maxSustainedG = 9f;

    /** Maximum brief (sub-second) G-force tolerable. */
    public float maxBriefG = 15f;

    /** Whether the entity is wearing a G-suit (1.5x tolerance multiplier). */
    public boolean hasGSuit;

    /** Accumulated G-fatigue (0-1+). Exceeding 1 triggers incapacitation. */
    public float gFatigue;

    /** Whether the entity is currently incapacitated from G-LOC. */
    public boolean isIncapacitated;
}
