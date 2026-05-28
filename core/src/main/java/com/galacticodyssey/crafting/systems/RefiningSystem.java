package com.galacticodyssey.crafting.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.crafting.RefiningJob;
import com.galacticodyssey.crafting.RefiningJobState;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.events.RefiningCompletedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.core.EventBus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Processes entities with a {@link RefineryComponent}, advancing active refining jobs
 * each tick based on delta time and the refinery's speed multiplier. When a job reaches
 * full progress, its output materials are deposited into the entity's
 * {@link MaterialStorageComponent} and the next queued job is activated.
 */
public class RefiningSystem extends IteratingSystem {
    private static final int PRIORITY = 5;

    private static final ComponentMapper<RefineryComponent> refineryMapper =
        ComponentMapper.getFor(RefineryComponent.class);
    private static final ComponentMapper<MaterialStorageComponent> storageMapper =
        ComponentMapper.getFor(MaterialStorageComponent.class);

    private final EventBus eventBus;

    public RefiningSystem(EventBus eventBus) {
        super(Family.all(RefineryComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        RefineryComponent refinery = refineryMapper.get(entity);
        RefiningJob activeJob = refinery.getActiveJob();
        if (activeJob == null) return;
        if (activeJob.getState() == RefiningJobState.QUEUED) {
            activeJob.setState(RefiningJobState.ACTIVE);
        }

        float progressDelta = (deltaTime * refinery.getSpeedMultiplier()) / activeJob.getTotalTime();
        activeJob.advanceProgress(progressDelta);

        if (activeJob.isComplete()) {
            completeJob(entity, refinery, activeJob);
        }
    }

    private void completeJob(Entity entity, RefineryComponent refinery, RefiningJob job) {
        MaterialStorageComponent storage = storageMapper.get(entity);
        Map<String, Integer> produced = new HashMap<>();

        for (RefiningJob.Output output : job.getOutputs()) {
            if (storage != null && storage.tryAdd(output.materialId, output.quantity)) {
                produced.put(output.materialId, output.quantity);
            }
        }

        job.setState(RefiningJobState.COMPLETE);
        refinery.removeJob(job);
        eventBus.publish(new RefiningCompletedEvent(entity, job, Collections.unmodifiableMap(produced)));
        eventBus.publish(new RefiningQueueChangedEvent(entity, refinery.getJobQueue()));

        RefiningJob nextJob = refinery.getActiveJob();
        if (nextJob != null && nextJob.getState() == RefiningJobState.QUEUED) {
            nextJob.setState(RefiningJobState.ACTIVE);
        }
    }
}
