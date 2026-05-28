package com.galacticodyssey.crafting;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single in-progress or queued refining job within a refinery.
 * Tracks the recipe being processed, consumed inputs, expected outputs,
 * and current progress toward completion.
 */
public class RefiningJob {

    private final String jobId;
    private final String recipeId;
    private final Map<String, Integer> inputsConsumed;
    private final List<Output> outputs;
    private final float totalTime;
    private RefiningJobState state;
    private float progress;

    /**
     * Creates a new refining job in the QUEUED state with zero progress.
     *
     * @param recipeId       identifier of the recipe being processed
     * @param inputsConsumed map of material IDs to quantities consumed
     * @param outputs        list of expected output materials and quantities
     * @param totalTime      total processing time in seconds
     */
    public RefiningJob(String recipeId, Map<String, Integer> inputsConsumed,
                       List<Output> outputs, float totalTime) {
        this.jobId = UUID.randomUUID().toString();
        this.recipeId = recipeId;
        this.inputsConsumed = Map.copyOf(inputsConsumed);
        this.outputs = List.copyOf(outputs);
        this.totalTime = totalTime;
        this.state = RefiningJobState.QUEUED;
        this.progress = 0f;
    }

    /**
     * Constructor for restoring a job from saved state (e.g. snapshot restore).
     */
    public RefiningJob(String jobId, String recipeId, Map<String, Integer> inputsConsumed,
                List<Output> outputs, float totalTime, RefiningJobState state, float progress) {
        this.jobId = jobId;
        this.recipeId = recipeId;
        this.inputsConsumed = Map.copyOf(inputsConsumed);
        this.outputs = List.copyOf(outputs);
        this.totalTime = totalTime;
        this.state = state;
        this.progress = progress;
    }

    /**
     * Advances the job's progress by the given amount, clamped to 1.0.
     *
     * @param amount fractional progress to add (0.0 to 1.0 range typical)
     */
    public void advanceProgress(float amount) {
        progress = Math.min(1.0f, progress + amount);
    }

    /**
     * Returns true if the job has reached full progress.
     */
    public boolean isComplete() {
        return progress >= 1.0f;
    }

    /**
     * Calculates the materials that should be returned to the player if this
     * job is cancelled. Each input quantity is scaled by the remaining progress
     * fraction, floored to an integer.
     *
     * @return map of material IDs to quantities to return
     */
    public Map<String, Integer> calculateReturnedInputs() {
        Map<String, Integer> returned = new HashMap<>();
        // Use double and round to 6 decimal places before flooring to avoid
        // float precision issues (e.g., 0.6f promoting to 0.600000023... in double).
        double remaining = Math.round((1.0 - progress) * 1_000_000.0) / 1_000_000.0;
        for (Map.Entry<String, Integer> entry : inputsConsumed.entrySet()) {
            returned.put(entry.getKey(), (int) Math.floor(entry.getValue() * remaining));
        }
        return Collections.unmodifiableMap(returned);
    }

    public String getJobId() {
        return jobId;
    }

    public String getRecipeId() {
        return recipeId;
    }

    /**
     * Returns an unmodifiable view of the consumed inputs map.
     */
    public Map<String, Integer> getInputsConsumed() {
        return inputsConsumed;
    }

    public List<Output> getOutputs() {
        return outputs;
    }

    public float getTotalTime() {
        return totalTime;
    }

    public RefiningJobState getState() {
        return state;
    }

    public void setState(RefiningJobState state) {
        this.state = state;
    }

    public float getProgress() {
        return progress;
    }

    /**
     * Immutable output entry specifying a material and its quantity.
     */
    public static final class Output {
        public final String materialId;
        public final int quantity;

        public Output(String materialId, int quantity) {
            this.materialId = materialId;
            this.quantity = quantity;
        }
    }
}
