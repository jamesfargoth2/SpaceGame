package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.components.BuoyancyComponent;
import com.galacticodyssey.water.components.DepthControlComponent;
import com.galacticodyssey.water.components.FloodableCompartmentComponent;
import com.galacticodyssey.water.components.SubmarineHullComponent;
import com.galacticodyssey.water.components.SubmarineStateComponent;
import com.galacticodyssey.water.events.CompartmentFloodedEvent;

/**
 * Simulates water ingress through hull breaches and spreading between compartments.
 *
 * Water enters through breached compartments at a rate proportional to depth
 * (deeper = higher pressure = faster flooding). Water spreads between connected
 * compartments through damaged bulkheads.
 *
 * The system also computes flood-induced weight asymmetry, which feeds into the
 * BuoyancySystem's stability calculations (roll/pitch from uneven flooding).
 */
public class FloodingSystem extends IteratingSystem {

    private static final int PRIORITY = 10;

    /** Pressure scaling: flow rate multiplier per 100m of depth. */
    private static final float PRESSURE_SCALE_PER_100M = 0.5f;

    /** Bulkhead degradation rate when water is pressing against it (per second). */
    private static final float BULKHEAD_DEGRADE_RATE = 0.01f;

    private final ComponentMapper<SubmarineStateComponent> stateMapper =
        ComponentMapper.getFor(SubmarineStateComponent.class);
    private final ComponentMapper<SubmarineHullComponent> hullMapper =
        ComponentMapper.getFor(SubmarineHullComponent.class);
    private final ComponentMapper<DepthControlComponent> depthMapper =
        ComponentMapper.getFor(DepthControlComponent.class);
    private final ComponentMapper<FloodableCompartmentComponent> compartmentMapper =
        ComponentMapper.getFor(FloodableCompartmentComponent.class);

    private final EventBus eventBus;

