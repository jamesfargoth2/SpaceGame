package com.galacticodyssey.crafting.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.crafting.RecipeInput;
import com.galacticodyssey.crafting.RecipeOutput;
import com.galacticodyssey.crafting.RefiningConfig;
import com.galacticodyssey.crafting.RefiningFailureReason;
import com.galacticodyssey.crafting.RefiningJob;
import com.galacticodyssey.crafting.RefiningJobState;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.crafting.data.RefiningRecipeRegistry;
import com.galacticodyssey.crafting.events.RefiningFailedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.events.RefiningRequestEvent;
import com.galacticodyssey.crafting.events.RefiningStartedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ashley EntitySystem that processes incoming {@link RefiningRequestEvent}s.
 * Subscribes to the event bus and collects requests, then validates and
 * processes them each frame in {@link #update(float)}.
 *
 * <p>Runs at priority 4 so that it executes before RefiningSystem (priority 5),
 * ensuring newly queued jobs are visible to the refining tick in the same frame.</p>
 */
public class RefiningRequestHandler extends EntitySystem {

    private static final int PRIORITY = 4;

    private final EventBus eventBus;
    private final RefiningRecipeRegistry recipeRegistry;
    private final MaterialRegistry materialRegistry;
    private final RefiningConfig config;
    private final SkillProvider skillProvider;
    private final List<RefiningRequestEvent> pendingRequests = new ArrayList<>();

    public RefiningRequestHandler(EventBus eventBus, RefiningRecipeRegistry recipeRegistry,
                                  MaterialRegistry materialRegistry, RefiningConfig config,
                                  SkillProvider skillProvider) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.recipeRegistry = recipeRegistry;
        this.materialRegistry = materialRegistry;
        this.config = config;
        this.skillProvider = skillProvider;

        eventBus.subscribe(RefiningRequestEvent.class, pendingRequests::add);
    }

    @Override
    public void update(float deltaTime) {
        for (RefiningRequestEvent event : pendingRequests) {
            processRequest(event);
        }
        pendingRequests.clear();
    }

    private void processRequest(RefiningRequestEvent event) {
        // 1. Check for refinery component
        RefineryComponent refinery = event.entity.getComponent(RefineryComponent.class);
        if (refinery == null) {
            eventBus.publish(new RefiningFailedEvent(event.entity, RefiningFailureReason.NO_REFINERY));
            return;
        }

        // 2. Look up recipe
        RefiningRecipe recipe = recipeRegistry.getRecipe(event.recipeId);
        if (recipe == null) {
            eventBus.publish(new RefiningFailedEvent(event.entity, RefiningFailureReason.RECIPE_NOT_FOUND));
            return;
        }

        // 3. Check tier
        if (!refinery.canProcessRecipe(recipe)) {
            eventBus.publish(new RefiningFailedEvent(event.entity, RefiningFailureReason.TIER_TOO_LOW));
            return;
        }

        // 4. Get material storage
        MaterialStorageComponent storage = event.entity.getComponent(MaterialStorageComponent.class);

        // 5. Loop batchCount times
        int jobsQueued = 0;
        for (int i = 0; i < event.batchCount; i++) {
            // 5a. Queue full check
            if (refinery.isQueueFull()) {
                eventBus.publish(new RefiningFailedEvent(event.entity, RefiningFailureReason.QUEUE_FULL));
                break;
            }

            // 5b. Material availability check
            boolean hasAll = true;
            for (RecipeInput input : recipe.inputs) {
                if (storage == null || !storage.hasEnough(input.materialId, input.quantity)) {
                    hasAll = false;
                    break;
                }
            }
            if (!hasAll) {
                eventBus.publish(new RefiningFailedEvent(event.entity, RefiningFailureReason.INSUFFICIENT_MATERIALS));
                break;
            }

            // 5c. Consume inputs
            Map<String, Integer> consumed = new HashMap<>();
            for (RecipeInput input : recipe.inputs) {
                storage.tryConsume(input.materialId, input.quantity);
                consumed.put(input.materialId, input.quantity);
            }

            // 5d. Calculate yield with skill bonus
            int skillLevel = skillProvider.getSkillLevel(event.entity, config.yieldSkillName);

            // 5e. Build output list with adjusted quantities
            List<RefiningJob.Output> outputs = new ArrayList<>();
            for (RecipeOutput output : recipe.outputs) {
                int adjustedQty = config.calculateYield(output.baseQuantity, skillLevel);
                outputs.add(new RefiningJob.Output(output.materialId, adjustedQty));
            }

            // 5f. Create job with adjusted time
            float totalTime = recipe.processingTime / refinery.getSpeedMultiplier();
            RefiningJob job = new RefiningJob(recipe.recipeId, consumed, outputs, totalTime);

            // 5g. If refinery has no active job, set this one to ACTIVE
            if (refinery.getActiveJob() == null) {
                job.setState(RefiningJobState.ACTIVE);
            }

            // 5h. Add job to refinery queue
            refinery.addJob(job);

            // 5i. Fire started event
            eventBus.publish(new RefiningStartedEvent(event.entity, job));
            jobsQueued++;
        }

        // 6. If any jobs were queued, fire queue changed event
        if (jobsQueued > 0) {
            eventBus.publish(new RefiningQueueChangedEvent(event.entity, refinery.getJobQueue()));
        }
    }
}
