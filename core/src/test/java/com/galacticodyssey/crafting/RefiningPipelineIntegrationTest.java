package com.galacticodyssey.crafting;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.crafting.data.RefiningRecipeRegistry;
import com.galacticodyssey.crafting.events.RefiningCompletedEvent;
import com.galacticodyssey.crafting.events.RefiningRequestEvent;
import com.galacticodyssey.crafting.events.RefiningStartedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.systems.DefaultSkillProvider;
import com.galacticodyssey.crafting.systems.RefiningRequestHandler;
import com.galacticodyssey.crafting.systems.RefiningSystem;
import com.galacticodyssey.core.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningPipelineIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private MaterialRegistry materialRegistry;
    private RefiningRecipeRegistry recipeRegistry;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();

        materialRegistry = new MaterialRegistry();
        materialRegistry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", "iron_ore"));
        materialRegistry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("iron_ingot", "Iron Ingot",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.0f, 0.5f, 50, "", null));

        recipeRegistry = new RefiningRecipeRegistry();
        recipeRegistry.register(new RefiningRecipe("process_iron_ore", "Process Iron Ore",
            RecipeCategory.PROCESSING, 1,
            List.of(new RecipeInput("iron_ore", 5)),
            List.of(new RecipeOutput("iron_concentrate", 3)),
            10.0f, 10f));
        recipeRegistry.register(new RefiningRecipe("refine_iron", "Refine Iron",
            RecipeCategory.REFINEMENT, 2,
            List.of(new RecipeInput("iron_concentrate", 4)),
            List.of(new RecipeOutput("iron_ingot", 2)),
            10.0f, 20f));

        RefiningConfig config = new RefiningConfig(0.005f, "engineering");

        engine.addSystem(new RefiningRequestHandler(eventBus, recipeRegistry,
            materialRegistry, config, new DefaultSkillProvider()));
        engine.addSystem(new RefiningSystem(eventBus));
    }

    @Test
    void fullPipeline_rawOreToRefinedIngot() {
        Entity entity = new Entity();
        entity.add(new RefineryComponent(2, 4, 1.0f, 10f));
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        storage.tryAdd("iron_ore", 10);
        entity.add(storage);
        engine.addEntity(entity);

        List<RefiningCompletedEvent> completions = new ArrayList<>();
        eventBus.subscribe(RefiningCompletedEvent.class, completions::add);

        // Step 1: Request processing of iron ore
        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f); // handler processes request

        assertEquals(5, storage.getQuantity("iron_ore")); // 5 consumed
        assertEquals(1, entity.getComponent(RefineryComponent.class).getJobQueue().size());

        // Step 2: Tick to completion (10 seconds at 1x speed)
        for (int i = 0; i < 10; i++) {
            engine.update(1.0f);
        }

        assertEquals(1, completions.size());
        assertEquals(3, storage.getQuantity("iron_concentrate"));

        // Step 3: Now refine the concentrate into ingots
        // Need 4 concentrate but only have 3 — process more ore first
        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        for (int i = 0; i < 10; i++) {
            engine.update(1.0f);
        }

        assertEquals(2, completions.size());
        assertEquals(6, storage.getQuantity("iron_concentrate")); // 3 + 3

        // Step 4: Refine iron concentrate → iron ingot
        eventBus.publish(new RefiningRequestEvent(entity, "refine_iron"));
        engine.update(0.016f);

        assertEquals(2, storage.getQuantity("iron_concentrate")); // 6 - 4 consumed

        for (int i = 0; i < 10; i++) {
            engine.update(1.0f);
        }

        assertEquals(3, completions.size());
        assertEquals(2, storage.getQuantity("iron_ingot"));
    }

    @Test
    void eventSequence_correctOrder() {
        Entity entity = new Entity();
        entity.add(new RefineryComponent(1, 4, 1.0f, 10f));
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        storage.tryAdd("iron_ore", 5);
        entity.add(storage);
        engine.addEntity(entity);

        List<String> eventLog = new ArrayList<>();
        eventBus.subscribe(RefiningStartedEvent.class, e -> eventLog.add("STARTED"));
        eventBus.subscribe(RefiningQueueChangedEvent.class, e -> eventLog.add("QUEUE_CHANGED"));
        eventBus.subscribe(RefiningCompletedEvent.class, e -> eventLog.add("COMPLETED"));

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertEquals(List.of("STARTED", "QUEUE_CHANGED"), eventLog);

        for (int i = 0; i < 10; i++) {
            engine.update(1.0f);
        }

        assertEquals(List.of("STARTED", "QUEUE_CHANGED", "COMPLETED", "QUEUE_CHANGED"), eventLog);
    }

    @Test
    void snapshotRoundTrip_preservesRefiningProgress() {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(1, 4, 1.0f, 10f);
        entity.add(refinery);
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        storage.tryAdd("iron_ore", 5);
        entity.add(storage);
        engine.addEntity(entity);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        // Advance to ~50%
        for (int i = 0; i < 5; i++) {
            engine.update(1.0f);
        }

        RefiningJob midJob = refinery.getActiveJob();
        assertTrue(midJob.getProgress() > 0.4f && midJob.getProgress() < 0.6f);

        // Snapshot and restore
        var refinerySnap = refinery.takeSnapshot();
        var storageSnap = storage.takeSnapshot();

        RefineryComponent restoredRefinery = new RefineryComponent(1, 4, 1.0f, 10f);
        restoredRefinery.restoreFromSnapshot(refinerySnap);

        MaterialStorageComponent restoredStorage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        restoredStorage.restoreFromSnapshot(storageSnap);

        RefiningJob restoredJob = restoredRefinery.getActiveJob();
        assertNotNull(restoredJob);
        assertEquals(midJob.getProgress(), restoredJob.getProgress(), 0.01f);
        assertEquals(midJob.getRecipeId(), restoredJob.getRecipeId());
    }
}
