package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.components.BuoyancyComponent;
import com.galacticodyssey.water.components.DepthControlComponent;
import com.galacticodyssey.water.components.FloodableCompartmentComponent;
import com.galacticodyssey.water.components.SubmarineHullComponent;
import com.galacticodyssey.water.components.SubmarineStateComponent;
import com.galacticodyssey.water.events.DepthWarningEvent;
import com.galacticodyssey.water.events.HullBreachEvent;

/**
 * Monitors hull integrity against depth pressure.
 *
 * When the submarine exceeds its crush depth:
 * 1. Hull integrity degrades over time, scaling with how far past crush depth.
 * 2. When integrity drops below thresholds, hull breaches occur in compartments.
 * 3. Depth warning events are published at warning/critical/catastrophic levels.
 *
 * Breach locations are determined by compartment position -- deeper compartments
 * (lower verticalOffset) are more likely to breach first.
 */
public class HullIntegritySystem extends IteratingSystem {

    private static final int PRIORITY = 9;

    /** Integrity threshold below which a new breach can occur. */
    private static final float BREACH_THRESHOLD_STEP = 0.15f;

    /** Minimum time between consecutive breaches (seconds). */
    private static final float MIN_BREACH_INTERVAL = 3f;

    /** Catastrophic threshold: fraction of crush depth where damage accelerates. */
    private static final float CATASTROPHIC_DEPTH_FRACTION = 1.3f;

    private final ComponentMapper<SubmarineHullComponent> hullMapper =
        ComponentMapper.getFor(SubmarineHullComponent.class);
    private final ComponentMapper<DepthControlComponent> depthMapper =
        ComponentMapper.getFor(DepthControlComponent.class);
    private final ComponentMapper<SubmarineStateComponent> stateMapper =
        ComponentMapper.getFor(SubmarineStateComponent.class);
    private final ComponentMapper<BuoyancyComponent> buoyancyMapper =
        ComponentMapper.getFor(BuoyancyComponent.class);
    private final ComponentMapper<FloodableCompartmentComponent> compartmentMapper =
        ComponentMapper.getFor(FloodableCompartmentComponent.class);

    private final EventBus eventBus;

    /** Tracks when the last breach occurred (to throttle). */
    private float timeSinceLastBreach = 0f;

    /** Tracks the last warning severity published (to avoid spam). */
    private DepthWarningEvent.Severity lastWarningSeverity = null;

    public HullIntegritySystem(EventBus eventBus) {
        super(Family.all(
            SubmarineHullComponent.class,
            DepthControlComponent.class,
            SubmarineStateComponent.class
        ).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final SubmarineHullComponent hull = hullMapper.get(entity);
        final DepthControlComponent depth = depthMapper.get(entity);
        final SubmarineStateComponent state = stateMapper.get(entity);

        timeSinceLastBreach += deltaTime;

        float currentDepth = depth.currentDepth;
        float depthFraction = hull.crushDepth > 0 ? currentDepth / hull.crushDepth : 0f;

        // Publish depth warnings
        publishDepthWarnings(entity, currentDepth, hull.crushDepth, depthFraction,
            hull.depthWarningFraction);

        // If above crush depth, nothing to do
        if (currentDepth <= hull.crushDepth) {
            state.timeBelowCrushDepth = 0f;
            return;
        }

        state.timeBelowCrushDepth += deltaTime;

        // Calculate overshoot factor: how far past crush depth (1.0 = at crush, 2.0 = double crush)
        float overshootFactor = depthFraction - 1f; // 0+ beyond crush depth

        // Damage rate increases quadratically with overshoot
        float damageMultiplier = 1f + overshootFactor * overshootFactor * 4f;
        float integrityLoss = hull.crushDamageRate * damageMultiplier * deltaTime;
        hull.integrity = Math.max(0f, hull.integrity - integrityLoss);

        // Check if we should create a new breach
        if (timeSinceLastBreach >= MIN_BREACH_INTERVAL && state.compartmentEntities.size > 0) {
            checkForNewBreach(entity, hull, state, depth);
        }

        // Critical failure check
        if (hull.integrity <= 0f) {
            state.criticalFailure = true;
            state.state = SubmarineStateComponent.State.SINKING;
        }
    }

    /**
     * Determines if a new hull breach should occur based on integrity thresholds.
     * Breaches occur at discrete integrity levels (e.g., 0.85, 0.70, 0.55, etc.).
     * The compartment with the lowest position (deepest) that is not yet breached
     * is selected.
     */
    private void checkForNewBreach(Entity entity, SubmarineHullComponent hull,
                                    SubmarineStateComponent state, DepthControlComponent depth) {
        // Calculate how many breaches should exist at this integrity level
        int expectedBreaches = (int) ((1f - hull.integrity) / BREACH_THRESHOLD_STEP);
        if (expectedBreaches <= hull.breachCount) return;

        // Find the lowest un-breached compartment
        Entity targetCompartment = null;
        FloodableCompartmentComponent targetComp = null;
        float lowestOffset = Float.MAX_VALUE;

        for (int i = 0; i < state.compartmentEntities.size; i++) {
            Entity compEntity = state.compartmentEntities.get(i);
            FloodableCompartmentComponent comp = compartmentMapper.get(compEntity);
            if (comp != null && !comp.breached && comp.verticalOffset < lowestOffset) {
                lowestOffset = comp.verticalOffset;
                targetCompartment = compEntity;
                targetComp = comp;
            }
        }

        if (targetComp != null) {
            targetComp.breached = true;
            hull.breached = true;
            hull.breachCount++;
            timeSinceLastBreach = 0f;

            state.state = SubmarineStateComponent.State.FLOODING;

            // Breach area scales with overshoot -- worse damage at greater depth
            float overshoot = depth.currentDepth / hull.crushDepth - 1f;
            float breachArea = 0.05f + 0.1f * overshoot;
            eventBus.publish(new HullBreachEvent(
                entity, targetComp.compartmentId,
                breachArea, depth.currentDepth));
        }
    }

    private void publishDepthWarnings(Entity entity, float currentDepth, float crushDepth,
                                       float depthFraction, float warningFraction) {
        DepthWarningEvent.Severity severity = null;

        if (depthFraction >= CATASTROPHIC_DEPTH_FRACTION) {
            severity = DepthWarningEvent.Severity.CATASTROPHIC;
        } else if (depthFraction >= 1f) {
            severity = DepthWarningEvent.Severity.CRITICAL;
        } else if (depthFraction >= warningFraction) {
            severity = DepthWarningEvent.Severity.WARNING;
        }

        // Only publish when severity changes
        if (severity != null && severity != lastWarningSeverity) {
            lastWarningSeverity = severity;
            eventBus.publish(new DepthWarningEvent(
                entity, severity, currentDepth, crushDepth, depthFraction));
        } else if (severity == null) {
            lastWarningSeverity = null;
        }
    }
}
