package com.galacticodyssey.crafting.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.crafting.RefiningJob;
import com.galacticodyssey.crafting.RefiningJobState;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.RefiningJobSnapshot;
import com.galacticodyssey.persistence.snapshots.RefinerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * ECS component representing a refinery module on a ship or station.
 * Holds configuration (tier, queue capacity, speed, power cost) and
 * the ordered queue of {@link RefiningJob} instances currently being processed.
 */
public class RefineryComponent implements Component, Snapshotable<RefinerySnapshot> {

    private final int tier;
    private final int maxQueueSize;
    private final float speedMultiplier;
    private final float powerCostPerSecond;
    private final List<RefiningJob> jobQueue = new ArrayList<>();

    public RefineryComponent(int tier, int maxQueueSize, float speedMultiplier, float powerCostPerSecond) {
        this.tier = tier;
        this.maxQueueSize = maxQueueSize;
        this.speedMultiplier = speedMultiplier;
        this.powerCostPerSecond = powerCostPerSecond;
    }

    /**
     * Returns the first job in the queue (the currently active job), or null if the queue is empty.
     */
    public RefiningJob getActiveJob() {
        return jobQueue.isEmpty() ? null : jobQueue.get(0);
    }

    /**
     * Returns true if the job queue has reached its maximum capacity.
     */
    public boolean isQueueFull() {
        return jobQueue.size() >= maxQueueSize;
    }

    /**
     * Returns true if this refinery's tier is sufficient to process the given recipe.
     */
    public boolean canProcessRecipe(RefiningRecipe recipe) {
        return recipe.requiredTier <= tier;
    }

    /**
     * Adds a job to the end of the queue. Callers should check {@link #isQueueFull()}
     * before adding.
     */
    public void addJob(RefiningJob job) {
        jobQueue.add(job);
    }

    /**
     * Removes the given job from the queue.
     *
     * @param job the job to remove
     * @return true if the job was found and removed
     */
    public boolean removeJob(RefiningJob job) {
        return jobQueue.remove(job);
    }

    /**
     * Returns an unmodifiable view of the job queue.
     */
    public List<RefiningJob> getJobQueue() {
        return Collections.unmodifiableList(jobQueue);
    }

    public int getTier() {
        return tier;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    public float getPowerCostPerSecond() {
        return powerCostPerSecond;
    }

    // -------------------------------------------------------------------------
    // Snapshotable
    // -------------------------------------------------------------------------

    @Override
    public RefinerySnapshot takeSnapshot() {
        RefinerySnapshot snap = new RefinerySnapshot();
        snap.tier = tier;
        snap.maxQueueSize = maxQueueSize;
        snap.speedMultiplier = speedMultiplier;
        snap.powerCostPerSecond = powerCostPerSecond;

        for (RefiningJob job : jobQueue) {
            RefiningJobSnapshot js = new RefiningJobSnapshot();
            js.jobId = job.getJobId();
            js.recipeId = job.getRecipeId();
            js.state = job.getState().name();
            js.progress = job.getProgress();
            js.totalTime = job.getTotalTime();
            js.inputsConsumed.putAll(job.getInputsConsumed());

            for (RefiningJob.Output output : job.getOutputs()) {
                js.outputs.add(new RefiningJobSnapshot.OutputEntry(output.materialId, output.quantity));
            }
            snap.jobs.add(js);
        }
        return snap;
    }

    @Override
    public void restoreFromSnapshot(RefinerySnapshot snapshot) {
        jobQueue.clear();

        for (RefiningJobSnapshot js : snapshot.jobs) {
            List<RefiningJob.Output> outputs = new ArrayList<>();
            for (RefiningJobSnapshot.OutputEntry oe : js.outputs) {
                outputs.add(new RefiningJob.Output(oe.materialId, oe.quantity));
            }

            RefiningJob job = new RefiningJob(
                    js.jobId, js.recipeId,
                    new HashMap<>(js.inputsConsumed),
                    outputs, js.totalTime,
                    RefiningJobState.valueOf(js.state),
                    js.progress
            );
            jobQueue.add(job);
        }
    }
}