    public FloodingSystem(EventBus eventBus) {
        super(Family.all(
            SubmarineStateComponent.class,
            SubmarineHullComponent.class,
            DepthControlComponent.class
        ).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final SubmarineStateComponent state = stateMapper.get(entity);
        final SubmarineHullComponent hull = hullMapper.get(entity);
        final DepthControlComponent depth = depthMapper.get(entity);

        if (!hull.breached || state.compartmentEntities.size == 0) return;

        float currentDepth = depth.currentDepth;

        // Pressure multiplier: increases with depth
        float pressureMultiplier = 1f + (currentDepth / 100f) * PRESSURE_SCALE_PER_100M;

        // Phase 1: Water ingress through hull breaches
        for (int i = 0; i < state.compartmentEntities.size; i++) {
            Entity compEntity = state.compartmentEntities.get(i);
            FloodableCompartmentComponent comp = compartmentMapper.get(compEntity);
            if (comp == null) continue;

            if (comp.breached && !comp.isFullyFlooded()) {
                float flowRate = comp.baseBreachFlowRate * pressureMultiplier;
                comp.breachFlowRate = flowRate;
                float ingressVolume = flowRate * deltaTime;
                float previousVolume = comp.floodedVolume;
                comp.floodedVolume = Math.min(comp.floodedVolume + ingressVolume, comp.volume);

                // Check if just became fully flooded
                if (previousVolume < comp.volume && comp.isFullyFlooded()) {
                    publishCompartmentFlooded(entity, compEntity, comp, state);
                }
            }
        }

        // Phase 2: Water spreads between connected compartments through bulkheads
        for (int i = 0; i < state.compartmentEntities.size; i++) {
            Entity compEntity = state.compartmentEntities.get(i);
            FloodableCompartmentComponent comp = compartmentMapper.get(compEntity);
            if (comp == null || comp.floodedVolume <= 0f) continue;

            for (int j = 0; j < comp.connectedCompartments.size; j++) {
                int neighborIdx = comp.connectedCompartments.get(j);
                if (neighborIdx < 0 || neighborIdx >= state.compartmentEntities.size) continue;

                Entity neighborEntity = state.compartmentEntities.get(neighborIdx);
                FloodableCompartmentComponent neighbor = compartmentMapper.get(neighborEntity);
                if (neighbor == null || neighbor.isFullyFlooded()) continue;

                // Get bulkhead integrity
                float bulkheadIntegrity = (j < comp.bulkheadIntegrity.size)
                    ? comp.bulkheadIntegrity.get(j) : 1f;

                // Degrade bulkhead if water is pressing against it
                if (comp.floodedVolume > 0f && bulkheadIntegrity > 0f) {
                    float degradation = BULKHEAD_DEGRADE_RATE * pressureMultiplier * deltaTime;
                    // Flooding fraction increases pressure on bulkhead
                    degradation *= comp.getFloodFraction();
                    bulkheadIntegrity = Math.max(0f, bulkheadIntegrity - degradation);
                    if (j < comp.bulkheadIntegrity.size) {
                        comp.bulkheadIntegrity.set(j, bulkheadIntegrity);
                    }
                }

                // Water flows through damaged bulkheads
                float damageOpening = 1f - bulkheadIntegrity;
                if (damageOpening > 0f) {
                    // Flow rate depends on water level difference and opening size
                    float levelDiff = comp.getFloodFraction() - neighbor.getFloodFraction();
                    if (levelDiff > 0f) {
                        float spreadRate = comp.bulkheadFlowRate * damageOpening
                            * levelDiff * pressureMultiplier;
                        float transferVolume = spreadRate * deltaTime;
                        transferVolume = Math.min(transferVolume, comp.floodedVolume);
                        transferVolume = Math.min(transferVolume, neighbor.volume - neighbor.floodedVolume);

                        if (transferVolume > 0f) {
                            comp.floodedVolume -= transferVolume;
                            float prevNeighborVol = neighbor.floodedVolume;
                            neighbor.floodedVolume += transferVolume;

                            if (prevNeighborVol < neighbor.volume && neighbor.isFullyFlooded()) {
                                publishCompartmentFlooded(entity, neighborEntity, neighbor, state);
                            }
                        }
                    }
                }
            }
        }

        // Phase 3: Compute aggregate flooding effects
        computeFloodingEffects(state);
    }

    /**
     * Computes the aggregate effects of flooding: total water mass and
     * weight asymmetry (which causes listing/pitching).
     */
    private void computeFloodingEffects(SubmarineStateComponent state) {
        float totalMass = 0f;
        float momentX = 0f; // lateral moment
        float momentZ = 0f; // longitudinal moment

        for (int i = 0; i < state.compartmentEntities.size; i++) {
            Entity compEntity = state.compartmentEntities.get(i);
            FloodableCompartmentComponent comp = compartmentMapper.get(compEntity);
            if (comp == null) continue;

            float waterMass = comp.getWaterMass();
            totalMass += waterMass;

            // Weight moment = mass * offset from center
            momentX += waterMass * comp.lateralOffset;
            momentZ += waterMass * comp.forwardOffset;
        }

        state.totalFloodWaterMass = totalMass;

        // Convert moments to angular offsets (simplified model)
        // The actual roll/pitch angle depends on the metacentric height and displacement,
        // but we compute a "desired" offset that the BuoyancySystem will use for torques.
        if (totalMass > 0f) {
            // Degrees of list per unit of moment (tuning parameter)
            float rollSensitivity = 0.001f;
            float pitchSensitivity = 0.0005f;

            state.floodInducedRoll = momentX * rollSensitivity;
            state.floodInducedPitch = momentZ * pitchSensitivity;
        } else {
            state.floodInducedRoll = 0f;
            state.floodInducedPitch = 0f;
        }
    }

    private void publishCompartmentFlooded(Entity submarine, Entity compartment,
                                            FloodableCompartmentComponent comp,
                                            SubmarineStateComponent state) {
        int floodedCount = 0;
        for (int i = 0; i < state.compartmentEntities.size; i++) {
            FloodableCompartmentComponent c = compartmentMapper.get(state.compartmentEntities.get(i));
            if (c != null && c.isFullyFlooded()) {
                floodedCount++;
            }
        }

        eventBus.publish(new CompartmentFloodedEvent(
            submarine, compartment, comp.compartmentId,
            floodedCount, state.compartmentEntities.size));
    }
}
